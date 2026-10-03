# Web、装配与 agent 开发体验：研究和改造建议

研究日期：2026-10-03。审计基线：`0ee9d547022371ad31f885605e999de17ec22777`。本子任务阅读源码、测试、README、DESIGN 与相关 ADR，并查证一手资料；**未修改应用源码或既有 ADR**。主任务随后在 JDK 25.0.4.1 / Maven 3.9.16 对原 POM（仍以 release 21 编译）完成 verify，1196 项测试通过；这证明旧基线可在该 JDK 运行，不等于框架升级已完成。本子任务复用生成的 classpath，在 `target/research/WebProbes.java` 增加临时消费者探针并实际执行，结果见 §6。探针不进入主源码/测试树。

本文范围为 `autoconfigure`、`context`、`locale`、`log`、`masking`、全部 `web.*` 子包，以及这些能力如何服务 agent。底层限流、锁、幂等 store、JSON、异步实现的完整审计另见同批研究。路径行号均对应本轮读取的源码。

## 1. 判断：先把隐含契约收回来，再把它作为脚手架的基础

当前仓库是一个带 11 组自动装配的设施 jar，并不是拥有应用入口、持久化、认证、部署脚本的可运行 Spring Boot 应用。它已经具备值得保留的工程纪律：错误值与展示层分开、重依赖 optional、装配层依赖单向、真实替换点存在 SPI、边界测试较多、ADR 记录反例。问题集中在另一处：少量静态入口把 ApplicationContext 生命周期、请求线程、响应格式、基础设施缺席时的行为藏到了调用者看不见的地方。

**建议的改造目标是“可组合的设施库 + 一个持续验证的应用样板”。** 设施库保留真正节省复杂度的 Module；样板提供一条确定的 golden path。不要让每个 agent 第一次使用时重新推导 optional 依赖、全局静态状态、filter 次序和四套错误响应的关系。这里 Module 的 Interface 不只是几个 public 方法，还包括失败模式、上下文归属、调用时序、容量上限和必需配置；如果这些知识仍由调用者背负，静态方法少并不等于 deep module。

优先级建议：

1. **P0：修正可导致数据/身份混淆的语义**——幂等 key 的作用域与请求一致性、全局响应缓冲、异步请求清理、静态上下文销毁归属。
2. **P1：统一 Web 契约**——安全的 RFC 9457 错误响应、受信代理处理、明确认证集成、基于标准 tracing 的可观测性、可组合且有资源上限的上传策略。
3. **P2：减少设施自身的框架化成本**——收缩静态 service locator、日志第二套管线、无约束排序字符串和抢占应用 MessageSource 的默认行为。
4. **P2：交付样板验收场景**——用真实应用和真实 Servlet 容器证明组合语义，再扩展 generator；不以新增工具方法或提高测试计数作为改造终点。

## 2. 必须先验证或修复的具体问题

### 2.1 幂等重放当前没有调用者、端点和请求内容的作用域

**源码事实。** [IdempotencyInterceptor.java:76](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/idempotency/IdempotencyInterceptor.java:76) 直接把请求头作为 `store.find/tryBegin/complete` 的 key；没有 HTTP method、route、principal/tenant、请求摘要，也没有 key 长度约束。命中 DONE 后在 controller 执行前重放。即使完全没有认证，只要 `/create-a` 与 `/create-b` 使用同一个 store 和相同 key，第二个端点就会得到第一个端点的结果；宿主一旦引入多用户认证，该缺口还可能演变为跨用户响应混淆。本文没有假设仓库已实现认证。

