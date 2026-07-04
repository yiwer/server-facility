# ADR-0017: 幂等完整语义(同 key 返首次响应)+ Filter 响应捕获 + PROCESSING/DONE 状态机

- **状态**:Accepted(2026-07-04)
- **源起**:幂等+分布式锁+HTTP client 实现计划(docs/superpowers/plans/2026-07-04-idempotency-lock-http.md),
  簇 E(Task E1 `IdempotencyStore` SPI + `InMemoryIdempotencyStore`,已提交;Task E2 web 集成 `@Idempotent` +
  拦截器 + Filter,已提交;Task E3 装配 + properties + 端到端,本任务补记三者共同的决策依据)

## 背景

server-facility 此前无幂等组件。计划 §簇E 为服务端组件库新增 HTTP 层幂等能力,用于防止客户端重试、
网络抖动、用户重复点击等场景导致同一操作(如支付下单)被重复处理。业界对"幂等 key 重复提交"存在两种
典型语义:

1. **防重复提交(仅拒绝)**:检测到同 key 重复请求直接返回 409/423 等拒绝状态码,不返回首次处理结果——
   实现简单(只需记一个"处理中/已处理"布尔标记),但调用方拿不到首次结果,仍需自行查询业务状态才能
   知道原始请求是否成功。
2. **完整幂等(返回首次响应)**:检测到同 key 重复请求时,将首次处理的响应(状态码 + Content-Type +
   body)原样返回,调用方视角与"只发送了一次请求"没有区别——这是 Stripe、GitHub 等主流 API
   `Idempotency-Key` 头的标准做法。

用户明确要求完整幂等语义(而非仅防重式 409),这意味着 `IdempotencyStore` 必须持久化首次响应的完整
字节内容,而不仅仅是一个"已处理"标记,装配/拦截层也必须解决"响应体在处理链结束后还能否被读到"这一
Servlet API 本身不提供的能力(`HttpServletResponse` 的输出流默认只能写、不能回读)。

## 决策

1. **完整幂等,非仅防重 409**:`IdempotencyRecord`(E1)携带 `statusCode`/`contentType`/`body` 三个
   响应字段,`DONE` 状态记录首次处理的完整响应;`IdempotencyInterceptor`(E2)在 `preHandle` 命中
   `DONE` 记录时直接 `writeCached` 写回该响应,不重新调用 handler。`PROCESSING`(并发中,尚无完整
   响应可用)与"新占位竞态落败"两种情况仍返回 409——完整幂等只承诺"处理完成后的重复请求"能拿到首次
   结果,不承诺"并发中的重复请求"会阻塞等待首次请求完成(见决策 3 的失败重试语义)。

2. **PROCESSING/DONE 两态状态机 + Filter/拦截器职责分工**:
   - `IdempotencyStore.tryBegin` 原子写入 `PROCESSING` 占位记录(E1 引用相等技法,见
     `InMemoryIdempotencyStore` javadoc),`complete` 写入终态 `DONE` 记录。
   - `IdempotencyFilter`(E2)是纯基础设施:`doFilterInternal` 把 `response` 包装为
     `ContentCachingResponseWrapper` 再传给链条,`finally` 块调用 `copyBodyToResponse()` 把缓冲内容
     拷回真实响应——它不做任何幂等判定,只负责让响应字节在处理链结束后仍可读。
   - `IdempotencyInterceptor`(E2)是纯判定逻辑:`preHandle` 读 key、查/写 `IdempotencyStore`,决定
     放行/写缓存/409/400;`afterCompletion` 在 handler 正常完成后从 `ContentCachingResponseWrapper`
     读出完整字节,写入 `DONE` 记录。
   - 两者必须协作的原因:`ContentCachingResponseWrapper.getContentAsByteArray()` 只有在
     `copyBodyToResponse()` 执行前才能读到缓冲内容,而 `HandlerInterceptor#afterCompletion` 恰好在
     `DispatcherServlet` 完成视图渲染之后、`Filter` 的 `finally` 执行之前触发——这是 Servlet Filter
     链 + Spring MVC `HandlerInterceptor` 生命周期的时序保证,而非巧合:Filter 包一层"外壳",内层的
     `DispatcherServlet`(含其间 `HandlerInterceptor` 全部生命周期方法)必须先完整跑完,才会把控制权
     归还给 Filter 的 `finally`。装配层(本任务)据此把两者拆成独立 bean(`FilterRegistrationBean` +
     `WebMvcConfigurer` 注册的拦截器),不合并成一个类,职责边界与可测试性(E2 已分别单测两者:
     `IdempotencyFilterTest` 只验证包装/拷贝,`IdempotencyInterceptorTest` 只验证状态机)保持清晰。

