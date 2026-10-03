# 两个外部服务的应用级 Adapter

这个独立 Maven 应用用 Catalog 的单个商品与 Inventory 的泛型库存列表形成 `PartnerModule.offer`，并演示 Inventory 的 204 预约命令。业务协议与错误分类属于应用；运行时库只增加标准 `ClientHttpRequestInterceptor` 实现 `ResponseBodyLimit`。没有远程任务 DSL。

在仓库根目录先运行 `mvnw.cmd install`（Linux 为 `./mvnw install`），再运行 `mvnw.cmd -f examples/partner-aggregation/pom.xml clean verify`。完整、可复现入口是 `java verification/Verify.java all --fresh`：它复制到仓库外包含空格和中文的新目录，使用复制的 Wrapper 构建，验证普通应用 jar／库 jar 的生产依赖图，在独立 128 MiB JVM 中启动两个真实服务并运行 5 个应用周期、200 次大响应拒绝。缺少真实覆盖率的全新副本必须构建失败。

## 配置与所有权

必须由可信部署配置提供 `partners.catalog.base-url/token` 与 `partners.inventory.base-url/token`。请求方法仅接受业务 SKU，不接受调用者提供的任意 URL。示例测试使用 loopback HTTP；部署时选择服务的 HTTPS 地址，并通过应用自己的两个 `HttpClient` bean 设置需要的 TLS、代理与连接超时。凭据由部署环境提供，不能提交到仓库或记录在日志中。

每个 Adapter 注入 Boot 的 prototype `RestClient.Builder` 并克隆，保留宿主 Jackson 转换器、customizer 和 observation registry。应用明确选择两个长期持有的 JDK `HttpClient`，连接超时各为 1 秒、默认不跟随重定向，关闭应用时 `shutdownNow`。这两个 bean 是有意的传输政策，**不会隐式继承 Boot 全局 factory／SSL／proxy／connect timeout**；需要这些政策时应在这两个应用 bean 中接入宿主设置。每次尝试只建立轻量 factory，复用网络 client。

| 每个服务的属性 | 默认 | 允许值 |
|---|---:|---|
| `request-timeout` | `PT2S` | ISO-8601 Duration，1 ms 至 60 s |
| `max-response-bytes` | 1048576 | 1 至 16777216 |

上限按实际解压后字节执行，发生在 JSON 物化前；它不是整个对象图或任意并发量的堆上限。应用的入口并发、业务对象规模及 CPU 转换器仍须有自己的政策。响应流不离开 `exchange`，框架在成功和失败时关闭响应；设施包装先关闭 body，再调用 transport 的 close，避免 JDK transport 为复用连接继续 drain 被拒绝的正文。调用者直接使用 interceptor 时也必须关闭返回的 response。

示例为每个操作建立一个 `System.nanoTime` 截止时间，在实际创建请求时把剩余整毫秒交给 Spring JDK transport。该 transport 的定时器覆盖响应头和响应体；取消还会保留调用线程的 interrupt。此约束不能强制终止任意宿主 customizer、CPU 转换器或不合作代码，计时器也不构成实时调度保证。

## 协议、失败和重试

`Catalog.find`、`Inventory.stock/reserve` 各发起一次应用尝试。`Catalog.findWithRetry` 是唯一显式重试入口：此示例协议声明 GET 无副作用，仅在收到 `503` 且 `Retry-After: 0` 时允许再尝试一次；两次共享原截止时间。其他 Retry-After 形式与其他状态直接交回调用者，不自行等待、解析日期或循环重试。JDK transport 自身对安全请求的连接恢复行为仍属于其实现；真实故障测试单独计数实际收到的请求。

预约 POST 从不自动重做。服务已接受命令后断连、响应超时、取消和无法确认的服务端错误返回 `UNKNOWN`，调用方进入协议规定的查询／人工核对流程。增加任意 `Idempotency-Key` 头不构成重试许可。本示例协议规定 4xx 在执行前拒绝命令；其他第三方必须重新评估这项约定。

`PartnerFailure` 区分连接失败、连接超时、响应超时、4xx、5xx、意外状态、坏响应、响应超限、取消与未知传输故障。实际 HTTP 状态已知时保留它，仅透传长度不超过 256、无控制字符的 `x-request-id` 与 `retry-after`；没有远端正文、URL、token 或嵌套异常。可读错误枚举属于应用，不加入库的通用错误码。SKU、返回商品/库存字段和预约数量有明确的本地契约；调用者参数错误在发送前抛出。

## 验证边界

`PartnerContractTest` 通过真实 HTTP 证明宿主 snake_case JSON、customizer、标准 Micrometer/Brave B3 传播，父 trace 内两个不同的 client span，泛型列表、204、双应用并发、关闭隔离、4xx/5xx/重定向、坏 JSON／空体／字段缺失／截断、gzip 膨胀、慢头／慢体、取消、拒绝连接、提交后断连及统一 deadline。Brave 只在测试依赖中，用来验证宿主配置继承；生产观测由宿主装配，示例不增加另一个全局 tracer。

连接超时分支通过标准 `HttpClient.sendAsync` 边界的确定性失败 future 验证，没有声称在本机配置了真实 SYN 黑洞。其他网络故障来自 loopback 服务。普通 jar 消费者使用手写 JSON 字面量，且运行时禁止 JUnit、Mockito、Brave；精确堆观测、源版本、依赖图与制品哈希由统一 runner 留证。票25完成状态与跨平台来源见仓库验证报告。

测试使用 JaCoCo 官方离线插桩，避免 Windows 原生 agent 的路径编码边界；测试成功后在 test 阶段恢复原始字节码，运行时依赖不包含 coverage agent。测试失败可能留下插桩输出，修复后使用 `clean verify` 重新执行，不能打包失败构建残留。验证目录同时包含空格、中文与希伯来字符。
