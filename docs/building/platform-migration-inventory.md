# Boot / Jackson 迁移影响登记（票 21–24）

旧协议基线为 Java 25 / Boot 3.5.16 / Framework 6.2.19 / Jackson 2.21.4（annotations 2.21），票 21 已由真实消费者和两 OS CI 冻结。票 22 切换 [Boot 4.1.1 目标依赖](boot4-platform.md)，票 23 按 [ADR-0046](../adr/0046-jackson3-application-ownership.md) 迁移公共类型、应用作用域、不可变 builder、错误和字段流预算；目标现状及命令见 [23 报告](../verification/ticket-23-jackson3.md)。下表和“22 新增接合交接”保留为历史迁移输入，不能当作当前实现说明。

## 票 21/22 的公共类型和调用链快照

| 位置 | 当前边界 / 配置归属 | 后续责任 |
|---|---|---|
| `json/Jsons` | 构造器与 `mapper()` 暴露 ObjectMapper；泛型读取暴露 TypeReference；树 API 暴露 JsonNode；Result 失败含 Jackson cause | 23 逐一迁移签名和 checked/unchecked 异常，出迁移说明 |
| `json/JsonUtil` | 同样暴露 TypeReference/JsonNode；所有默认操作经进程级 registry，公开 `use` / `registry` | 23 移除其作为新默认配置权威的地位；旧消费者需显式迁移 |
| `json/JsonsRegistry` | 可单独构造；静态实例由 JsonUtil 拥有。DEFAULT 可被 Spring 替换；其余 namespace 自建 mapper，未继承应用政策 | 23 明确应用作用域 registry/namespace 语义；21 不暗改 |
| `json/support/JsonConfig.Builder` | Module、JsonInclude.Include、ObjectMapper、旧 Consumer<ObjectMapper>；新增 Consumer<JsonMapper.Builder> 构建期入口 | 23 构建不可变 mapper；旧突变回调是明确破坏面，不能只换 import |
| `json/support/TypeRef` | 继承 Jackson TypeReference；集合/Map 工厂和 getType | 23 更换父类型，回归无直接 Jackson import 的 TypeRefTest |
| `json/support/InputStreamSerializer` / `Deserializer` | Jackson 基类、generator/parser/context 签名；无参无上限兼容，新增 int 解码字节预算 | 23 迁移签名并复验预算/所有权/Result 失败；保留 21 金样 |
| `autoconfigure/FacilityJsonAutoConfiguration` | after Boot JacksonAutoConfiguration，ObjectMapper class/bean 条件；新 Jsons 应用 bean，用户 bean 让位；旧 registry 仍全局发布 | 22 更新技术模块归属；23 用 JsonMapper/标准 builder 和本应用生命周期 |
| `web/filter/RepeatableRequestFilter` | 21 时有私有 **全限定名** Jackson ObjectMapper；05 已删除，改用应用 FacilityHttpErrors | 原隐藏 Jackson 入口已退出；23 迁 FacilityHttpErrors，24 复验默认禁用和显式预算的 413 协议 |
| `web/util/ResponseUtil` | 通过静态 JsonUtil 写响应，受全局默认影响 | 23/26 迁移依赖归属；04 负责 HTTP 错误状态 |
| `web/exception/FacilityHttpErrors`（04 新增） | 构造器暴露 ObjectMapper，Servlet 错误写入使用注入 mapper；私有 FrameworkResolver 组合 Spring 标准异常解析 | 23 迁移公开 Jackson 类型，保留已验证的安全错误、提交后处理、traceId 与 HTTP 状态政策 |
| `autoconfigure/FacilityWebAutoConfiguration.facilityHttpErrors`（04 新增） | ObjectProvider<ObjectMapper> 与 Jackson2ObjectMapperBuilder fallback；错误页 Bean 暴露 Boot 类型 | 22 已迁 ErrorPage/Registrar/Registry；23 迁 mapper 归属与 fallback，24 实测 ERROR 派发 |
| `http/HttpClients` / `FacilityHttpAutoConfiguration` | 静态 holder 查 RestClient；自动装配自己调用 RestClient.builder，不能推定使用了 Boot JSON/customizer | 25 主责宿主 builder；23/26 接合 |

路径均相对 `src/main/java/cn/code91/facility`。除了以上代码，JsonsRegistry 的 Javadoc 也直接提及 Jackson；搜索必须覆盖全限定名和父类型，不能只看 import。

构造链为 Boot `JacksonAutoConfiguration` → 应用 ObjectMapper → 注入 `Jsons` → 服务；MVC converter 使用同一应用 mapper。独立消费者分别在默认和用户 customizer 场景用字面协议样本验证两个出口。用户显式提供 Jsons 时由用户负责其与 MVC 策略是否一致；自动装配不覆盖该 bean。静态 registry 仍会跨 context 覆盖，且 bean 初始化期间捕获 DEFAULT 可能早于装配，不承诺恢复/隔离。

