# Ticket 25 — 应用拥有双服务 HTTP 与失败政策

2026-10-04，本票 **closed**。Windows完整本地验证及集成候选9009810的Windows/Linux完整CI均通过，见[联合闭合](ticket-25-26-ci.md)。本文历史阶段数字只属于各自标注的本地来源，不作为Linux执行数量。

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
| Q10 | 本地代码/迁移/ADR/证据齐备；CI13在9009810同源候选通过Windows/Linux完整门，已闭合。 |

## TDD 与失败归档

`.verification-results/ticket-25/` 保留01–25轮证据。真实 RED 包括宿主builder被绕过、旧超时0、聚合无body、状态/坏响应、响应超时、取消分类、连接失败结果、显式重试/原deadline、trace缺失、非法配置、错误缺失安全request-id，以及响应关闭仍drain导致3秒延迟。19轮无界尾流问题修复后另跑19项库HTTP契约，完整门再次覆盖。

早期04 aggregation-red和red-confirmed日志是尚未安装当前库导致的harness失败，不当作产品RED；实际产品RED为04-aggregation-product-red。07首次green发现截断fixture未flush HTTP头，修复fixture后保留两次结果。压缩流与并行生命周期是既有行为的绿色扩展，未冒称先失败。23连接超时分类是标准边界故障注入；24调用输入限制有真实RED/GREEN。最终25-all-fresh独立记录完整结果，不拼接局部门冒充同源完整执行。

## 适用限制

运输超时约束网络等待，不承诺强制抢占任意应用customizer/CPU解析。默认示例预算和128MiB验证证明本场景有界，不宣称所有宿主配置均满足同一SLO。大响应必须由调用者在exchange作用域消费并关闭；不是流式JSON引擎。测试未对外部真实第三方发送副作用请求，未加入自动POST重试。

## Unicode 路径 CI 回归（2026-10-04）

CI8 `37152209100`（6a66672）和 CI9 `37152949481`（b7b7ea4）均为 Linux 全部通过、Windows partner-build 失败。CI9 的有限失败摘要公开了 JVM 启动前 `AddToSystemClassLoaderSearch` error 103；不是 HTTP 超时或业务断言失败。原始公共 job/annotation JSON 留在集成 `.verification-results/ci-http-security-claims/`；未获得下载权限的日志或 artifact 内容不冒称已检查。

本机以完全相同的类/agent在 ASCII、中文、不可由 GBK 表示的路径作最小对照；在希伯来 BMP 路径重现 error103 / tests=0，而更改 file.encoding / sun.jnu.encoding 未解决。换为官方离线插桩后，相同路径聚合应用14测试通过，指令814/854、行111/114、分支75/88；重复 `test` 再14项通过，证明成功运行后的字节码恢复。安全模板完整47测试通过，指令708/739、行115/122、分支59/68。门槛仍88%/88%/75%，全新清理副本跳过测试仍因缺执行数据/XML失败。

另一个实际 Java→ProcessBuilder→Java 最小回归证实不可编码的 argv 变成四个问号。内部路径参数采用 ASCII file URI；模板复制器兼容原始路径与文件 URI，打包消费者对子进程使用相对 ASCII 文件名及工作目录。独立可执行 jar 在该路径的 platform/virtual 启动、认证/授权、重启和配置失败负控全部通过。模板两个故障子进程使用 manifest Class-Path URI；ExecFileLoader 证明两个子 JVM session 及 RequestExecutionConfiguration 的16/28 probes确实写入共同覆盖率。架构断言读编译器原始字节码，不放宽业务依赖白名单。

聚合与模板各12个生产 class 均与恢复后的 classes 完全一致，无 JaCoCo 引用；模板发布 jar 不含 JaCoCo runtime。失败构建现在也归档聚合 Surefire/coverage 报告。证据：票25 `.verification-results/ticket-25/offline-probe/`，含 online RED、offline GREEN、重复执行、负控、Java argv RED、复制器及可执行 jar GREEN。中间 initialize 恢复在 clean 无备份目录时失败的尝试保留，最终配置取消该错误假设；失败/中断测试之后必须 `clean verify`。

依据：[JaCoCo offline instrumentation](https://www.jacoco.org/jacoco/trunk/doc/offline.html)、[restore goal](https://www.jacoco.org/jacoco/trunk/doc/restore-instrumented-classes-mojo.html)。此段为精确局部回归；修复后的同源完整 CI 仍待执行，不将这些局部结果合并冒称全门。

补充：冻结修复 adab8e5 的实际 Verify.partnerConsumer/securedTemplate 共13个命令在本机通过（含两个质量门、独立普通jar、可执行jar、两个缺覆盖率负控），保存 `.verification-results/ticket-25/offline-runner/`；本轮仅复验构建入口，库依赖为原已验证 a9c6400 对应产物，不冒称对当前库全部源码复验。CI10 `37155353238` 的Linux完整门通过；Windows已越过partner，失败转到template-build。公共annotation将10,000字符摘要截断，未取得最终断言，因此仅收窄诊断摘要为最后3,000字符后重跑，未猜改产品或放宽断言。

CI11 `37155905485`（49b3148）已取得完整诊断：Linux全门通过；Windows模板测试和JaCoCo比例检查也通过，失败是 RequireFilesExist 在真实存在的文件上拒绝 `C:\Users\RUNNER~1` 短路径别名。官方 Enforcer 3.6.3 源码不仅判 exists，还严格比较原URI与canonical URI。本机用真实 `C:\PROGRA~1\Java\jdk-25.0.4.1\release` 跑同版本 Enforcer：文件确实存在，但原别名RED、toRealPath展开后GREEN（0.781s/0.704s），见 `.verification-results/ticket-25/short-path-probe/`。模板临时根与partner一致改为toRealPath，保留全部文件存在门与负控；不改测试、产品或coverage值。源码依据：[RequireFilesExist](https://github.com/apache/maven-enforcer/blob/enforcer-3.6.3/enforcer-rules/src/main/java/org/apache/maven/enforcer/rules/files/RequireFilesExist.java)。

补充协议回归：真实库存服务返回 `[null]` 或有效项夹带 null 时，旧聚合在 Offer 构造处抛 NPE（26-null-inventory-red.log）。Inventory adapter现于自己的响应边界识别为BAD_RESPONSE/NO_EFFECT，保留200与安全request-id，无cause；空列表仍正常。完整示例15测试与原coverage门通过（26-null-inventory-green.log）。这项新增产品修复需要后续同源CI覆盖，不能用之前14测试来源替代。

## 当前闭合状态

CI13已将25最终null库存边界、26标准观测与此前所有消费者在同一9009810候选上完整复验，两OS及平台负控/归档全通过；Q08/Q10不再pending。先前失败和局部验证的来源边界保留，详见[25/26同源CI](ticket-25-26-ci.md)。