**成熟实现的启示。** Stripe 会比较同 key 的请求参数，并拒绝不一致的重试；其公开规则明确缓存首次结果，包括 500；key 有长度限制。它不是“只要一个 UUID header 就完整幂等”的背书。[Stripe Idempotent requests](https://docs.stripe.com/api/idempotent_requests)

**建议。** 在 Web Interface 明确定义 `scope + operation + clientKey + requestFingerprint`。scope 由可信宿主身份提供；没有身份时显式采用匿名 scope，不能从不可信任意请求头推断租户。operation 使用稳定业务操作 ID 或匹配后的 route 加 method；指纹明确是原始 payload 字节还是规范化命令，不能默认 JSON 字段顺序没有意义。没有可用 scope 的敏感业务应拒绝启用，而不是悄悄变成进程级共享 key。

**验收。** 同 key、同 scope、同操作、同 payload 仅执行一次且重放一致；同 key 改 payload 明确失败；不同 scope/操作不串响应；空白、过长 key 明确拒绝；先鉴权授权再允许读取重放；未授权请求不能探测其他调用者的幂等状态。业务写入与幂等记录持久化之间的崩溃窗口必须在 Interface 中写明——仅将 store 换成 Redis 不证明业务操作 exactly-once。

### 2.2 “只给注解方法做幂等”与“全部响应都进入缓存”矛盾

**源码事实。** [FacilityIdempotencyAutoConfiguration.java:55](E:/GenCode/server-facility/src/main/java/cn/code91/facility/autoconfigure/FacilityIdempotencyAutoConfiguration.java:55) 默认将 Filter 注册到 `/*`；[IdempotencyFilter.java:34](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/idempotency/IdempotencyFilter.java:34) 对每个请求都创建 `ContentCachingResponseWrapper`，直到 filter 的 finally 才 `copyBodyToResponse()`。其作用范围不取决于 `@Idempotent`。因此普通下载也被整段缓冲，流式输出无法维持原先 flush 语义。当前没有响应体大小上限。

**建议。** 幂等能力显式选择有界、同步、有限响应的命令端点。将捕获策略收敛到已选择操作，或者采用业务结果重放，将“写响应”与“保存业务结果”分离。不要用全局无界缓冲为少数注解方法支付成本。SSE、`StreamingResponseBody`、大文件和异步端点暂不支持时，应该在使用/启动校验中报清楚，不要默默挂到同一链。

**验收。** 未启用幂等的 100 MiB 下载不出现等量额外响应缓存；SSE 首事件按预期及时到达；标注端点超出重放上限有明确且不会重新执行业务的结果；相邻 filter 多层 response wrapper 不导致缓存丢失；真实容器下验证异步 dispatch。

### 2.3 “handler 异常不缓存”并不由 afterCompletion 的 ex 参数保证

**源码事实。** [IdempotencyInterceptor.java:108](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/idempotency/IdempotencyInterceptor.java:108) 以 `ex != null` 判断失败；否则记录响应。Spring 明确说明 `afterCompletion` 的 `ex` **不包含已经由 exception resolver 处理的异常**。所以 controller 抛出异常但被当前全局 advice 处理后，该响应仍会被缓存，这与 ADR-0017 第 3 条的泛化陈述不一致。[HandlerInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/HandlerInterceptor.html)

**测试证据。** [IdempotencyInterceptorTest.java:209](E:/GenCode/server-facility/src/test/java/cn/code91/facility/web/idempotency/IdempotencyInterceptorTest.java:209) 手工向 `afterCompletion` 传异常；[IdempotencyEndToEndTest.java:50](E:/GenCode/server-facility/src/test/java/cn/code91/facility/web/idempotency/IdempotencyEndToEndTest.java:50) 的 standalone MockMvc 只装一个 filter、一个 interceptor 和正常 controller，没有 advice、Security、异步 dispatch 或真实容器。测试能证明简单同步路径，不能外推全部生产装配路径。

**建议。** 用新 ADR 明确：在业务执行之前的参数失败是否允许改正后重试；业务开始后的业务失败、内部失败、响应写出失败分别保存哪一种终态；不允许把“异常有没有逃出 MVC”当业务是否成功。恢复/释放占位还需要与底层 owner token、过期竞态一起设计。不要自动在失败时建议客户端换 key 重试支付类操作。

**验收。** controller 抛异常 + advice 解析、校验失败、显式 4xx/5xx ResponseEntity、连接中断、store 完成失败分别有外部可见契约；201 重放保留必要的 `Location` 等允许重放的头。当前 [writeCached:125](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/idempotency/IdempotencyInterceptor.java:125) 只恢复 status/content-type/body，“首次响应原样”应限定或补齐，不能重放 Set-Cookie 等与当前会话不相容的头。

### 2.4 会话 ThreadLocal 的兜底清理只覆盖一部分 Servlet 生命周期

**源码事实。** [SessionUserHolder.java:33](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/session/SessionUserHolder.java:33) 使用 `ThreadLocal<Object>`；[SessionUserClearInterceptor.java:20](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/interceptor/SessionUserClearInterceptor.java:20) 仅在 `afterCompletion` remove。Spring MVC 开始异步处理后，初始线程退出时不会调用 `afterCompletion`，而会调用 `AsyncHandlerInterceptor.afterConcurrentHandlingStarted`；部分超时/网络错误还没有后续 dispatch。[AsyncHandlerInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/AsyncHandlerInterceptor.html)

**建议。** 如果只是请求数据，优先显式参数或 request attribute，避免第二份身份上下文。宿主选择 Spring Security 时，认证身份统一取 Principal/Authentication，利用其 filter 生命周期；不再把 `setUser(Object)` 等同“完成登录”。Spring Security 的 `FilterChainProxy` 负责清理其 SecurityContext，并提供 MVC principal 解析。[Spring Security authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)

过渡期保留 holder 时，至少用覆盖目标 dispatch 的 filter `try/finally` 清理当前线程；要传播到异步任务，必须捕获明确的身份值并在目标线程 finally 恢复/清理，不能用 InheritableThreadLocal 作为线程池补丁。若继续用 interceptor，还要实现 async 初始线程清理，并明确它无法覆盖 filter 提前失败和未进入 MVC 的请求。

**验收。** 平台线程池复用下，A 用户同步/异步请求后接匿名 B 请求，B 始终读不到 A；handler 前短路、filter 异常、Callable/DeferredResult、超时都覆盖；虚拟线程开关不能作为跳过这些测试的理由。

### 2.5 静态上下文的销毁没有所有权

**源码事实。** [SpringContextHolder.java:219](E:/GenCode/server-facility/src/main/java/cn/code91/facility/context/SpringContextHolder.java:219) 只允许第一个 context CAS 成功，后续 holder 不记归属；[destroy:238](E:/GenCode/server-facility/src/main/java/cn/code91/facility/context/SpringContextHolder.java:238) 却无条件 `getAndSet(null)`。同 JVM 中 A 先注册、B 注入被忽略，随后销毁 B 的 holder，会清空 A 的引用。`AtomicReference` 解决并发发布，不解决对象生命周期归属。

**建议。** 先用“只有发布该 context 的实例才能 CAS 清理它”堵住错误销毁，再把有配置/有生命周期能力改为构造器注入。静态门面只保留无状态纯函数或独立且明确的默认实例；需要 Spring bean 的门面进入弃用迁移层。Spring 推荐构造器表达必需依赖，便于保持对象完整与不可变。[Spring dependency injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

**验收。** 两 context 并存与任意关闭顺序、父子 context、并行测试、上下文刷新后旧引用均受验证；不得靠每个测试手工 reset 来证明生产生命周期正确。更长期的目标是消费实例只持有自身 context 提供的依赖，互不影响。

## 3. 各模块现状、保留点和改造目标

### 3.1 autoconfigure：保留 Boot 原生机制，补齐真实组合测试

现有 `AutoConfiguration.imports`、独立命名空间、核心 bean 让位、Async 明确让 Boot 先装配都是正确方向。Boot 官方支持自动装配与 starter 分离，推荐 class 条件和 missing bean 条件；也特别要求缺失依赖可能影响方法加载时隔离配置类，并用 `ApplicationContextRunner`/`FilteredClassLoader` 验证组合。[Boot custom auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)

| 装配 | 当前功能与场景 | 判断与目标 |
|---|---|---|
| Core | holder、日志后处理器、facilityMessageSource | 库基础功能注入。保留 facility 文案来源；移除对所有消费者强制启用静态上下文/第二套日志 pipeline 的要求。证据：`FacilityCoreAutoConfiguration:25-45`。 |
| Id | 默认 SnowIdGenerator，可让位 | 多实例身份配置属于有状态能力；让位机制保留，参数正确性和部署身份碰撞由底层研究处理。证据：`FacilityIdAutoConfiguration:16-19`。 |
| Json | Boot ObjectMapper 加入静态 registry | 复用宿主 mapper 的动机合理，生命周期仍被进程级 registry 扩大。目标为 context 内注入并配合 Jackson 主版本迁移。证据：`FacilityJsonAutoConfiguration:25-39`。 |
| Locale | 早于 Boot 抢注 primary messageSource | 聚合模块文案有用；不应使应用标准 `spring.messages.*` 意外失效，见下节。 |
| Async | Boot TaskExecutor 不存在时创建虚拟线程 Executor | 保留 `after=TaskExecutionAutoConfiguration`。验证自定义普通 Executor、TaskExecutor、有/无 Boot 默认三组状态，关闭时终止自有 executor；不要默认 JDK 25 自动解决上下文传播。证据：`FacilityAsyncAutoConfiguration:23-37`。 |
| Web | Trace、重复读、访问日志、CORS、异常 advice、会话清理 | 功能多而生命周期不同，应内部拆成有独立条件的配置类；默认开启捕获类功能须谨慎；不是必须拆成六个发布 jar。 |
| RateLimit | SPI 默认实现 + 方法级 interceptor | 保留通用/web 分离；修正可信 identity、匹配与顺序，见下文。 |
| Cache | Caffeine 或 ConcurrentMap | `:64-67` 检查 Caffeine 与 CaffeineCacheManager，但 `:78` fallback 仅检查 Caffeine 缺失；“Caffeine 存在且 support 缺失”不注册 CacheManager。类注释写回退 ConcurrentMap，而 ADR-0015 决策 5 承认无 bean 后由门面降级、同 ADR 其他段又称回退，属于文档间契约不一致。先决定预期行为，再覆盖两依赖的四种 classpath 组合。 |
| Lock | SPI 默认单机实现 | SPI 保留；默认名称和部署适用性由底层研究明确。不得把 bean 存在当成分布式正确性证据。 |
| Http | 默认 RestClient + timeout | [FacilityHttpAutoConfiguration:49](E:/GenCode/server-facility/src/main/java/cn/code91/facility/autoconfigure/FacilityHttpAutoConfiguration.java:49) 直接 `RestClient.builder()`，应优先使用宿主 Boot builder 的 customization/observation。 |
| Idempotency | store 与 Servlet 重放链 | 保留 store/web 分离；先落实 §2 的 scope、捕获与失败语义，再推广默认装配。 |

Web filter 的替换契约与其他 bean 不一致：[FacilityWebAutoConfiguration.java:42](E:/GenCode/server-facility/src/main/java/cn/code91/facility/autoconfigure/FacilityWebAutoConfiguration.java:42) 的 Trace/Repeatable/AccessLog 只有 property 条件，没有 missing bean；[Idempotency filter registration:55](E:/GenCode/server-facility/src/main/java/cn/code91/facility/autoconfigure/FacilityIdempotencyAutoConfiguration.java:55) 也无 missing bean，尽管类注释声称全部 bean 让位。README 已披露一部分差异，但 DESIGN §4 “每个 bean 都让位”仍过于绝对。建议统一“策略 bean 可替换、注册 bean 唯一、关闭注册有单独开关”的契约。

**验收矩阵。** 不只是 bean 存在：只 core 的非 Web 消费者、Servlet 无 MVC、MVC 默认、用户覆盖同类型/同名、关闭开关、依赖半缺、第三方 advice、标准 Boot tracing、可选 Security、两个 context、升级后的 Boot package/classpath。需要确认 filter 仅入链一次并列出真实顺序；当前 trace 与 idempotency 同为 `HIGHEST_PRECEDENCE`，不能把同阶注册顺序当 Interface。

### 3.2 context / locale：应用配置应归应用所有

`context` 的使用场景是非 Spring 管理代码访问 bean。它的 Result 错误通道和首个 context 警告可读，但也把“依赖不存在/类型冲突/初始化次序”从启动期移动到运行路径。本文建议以注入为默认，holder 只作为有期限的兼容入口；§2.5 是先行修复。

`locale` 把 error 包维持纯 JDK、在展示位置本地化，这是 ADR-0010 应保留的决定。[AggregatedMessageSource.java:25](E:/GenCode/server-facility/src/main/java/cn/code91/facility/locale/AggregatedMessageSource.java:25) 排序并复制 delegates，首命中规则清楚；资源 bundle 关闭系统 locale 回退也有利于可预测测试。

问题在默认主导权：[FacilityLocaleAutoConfiguration.java:25](E:/GenCode/server-facility/src/main/java/cn/code91/facility/autoconfigure/FacilityLocaleAutoConfiguration.java:25) 早于 Boot 注册全局 `messageSource`，因此应用通常的 `spring.messages.basename` 等设置不再走 Boot 原装配。Boot 本来提供这些配置及默认 bundle 的自动发现。[Boot internationalization](https://docs.spring.io/spring-boot/reference/features/internationalization.html)

**建议。** `FacilityMessages` 作为注入的 Module，优先查询应用 MessageSource，再查 facility 内置资源，最后用安全默认消息；不为了本库几个错误键接管应用唯一的 messageSource。仅当宿主主动需要多模块 source 聚合时，显式启用聚合方案。fallback 的 `MessageFormat` 应使用调用者指定 Locale，而不是 [LocaleUtil.java:191](E:/GenCode/server-facility/src/main/java/cn/code91/facility/locale/LocaleUtil.java:191) 的静态默认 Locale 格式化。普通 `translateMessage` 缺键会抛、缺 bean 返回 key 的不对称应明确，Web 错误渲染则必须始终有兜底。

**验收。** 原生 `spring.messages.*`、用户自定义 MessageSource、同名键优先级、中/英/其他 locale、参数中的数字/日期、缺键、模板错误、多 context 都有消费者测试。无需强制把 i18n 加入所有业务返回值。

### 3.3 web.exception / web.response：标准化真正的 HTTP 失败

`BusinessException/SystemException` 的共有载体、克隆 args 和面向 `ErrorTypeInterface` 的消息键值得保留；无需继续增加异常继承层次。问题是 [AbstractGlobalExceptionHandler.java:117](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/exception/AbstractGlobalExceptionHandler.java:117) 之后几十个分支同时维护自有响应与 ProblemDetail，默认 [FacilityWebExceptionProperties.java:26](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/exception/FacilityWebExceptionProperties.java:26) 保留 HTTP 200 错误包络。

最直接缺口是 [buildProblemDetail:519](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/exception/AbstractGlobalExceptionHandler.java:519) 直接把 `ex.getMessage()` 放到 detail；参数解析器、内部异常消息可能含类名、SQL/路径或敏感输入。它也丢弃了 handler 前面已计算的本地化安全消息。仅控制 `includeTraceProfiles` 并不能控制此通道。当前 [GlobalExceptionHandlerTest.java:743](E:/GenCode/server-facility/src/test/java/cn/code91/facility/web/exception/GlobalExceptionHandlerTest.java:743) 还把“detail 为异常消息”固化为测试。

**成熟方案。** RFC 9457 已取代 RFC 7807，规范要求错误细节避免暴露实现信息；Spring 提供 `ProblemDetail`、`ErrorResponse` 与 `ResponseEntityExceptionHandler`，可处理框架异常并支持额外字段。[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)；[Spring error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

**建议。** 下一主版本默认 RFC 9457 和真实 HTTP status。复用 `ResponseEntityExceptionHandler` 的框架异常映射与 headers，本库只负责稳定业务 `code`、安全 detail、本地化和 trace 关联。400/404/409/422 等按业务语义定义，不把所有 BusinessException 固定为 400；原始异常只进受控服务端诊断。旧 BaseResponse 作为迁移适配器，不让每个 handler 永远背双轨分支。`BaseResponse` 的成功包络是否保留属于 API 风格选择，RFC 9457 不要求成功响应也改成一种统一包装。

**其他具体缺口。** `MultipartException` 被全部映射为 413（并非每个 multipart 解析错误都是过大）；方法返回值校验失败也被统一归 400；405/415 自定义 build 路径没有保留框架的相关 response headers；`Retry-After` 毫秒转秒应向上取整，当前 `:366` 向下取整可能过早重试。`FacilityException` handler 的注解只枚举两个内置异常类型，扩展接口本身不意味着第三方实现自动进入此分支。

**验收。** 400/401/403/404/405/406/409/413/415/422/429/500/503 的实际 HTTP 状态、content type、允许字段、必要 headers；安全哨兵字符串不能出现在 5xx body；中文业务错误保留本地化；参数错误给结构化字段级 `errors` 而不是要求客户端拆分分号文本；filter 的 413、幂等 400/409、MVC advice、可选 Security entry point/denied handler 共享一种错误渲染策略。当前三者分别走私有 Jackson、`sendError` 和 advice，不能仅测试 advice 方法。

### 3.4 web.filter：只为需要重读的端点付出缓冲成本

重复读 wrapper 的限定字节数、防御性返回副本、排除路径是有价值的实现。[RepeatableRequestWrapper.java:64](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/filter/RepeatableRequestWrapper.java:64) 构造期完整读取，适合短 JSON webhook 验签或确需同一原始 payload 的处理链；不应因为引入通用设施库就对所有 JSON/XML/text 默认额外缓存。

**建议。** 默认关闭全局重复读，样板在 webhook/幂等 fingerprint 场景显式开启并有字节上限。Spring `ContentCachingRequestWrapper` 是“消费时缓存”，不等同重复读取 wrapper，不能做行为不等价的一行替换。当前 wrapper 的 `setReadListener` 空实现和 UTF-8 固定 reader 是同步窄场景限制，应显式记入 Interface；对不支持的非阻塞读取给明确失败或跳过。媒体类型用 MediaType 解析和兼容匹配，避免 `application/json-unknown` 被前缀误纳、`application/problem+json` 被漏纳。

TraceIdFilter 对入站值白名单的保护应保留；但它是自定义 correlation ID，不是完整分布式 tracing。[TraceIdFilter.java:65](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/filter/TraceIdFilter.java:65) 覆盖 MDC，finally 删除而不恢复已有值；命名为 `traceId` 会与 Micrometer 的同名 MDC 冲突。

**建议。** 有 Micrometer 时让其拥有 trace/span 语义；若产品需要回显请求编号，独立使用 `requestId` 并保存/恢复此前 MDC 值。Boot 已提供 tracing 自动装配、日志 correlation，并明确只有通过自动配置的 HTTP client builder 创建的客户端才自动传播 trace。[Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)

**验收。** 请求体上限、chunked 无 Content-Length、非 ASCII/畸形 trace、嵌套 MDC 作用域、ERROR/ASYNC dispatch、客户端断开、并行请求不串上下文。跨线程 propagation 要与 async 模块共同验证。

### 3.5 web.interceptor / web.ratelimit：把可观测性与身份信任区分开

AccessLogInterceptor 简洁、默认不记录 body/query、慢请求阈值容易使用，适合初始排障。可是只覆盖 MVC 生命周期；filter 提前拒绝看不到，async 再 dispatch 可能重写开始时间。[AccessLogInterceptor.java:45](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/interceptor/AccessLogInterceptor.java:45) 使用 wall clock 计算时长，建议换单调计时；生产指标使用 Boot HTTP observation，访问日志交给容器或明确异步生命周期的专用实现。不要把每个预期 404/客户端错误都默认升级 WARN，避免扫描器制造告警噪音。

[RequestUtil.java:75](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/util/RequestUtil.java:75) 信任任意 XFF/多种历史代理头，`isValidIp` 实际只检查非空和非 unknown；[RateLimitInterceptor.java:64](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/ratelimit/RateLimitInterceptor.java:64) 直接以此 IP 参与默认限流 key。文档警告是诚实的，但 agent 很容易直接采用默认方案，不能指望警告使不安全默认成立。Spring 官方要求在信任边界处理 forwarded headers，边界代理移除外部传入的不可信值。[Spring forwarded headers](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html)

**建议。** 默认使用容器认定的 remote address；代理部署在容器/边界统一配置可信代理，不再手工猜测多个历史 header。公开 API 的粗粒度连接/IP 防护放网关，业务配额按已验证 principal/tenant；方法注解限流保留为细粒度能力。当前类简单名+方法名还可能发生跨包同名/重载冲突，operation ID 应稳定且可见。幂等重试是否消耗配额需显式决定 interceptor 顺序，并分开“入口防滥用”和“业务操作配额”。

**验收。** 公网直连伪造 XFF 不能换桶；代理链只采纳可信边；route identity 不冲突；429 有向上取整的 Retry-After；Security 前后拦截顺序由样板测试验证。MVC interceptor 不能承担完整认证安全层，Spring 也建议采用 Security/filter chain。[HandlerInterceptor 安全建议](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/HandlerInterceptor.html)

### 3.6 web.session / web.util：减少“看起来像认证”的工具

SessionUtil 的读不创建 session、写才创建 session 是可保留语义。`SessionUserHolder.isLoggedIn()` 只判断 Object 是否存在，名称容易使 agent 推断出未提供的认证保证；建议弃用该名称，最终交给宿主 Principal。CookieUtil 简化重载默认 HttpOnly/Secure 是好的；应提供 SameSite 等完整属性的窄策略入口，删除时匹配原 Domain/Path，而不是只知道 Path。它不应自己演变成第二套登录框架。

RequestUtil/ResponseUtil 中 `getMethod`、`getHeader` 这类一行包装不增加 Module depth；`getBearer` 只是字符串提取，不完成 token 校验。建议只保留增加明确行为的能力，余下直接用 Servlet/Spring API。`ResponseUtil.writeJson` 提前设状态然后才序列化，调用者若忽略 Result 可能留下空/部分响应；应由统一错误 writer 或 MVC message converter 处理，减少第三套序列化路径。

XssUtil 已采用 jsoup Safelist，适合富文本 HTML 清洗，方向正确。[XssUtil.java:20](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/util/XssUtil.java:20) 只是输入 HTML sanitizer；jsoup 官方也将它定位为受允许标签/属性约束的 HTML 清洗。[jsoup sanitizer](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer) **建议更名为用途明确的 HTML sanitizer 或强化 Javadoc，不新增全局“所有字符串过 XSS filter”。** HTML、JS、URL、CSS 输出位置需要不同处理，OWASP 明确反对把全局 interceptor 当 XSS 总解。[OWASP XSS prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)

**验收。** Cookie 属性与删除范围一致；清洗 HTML 的协议/属性策略覆盖富文本真实用例；纯文本不被无意义改写；JSON 返回不被宣称天然消除下游 DOM XSS；没有 jsoup 时不会因 unrelated core 使用而触发类加载故障。

### 3.7 web.argument / web.response：组合 DTO，避免无约束通用查询语言

[PageQuery.java:46](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/argument/PageQuery.java:46) 有合理页数/大小上限，但依赖消费者实际执行 Bean Validation；当前设施 jar 不强制 provider，因此 setter 仍能形成非法状态。`orderBy` 示例为 `create_time desc`，这是**潜在 SQL 拼接诱因，不是仓库已有 SQL 注入的证据**——当前没有数据访问代码。

**建议。** 值类型 `PageRequest`/`SortKey` 在构造时持有不变量，查询 DTO 组合它；排序字段用 endpoint 允许集映射到持久化属性，避免原始 SQL 片段。已有 Spring Data 应用可直接采用其分页抽象，不必将 Spring Data 强塞进通用 core。PageBaseResponse 是已有 wire format 时保留的适配器；新样板用清晰响应 record。游标分页等另一种查询模式在实际场景出现后再加，不要将一个基类扩展成全能 query DSL。

**验收。** page=0、size=0/超限、极端偏移、未知排序键有一致 400；排序值无法穿透为 SQL；列表响应 schema 稳定；不把每个 DTO 都绑到继承层次。

### 3.8 web.upload / web.download：收敛资源、存储名和内容策略

SafeUpload 已做文件名清洗、危险扩展名拦截、可选魔数 MIME 与大小检查；HttpFileResponses 做中文下载名、length、attachment，常规下载场景有实际价值。改造优先处理下列组合缺口：

- [SafeUpload.java:65](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/upload/SafeUpload.java:65) 的类型检查和 `:84` 大小检查是分别保存的方法，调用者没有一个同时执行两者的主入口；默认 save 没有大小/类型允许集。
- `:53` 的 lexical startsWith 不能解决已存在 symlink/reparse point 指向目录外；原始文件名决定目标且 `transferTo` 没有声明冲突策略，存在覆盖同名文件的风险。目录路径的绝对化/normalize 也应统一。
- `:109` 临时文件 prefix 源于短文件名，Java 临时文件 prefix 最少三字符；`a.txt`/`ab.txt` 可形成未捕获的 IllegalArgumentException。`:113` 的 deleteOnExit 只在进程退出时清理，长驻服务不合适。
- [HttpFileResponses.java:73](E:/GenCode/server-facility/src/main/java/cn/code91/facility/web/download/HttpFileResponses.java:73) 对任意探测类型给 inline；用户上传 HTML/SVG 等在同源预览需独立策略，不能仅凭 MIME 探测就认为可安全 inline。

**建议。** 一个 `save(file, policy)` 的 Module 完整负责上限、允许内容类型、随机存储名、原始显示名、目录约束、冲突策略、失败清理；对象存储 Adapter 有实际消费者时才增加。临时资源用作用域关闭/显式删除。默认 attachment，内联预览仅白名单并优先隔离域；不以危险扩展名黑名单替代允许类型。OWASP 推荐限制类型、随机化名称、限制大小、与 Web 根目录隔离；这些属于纵深防御，各项不能互相替代。[OWASP file upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)

**验收。** 大小+类型同时生效、同名上传不覆盖、并发冲突、短文件名、流中途失败、清理成功、链接逃逸、允许的图像内容、拒绝的活动内容；大文件下载不被幂等全局捕获。鉴权和“此用户是否有权下载此文件”属于宿主业务，不由安全文件名工具自动提供。

### 3.9 log / masking：保留纯函数脱敏，收缩第二套日志框架

保留点很清楚：主源码只依赖 slf4j-api；SLF4J MessageFormatter 代替手写占位符；per-package level 的回归测试；脱敏在消息落盘之前执行而非仅在后处理器执行。`MaskUtil` 纯 JDK、六类规则合并扫描、身份证/卡号校验抑制误伤，是适合独立测试的纯函数 Module。

代价同样具体：[LogUtil.java:256](E:/GenCode/server-facility/src/main/java/cn/code91/facility/log/LogUtil.java:256) 每次获取调用栈确定 logger，`:293` 又缓存 logger，`:300` 从全局 Spring holder 缓存 post handler，`:330` 同步调用。这使禁用级别仍有 caller 解析、context 重启可能持有旧 handler、慢 handler 阻塞业务；handler 再调用 LogUtil 还缺少重入策略。`LogPostHandlerComposite` 过滤自身类型只防止组合器嵌入，不能阻止 handler 中发日志的递归。

SLF4J 2 已提供 fluent event、键值字段、Supplier 参数；设施库只依赖 facade、不绑定 provider 是官方推荐。[SLF4J manual](https://slf4j.org/manual.html) Boot 已有结构化日志及定制出口，不必为了告警/收集再复制一条由业务线程执行的管线。[Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html)

**建议。** 新代码直接用 SLF4J 的类级 logger；必要敏感字段由显式 mask/omit 处理，日志 sink 层另有最终保护。LogUtil 保留兼容过渡，不再拓展 SPI/更多签名；告警改基于业务事件/指标，日志转运由成熟 appender/collector 完成。若保留 post handler，必须绑定 context 生命周期、定义重入、超时/背压与关闭策略，而不是把所有失败吞掉就称为隔离。

**脱敏不能作保密保证。** [LogUtil.java:328](E:/GenCode/server-facility/src/main/java/cn/code91/facility/log/LogUtil.java:328) 把原 Throwable 交给后处理器；[LogUtilMaskingTest.java:69](E:/GenCode/server-facility/src/test/java/cn/code91/facility/log/LogUtilMaskingTest.java:69) 明确断言异常里的完整卡号不变。MaskUtil 的校验位抑误伤、未闭合引号等已在 ADR-0020 披露，这些局限应影响默认数据采集策略：不收集 token、密码、整段 payload 是第一道措施；正则脱敏是补充。OWASP 日志建议也包括排除/处理敏感数据和防止 CR/LF 日志注入。[OWASP logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

**验收。** 同一秘密放在普通消息、Throwable/cause/suppressed、MDC、结构化键值、第三方日志中分别验证最终 sink；标注哪些通道受保护。超长文本/恶意模式的性能由独立基准测量，不放进时间敏感单测。覆盖率不能证明脱敏 completeness；`setMaskingEnabled(false)` 进程级开关建议从默认业务入口移除或缩小到显式实例策略。

## 4. 给 agent 的 golden path：设施可组合，样板可执行

以下是建议，不是现有仓库功能。

### 4.1 先交付一个消费者样板，再决定是否需要生成平台

最小样板使用 JDK 25、选定的稳定 Spring Boot BOM、Servlet MVC、校验、Actuator、RFC 9457、一个有业务不变量的同步命令端点和可执行 curl/HTTP 示例。存储先按真实业务需要选择；本仓库没有现成数据库或认证实现，不能把它们写成已完成能力。只有需要认证的样板才提供一条明确的 Security 路线（会话或 OAuth2 resource server），不要同时默认装上两个身份系统。

成熟方案取舍：

| 方案 | 一手能力 | 对本仓库的建议 |
|---|---|---|
| Spring Initializr | 生成 JVM 项目并暴露依赖/版本/兼容范围等元数据。[官方指南](https://docs.spring.io/initializr/docs/current/reference/html/) | 借鉴“声明能力和兼容矩阵”，先维护单个 consumer fixture；当真的需要多项目生成时再基于 Initializr 扩展，避免自建模板引擎。 |
| Spring Modulith | 从业务包识别模块，验证无环、只依赖公开部分、可限制允许依赖；可单独集成测试模块。[结构验证](https://docs.spring.io/spring-modulith/reference/verification.html)、[模块测试](https://docs.spring.io/spring-modulith/reference/testing.html) | 适合生成应用的业务包结构；不把 facility 的 29 个横切工具包硬解释成 29 个业务领域模块。 |
| JHipster | 应用生成含 monolith/microservice/gateway 等选项，官方默认推荐 monolith。[官方生成说明](https://www.jhipster.tech/creating-an-app/) | 借鉴生成结果可运行、全链路测试和清晰路线；不复制其完整选项空间给首次 agent 任务。 |
| ArchUnit | 字节码级包/类/层依赖和环检查，已有项目可冻结旧违规并阻止新增。[用户手册](https://www.archunit.org/userguide/html/000_Index.html) | 保留当前五条规则，追加“纯值不引用 Spring”“设施内部不新增 service locator”等确有意义的规则；不要以 class 必须以 ServiceImpl 结尾等表面规则充数。 |

模块组织按变化原因和业务能力。例如预约应用的 `booking` 对外暴露 `reserve`、`cancel`、`getAvailability`，将冲突检测和占用状态收进该 Module；不要让 agent 一开始为每个实体生成 Controller/Service/IService/Impl/Manager/Repository/Converter 七层空壳。Interface 应包含“重复预约如何处理”“并发冲突返回什么”“谁能取消”，这些才是调用者真正需要知道的东西。

### 4.2 文档只保留可导航、可执行且有归属的信息

当前 README 的按任务路由和 ADR 索引应保留。建议额外让每个能力页都回答六件事：适用场景、默认行为、依赖/装配、并发/生命周期限制、失败返回、最短验证命令。不要让 agent 为使用一个默认功能先读十篇历史修复叙事。历史 ADR 保留；最新生效的 Interface 用短文档和测试表述，改变决策时新增 ADR supersede，而不是悄改旧结论。

配置表、依赖矩阵、AutoConfiguration 数量、公共入口列表能由源码生成的部分由构建校验；叙事文档只写动机和取舍。禁止把多个长文档的重复计数当“权威链”。本文不直接生成 AGENTS.md；是否需要应在样板实践中决定。

OpenAPI 应来自一种明确的主源：内部快速开发可从实现生成并检查 diff；跨团队公共协议可契约先行。不同时手写一份 spec、手写一份 DTO、再手写一份返回文档却不校验漂移。OpenAPI schema 能约束结构，但不能单独证明权限、幂等、事务行为。[OpenAPI specification](https://spec.openapis.org/oas/v3.1.2.html) 测试产生的真实请求/响应示例可采用 Spring REST Docs，生成片段错误会导致相应测试失败。[Spring REST Docs](https://docs.spring.io/spring-restdocs/)

### 4.3 验证应覆盖消费者可观察行为，不继续堆内部交互断言

建议分三层：

1. **纯 Module 测试**：值与不变量、纯函数、owner/TTL 等状态机。调用真实 Interface，不用 verify(method-called-once) 代替结果。
2. **消费者组合测试**：ApplicationContextRunner classpath/让位矩阵；MVC 测试验证错误内容、安全 headers、过滤链重放。现有测试直接调用 handler 方法有价值，但不足以证明 HTTP wire contract。
3. **真实容器 fixture**：`@SpringBootTest(RANDOM_PORT)` 验证异步/ERROR dispatch、下载/SSE、异常 advice、可选 Security 与 tracing。Boot 的随机端口启动真实 server；其 HTTP 客户端与服务端不在同一事务，测试上的 `@Transactional` 不会自动回滚服务端写入，需显式数据隔离。[Boot testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)

如果样板确实引入 Postgres/Redis，使用 Testcontainers 和 Boot `@ServiceConnection`，容器版本与生产主要版本一致；不拿 H2 的通过证明 Postgres 方言、锁和隔离级别。Boot 支持让 test container 自动供给连接配置；本库无外部依赖的测试不必被强行 Docker 化。[Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)

自动生成或拷贝出的样板也要在 CI 执行，而不是只编译生成器。验收指标是：干净 checkout 能按文档启动并完成一个业务用例；agent 改动违反 module 规则/错误 schema/关键语义时有清晰失败；依赖升级后该流程仍成立。

### 4.4 JDK 25 升级给的是更好的基础，不是隐藏错误的许可

本篇不锁定整仓依赖版本，具体 JDK/Boot/Jackson/Testcontainers 矩阵由平台升级研究负责。与本篇直接相关的迁移重点是：Boot 自动装配类型/包路径、Servlet/MVC 异常与测试 API、Jackson 2 私有 mapper（重复读 413）和宿主 Jackson 的双栈、nullability 注解、Micrometer 传播与虚拟线程上下文。不能仅将 `maven.compiler.release` 改为 25 就宣布兼容。

样板应带固定 Maven Wrapper 及下载校验，CI 使用明确 JDK 25 toolchain。Maven Wrapper 支持 wrapper/distribution 的 SHA-256；可复现构建还要求插件及输出时间等配置，并应比较重新构建的 artifact，不能把“同机器两次一样”称为跨环境已验证可复现。[Maven Wrapper](https://maven.apache.org/tools/wrapper/)、[Maven reproducible builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html)

## 5. 建议拆成的可验收改造批次

| 批次 | 范围 | 完成条件 |
|---|---|---|
| W0：补外部语义反例 | 幂等 scope/body、advice 后异常、全局响应缓存、异步 session、双 context 销毁 | 反例由 fixture 复现；已知旧行为写入兼容清单；不以改断言接受错误行为。 |
| W1：修正生命周期 | context 所有权、静态 cache 清理/迁移、会话清理、filter 注册唯一与排序 | 两 context、三 dispatch、平台/虚拟线程和用户自定义 bean 的组合通过。 |
| W2：Web 协议收敛 | RFC 9457、安全消息、完整 status/headers、统一 filter/MVC writer、旧 envelope adapter | 外部 HTTP 验收矩阵通过；5xx 无敏感哨兵；迁移说明可操作。 |
| W3：幂等契约 | scope/fingerprint、响应上限、失败策略、必要 headers、排除 streaming | 双用户/双端点/相同不同 payload、异常/超时/大响应及崩溃窗口文档全部明确。 |
| W4：生态原生集成 | Boot builder、Micrometer、应用 MessageSource、可选 Security | 标准配置生效且无第二份 trace/auth/message source 真相；设施 core 仍可独立使用。 |
| W5：样板和资源边界 | JDK 25 消费者、上传 policy、分页 sort allowlist、日志收缩 | 干净环境启动与验证一次通过，生成结果纳入 CI；SDK/设施/样板职责清晰。 |

保留足够有用的公共入口，但以删除测试审视每一个包装：删掉它后，调用者必须重新处理文件安全策略、完整错误映射、请求一致性等复杂性，它值得留；删掉它只是把 `RequestUtil.getMethod(request)` 变回 `request.getMethod()`，它没有足够价值。改造后的 code taste 应体现在默认正确、行为可定位、失败可验证，而不是统一给成熟框架再包一层 `Util`。

## 6. 临时 Web 消费者探针：旧测试全绿之外的证据

执行方式：用主任务提供的 `target/classes`、`target/test-classes` 和 Maven test classpath，`javac -proc:none` 编译 [WebProbes.java](E:/GenCode/server-facility/target/research/WebProbes.java)，再执行其 main。输出保存于 [WebProbes.log](E:/GenCode/server-facility/target/research/WebProbes.log)。九条观察均满足断言，进程成功退出。其中两条是对照观察，七条反映具体缺口。没有真实 Servlet 容器，因此不把 MockMvc flush 观察外推为所有容器场景已验证；它直接证明当前 wrapper 的缓冲契约。

| 探针观察 | 实测结论 |
|---|---|
| A holder 注册后 B holder 注册 | A 起初仍被保留（对照）。 |
| 随后销毁 B holder | 全局 context 变 null，A 被错误清除。 |
| `/a`、`/b` 使用相同 raw key | `/b` 返回 `/a` 响应，`/b` controller 执行次数为 0。 |
| `/fail` 抛异常并由默认 advice 生成 ProblemDetail | 两次请求均 500，controller 只执行 1 次，说明已处理异常响应被缓存。 |
| 同一 500 的响应体 | 含 `INTERNAL_SECRET_SENTINEL`，没有 dev/test/local active profile 仍泄露原始消息。 |
| 普通无幂等注解请求写出并 flush | 在 filter 返回前，原始响应体长度仍为 0。 |
| filter 返回 | 缓冲体才复制到原始响应（对照）。 |
| MVC Callable 启动异步并结束初始 dispatch | 发起请求的线程仍能读到 `SessionUserHolder` 用户，后续 dispatch 清理不能补救此前线程复用窗口。 |
| `SafeUpload.toTempFile(a.txt)` | 向调用者抛 IllegalArgumentException，越过 Result 错误通道。 |

这些结果没有推翻所有现有测试的价值；它们指出缺失的是生命周期、不同能力相遇时的契约和真正外部调用路径。改造时应将相关探针改写为常驻消费者测试，再删除与新 Interface 不再相关的旧内部交互测试，不叠加两套永久维护成本。

## 7. 独立新架构方案 C：可运行 Boot 应用模板 + 极小 facility runtime

这一方案从“agent 尽快完成一个可验证的 Web 用例”重新设计产品。它不要求把现有 29 个功能包全部带到下一代运行时。Spring 原生 API 是主路，设施的价值主要来自经过验证的组合、默认策略和应用样板。

### 7.1 交付物与默认使用路径

交付物建议控制为三种角色，最初可以在一个仓库维护：

- **应用模板**：JDK 25 + 选定稳定 Boot BOM + Maven Wrapper + MVC + validation + Actuator + 统一 ProblemDetail，包含一个能执行的垂直业务示例、配置、README、启动/验收命令。样板代码生成后归应用所有，agent 可直接修改，不需要继承 framework 基类。
- **极小 runtime**：只留下被多个应用证实有价值、且标准生态没有直接覆盖的 Module。第一阶段候选为安全业务错误到 ProblemDetail 的策略、组合上传策略；Result 等纯值能力可独立保留。幂等作为明确部署语义的可选能力，不默认全局包裹 HTTP；已有 XSS/MaskUtil 是显式工具，不承担“全局安全”承诺。
- **消费者验证夹具**：对发布 runtime 和生成后的应用均执行，可选依赖有实际测试组合。没有独立发布日期/消费者需求前，不为了概念纯度拆成十几个 Maven artifact。

默认模板不引入 Redis、队列、分布式锁、雪花 ID、全局 SpringContextHolder、日志后处理 SPI或另一套异步 DSL。第一条路就是单进程、同步请求、原生 Spring 注入；明确需要持久化时才选择一个数据库方案。这个默认是范围选择，不声称所有生产应用都无需这些能力。

### 7.2 Module 与 Interface：以订单创建示例说明深度

下列只是方案接口草图，订单域不属于当前设施库，也不是本次新增应用代码：

```java
CreateOrderResult result = orders.create(actor, requestKey, command);
```

`Orders` 是应用中的 Module。`create` 是它对 controller、消息消费者和测试共同开放的 Seam；它可以是具体类的 public 方法，不需要机械生成 `OrdersService` + `OrdersServiceImpl`。`Actor` 是已验证的身份值，`RequestKey` 是此操作的重试身份，`CreateOrder` 是业务输入。以上三个参数之所以分开，是分别拥有信任来源、幂等含义和业务约束；不要塞进万能 `Map<String,Object>` 或 Servlet request。

Interface 除类型以外还必须明确：

| 契约维度 | 本例的目标 |
|---|---|
| 不变量 | 商品/数量合法；同 actor 的同 key 只代表同一个规范化命令；订单只能从允许状态变化；返回 receipt 稳定。 |
| 顺序 | HTTP 认证建立 Actor → 输入解码 → Module 校验权限和业务不变量 → 幂等 claim/检查 → 业务写入与可持久化结果同一一致性策略 → 返回 receipt；响应序列化在此之后。 |
| 错误 | 输入不合法、禁止操作、库存冲突、key 请求不一致、同操作处理中是有类型的可预期拒绝；存储不可用是设施故障，日志保留因果，HTTP 只见安全错误。 |
| 重试 | 重放不能跳过当前调用者应有的授权检查；同 key 不同命令拒绝；重试是否重放某类业务失败预先确定。 |
| 资源 | 指纹输入与结果有上限；事务和远程调用有时间预算；超时并不证明操作未发生。 |
| 一致性 | 同库业务写入与幂等结果可在同一事务提交；跨库/外部支付不承诺这个原子性，采用另有定义的工作流/对账。 |

`CreateOrderResult` 可以是 sealed domain outcome，也可以沿用有约束的 Result；选择取决于应用是否需要显式穷举拒绝，不因为“全库必须 Result”而给任何纯 getter 包装错误通道。HTTP Adapter 将 outcome 映射为 201/409/422 等与 ProblemDetail，Module 内不接触 HttpServletResponse。

**隐藏的复杂性。** controller 不再知道锁名称、store key 前缀、TTL、响应 wrapper、序列化回放、事务回调和日志 MDC。Module 内部可以有私有协作者处理这些责任；Depth 来自调用者少学一整组时序约束，Locality 来自幂等/权限/创建行为的变更集中在订单 Module。不是把所有逻辑塞进一个千行方法。

**一次实际场景的调用链。** 移动端提交订单，网络丢失响应，再用同 key 重试。HTTP Adapter 每次都先验证身份并解码；Orders 返回同一 receipt；API Adapter 生成同一业务表示及稳定 Location。另一个用户拿到同 key 仍属于不同 scope，不能得到前者 receipt。命令换了数量则得到明确的 key-conflict。这里重放的是明确的业务结果，消除了原全局缓存拦截器对每个下载/SSE 的影响。

### 7.3 依赖分类决定内部 Seam，不在外部 Interface 暴露测试装配

| 依赖类别 | 订单示例 | Seam / Adapter 策略 | 验证策略 |
|---|---|---|---|
| 进程内 | 价格规则、命令规范化、状态转换 | 保持直接计算，合并浅包装；不为纯计算创建 port。时间在确需控制时通过 Clock 注入。 | 测试从 Orders/规则 Module 的 Interface 输入并观察结果。 |
| 本地可替换 | 同一应用管理的 PostgreSQL、临时文件 | 仓储是 Module 内部实现；使用真实 Spring 数据访问，不为了测试对外暴露所有 CRUD port。 | Testcontainers 使用同类数据库，真实事务/约束/并发测试；临时目录验证文件行为。H2 与 PostgreSQL 不是无条件等价 Adapter。 |
| 远程但自有 | 公司内部库存 HTTP 服务 | 只有此远程变化真实存在时，定义 `InventoryReservation` port 作为内部 Seam；生产 HTTP Adapter，测试 in-memory Adapter。 | 从 Orders Interface 测试预留成功、冲突、超时；另用契约测试校验自有服务协议。 |
| 真正外部 | 第三方支付、短信 | 明确外部 port；生产 SDK/HTTP Adapter，测试受控 mock Adapter；保留外部操作 ID 和幂等规则。 | 从 Module Interface 测试超时、重复回调、未知状态；不能让 mock 证明第三方真实协议，另做 sandbox 合约验证。 |

若只有一个实现且没有有意义的替换对象，先不创建 interface。生产数据库与 Docker 里的同种数据库并不自动要求两套 repository 实现；真实可替换的测试 Adapter 应有行为差异和验证价值，不能只是给每个类配 Mockito。内部 Seam 不等于业务调用者的 Interface，不要把连接、mock factory、transaction handle 等穿到 `orders.create` 参数。

外部支付尤其不应在长数据库事务里保持锁等待 HTTP。若用例真的需要支付，将它设计为显式订单状态机和持久事件/恢复流程；默认 CRUD 样板不提前内置这套分布式流程。

### 7.4 标准框架拥有标准问题，应用 Module 拥有业务承诺

| 问题 | 默认所有者 |
|---|---|
| DI、生命周期、配置、HTTP 序列化 | Spring Boot/Framework 原生机制。 |
| 身份建立、会话认证、OAuth2 token 校验 | 明确启用的 Spring Security Adapter；应用拥有授权规则。 |
| trace/span、HTTP metrics、上下文传播 | Micrometer/Boot 的原生配置与 builder。 |
| 日志事件、结构化输出、收集 | SLF4J + 宿主 logging/collector；业务选择可记录的字段。 |
| 输入 DTO 校验 | Jakarta Validation；业务不变量由 Module 自己保证。 |
| 业务一致性、幂等范围和恢复 | 应用 Module；设施最多提供经过验证的内部 Adapter，不能靠注解猜出业务事务。 |
| RFC 9457 文案/业务码扩展、上传策略 | 极小 runtime 的可选 Module，生命周期仍由注入管理。 |
| 架构规则与最佳使用路径 | 模板中的可执行规则、业务示例和消费者 CI。 |

### 7.5 取舍、迁移与比较轴

方案 C 的优势是第一天的依赖和 Interface 最少，agent 可以沿 controller → 单个业务 Module → 数据访问读懂一个用例；Spring 升级主要面对标准 API。其成本是模板生成后会出现多应用副本，少量相同配置需要自动更新方案，而不是只升级一个万能 jar。应只将被多消费者验证稳定的策略回收进 runtime，避免为消除五行复制引入长期框架耦合。

与任何其他候选方案比较时，采用以下量尺，不以公共方法或 jar 数少为唯一标准：一个新业务用例要改多少文件；必须学习多少非标准语义；业务 Interface 是否隐藏了时序约束；故障是否集中在一个 Module；测试是否从同一个 Seam 观察行为；升级 Boot 后需维护多少自有适配层；生产场景的故障模型是否被准确表达。

迁移路径可以逐步完成：先把新消费者样板切到原生注入/安全 ProblemDetail/Micrometer；旧服务继续走兼容入口；将最有价值的上传与错误策略提取为实例 Module；把进程级 holder、全局响应缓存、日志二次分发标记为旧路径；用新消费者 fixture 决定每一项能否删除。**不需要一次重写全部设施库，也不应让新设计永远被旧 static Interface 约束。**

方案 C 的验收目标：一个新 agent 只读入口 README 和当前用例包即可增加端点；无需修改 facility runtime；一个请求验证覆盖真实状态变化、HTTP 错误与可观测性；违背 Module 访问规则会失败；没有业务需求的 Redis/锁/重放/认证扩展完全不在 classpath 或请求链上。