## 装配与引擎依赖

- 根工程只 import Boot BOM，**没有 Boot parent**。现有 11 个 `@AutoConfiguration` 注册文件必须随模块迁移复验，不得假设 BOM 会替换插件或补齐技术模块。
- 直接 Boot 入口包括 AutoConfiguration / Before / After / conditions、JacksonAutoConfiguration、TaskExecutionAutoConfiguration、MessageSourceAutoConfiguration、ConfigurationProperties / EnableConfigurationProperties、Servlet FilterRegistrationBean。Jackson、任务、消息、Servlet/MVC、RestClient 应按 Boot 4 技术模块逐项归位。
- 根测试使用 `spring-boot-starter-test`、ApplicationContextRunner / WebApplicationContextRunner / FilteredClassLoader、JUnit Jupiter 5、AssertJ、Spring mock web、Mockito，另有 `archunit-junit5:1.5.1` 引擎。22 须同步 JUnit 6 / ArchUnit JUnit 6 与需要的技术测试 starter，独立引擎探针证明被发现；不能用“构建绿色但 0 测试”验收。
- 五条 ArchUnit 契约为 package cycle、error 只依赖 JDK、main 不依赖 Logback、main 不反向依赖 autoconfigure、Excel facade 不依赖 POI。依赖检查只保留逐项有理由的 exception；不能为迁移新增宽泛 ignore。
- annotation processor paths 的 Boot configuration processor 与 Lombok 版本仍需同步；`AutoConfiguration.imports`、configuration metadata、普通 jar 无 BOOT-INF 与 class major 69 由独立非 Web consumer 检查。
- 21 新增的独立 Web consumer 显式选择 Boot 3 `spring-boot-starter-web`。22/24 必须把其 MVC/Jackson/Servlet 技术依赖一起迁移；不能依赖根 test scope 掩盖 consumer 缺包。04 的根测试容器是单独的测试 fixture，不成为生产库传递依赖。

旧完整声明账本见 [Java 25 基线](java25-baseline.md)，目标模块与版本见 [Boot 4 平台](boot4-platform.md)。运行报告保存根与 Web consumer 的 `effective-pom.xml` 和 `dependency-tree.txt`，含所有实际传递版本；版本表不能替代运行时解析证据。

## 官方目标版本复核

2026-10-03 实际从 Maven Central 取得以下 POM（HTTP 200），并核对 Boot BOM 属性。这里只核对可获取性，不把目标依赖下载等同于目标验证。

