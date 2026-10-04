# Changelog

## 进程内锁契约（票07）

新增明确实例范围的LocalKeyedMutex：严格活动key预算、包含等待者的安全引用回收、线程owner/重入和关闭政策。缺所需锁不执行业务；默认不再将单机实现注册为DistributedLock。保留旧公开签名并给出迁移，异常释放保留首因、诊断不复制key；真实Async observer终止不得提前解锁。

面向消费方的破坏性变更与行为变更记录(含迁移指引)。格式取意 [Keep a Changelog](https://keepachangelog.com/);
当前尚无已发布版本；原开发线为 0.1.0-SNAPSHOT，Boot4/Jackson3 新开发线明确使用 0.2.0-SNAPSHOT。内部决策全史见 [ADR 索引](docs/adr/INDEX.md)。

## [Unreleased] — 0.2.0-SNAPSHOT

### 独立模板与运行时版本（2026-10-04，ADR0054）

- Boot4/Jackson3 开发坐标改为 `cn.code91:server-facility:0.2.0-SNAPSHOT`；历史 Boot3/Jackson2 二进制与证据仍保留原坐标，不覆盖或重标旧 jar。
- 模板 `secured-api` 的 `2026.10.0` 修订号独立于应用版本；生成目录携带升级、运行及观测说明。精确 Git 来源和已解析 jar 的 SHA256 由每次交付记录保存。
- 未发布、不设未经消费者盘点的旧线停止支持日期；公共 Jackson 类型与工厂方法有明确 ABI 变化，不承诺跨发布线二进制兼容。

以下为原开发坐标下发生并保留在新线中的演进记录。

### 命令恢复与有限回执清理（2026-10-04，ADR0053）

- secured-api模板新增V4迁移与私有维护函数；启动要求完整V1–V4，原V1–V3内容不变。维护只清原回执的note ID/slug/title/body，永久保留完整命令身份、指纹、到期时间及终身额度；当前已授权且内容匹配的重试在清理后返回410，不重新执行业务。
- 清理cutoff必须有限且不晚于数据库时钟，每批1–1000，SKIP LOCKED下返回0不代表全局已清空。函数绑定实际应用schema、采用调用者权限并撤销PUBLIC执行权；操作员须显式授权并在调用前分别设置有限statement/lock预算，无自动调度或HTTP维护入口。
- 断连、超时、提交异常或进程终止本身不能证明未提交；调用方保留相同key和命令重试，并重新接受当前授权检查。保证限于同一PostgreSQL事务及保留身份，外部非事务副作用需要应用自己的协议。迁移、操作示例和边界见[命令协议](templates/secured-api/COMMANDS.md)与[实际恢复验证](docs/verification/ticket-30-command-recovery.md)。

### 显式时间、容量与模式政策（2026-10-04，ADR0043）

- DateUtil退出动态formatter缓存；保留SMART与调用时默认FORMAT Locale，环境切换不再沿用首次Locale。新业务通过应用自有ExportRequests使用Clock/ZoneId/Locale、固定严格日期、显式DST政策及正数有限MiB预算。
- NumberFormat.parseSize保留有符号向零截断，改用精确十进制；拒绝long溢出、非有限数及超出128UTF16/scale[-128,128]的输入。ASCII数字范围保留，旧hex浮点等扩展语法明确退出；零/负值仍不是资源预算。
- Patterns最多保留256项，超过4096UTF16的可信开发者模式不缓存；不再承诺跨逐出的对象身份，亦不承诺任意不可信regex的执行时限。NumberUnits弃用并保留原double/Math.round行为；应用直接选择BigDecimal舍入政策。
- [迁移说明](docs/building/explicit-value-policies.md)区分旧金样和有意收紧；JDK-only导出输入示例与普通jar兼容/资源消费者随验证入口执行。

### UUID默认与显式SnowId（票10，ADR0033）

- 新业务直接JDK UUID；SnowId自动装配默认关闭，启用时两项节点必须显式配置。IdUtil保留签名并弃用，缺provider明确拒绝，移除隐式节点0。
- SnowId保留旧55位布局、实例epoch解析及JSON long数字；每次发号的准入/恢复/序列耗尽共用正数有限预算。中断保留标志，失败不消费序列；false不再无限等待，取代ADR0023。
- 每次观察回拨均执行阈值政策；检查生成时间差范围，保留旧负epoch及历史reader。节点分配、重启高水位和数字/字符串API迁移见[标识政策](docs/building/identifier-policy.md)。

### 显式DTO映射与有界旧复制（2026-10-04，ADR0042）

- 新订单到发运DTO示例用具名record和显式构造，不依赖反射映射器；实际消费者验证字段完整性、顺序/重复行、独立容器和源码演进负控。
- `autoCopy`弃用但不删签名；未忽略的final字段、不可访问字段、不兼容具体深容器及深排序容器明确拒绝。原null默认值、浅引用和独立复制深别名政策保持并列明。
- 所有旧复制入口同步嵌套调用共用10,000工作单元和32活动层限制；实际遍历计数，循环/超限/协作中断用CopyException拒绝，异常后清理作用域。依赖无界输入或final反射的调用方必须迁移。
- 可选警告改标准SLF4J固定安全消息，backend RuntimeException不改变复制结果；不再经LogUtil二次分发。见[完整迁移](docs/building/explicit-mapping.md)。

### Cookie与HTML政策（2026-10-04，ADR0055）

- Cookie使用标准ResponseCookie，完整写入/删除保留scope；旧默认增加SameSite=Lax，删除发送空协议值及同默认flags。写入需Spring Web；最大4096ASCII头、整秒-1..400天、明确路径/安全组合，重复请求名字不再首值/末值任选。
- jsoup1.18.3→1.23.2，仍optional。16个历史比较样本明确无host HTTP href移除和iframe后备文本不保留；旧公开清洗签名保留，所有入口增加262144 UTF-16输入上限和必需策略校验。
- [迁移与适用边界](docs/building/cookie-html-policy.md)：不自动重写业务输入，不宣称HTML清洗覆盖其他输出上下文。

### 已授权有界 HTTP 重放（2026-10-04，ADR-0035）

- `@Idempotent` 整条 HTTP 路径迁至 qualified claim。新增必需 `IdempotencyAuthorization`：每次取得或重放前进行当前操作授权与命令规范化；可信 tenant/actor、具体方法与路由隔离结果。缺 Adapter/provider/capture 明确拒绝，不回落旧无 owner 执行。
- 请求/响应仅在显式有限同步目标上有界捕获；普通流式响应直通。内层 filter 成功退出后才保存允许状态与 Content-Type/Location；advice 异常、5xx、超限、断连及异步逃逸终止，不因等待更久重新执行。
- `lease` 与 `result-retention` 独立，旧 `default-ttl`/注解 TTL 仅作明确兼容。过期但仍为当前 PROCESSING 的 owner 可终止；迟到旧 owner 不得更新已替换 generation。记录 CAS 不取消旧业务副作用。
- 保留旧 SPI/构造器签名并隔离不安全回退；既有消费者必须迁移授权政策、字节预算与状态/头政策，见 [HTTP 迁移说明](docs/building/authorized-http-replay.md)。提交响应后的 Store 故障仍可能导致连接失败与宿主容器日志，不伪造完整成功响应。

### 应用消息、日志与观测（2026-10-04，ADR0049）

- MessageSource 改为 Boot/宿主优先，设施 bundle 通过明确 basename 顺序贡献；退出默认聚合委托。BusinessException 的公开本地化只查宿主 bundle，不插入异常 args/defaultMessage。
- 新日志路径直接使用 SLF4J 安全字段；下载失败只返回 Result，不重复记录原因。LogUtil、LocaleUtil、TraceIdFilter 保留签名并弃用；旧 masking/post-handler 不承诺覆盖任意秘密。
- 旧 trace 默认关闭。模板以 Boot/Micrometer 标准 W3C tracing 和应用私有 ObservationRegistry/task decorator 传播真实 scope；错误无活动 span 时生成安全 incident reference，不再默认响应 X-Trace-Id。
- 详细替代方式、旧入口边界、配置及独立可执行示例见 [应用观测迁移](docs/building/application-observability.md)。

### 有界 Excel 迁移（2026-10-04，ADR-0039）

- POI升级5.5.1，完整poi-ooxml传递图按需引入；Commons Compress1.28.0用于实际Zip64预检，仍optional。缺格式引擎成对回退由四种真实普通jar图检验。
- 旧read/write收紧为有限DEFAULT；大XLSX用显式ExcelLimits/ExcelReadOptions的forEach与Iterable写入，XLS保留1 MiB上限。补齐空单元格也计预算；回调行以及read结果中的行改为独立不可变List。
- 旧自动公式计算改为缓存读取或拒绝，默认Locale.ROOT；没有缓存不重新计算，陈旧缓存不由设施验证。写公式样式字符串仍是文本。应用需要公式计算时应在受控生产端完成并保存缓存。
- 借用输入不再被关闭，自有流/临时目录始终清理；清理失败保持Err或附首因。取消为协作边界，输出/回调的先前效果不回滚；不再宣称任意工作簿恒定内存或never-throw。完整预算和故障政策见USAGE。

### 外部服务调用迁移（2026-10-04，ADR-0048）

- 默认兼容RestClient在Boot之后克隆宿主builder，保留JSON/customizer/observation/factory；旧公开单参数构造方法与HttpClients静态签名保留并弃用。
- 历史facility.http超时仅在无宿主builder的兼容factory使用，拒绝零、负数、不足1ms和超过2147483647ms；宿主存在时改用宿主或应用Adapter自己的配置。
- 新增ResponseBodyLimit标准interceptor，限制转换前实际响应字节，并先关闭body避免transport在关闭时继续读取被拒绝尾部。两个类型化Adapter、协议错误与一次deadline重试在独立示例应用内；不扩展通用远程DSL。
- 迁移示例与已声明限制见[partner-aggregation](examples/partner-aggregation/README.md)。旧HttpClients仍有历史无配置回退与URL/cause错误，不具备新Adapter契约。

### 独立 claim 执行资格（2026-10-04，ADR-0034）

- 新增scope/fingerprint与owner/generation条件更新，区分取得、处理中、回执、冲突和不可用；旧自定义SPI默认不支持新能力，保持三方法二进制迁移路径。
- 结果保存期与租约分离，DONE过期、RELEASED和UNKNOWN保留命令绑定；只有PROCESSING租约例外允许同内容新owner。CAS仅保护记录，不能撤销外部副作用。
- maxEntries成为共享硬上限，新增单条/总回执字节预算和永久关闭；旧complete不能凭空插入，旧记录改为防御性字节所有权。新输入/旧key、TTL与记录形状严格校验。
- 旧HTTP路径未在本票迁移，不宣布整体HTTP幂等安全。详见[迁移说明](docs/building/qualified-claims.md)；12迁HTTP，29负责同库事务receipt。
### ZIP 与目录完整性（2026-10-04，ADR-0037）

- ZIP 任一条目缺失、重复 basename、读取/关闭失败即 Err，不再跳过并报成功；目录统计也不把不可读节点计作零。ZIP 保留空目录。
- ZIP 默认不覆盖，禁止输出位于输入树；拒绝链接和链接祖先。关闭完整 ZIP 后以同目录硬链接发布，不支持该能力时失败。成功结果才是消费信号，父目录创建及文件系统拒绝清理时的残留政策见 USAGE。
- 新增正数 Limits；ZIP 默认 10,000 条目、256 MiB 实际输入和输出、64 层；PathIo 默认 10,000 后代、256 MiB 逻辑文件大小、64 层。ZIP 名字最多 1,024 UTF-8 bytes，拒绝冒号/反斜线/dot 路径片段。需要更大输入的调用方显式传有限预算。
- 递归删除有界逐项进行，失败可能已经删除部分条目，不回滚；拒绝文件系统根。中断保留标志并停止后续工作，清理失败附在原始错误之后。

### 有界 CSV 迁移（2026-10-04，ADR-0038）

- 解析转为 Commons CSV 1.14.1 required 依赖。新 `CsvDialect.STRICT/LEGACY` 区分闭合引号后尾随文本；旧 `read` 保留 LEGACY。STRICT 的库语法容差有明文说明，不等于完整 RFC 语法验证。
- 旧 `read`/`write` 收紧到 1 MiB、10,000 行、128 列、每字段 1,024 个 UTF-16 单元；预算全部正数。较大任务迁移至显式 `CsvLimits` 的 `forEach`/`readAll`/导出入口；错误累计固定首次失败停止。坏 UTF-8 确定拒绝，不再替换为乱码。
- `writeMachine` 无 BOM 并保持原值；`writeSpreadsheet` 带 BOM，拒绝文档列出的公式/控制字符前缀而不改写。旧 BOM 写法继续保留原值，不能当成安全电子表格导出。
- 借用流不关闭，Path 打开的流总会关闭；取消发生在打开前时不截断已有目标。消费异常传播，先前副作用/已写前缀不回滚。安全外层行列诊断不暴露字段，外部 IO cause 不作为公开文本。详见 USAGE 与票15报告。

### 本地入口配额迁移（2026-10-04，ADR-0032）

- 拒绝非正cost、超容量成本、非有限/非正rate、非法key及同驻留key冲突政策；long大容量保持精确扣费，按实际缺额向上取整等待。
- maxBuckets改为严格槽位预算，只回收已经补满的桶；无法准入抛设施不可用，不再整体清空恢复其他主体额度。
- RateLimiterUtil普通入口从缺Bean放行改为必需；显式tryAcquireOptional/acquireOptional保留可接受的降级。HTTP缺Adapter/运行故障默认安全503；fail-open=true显式允许降级，enabled=false只关闭默认provider，仍保留注解守卫。
- @RateLimit新增IP/PRINCIPAL/GLOBAL scope；DEFAULT保留旧空key/IP、固定key/global选择，操作默认含完整类名与参数类型。SPI key编码改变且限512单元，外部后端需迁移旧key；Principal只由宿主认证提供。
- 入口额度先于默认幂等重放扣费，新重试仍计费，同请求ASYNC完成不重复扣费；业务配额不因此获得事务语义。见USAGE及ADR0032。

### 历史密文与错误诊断（2026-10-04，ADR-0040）

- 保留原AES-GCM密文和PBKDF2-HMAC-SHA256/210000读取政策，不改变旧密文、key或salt存储格式。
- 全部crypto Result失败移除原始cause，防止provider异常泄露秘密；只记录稳定错误码，程序Error仍传播。不能继续依赖加密/KDF/MAC失败中的原异常文本。
- AES key导入在解码前拒绝超过44字符，截断GCM数据在访问key前拒绝。旧raw入口保留历史数据规模；应用必须先限制输入/并发，见[历史读取与预算示例](docs/building/legacy-crypto.md)。

### 上传、MIME 与摘要迁移（2026-10-04，ADR-0036）

- 保存改按实际字节检查，新增 `saveFile(MultipartFile, Path, long, Set<String>)` 同时限制大小和类型；旧便利方法默认 10 MiB，显式大小 ≤0 返回 Err，不再表示不限制。空文件继续拒绝，拒绝后不 drain。
- 原名/customFileName 只作展示名校验，返回存储键固定为服务端 UUID.upload。调用者必须保存返回 Path，不能按原名推导；同名并发互不覆盖。根目录必须由应用独占并支持同卷硬链接，预存链接拒绝、能力不支持失败，不退化为复制到成品。
- MultipartFile 输入由设施打开并关闭，探测前缀参与同一次保存。toTempFile 取消 deleteOnExit，成功结果由调用者显式删除，失败清理自有暂存并保留清理故障。
- Tika 升至 4.1.0，固定 core detector、64 KiB 内容探测；上传不再把客户端文件名当提示，ZIP 不自动等于 XLSX。借用 InputStream 必须可 mark/reset；原始不可 mark 流读取前返回 Err，调用方应保留 BufferedInputStream。旧 String MIME 重载在 I/O 失败时抛 UncheckedIOException，不再静默返回 octet-stream。探测不是安全审查。
- Hashing 的 null 算法改走 FILE_HASH_ERROR；保留空 File 标准摘要、空/null byte[] 返回 FILE_READ_ERROR 的差异。MD5/SHA-1 仅作旧非安全校验兼容。详见 USAGE「上传、MIME 与摘要」。

### 请求边界迁移（2026-10-04，ADR-0029）

- 客户端IP默认只采用数值remoteAddr；显式 `facility.web.proxy.trusted-proxies` 才按有界可信链解析XFF，旧厂商头不再生效。
- 请求边界统一清理兼容SessionUser，适配宿主Principal，覆盖短路、Callable与ASYNC/ERROR；`isLoggedIn()`弃用为非认证检查。
- trace尊重并恢复宿主MDC，提供accept-inbound政策；TraceIdFilter通过唯一边界调用，旧独立注册禁用。迁移与资源边界见ADR0029和USAGE。

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