3. **`afterCompletion` 异常不缓存 = 允许重试的语义**:handler 抛出异常时,`afterCompletion` 直接
   返回,不调用 `store.complete`——`PROCESSING` 占位记录原样保留,待其 TTL 到期后自然允许同 key
   重新尝试(`IdempotencyInterceptorTest#afterCompletion_withException_doesNotCache` 已验证)。备选
   方案是把异常也编码为一种"终态"缓存下来(如 500 + 错误体),但这会把一次偶发失败(如下游超时)永久
   固化为该 key 的"首次响应",此后所有重试都会立即拿到同一个失败结果,直到 TTL 到期才能重新尝试——
   这比"占位悬挂到 TTL 到期、期间 409"更违反直觉,故不采纳(见备选)。代价:TTL 到期前,原本因异常
   失败的 key 会一直落在 `PROCESSING` 态(重复请求收到 409),调用方需要等待或换一个 key 重试;这是
   完整幂等语义下"防止部分执行的操作被观测到两次"与"允许失败后立即重试"两个目标间的权衡,本 ADR
   选择前者。

4. **缺失/空白 key 请求头 → HTTP 400**:`@Idempotent` 标注的方法要求调用方显式提供幂等 key
   (`ann.headerName()`,默认 `Idempotency-Key`),这是该方法的一个必要契约条件——类比必填请求参数
   缺失的语义,`400 Bad Request` 比"放行(视为不幂等执行)"或 500 更准确:放行会悄悄丢失幂等保护
   (消费方可能忘了在客户端设置该头,而服务端在毫无提示的情况下从不去重,直到线上出现重复扣款才被
   发现);500 又不是"客户端输入缺陷"该有的状态码——`400` 精确表达"这是调用方请求本身的缺陷,而非
   服务端故障"。

5. **通用 `idempotency` / web 集成 `web.idempotency` 分离,避免与限流簇同款的包环**:
   `IdempotencyStore`/`IdempotencyRecord`(E1)零 web 依赖,可在非 web 场景(如内部 RPC 去重)直接
   复用;`@Idempotent`/`IdempotencyInterceptor`/`IdempotencyFilter`(E2)依赖 servlet + spring-webmvc
   类型,单独放入 `web.idempotency`。依赖方向单一:`web.idempotency → idempotency`,反向从不发生。
   这是 ADR-0014 决策 5(限流 `ratelimit`/`web.ratelimit` 拆分)记录的同一条 ArchUnit
   `packages_are_cycle_free` 约束下的必然选择——若幂等从一开始就不拆分,装配层同时需要"通用 store
   bean(无 web 条件)"与"web 专属拦截器/Filter bean(有 web 条件)"两种粒度,一旦将来任何 web 专属
   类型反向被通用包引用(即使只是类型声明),就会在 `idempotency`/`web` 两个顶层 slice 间成环;
   E1/E2 从一开始就按此分离设计(而非事后修复),本任务(E3)的装配层
   `FacilityIdempotencyAutoConfiguration` 正是同时消费两个包各自的类型,验证了这一分离在实际装配
   场景下的正确性(`packages_are_cycle_free` ArchUnit 规则保持绿)。

6. **装配层三粒度 bean,`IdempotencyStore` 无 web 条件**:`FacilityIdempotencyAutoConfiguration` 的
   `facilityIdempotencyStore`(`@ConditionalOnMissingBean(IdempotencyStore.class)`)不带
   `@ConditionalOnWebApplication`——与 `DistributedLock`(ADR-0016)、`RateLimiter`(ADR-0014)一致,
   纯通用能力,非 web 场景也可直接注入使用;`IdempotencyInterceptor`(`@ConditionalOnMissingBean` +
   `@ConditionalOnWebApplication(SERVLET)`)与 `FilterRegistrationBean<IdempotencyFilter>`
   (`@ConditionalOnWebApplication(SERVLET)`,`setOrder(Ordered.HIGHEST_PRECEDENCE)` 确保它在处理链
   最外层生效,`addUrlPatterns("/*")` 覆盖全部路径)仅在 servlet 栈 web 应用中装配;
   `WebMvcConfigurer`(`@ConditionalOnMissingBean(name = "facilityIdempotencyWebMvcConfigurer")` +
   `@ConditionalOnBean(IdempotencyInterceptor.class)`)把拦截器注册进 MVC——与限流簇(ADR-0014)、
   `FacilityWebAutoConfiguration` 已有的 `facilityWebMvcConfigurer`/`facilityCorsWebMvcConfigurer`
   完全同款写法,消费方可以声明同名 bean 整体覆盖注册逻辑,或声明自己的 `IdempotencyStore`/
   `IdempotencyInterceptor` bean 分别覆盖对应粒度。

