# ADR 索引

> 0001-0008 为 inherited(源:beacon 仓库,facility 专属决策);0009 起为 server-facility 本工程决策。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](0001-rp-02-jsoup-tika-optional.md) | inherited | jsoup / tika-core 声明为 Maven optional |
| [0002](0002-rp-04-async-bean-type-matching.md) | inherited | 异步线程池注入按类型匹配,不按 bean 名 |
| [0003](0003-rp-06-rfc-7807-problem-details.md) | inherited | RFC 7807 ProblemDetail 双轨(use-problem-detail 开关) |
| [0004](0004-rp-07-facility-exception-interface.md) | inherited | FacilityException 接口解耦异常层次 |
| [0005](0005-rp-08-slf4j-throwable-position.md) | inherited | LogUtil Throwable 参数对齐 SLF4J 末位 |
| [0006](0006-rp-13-cas-compare-and-exchange.md) | inherited | LogUtil 内部状态 compareAndExchange 消除 ABA |
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
