# Ticket 25 — 应用拥有双服务 HTTP 与失败政策

2026-10-04，Windows 完整验证通过；本票暂为 **verification-pending**，等待集成候选同源 Windows/Linux CI。本文数字只属于下述本地来源，不作为 Linux 执行数量。

## 来源与可重放入口

- 完整被测来源 `a9c6400cf2109722cc7e67ccb00589f794eaa9c8`，工作树干净，含当时集成 `5bdfcb0`。随后同步27集成 `5674f6d`；25生产代码、示例及测试无变化，验证入口保留两个独立消费者调用。合并入口已 javac 编译检查，联合行为由候选 CI 验证。
- Windows 11 10.0 amd64，Oracle JDK `25.0.4.1+1-LTS-5`，Asia/Shanghai / zh_CN。Wrapper、全新依赖仓、有效 POM/依赖树和每个命令均由 runner 留档。
- 执行 `java verification/Verify.java all --fresh`，`VERIFY_WRONG_JAVA_HOME` 指向实际 JDK21。完整证据 `.verification-results/20261004-041709-093-all/`，共66个命令，最终 `RESULT=PASS`。
- 库 **1541 tests，0 failure/error/skipped**，架构5项；覆盖率指令21174/22710、行4232/4506、分支2233/2608，均超过88%/88%/75%原门槛。
- 独立聚合应用 **14 tests，0 failure/error/skipped**；覆盖率指令95.3162%、行97.3684%、分支85.2273%。执行数据与 XML 都必须存在，清理后跳过测试的负控确实失败；未降低门槛。
- 库普通 jar SHA256 `a5d543b56c0b26201c5699c59c7cdde6fae221725a16af7da1492b9b1ab920fa`；示例普通 jar SHA256 `98258a8c634f5c93a9a220352bf7c505cfdc04ff42141a010324b556886cc707`。这些是本次产物身份，不宣称逐字节可重现构建。

## 交付边界

[ADR0048](../adr/0048-application-owned-outbound-http.md) 部分替代0018。默认装配克隆宿主 Boot `RestClient.Builder`，保留其工厂/customizer/JSON/观测；只有没有宿主 builder 的兼容分支采用 facility 超时。旧公开装配签名及 `HttpClients` 均保留并标注迁移。

[真实聚合应用](../../examples/partner-aggregation/README.md) 自有 Catalog/Inventory 类型化适配器与两个 JDK HttpClient 生命周期；每个服务自行配置凭据、URI、传输工厂和预算。共享 helper 是应用私有实现，无远程任务 DSL。宿主 JSON、customizer 和 ObservationRegistry 沿标准 builder 继承，SSL/proxy/运输参数须配置到应用拥有的两个 HttpClient，不能假定被替换的工厂仍生效。

库新增 `ResponseBodyLimit` 标准拦截器按实际流量计数，支持正常 InputStream API、重复 getBody、N+1 检测、Long.MAX_VALUE，不用 Content-Length 宣称安全。超限关闭先关闭有界 body 再关闭响应，避免 Spring JDK 响应关闭时继续 drain 远端尾部。压缩解码由标准工厂完成，本例按解码后字节限制。

失败只携带稳定 kind、状态、有限白名单头和副作用结果；无远端 URL、token、body、原始 cause。写请求默认一次，提交后断连/5xx/超时为 UNKNOWN；连接未建立为 NO_EFFECT。显式 GET 重试仅允许一次 `503 + Retry-After: 0`，两个尝试共享原始单调 deadline；不把 JDK 自身安全连接恢复混同应用重试。

## Q01–Q10 与场景证据

| 标准 | 公开契约、样本与结果 |
|---|---|
| Q01 | FR-08 / AC-11、15 对应 HostBuilderHttpContractTest、ResponseBodyLimitHttpContractTest、PartnerContractTest、PartnerConsumer；旧API迁移见 USAGE/CHANGELOG/示例README及ADR0048。 |
| Q02 | 超时null/0/负数/小于1ms/溢出、预算0/负数/N−1/N/N+1/Long.MAX_VALUE、无效调用参数不发请求、DTO/泛型列表/204、空体/缺字段/坏JSON/截断/超限、状态302/403/429/503、头白名单。 |
| Q03 | JDK HttpServer两服务真实网络，字面JSON外部oracle；宿主snake_case/customizer/自定义请求工厂与Brave实际B3发送span均验证。两上下文不同凭据/baseURL/超时；不是只验mock调用次数。 |
| Q04 | 闩锁定位headers/body阻塞与中断；写成功计数后关闭连接证明UNKNOWN且一次；拒绝连接实际socket；连接超时分类另以标准HttpClient失败future注入 HttpConnectTimeoutException，明确不是SYN黑洞实测。两上下文并行16次聚合各自隔离，关闭其一后另一继续工作。 |
| Q05 | 示例单次默认2s、可配置1ms..60s；响应默认1MiB、1..16MiB；原deadline重试。关闭尾流实测从旧实现3.003s降至<1s。独立128MiB/2CPU/90s进程5轮启动关闭、200次大响应拒绝、205次catalog/5次inventory请求，自有client最终terminate、自然退出；存活堆13324064→13481568 bytes。 |
| Q06 | 独立JDK服务器发送字面JSON/HTTP状态/压缩流/截断，不使用同一映射器写回自身读取；旧HttpClients9项保留。独立普通jar消费者只用production依赖，明确拒绝JUnit/Mockito/Brave出现在runtime。 |
| Q07 | 有限确定性遍历所有流入口 read/readNBytes/readAllBytes/transferTo/skip 与N边界，重试次数/状态表、配置边界表可直接重放；无自造格式/数值算法，不为此引入随机fuzz框架。 |
| Q08 | SHA、JDK/OS/locale/时区、有效POM/依赖树、普通jar哈希、原始命令/日志均留档；安全错误哨兵保证URL/token/远端内容不进入失败对象。仅一次连接超时注入的限制如上。 |
| Q09 | 库与独立示例原质量门均通过；覆盖率缺失负控真正失败。没有跳过测试，旧静态API测试保留；新增真实HTTP断言而非实现镜像。 |
| Q10 | 本地代码/迁移/ADR/证据已交付；同源Linux及Windows CI待执行，故本票未关闭。 |

## TDD 与失败归档

`.verification-results/ticket-25/` 保留01–25轮证据。真实 RED 包括宿主builder被绕过、旧超时0、聚合无body、状态/坏响应、响应超时、取消分类、连接失败结果、显式重试/原deadline、trace缺失、非法配置、错误缺失安全request-id，以及响应关闭仍drain导致3秒延迟。19轮无界尾流问题修复后另跑19项库HTTP契约，完整门再次覆盖。

早期04 aggregation-red和red-confirmed日志是尚未安装当前库导致的harness失败，不当作产品RED；实际产品RED为04-aggregation-product-red。07首次green发现截断fixture未flush HTTP头，修复fixture后保留两次结果。压缩流与并行生命周期是既有行为的绿色扩展，未冒称先失败。23连接超时分类是标准边界故障注入；24调用输入限制有真实RED/GREEN。最终25-all-fresh独立记录完整结果，不拼接局部门冒充同源完整执行。

## 适用限制

运输超时约束网络等待，不承诺强制抢占任意应用customizer/CPU解析。默认示例预算和128MiB验证证明本场景有界，不宣称所有宿主配置均满足同一SLO。大响应必须由调用者在exchange作用域消费并关闭；不是流式JSON引擎。测试未对外部真实第三方发送副作用请求，未加入自动POST重试。
