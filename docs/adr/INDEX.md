# ADR 索引

> 0001-0008 为 inherited(源:beacon 仓库,facility 专属决策);0009 起为 server-facility 本工程决策。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](0001-rp-02-jsoup-tika-optional.md) | inherited | jsoup / tika-core 声明为 Maven optional |
| [0002](0002-rp-04-async-bean-type-matching.md) | inherited; 部分由 [0026](0026-async-execution-contract.md) 替代 | 保留按类型让位与 Boot 优先，TaskExecutor-only 条件和裸虚拟线程兜底由 0026 替代 |
| [0003](0003-rp-06-rfc-7807-problem-details.md) | inherited | RFC 7807 ProblemDetail 双轨(use-problem-detail 开关) |
| [0004](0004-rp-07-facility-exception-interface.md) | inherited | FacilityException 接口解耦异常层次 |
| [0005](0005-rp-08-slf4j-throwable-position.md) | inherited | LogUtil Throwable 参数对齐 SLF4J 末位 |
| [0006](0006-rp-13-cas-compare-and-exchange.md) | inherited; 部分由 [0025](0025-context-ownership.md) 替代 | LogUtil 内部状态 compareAndExchange 消除 ABA |
| [0007](0007-rp-10-result-empty-factory.md) | inherited | Result.empty() 表达"成功但无值" |
| [0008](0008-rp-15-snowid-parsetimestamp-instance.md) | inherited | SnowId parseTimestamp/parseInfo 改 instance |
| [0009](0009-alias-trimming.md) | Accepted | Result/Tuple/Triple 纯别名精简(13 个方法) |
| [0010](0010-error-message-boundary-localization.md) | Accepted | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
| [0011](0011-logutil-setlevel-removal.md) | Accepted | 删除 LogUtil.setLevel,主源码零 logback 依赖 |
| [0012](0012-logutil-slf4j-messageformatter.md) | Accepted | formatMessage 委托 SLF4J MessageFormatter(RV2-17 翻案) |
| [0013](0013-properties-validation-constructor-guard.md) | Accepted | 配置属性不用 @Validated,构造器兜底(消费方无 provider 可启动) |
| [0014](0014-ratelimit-token-bucket-seam.md) | Accepted | 限流令牌桶 + RateLimiter SPI(Seam 可替换),web 集成分离 web.ratelimit 避环 |
| [0015](0015-cache-facade-cachemanager.md) | Accepted | 缓存 CacheUtil 门面复用 Spring CacheManager,Caffeine+spring-context-support 成对 optional |
| [0016](0016-distributed-lock-seam.md) | Accepted | 分布式锁 DistributedLock SPI + 默认单机 InMemory,real seam 升级 Redisson 示范 |
| [0017](0017-idempotency-full-semantics-response-capture.md) | Accepted | 完整幂等(同 key 返首次响应),Filter 捕获响应 + 拦截器状态机,通用/web 分离 |
| [0018](0018-http-client-restclient-result.md) | Accepted | HttpClients 门面委托 RestClient 返 Result,超时 properties + RestClient bean Seam |
| [0019](0019-crypto-facade-safe-defaults.md) | Accepted | crypto 加解密门面——安全默认 AES-GCM、内管 IV、不透明失败通道、纯 JDK |
| [0020](0020-masking-log-pre-write-checksum-suppression.md) | Accepted | 日志脱敏——LogUtil 写前集成(LogPostHandler 证伪)+ 校验位误伤抑制 + SECRET substring 语义 |
| [0021](0021-excel-csv-optional-poi-runtime-probe.md) | Accepted | Excel/CSV——POI optional 运行时探测降级(双类探针+类型隔离)与纯 JDK CSV(RFC 4180) |
| [0022](0022-logutil-caller-gating-stackwalker.md) | Accepted | LogUtil 门控基于调用方 logger(per-package 生效)+ StackWalker 惰性解析 |
| [0023](0023-snowid-clock-backwards-nothrow-wait.md) | Accepted | SnowId 回拨:false 无界等待绝不抛;spin 上限随阈值放宽 |
| [0024](0024-java25-reproducible-consumer-baseline.md) | Accepted | Java 25 中间基线、校验固定 Maven Wrapper、独立普通 jar 消费与跨平台验证入口 |
| [0025](0025-context-ownership.md) | Accepted | Context 实例注册归属、刷新/关闭隔离与构造器注入；兼容 ID/日志不跨 context 缓存 Spring bean |
| [0026](0026-async-execution-contract.md) | Accepted | Async 声明执行器、整体 deadline、实际线程上下文作用域与协作取消；标准执行器生命周期、有界资源 |
| [0044](0044-json-application-scope-expand.md) | Accepted | JSON 应用作用域注入、构建期配置与显式流预算；旧平台消费者金样及 22–24 非发布迁移门 |
