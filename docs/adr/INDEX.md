# ADR 索引

> 0001-0008 为 inherited(源:beacon 仓库,facility 专属决策);0009 起为 server-facility 本工程决策。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](0001-rp-02-jsoup-tika-optional.md) | inherited | jsoup / tika-core 声明为 Maven optional |
| [0002](0002-rp-04-async-bean-type-matching.md) | inherited; 部分由 [0026](0026-async-execution-contract.md) 替代 | 保留按类型让位与 Boot 优先，TaskExecutor-only 条件和裸虚拟线程兜底由 0026 替代 |
| [0003](0003-rp-06-rfc-7807-problem-details.md) | inherited; 部分由 [0027](0027-safe-http-error-policy.md) 替代 | 保留标准 ProblemDetail 与显式 legacy 入口理由；默认协议、detail 安全边界和状态映射由 0027 替代 |
| [0004](0004-rp-07-facility-exception-interface.md) | inherited | FacilityException 接口解耦异常层次 |
| [0005](0005-rp-08-slf4j-throwable-position.md) | inherited | LogUtil Throwable 参数对齐 SLF4J 末位 |
| [0006](0006-rp-13-cas-compare-and-exchange.md) | inherited; 部分由 [0025](0025-context-ownership.md) 替代 | LogUtil 内部状态 compareAndExchange 消除 ABA |
| [0007](0007-rp-10-result-empty-factory.md) | inherited | Result.empty() 表达"成功但无值" |
| [0008](0008-rp-15-snowid-parsetimestamp-instance.md) | inherited | SnowId parseTimestamp/parseInfo 改 instance |
| [0009](0009-alias-trimming.md) | Accepted | Result/Tuple/Triple 纯别名精简(13 个方法) |
| [0010](0010-error-message-boundary-localization.md) | Accepted; 静态本地化推荐部分由 [0049](0049-application-owned-observability.md) 替代 | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
| [0011](0011-logutil-setlevel-removal.md) | Accepted | 删除 LogUtil.setLevel,主源码零 logback 依赖 |
| [0012](0012-logutil-slf4j-messageformatter.md) | Accepted | formatMessage 委托 SLF4J MessageFormatter(RV2-17 翻案) |
| [0013](0013-properties-validation-constructor-guard.md) | Accepted | 配置属性不用 @Validated,构造器兜底(消费方无 provider 可启动) |
| [0014](0014-ratelimit-token-bucket-seam.md) | Accepted; 部分由 [0029](0029-request-boundaries.md) / [0032](0032-local-rate-limit-contract.md) 替代 | 保留本地令牌桶/SPI/Web分包理由；0029替代代理来源假设，0032替代数值/缺设施放行/整体clear/操作身份政策 |
| [0015](0015-cache-facade-cachemanager.md) | Accepted; 装配条件由 [0047](0047-boot4-consumer-integration.md) 补全 | 保留CacheManager门面/成对optional理由；缺任一的真实回退由0047验证，TTL/容量政策归08 |
| [0016](0016-distributed-lock-seam.md) | Accepted; 部分由 [0030](0030-local-keyed-mutex.md) 替代 | 保留旧SPI签名；默认本地能力分名、缺实现拒绝、无租约持有及严格容量回收由0030定义 |
| [0017](0017-idempotency-full-semantics-response-capture.md) | Accepted; 部分由 [0028](0028-bounded-web-streams.md) / [0034](0034-qualified-legacy-claims.md) / [0035](0035-authorized-bounded-http-replay.md) 替代 | 0028替代全站/无界捕获；0034替代无owner完成、到期重授与advisory容量，0035定义当前授权、HTTP作用域与有界终态重放 |
| [0018](0018-http-client-restclient-result.md) | Accepted; 部分由 [0048](0048-application-owned-outbound-http.md) 替代 | HttpClients 门面委托 RestClient 返 Result,超时 properties + RestClient bean Seam |
| [0019](0019-crypto-facade-safe-defaults.md) | Accepted; 部分由 [0040](0040-legacy-crypto-reader-policy.md) 替代 | 保留纯JDK/固定历史协议；原始cause安全性、随机IV与never-throw过度保证由0040替代 |
| [0020](0020-masking-log-pre-write-checksum-suppression.md) | Accepted; 新路径默认日志入口部分由 [0049](0049-application-owned-observability.md) 替代 | 日志脱敏——LogUtil 写前集成(LogPostHandler 证伪)+ 校验位误伤抑制 + SECRET substring 语义 |
| [0021](0021-excel-csv-optional-poi-runtime-probe.md) | Accepted; 部分由 [0038](0038-bounded-csv-dialects.md) / [0039](0039-bounded-excel-formats.md) 替代 | 保留裸列表/无表头ORM和POI optional理由；0038替代手写CSV/用途政策，0039替代Excel无界读取/公式计算/临时清理与仅探针保证 |
| [0022](0022-logutil-caller-gating-stackwalker.md) | Accepted; 新路径默认日志入口部分由 [0049](0049-application-owned-observability.md) 替代 | LogUtil 门控基于调用方 logger(per-package 生效)+ StackWalker 惰性解析 |
| [0023](0023-snowid-clock-backwards-nothrow-wait.md) | Accepted | SnowId 回拨:false 无界等待绝不抛;spin 上限随阈值放宽 |
| [0024](0024-java25-reproducible-consumer-baseline.md) | Accepted; 平台版本部分由 [0045](0045-boot4-platform-toolchain.md) 替代 | 保留 Java 25、固定 Wrapper、普通 jar 与原质量门；Boot 3 中间平台由 0045 目标依赖替代 |
| [0025](0025-context-ownership.md) | Accepted | Context 实例注册归属、刷新/关闭隔离与构造器注入；兼容 ID/日志不跨 context 缓存 Spring bean |
| [0026](0026-async-execution-contract.md) | Accepted | Async 声明执行器、整体 deadline、实际线程上下文作用域与协作取消；标准执行器生命周期、有界资源 |
| [0027](0027-safe-http-error-policy.md) | Accepted; Security接合由 [0050](0050-secured-application-template.md) 补充; 默认本地化与诊断关联部分由 [0049](0049-application-owned-observability.md) 替代 | Filter/MVC/ERROR 共用安全 RFC 9457 错误策略、真实状态和必要头；宿主 mapper/locale、已提交边界与显式 legacy 迁移 |
| [0028](0028-bounded-web-streams.md) | Accepted; [0047](0047-boot4-consumer-integration.md) 补充目标重载 | 普通响应直通、显式有界捕获、repeatable正预算与流所有权；6.1新入口接合由0047登记 |
| [0029](0029-request-boundaries.md) | Accepted; 应用身份与异步接合由 [0050](0050-secured-application-template.md) 补充; 默认trace生成政策部分由 [0049](0049-application-owned-observability.md) 替代 | 显式可信代理和冻结来源、REQUEST/ASYNC/ERROR及Callable上下文归属；兼容身份清理与宿主trace恢复 |
| [0030](0030-local-keyed-mutex.md) | Accepted | 实例内线程owner互斥、原子活动键预算和等待者安全回收；关闭不强制释放，旧入口明确迁移 |
| [0032](0032-local-rate-limit-contract.md) | Accepted | 正成本与精确余额、真实缺额等待、有界主体准入和满桶回收；required/Optional设施政策、可信主体及入口计费 |
| [0033](0033-explicit-id-policy.md) | Accepted | UUID默认，显式节点SnowId与单调有界等待；替代0023无限等待 |
| [0034](0034-qualified-legacy-claims.md) | Accepted; release的有效租约限制部分由 [0035](0035-authorized-bounded-http-replay.md) 替代 | 独立claim执行资格、owner/generation条件更新、结果保留与永久命令绑定；有界内存、旧SPI隔离与失败首因 |
| [0035](0035-authorized-bounded-http-replay.md) | Accepted | 当前操作授权与规范化、可信身份/完整操作scope、有限同步HTTP目标、安全回执与失败终态；当前过期owner可终止但不可覆盖新generation |
| [0036](0036-upload-integrity.md) | Accepted | 实际字节预算、内容探测流所有权、服务端存储键与同卷硬链接发布；保留0001的optional理由 |
| [0037](0037-complete-zip-and-directory-results.md) | Accepted | ZIP完整关闭后不覆盖发布、有限读写/条目/深度预算；目录完整统计与有界逐项删除，失败及残留真实可见 |
| [0038](0038-bounded-csv-dialects.md) | Accepted | Commons CSV明确方言、有界逐行消费与正数预算；机器原值/电子表格拒绝政策、流所有权和安全位置 |
| [0039](0039-bounded-excel-formats.md) | Accepted | POI5.5.1实际格式图、小XLS/HSSF与有界XLSX/SAX；Locale/公式缓存、借用流和SXSSF自有临时预算 |
| [0040](0040-legacy-crypto-reader-policy.md) | Accepted | 保留历史AES-GCM/PBKDF2读取，全部Result失败不携原始cause；协议最小/最大长度前置拒绝，应用显式资源预算 |
| [0041](0041-core-value-contracts.md) | Accepted | 保留核心 Result/领域错误语义，明确浅引用所有权、必需回调与集合算术边界；无框架普通 jar 消费 |
| [0044](0044-json-application-scope-expand.md) | Accepted; 旧兼容阶段由 [0046](0046-jackson3-application-ownership.md) 替代 | JSON 应用作用域注入、构建期配置与显式流预算；旧平台消费者金样及 22–24 非发布迁移门 |
| [0045](0045-boot4-platform-toolchain.md) | Accepted | Boot 4/Jackson 3 目标依赖、技术模块归属、JUnit 6/ArchUnit 与独立工具链探针；Jackson 编译归23、完整门归24 |
| [0046](0046-jackson3-application-ownership.md) | Accepted | Jackson3不可变配置、应用mapper/registry所有权、安全错误与正数字段流预算；保留旧金样和明确静态迁移 |
| [0047](0047-boot4-consumer-integration.md) | Accepted | 补全0045/0046平台门、0028的Servlet6.1入口与0015缺类装配；真实五图/普通jar/Web/上传消费，OS状态按报告 |
| [0048](0048-application-owned-outbound-http.md) | Accepted | 部分替代0018；宿主拥有HTTP配置、有限响应与应用级重试，双服务实际消费者 |
| [0049](0049-application-owned-observability.md) | Accepted | 应用 MessageSource/SLF4J/Micrometer 所有权；退出默认静态日志与旧 trace，保留迁移入口 |
| [0050](0050-secured-application-template.md) | Accepted; 业务持久接合由 [0051](0051-postgresql-business-module.md) 扩展 | 独立MVC模板的应用自有JWT信任、Actor与标准Security授权；安全401/403/503、JWK有限I/O、真实Servlet/执行器上下文及独立打包门 |
| [0051](0051-postgresql-business-module.md) | Accepted | 扩展0050：应用自有PostgreSQL/JdbcClient/Flyway，当前成员授权、事务/分页/独立迁移与有限数据库预算 |
