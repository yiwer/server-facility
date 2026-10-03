# Changelog

面向消费方的破坏性变更与行为变更记录(含迁移指引)。格式取意 [Keep a Changelog](https://keepachangelog.com/);
当前尚无已发布版本,以下均为 0.1.0-SNAPSHOT 发布前的演进记录。内部决策全史见 [ADR 索引](docs/adr/INDEX.md)。

## [Unreleased] — 0.1.0-SNAPSHOT

### Boot 4 平台迁移中间态（2026-10-04，ADR-0045）

- 目标依赖切换 Boot 4.1.1 / Spring 7.0.9 / Jackson 3.1.5，测试引擎切换 JUnit 6.0.3 和 ArchUnit JUnit 6。Jackson annotations 保留原组；合并进 databind 的 Java 8 模块移除。
- Web 应用使用 `spring-boot-starter-webmvc`；库只声明实际技术模块，不传递生产容器。Boot 错误页、Servlet context、Tomcat 和 MVC 类型按新模块归属迁移。
- 此提交仅供非发布集成线：旧 Jackson Java 签名仍有精确编译缺口，由票 23 迁移；票 24 恢复完整门后才可消费候选版本。不提供同 jar 的双 Boot/Jackson 主版本兼容。当前范围、迁移清单和工具链子集入口见 [平台账本](docs/building/boot4-platform.md)。

### 有界 Web 流迁移（2026-10-03，ADR-0028）

- 普通下载、SSE 与非目标响应直接发送，不再全量缓冲。旧幂等 claim 成功后才开启 1 MiB 默认响应副本；`facility.idempotency.max-response-bytes` 必须正数。超限继续发送原响应但不保存副本；失败/部分提交不会变成完整重放，原 claim 的过期语义仍需业务协议处理。
- Repeatable request 默认关闭；显式启用时 `max-body-bytes` 必须正数，0/负数不再表示无界，无参数 wrapper 默认 10 MiB。读者/流有独立游标、尊重声明 charset（缺省 UTF-8）；非法 charset 400，实际超限 413；不支持的非阻塞 listener 明确拒绝。
- 容器输入/输出流不再由 wrapper 或下载辅助类关闭。下载关闭自己打开的文件，观察中断/写失败并返回 Err；已提交后不追加第二份错误正文。
- 手工 `ContentCachingResponseWrapper` 不再作为绕过预算的保存入口，迁移到默认有界 filter。旧方法签名保留；完整流/预算、过滤次序和后续 Servlet 6.1 迁移门见 [ADR-0028](docs/adr/0028-bounded-web-streams.md)。

### JSON 配置扩展（2026-10-03，ADR-0044）

- 新增应用作用域 `Jsons` 自动装配，复用本应用 ObjectMapper/customizer，用户 Jsons bean 优先；服务通过构造器注入。静态 JsonUtil/registry 的旧共享行为保留。
- 新增 `JsonConfig.Builder.customizeBuilder`，在预设之后、build 之前定制；旧 mapper 回调仍最后执行。Boot 3/Jackson 2 平台和旧签名保留，主版本替换归票 22–24。
- InputStream 字段 serializer/deserializer 新增显式 `int maxBytes` 构造参数，正数限制原始/解码字节数，默认及 ≤0 保留无上限。字段源流在成功/超限/读写失败时关闭，解码结果由调用方管理。
- 新增独立普通 jar JSON/真实 HTTP 消费者及旧协议金样，覆盖默认/用户定制、两个应用和关闭重建；详见 [迁移影响登记](docs/building/platform-migration-inventory.md)。

### Async 行为迁移（2026-10-03，ADR-0026）

- 默认由每次创建虚拟线程执行器改为共享有界平台线程池（4 工作线程/256 等待项）；容量满会通过 Result 返回提交拒绝。应用显式向 Async 传入注入的 Boot/User Executor；要用虚拟线程，通过标准 Boot 配置或显式 Executor 选择。
- 整体 timeout 从 submit 开始，覆盖后续组合/恢复及子任务；子任务和重复 timeout 不能延长。await(Duration) 超时、cancel(true)、any 首成功都会请求中断相关工作；忽略中断的业务仍可运行，资源必须在任务自身 finally 释放。
- 拦截器由整个 pipeline 一次改为实际工作线程的每个用户执行段一次。使用 try/finally 恢复 ThreadLocal 原值；删除跨线程 whenComplete 清理模式。proceed 与 interceptor 必须返回已完成 Future；不再接受拦截器自行派发异步工作。MDC 自动捕获与恢复，不传播事务/安全身份。
- Result 保留原始失败对象，包括 AssertionError 和提交拒绝；不再误写成 TimeoutException。普通 Executor 也会让 facility fallback 让位；fallback 的容器销毁等待最多 1000ms，未终止可由 isTerminated 观察。迁移例子见 USAGE「异步」。

### Context 生命周期迁移（2026-10-03，ADR-0025）

- `SpringContextHolder` 弃用，推荐构造器注入所需服务。兼容门面改为成功刷新时发布，并仅由发布的 holder 实例撤销；被拒绝的容器关闭、启动失败、父子事件或重复 destroy 不清理另一个应用的注册。
- `setApplicationContextManually` 不再替换已有 owner，只接受 refresh 已返回、未开始关闭且使用 Spring singleton registry 的活跃 `AbstractApplicationContext`，自动随其关闭/原地刷新撤销；无效生命周期输入抛 `IllegalArgumentException`。必需 Class 查询参数 null fail-fast；null 名称返回缺席语义。
- `IdUtil` 与 `LogUtil` 不再跨 context 关闭缓存 Spring bean，重启使用新应用的服务。显式 `IdUtil.setGenerator`/`resetGenerator` 的调用方管理语义保留；`LogUtil.clearHandlerCache` 已弃用并成为兼容空操作。
- 测试迁移：持有并关闭自己创建的 Spring context；移除全局 holder reset/反射清理。旧静态入口仍只代表一个 owner，多个应用使用构造器注入保持各自政策。

### Java 25 中间基线（2026-10-03，ticket 01）

- 最低运行/编译版本改为 Java 25，产物 class major 69，不使用 preview；Java 21 消费方须先升级 JDK。
- Maven Wrapper 固定 3.10.0 并校验下载，Boot 3.5.16 为中间基线，尚不代表 Boot 4 / Jackson 3 已完成。
- 无 Servlet/MVC 依赖的非 Web 应用不再因幂等/限流自动装配提前链接 Web 类型而启动失败；Web 条件、Bean 名和业务语义保留。
- 增加独立普通 jar 消费者和跨平台验证入口，见 [构建与依赖账本](docs/building/java25-baseline.md)。

### Breaking(API 变更,2026-07-06 一致性宪法批)

- **`ErrorTypeInterface.formatFallback(...)` 移出接口契约面(降为 private 实现细节)**。
  - 影响:调用或覆写过该 default 方法的代码编译失败。
  - 迁移:删除对它的调用/覆写即可——`format()` 内部自带 MessageFormat 失败兜底,无需外部参与。
- **`ErrorTypeInterface.getSeverity()` 与 `ErrorSeverity` 枚举删除**(全库零消费的投机扩展点)。
  - 影响:引用处编译失败。
  - 迁移:严重度语义不再由本库承载;如需分级,按 `getCode()`/`getModule()` 在消费方自行映射。
- **`NullSafe.allNotNull()` 对空参数组由返回 `false` 改为返回 `true`**(对齐「空集合上全称命题为真」惯例;
  `allNotNull(null)`(null 数组)仍返回 `false`)。
  - 影响:**编译不报错、行为静默反转**,依赖旧「空数组=false」的调用点请重点排查。
  - 迁移:若语义是「至少一个且全非 null」,显式加 `args.length > 0` 判断。

### Behavior changes(无 API 变更,语义修正)

- **裸 `TypeMismatchException` 归 400,`ConversionNotSupportedException` 保持 500**:非方法参数场景的
  绑定/转换失败(绕过 `MethodArgumentTypeMismatchException` 专门 handler)此前落兜底 500 + ERROR,
  按 Spring 默认解析器语义归 **400**+WARN(复用 `type_mismatch` 键);其子类
  `ConversionNotSupportedException` 是服务端转换器缺失/配置问题,单独拦截**保持 500**+ERROR,
  避免被父类 handler 误判客户端错。至此 2026-07-10 错误处理审计残余全部清零。
- **异步请求超时归 503,不再被兜底误判为 500**:`AsyncRequestTimeoutException`(Callable/DeferredResult/
  WebAsyncTask 超时)实现 `ErrorResponse`(自带 503)但不继承 `ErrorResponseException` 类,下述审计的
  状态透传 handler 覆盖不到;此前落兜底 500 + ERROR。新增专门 handler 归 **503**(统一包络 `code=503`;
  problemDetail HTTP 503),日志降 WARN;新增 i18n 键 `facility.web.error.async_timeout`(四语)。
- **错误处理面同类遗漏审计收口(2026-07-10,五项)**:对下方 404 与 NoSuchMessageException 两修复归纳的
  失效模式(handler 内部调用抛异常逃出 advice;兜底 `Exception.class` 遮蔽 Spring 默认解析器致状态错配)
  全面排查并堵死孪生——
  ①全局异常处理器全部 9 处固定 i18n 键(`facility.web.error.*`)改经 fallback 解析(内置默认文案与基座
  bundle 英文同文):消费方自带 `messageSource` bean(facility 聚合链退让)时不再穿透
  `NoSuchMessageException`,**兜底 handler 自身亦受保护**;缺键/无 context 时 message 由裸键变为默认文案渲染。
  ②参数类型不匹配(如 `?age=abc`)由兜底 500+ERROR 归 **400**+WARN(新键 `type_mismatch`,四语)。
  ③`Accept` 不可满足由兜底 500 归 **406**(新键 `not_acceptable`,四语)。
  ④`ResponseStatusException` 等带状态异常(`ErrorResponseException` 族)不再被兜底压成 500 丢弃预期状态:
  统一包络 `code`=预期状态值,problemDetail 模式透传异常自带 status/headers/body;4xx WARN/5xx ERROR。
  ⑤problemDetail 的 instance URI 构造对畸形原始路径不再抛 `IllegalArgumentException`(instance 省略,
  RFC 7807 可选项)。
  - 影响:上述场景的 HTTP 状态/body `code`/日志级别变化(500→400/406/预期状态,ERROR→WARN);
    固定键缺失时回退文案由裸键变为可读默认文案。
- **`FacilityException` 缺失 messageKey 不再抛 `NoSuchMessageException` 击穿统一响应契约**:全局异常处理器
  改经 `LocaleUtil.localize(errorType, args)`(ADR-0010 C1 边界本地化)解析——`MessageSource` 未命中
  messageKey 时回退 `errorType.getDefaultMessage()` 模板渲染,而非让 `NoSuchMessageException` 逃出
  `@ExceptionHandler` 退化为容器 500/HTML(默认与 problemDetail 双模式均受保护)。
  - 影响:`BusinessException`/`SystemException` 的 messageKey 未在 bundle 登记时,面向用户 message 由
    「裸 messageKey」变为「`defaultMessage` 模板渲染」;统一 `BaseResponse` 契约不再被击穿。
- **未匹配路由归 404,不再被兜底误判为 500**:`NoResourceFoundException`(Spring 6.1+ 未匹配路由/
  静态资源默认抛)与 `NoHandlerFoundException` 此前落入兜底 `@ExceptionHandler(Exception.class)`,返回
  `code=500`(统一模式 HTTP 200)/HTTP 500(problemDetail),并以 **ERROR** 级记「系统异常」——扫描/探测/
  拼错 URL 污染错误日志、可能误触告警。新增 `handleNotFound` 按真实语义归 404(统一模式 HTTP 200 +
  `code=404`;problemDetail HTTP 404),日志降为 **WARN**「未匹配路由」;新增 i18n 键
  `facility.web.error.not_found`(en/zh_CN/zh_TW/base 四语)。
  - 影响:依赖旧「未匹配路由返 code=500」的客户端判断需改按 404;监控中这类事件由 ERROR 降为 WARN。
- **LogUtil 级别门控改按调用方 logger 判定**(ADR-0022):`logging.level.<调用方包>` 的 per-package
  配置对 LogUtil 通道生效(旧实现按 LogUtil 自身/root 级别短路,业务包放开也无输出)。升级后同
  配置下日志量可能增多——这是修正而非回归。调用方解析同时切换 `StackWalker` 惰性遍历。
- **LogUtil 默认开启日志脱敏**(ADR-0020):写盘与 LogPostHandler 旁路均收到脱敏后消息(六类内置
  规则,身份证/银行卡带校验位抑误伤);`LogUtil.setMaskingEnabled(false)` 为总开关逃生舱;
  Throwable 的 message/stack trace 不脱敏(诚实局限)。
- **SnowId `throw-on-clock-backwards-exceed-threshold=false` 语义忠实化**(ADR-0023):任何幅度的
  时钟回拨都等待追上、绝不抛(旧实现回拨超约 1 秒仍抛);等待期间 ID 生成阻塞,风险见 properties javadoc。
- **锁/幂等的溢出防护改 fail-closed**(ADR-0016/0017 修订):锁数/记录数达 max 上限时**拒绝新建**
  (`tryLock` 返 false;幂等先清过期再拒)而非清空全表——在途持锁与未过期幂等记录永不因防护被打破。
  限流的 maxBuckets 保持 clear-all(fail-open):锁是正确性组件、限流是保护组件,不对称有意。
- **限流门面降级哨兵改 `-1`**:无 `RateLimiter` bean 时 `RateLimitResult.remaining()` 返 `-1` 表
  「未知/降级」(旧为 `Long.MAX_VALUE`);勿将该值直接透传到 `X-RateLimit-Remaining` 等响应头。
- **TraceIdFilter 校验入站 `X-Trace-Id`**:仅接受 `[0-9A-Za-z_-]{1,64}`,不合法按缺失处理(依
  `generate-if-absent` 重新生成或不注入)——防日志伪造/响应头注入。
- **构造器参数守卫兑现**(ADR-0013):`InMemoryDistributedLock`/`InMemoryIdempotencyStore`/
  `TokenBucketRateLimiter` 对非正参数(maxLocks/maxEntries/capacity/permitsPerSecond/maxBuckets ≤0)
  启动期抛 `IllegalArgumentException` 快速失败(旧实现静默接受并产生荒谬行为)。

### Removed(配置项)

- **`facility.web.access-log.log-headers` 删除**(从未被消费的死配置;Spring 宽松绑定下遗留配置行
  不会导致启动失败,建议清理);同期 `slow-threshold-millis` 真实生效(超阈值 WARN + slow 标记)。

### Added(0.1.0 主体能力,概要)

- 自 beacon-facility 迁移的基座簇:`Result<T,E>` 错误通道、error/i18n、雪花 ID、JSON 多命名空间、
  日志门面、date/number/copy/io/path/mime/pattern/hash、Web 栈(traceId/可重复读请求体/访问日志/
  全局异常/统一响应/安全上传下载/XSS)。
- §10 新组件八项:令牌桶限流(SPI)、缓存门面(Caffeine optional)、完整幂等(SPI)、分布式锁(SPI)、
  HTTP client(RestClient 委托)、crypto(AES-256-GCM/HMAC/PBKDF2,纯 JDK)、日志脱敏 masking、
  Excel/CSV(POI 双类探测降级 + RFC 4180 纯 JDK)。
- 质量门:1196 测试、5 条 ArchUnit 架构守护、JaCoCo gate 0.88/0.75、`dependency:analyze` failOnWarning。