| 目标 | 可获取版本 / 来源 |
|---|---|
| Boot BOM | [4.1.1](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom) |
| BOM 管理的 Framework / Jackson / JUnit | 7.0.9 / 3.1.5 / 6.0.3 |
| Jackson 技术模块 | [spring-boot-jackson 4.1.1](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-jackson/4.1.1/spring-boot-jackson-4.1.1.pom) |
| Servlet 技术模块 | [spring-boot-servlet 4.1.1](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-servlet/4.1.1/spring-boot-servlet-4.1.1.pom) |
| ArchUnit JUnit 6 引擎 | [1.5.1](https://repo.maven.apache.org/maven2/com/tngtech/archunit/archunit-junit6/1.5.1/archunit-junit6-1.5.1.pom) |

[Boot 4 官方迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)明确技术生产/测试模块拆分，默认改 Jackson 3。[Jackson 官方发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)明确不可变 builder、三种 Java 8 模块并入 databind，以及 core/databind 的 tools.jackson 组和包；annotations 仍是 com.fasterxml.jackson。`JsonMapperBuilderCustomizer` 和 `JacksonAutoConfiguration` 新包为 `org.springframework.boot.jackson.autoconfigure`；覆盖 JSON mapper 需声明 JsonMapper，不能仅照搬 ObjectMapper bean。[官方 API](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/JsonMapperBuilderCustomizer.html)

## 金样、资源及错误边界

`verification/json-consumer` 保存人工编写、在旧产物实测的 UTF-8 字面样本，来源和配置见其 README。默认与定制政策包含 record、泛型、时间/时区、枚举、long ID、Decimal scale、null/Optional、未知字段和非法/尾随数据。HTTP 使用回环真实端口、五秒请求截止、每应用最多四个容器线程；runner 限制 JVM 256 MiB / 90 秒并要求正常退出。constructed 和 injected 共享断言，不由同一 mapper 生成 expected。

非 BMP 的 raw 字符和 surrogate escape 都是有效 JSON；分别保留旧 wire 文件作为观察记录，目标迁移优先保证解析后的 Unicode/数值/类型契约，不为等价转义新增兼容开关。若 23 出现预期的等价 wire 差异，应说明其独立语义证据并审阅金样变更；不得放宽类型、丢失精度或忽略未知政策变化。

消费者显式选择 `facility.web.exception.use-problem-detail=true`，使旧平台 malformed / strict trailing HTTP 输入返回真实 400；旧默认 envelope 的 HTTP 200 缺陷交 04 修复，不把缺陷冻结成协议。错误 message/detail 不是兼容金样；23 必须移除 Jsons 中 payload 截断日志与 WrappedError 参数，21 不宣称已满足这项安全契约。

流字段新正数预算按解码后字节计，序列化最多读 N+1，解码前按 Base64 长度拒绝明显超限，再验证实际字节数。长度闸门把临时解码数组限制在 N+2 字节以内（3 字节分组的最多 2 字节固定舍入开销），返回结果严格 ≤N；没有先无界分配后再判断。JSON 文本本身已由 parser 读取，需要宿主另外设置文档/字符串预算。字段输入归 serializer、返回解码流归 caller；根 JSON I/O 归属遵从 AUTO_CLOSE_SOURCE / AUTO_CLOSE_TARGET。旧 no-arg / ≤0 无上限是兼容入口，不是受限入口的安全保证。票 21 测试有可重放 seed 210025、128 例、1–32 字节预算，不通过耗尽宿主内存测试上限。

**23 收缩要求**：迁移后的新推荐 InputStream 字段入口必须提供正数预算，或明确不注册该能力；不能仍以无参/≤0 默认无界作为新默认。继续在 Base64 解码数组分配前控制上界，并在 parser 读取/形成完整 JSON 字符串前设置应用输入预算。关闭 Jsons 敏感 payload 日志/错误参数以及预期解析异常和程序错误的 catch 边界缺口，不把兼容旧实现当成目标平台已完成。

## 22–24 合入规则

唯一非发布集成线为 `codex/server-facility-next`。22 改 BOM、技术模块、processor、测试引擎，并精确列出归 23 的 Jackson 编译缺口；23 完成公开类型/serializer/不可变 mapper/错误通道迁移，并清空缺口。24 在同一候选提交及普通产物上闭合 Windows/Linux 全质量门、非 Web/Web consumer、缺席/覆盖和 imports/metadata/注册顺序。24 闭合前不进入 master、不发布中间制品、不宣称同 jar 双主版本二进制兼容。

## 22 新增接合交接

- 根实际目标编译诊断逐项见 [Jackson 清单](../verification/ticket-22-jackson-diagnostics.md)。仅有主编译阶段的真实输出；测试和消费者因上游未产出 jar 尚未编译，不将静态 import 清单冒充实际测试错误，更不能把工具链探针的 5 项说成原全库测试已发现。
- 根使用 `spring-boot-jackson`，Web consumer 使用 `spring-boot-starter-webmvc`。FilterRegistrationBean、TaskExecutionAutoConfiguration、MessageSourceAutoConfiguration 和 context runner 保持旧包；新 Jackson/MVC/Tomcat/Servlet context 归属逐项核对，见目标账本。Jackson2ObjectMapperBuilderCustomizer 的 Java 签名和策略迁移归 23。
- 24 必须覆盖无 Servlet、无 Jackson 技术模块、无 validation provider、无 Tika/POI、Caffeine 或 context-support 仅缺一个、用户 Bean 覆盖与实际过滤器注册顺序。22 只解析声明依赖，不承诺这些运行组合已通过。
- 04 的真实 HTTP 错误策略/Servlet ERROR 派发与共享 Tomcat fixture 在目标平台继续保留；测试容器不能变成库的生产依赖。其完整运行归 24；23 要避免 JSON 错误 catch 或消息转换迁移改变既有 HTTP 状态/安全错误输出。
- 04 的 `GlobalExceptionHandlerTest` 与 `HttpErrorContractTest` 仍使用 Jackson2ObjectMapperBuilder、ObjectMapper、SimpleModule/serializer 和 MappingJackson2HttpMessageConverter；这些是 23 的额外 Jackson 测试迁移输入。22 只更新 Boot 技术进口，未执行这些受主编译阻塞的测试。
- 已同步 05，保留其 BOM 管理的显式 `junit-jupiter-params` 测试依赖；下载、请求重读、流式幂等三个 HTTP 场景的容器自动装配归属已更新。05 移除私有 413 mapper，因此该类不再属于 23 编译缺口。05 在旧平台的1323项绿色不外推为目标平台已发现。
- 05 `BoundedResponseCapture` 的 Servlet 6.0 旧覆盖不能直接外推到 6.1：`HttpServletResponseWrapper` 三个新 `sendRedirect` 重载和 `ServletResponseWrapper.setCharacterEncoding(Charset)` 直接委托 wrapped response，可绕过旧 String 重载。**24 必须在目标 Servlet 6.1 补齐覆盖并实际测试响应捕获、commit 与预算语义**。官方依据：[HTTP wrapper](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletresponsewrapper)、[Servlet wrapper](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/servletresponsewrapper)。本登记不算这些场景已绿。

## 23 已实施与 24 交接

- 68 个旧 Jackson 主编译诊断已逐项迁移；主/测试源码恢复编译。额外发现的 Spring 7 HttpHeaders.containsHeader、ParameterValidationResult/NoResourceFoundException 构造器变化分别作等价源迁移，不混记为 Jackson 错误。
- Jsons 和宿主 MVC 使用应用不可变 mapper；registry bean 独立，默认复用 Jsons bean，静态 JsonUtil 保留 standalone。旧 mutable customize 已删除，旧三种 Java8 模块不再加载；annotations 原坐标保留。
- Result 预期失败不带 payload、原异常、cause 或日志；真实用户 codec 的程序故障在 WRAP_EXCEPTIONS 开/关均传播，宿主政策不被改写。字段流正预算、默认有限 1MiB、Base64 N+2 分配界及 factory parser 文档/字符串限制均有公共入口测试。
- Spring7 的 ProblemDetail 需标准 mixin；兼容 fallback 使用标准 converter mapper，宿主 mapper 保持同一实例。错误 type 显式设置 about:blank 保留旧金样。Boot4 error path/include-* 改为 spring.web.error.*；真实 HTTP 状态和安全字段断言保留。
- 独立 Web consumer 改用标准 JsonMapperBuilderCustomizer，保留原金样文件。旧默认尾随值接受仅在该比较应用显式配置；等价 Unicode escaping/字段顺序按 JSON 值比较，不放宽数值和字符串类型。
- ResponseUtil 等静态门面只享受类型迁移和安全错误通道，仍不取得应用政策；26 主责注入式替代。25 主责 RestClient 宿主 builder。
- **24 仍负责**：Servlet6.1 新重载捕获/commit 预算，所有缺类/覆盖/注册顺序矩阵、同一普通 jar 的完整平台 consumer/两 OS 质量门。23 的本机报告不代替这些场景，不发布中间制品。

## 24 消费者盘点与退出结论

票 24 的实现/结果以 [报告](../verification/ticket-24-platform-integration.md) 和 [ADR-0047](../adr/0047-boot4-consumer-integration.md) 为准，不把未执行的 OS 场景计作完成。

| 入口 / 迁移辅助 | 消费者证据与处理 |
|---|---|
| 旧 mutable customize / Jackson 2 类型 / 三种独立 Java8 模块 | 23 已删除或迁移；普通 jar 与两个 JSON consumer access 模式没有借助兼容 starter 编译或运行 |
| new Jsons(mapper) | 21 冻结的 constructed 消费者真实调用，24 继续与 injected 对照；保留显式应用所有权入口 |
| Spring 注入 Jsons/registry | 默认复用应用 JsonMapper，用户 Jsons/mapper、primary 和歧义由真实依赖图与 HTTP 验证；无静态发布 |
| enableJava8Support | 库 presets/现有源示例仍调用，已是 documented no-op；无运行期模块安装，不以平台票删除现存调用 |
| JsonUtil / ResponseUtil / 旧异常门面 | 库尚有显式静态/兼容消费者；不是应用默认权威。26 负责注入式替代，24 不先删后补 |
| Servlet 6.1 wrappers | Charset 和三个 redirect 重载补齐，真实 Tomcat 验证头/字节、clearBuffer、状态/Location 与禁止错误重放；旧捕获预算/流式测试保留 |
| Caffeine/context-support 半缺图 | 真实 Maven 图复现并修复缺支持类时无 CacheManager；按 ADR0015 进入已有回退。无 TTL/容量的新业务政策仍归 08/33 |
| Tika 4.1.0 / 无 Tika | 13 的实际字节上传通过两个普通 jar 生产图验证；必需类型政策缺 detector 明确拒绝，未选择类型政策仍可用；POI 缺席不阻断其他能力 |

旧 Boot 3.5/Jackson 2 发布线与新 Boot 4/Jackson 3 线保持源码/二进制破坏边界，不宣称同一 jar 兼容两个主版本。旧金样与历史 CI SHA 留存，当前 consumers 明确选择目标版本；模板与最终发布步骤由 31/33 负责。