7. **端到端验证结论(standalone MockMvc)**:`IdempotencyEndToEndTest` 用
   `MockMvcBuilders.standaloneSetup(controller).addFilters(new IdempotencyFilter())
   .addInterceptors(new IdempotencyInterceptor(...)).build()` 验证完整链路——同一
   `Idempotency-Key` 连续两次 `POST`,第二次响应体与第一次逐字节相同,且 controller 方法体只执行
   一次(`AtomicInteger` 计数器验证 `execCount == 1`);缺失 key 头返回 400,controller 不执行;不同
   key 各自独立执行(`execCount == 2`)。**实证结论**:MockMvc 的 `MockFilterChain` 把 `addFilters`
   注册的过滤器排在最外层、承载 `addInterceptors` 注册的拦截器的 `TestDispatcherServlet` 作为链条
   终点,这与生产环境 Servlet 容器"Filter 包 Servlet"的真实调用顺序一致;`preHandle` 写缓存/放行
   短路返回 `false` 时,`HandlerExecutionChain.applyPreHandle` 只对已成功执行 `preHandle` 的
   在先拦截器触发 `afterCompletion`(本例中我们唯一的拦截器自身短路,不会重复触发自身
   `afterCompletion`),不会与 `writeCached` 已写入的缓存字节冲突;`afterCompletion` 正常完成路径
   读到的字节,也确认发生在 `DispatcherServlet` 视图渲染完成之后、`IdempotencyFilter` 的
   `copyBodyToResponse()` 之前。三条断言(相同响应体、controller 只执行一次、缺 key 400、不同 key
   各自执行)全部一次性通过,无需为让测试通过而放宽任何断言,因此该测试是对生产装配行为的忠实模拟,
   而非仅验证接口契约的伪端到端测试。

## 备选(否决)

- **仅防重复提交(命中即 409,不返回首次响应)**:实现更简单(`IdempotencyRecord` 不需要携带响应
  字节),但不满足用户明确要求的"完整幂等"语义,且调用方仍需自行查询业务状态才能知道首次请求的
  处理结果,体验不如 Stripe/GitHub 式 `Idempotency-Key`,否决。
- **`afterCompletion` 把异常也编码为终态缓存**:见决策 3,会把偶发失败永久固化为该 key 的"首次
  响应",此后所有重试立即拿到同一失败结果直至 TTL 到期,比"占位悬挂到期"更违反直觉,否决。
- **缺失 key 时放行(视为不幂等执行)**:会让消费方在毫无提示的情况下从不获得幂等保护(例如客户端
  遗漏设置请求头),用 400 显式暴露这一契约缺陷,便于尽早在联调阶段发现而非线上事故后排查,否决。
- **Filter 与拦截器合并成一个类**:`ContentCachingResponseWrapper` 包装(基础设施职责)与幂等判定
  逻辑(业务职责)合并会让职责边界模糊,且无法像 E2 那样分别对两者做聚焦的单元测试,否决。
- **`IdempotencyInterceptor`/`IdempotencyFilter` 直接留在通用 `idempotency` 包,不拆分
  `web.idempotency`**:与 ADR-0014 决策 5 否决同款方案的理由一致——会导致
  `packages_are_cycle_free` 在 `idempotency`/`web` 两个顶层 slice 间成环,否决。

## 后果

- 消费方只需在 classpath 引入本库并保持默认装配开关(`facility.idempotency.enabled=true`,默认值),
  即可让任意 `@Idempotent` 标注的 Controller 方法获得完整幂等保护;需要跨实例共享幂等状态时,替换
  `IdempotencyStore` bean(如接入 Redis)即可,`@Idempotent`/`IdempotencyInterceptor`/
  `IdempotencyFilter` 调用点不受影响。
- 完整幂等的代价是必须持久化响应字节(`IdempotencyRecord.body`)——相比"仅防重"方案,内存/存储
  占用更高;真正生产多实例场景替换为 Redis 等外部存储时,响应体大小需要纳入容量规划
  (`IdempotencyRecord` 为纯数据 record,可直接序列化,已在 E1 package-info 说明)。
- `afterCompletion` 异常不缓存的重试语义(决策 3)是本 ADR 重点披露的行为边界:失败请求的占位记录
  会悬挂到 TTL 到期,期间同 key 重试一律 409,而不是立即允许重试——消费方需要根据业务场景选择合适
  的 `defaultTtl`(过长会拖慢失败后的重试节奏,过短会削弱幂等保护窗口),这一权衡已在
  `FacilityIdempotencyProperties.defaultTtl` javadoc 与本 ADR 双重记录。
- `packages_are_cycle_free` ArchUnit 规则对 `idempotency`/`web.idempotency`/`autoconfigure` 三包
  保持绿,`web.idempotency → idempotency` 单向依赖,未引入新的包间环。
- **Carry-forward**:README/USAGE/DESIGN 文档三件套的幂等特性矩阵、装配开关表
  (`facility.idempotency.*`)、ADR 索引更新留给计划收尾阶段统一处理(与 ADR-0015/0016/0018 的
  Carry-forward 处理方式一致),本任务(E3)未涉及。
