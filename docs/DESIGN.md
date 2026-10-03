# server-facility 设计

Boot4.1.1/Jackson3.1.5目标平台已在`80670fa`通过Windows/Ubuntu完整构建、普通jar消费者、Servlet6.1、真实依赖矩阵和独立引擎控制，见[同源CI闭合](verification/ticket-24-ci.md)。03/05/06/13/17/24适用平台项关闭；09/14/15随后在`c2f0f6b`通过[两端完整门](verification/ticket-09-14-15-ci.md)。其余能力继续实施，最终候选组合由33验收。

## 1. Deep module 哲学

server-facility 遵循 Ousterhout 的 **deep module** 原则:接口窄、实现宽。消费方看到的是
少量易记的入口 —— 静态门面(`IdUtil`、`JsonUtil`、`LogUtil`、`DateUtil`、`LocaleUtil`、
`CopyUtil` …)、值类型(`Result<T,E>`、`Tuple`、`Triple`)与一组自动装配 bean —— 背后
是被反复打磨、覆盖边界的实现。设计目标是让"引一个依赖就少写一大片样板",而不是暴露
可配置旋钮的大工具箱。

三条一以贯之的取向:

- **错误显式化**:可预期失败一律走 `Result<T,E>` 的错误通道,不用 `null`、不靠受检异常
  穿透。序列化、日期解析、文件 IO 等失败点全部返回 `Result`。
- **降级安全**:自动装配的每个 bean 都 `@ConditionalOnMissingBean` 兜底,消费方声明同类
  bean 即覆盖;配置属性不用 `@Validated`(ADR-0013),消费方即便没有校验 provider 也能启动。
- **窄依赖**:主源码不依赖 logback(ADR-0011)、error 包纯 JDK(ADR-0010),Web/XSS/MIME
  等重依赖一律 optional,按需引入。

## 2. 包簇依赖地图

29 个顶层功能子包按责任聚类(另有 `id.support`/`json.support`/`web.*` 等下层子包),依赖自底向上单向流动(ArchUnit `packages_are_cycle_free` 守护):

```
              autoconfigure  ← Spring Boot 装配入口(11 个 @AutoConfiguration)
                   │  依赖各组件包,自身不被任何主包依赖(ArchUnit 守护)
   ┌───────────────┼─────────────────────────────────────────┐
 web.*           async        json / copy / date / number / …  ← 组件层
   │               │                     │
   │           result(值)         result / error
   │
 result / error / log / json / locale / mime / path          ← web.* 依赖的基座
   │
 error(纯 JDK) ← structure(纯值) ← common          ← 叶子层
```

- **叶子层**:`error`(纯 JDK,C1 断环)、`structure`(纯值,C2 断环)、`result`。
- **组件层**:各能力簇,依赖基座与叶子,互不横向依赖。
- **装配层**:`autoconfigure` 依赖组件包组装 bean;ArchUnit 规则
  `autoconfigure_is_not_depended_on_by_main_packages` 保证它不被反向依赖 —— 组件包不感知
  自己如何被装配,配置属性类与消费组件同包(见断环 C3)。

## 3. 三组包循环断环(spec §4.4)

迁移前源仓存在三处包循环,本工程逐一断开并以 ArchUnit 规则锁定:

| 循环 | 成因 | 断法 |
|---|---|---|
| **C1** `error → locale → context → error` | 错误消息在 error 包内做 i18n 解析,拉入 locale/context | error 包退回纯 JDK,i18n 解析上移到展示边界(`LocaleUtil.localize`);ADR-0010 |
| **C2** `common → structure → copy → common` | `WrappedContainer`/`WrappedDataType` 横跨 structure 与 copy | 二者零消费者,直接 drop;structure 退为纯值叶子 |
| **C3** `web → autoconfigure`(properties 反向边) | 5 个 web properties 曾住 `autoconfigure.properties`,web 组件依赖它 → 又被 autoconfigure 依赖 | properties 归位到各自消费组件同包(trace/repeatable→`web.filter`,access-log→`web.interceptor`,exception→`web.exception`,cors→`web`);`autoconfigure.properties` 包消亡 |

五条 ArchUnit 规则(`ArchitectureTest`,随测试套运行):`packages_are_cycle_free`、
`error_package_depends_only_on_jdk`、`main_code_does_not_depend_on_logback`、
`autoconfigure_is_not_depended_on_by_main_packages`、
`excel_facade_does_not_depend_on_poi`(ADR-0021:锁定 `ExcelUtil` 门面零 POI 类型引用,
POI 类型隔离在包私有读写实现（0039保留0021类型隔离理由）)。

## 4. 自动装配范式

11 个 `@AutoConfiguration` 经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
注册:`Core`、`Id`、`Json`、`Locale`、`Async`、`Web`、`RateLimit`、`Cache`、`Lock`、`Http`、`Idempotency`。共同约定:

- **兜底不抢占**:每个 bean `@ConditionalOnMissingBean`(按类型或名称),消费方声明的同名/
  同类型 bean 永远优先。
- **按类型让位 Boot**:`FacilityAsyncAutoConfiguration` 的 `facilityAsyncExecutor` 条件为
  `@ConditionalOnMissingBean(Executor.class)`,并 `@AutoConfigureAfter(TaskExecutionAutoConfiguration)`
  —— 让 Boot 的 `applicationTaskExecutor` 先注册；facility 仅缺席时提供有界平台线程池。
  消费方显式向 Async 传入 Executor；静态默认不查容器；执行段上下文、整体预算和取消见 ADR-0026（部分替代 ADR-0002）。
- **i18n 聚合抢注 primary**:`FacilityLocaleAutoConfiguration` 以 `@AutoConfigureBefore(MessageSourceAutoConfiguration)`
  注册 `@Primary` 的 `AggregatedMessageSource`(名为 `messageSource`),把各模块贡献的具名
  `MessageSource` bean 聚合为一个;`facilityMessageSource` 提供 facility 自带的 i18n 文案
  (basename `i18n/facility-messages`,含 base + en/zh_CN/zh_TW 四份,`fallbackToSystemLocale=false`
  使非中英 locale 确定性回落英文 base)。
- **Web 条件门**:`FacilityWebAutoConfiguration` 整体 `@ConditionalOnWebApplication(SERVLET)`,
  各组件再由 `facility.web.*.enabled` 单独 `@ConditionalOnProperty` 开关。
- **并非所有能力簇都装配**:11 是「需要 bean/配置属性」的子集数,不是能力簇总数——无状态、无可
  替换策略的静态门面型能力(`hash`/`crypto`/`masking`/`csv`/`excel`)不注册 `@AutoConfiguration`、
  无 `facility.*` properties,恒可用,`AutoConfiguration.imports` 不含它们(ADR-0019、ADR-0020、
  ADR-0021)。`masking` 的引擎是单个预编译合并 `Pattern`(六规则 alternation)+ 单遍 `Matcher`
  扫描 + 按命中组 dispatch 到对应遮蔽函数 + 身份证/银行卡的校验位级联(mod11-2/Luhn 通过才遮,
  详见 ADR-0020);`log` 包在消息写盘与 `LogPostHandler` 分发之前默认调用该引擎(单向依赖
  `log → masking`,由 `MaskUtil` 零依赖设计——仅 `java.*`、零 facility 引用——保证;ArchUnit
  `packages_are_cycle_free` 守护的是未来出现反向边时立即报警,而非断言方向本身),`masking`
  自身零依赖、零装配、零 bean。`csv`/`excel` 同属这一类:`CsvUtil` 以 Commons CSV required 依赖提供有界逐行消费与明确方言（ADR-0038）;
  `ExcelUtil` 依赖 POI(optional),但装配开关的角色由**运行时探测**(而非
  `@ConditionalOnClass`)承担——静态门面无 bean 无从条件化,改为缓存的双类 `Class.forName`
  探针,POI 缺失时四个 API 全返 `err(EXCEL_LIB_MISSING)` 而非崩溃(ADR-0021);两包均零
  properties、零 `@AutoConfiguration`。

## 5. ADR 索引

43 条架构决策记录(`docs/adr/`);0001-0008 为源仓继承决策,0009 起为本工程决策。并行票按预留编号登记，当前编号不连续。

| ADR | 决策 |
|---|---|
| 0001 | jsoup / tika-core 声明为 Maven optional |
| 0002 | 异步线程池按类型匹配注入,`@AutoConfigureAfter` 让位 Boot |
| 0003 | RFC 7807 ProblemDetail 与显式 legacy 的历史理由保留；默认/安全边界部分由 0027 替代 |
| 0004 | `FacilityException` 接口解耦异常层次 |
| 0005 | `LogUtil` Throwable 参数对齐 SLF4J 末位 |
| 0006 | `LogUtil` 内部状态 `compareAndExchange`（进程级 handler 缓存策略由 0025 替代，单次分发保证保留） |
| 0007 | `Result.empty()` 表达"成功但无值" |
| 0008 | SnowId `parseTimestamp`/`parseInfo` 改 instance 方法 |
| 0009 | Result/Tuple/Triple 纯别名精简 |
| 0010 | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
| 0011 | 删除 `LogUtil.setLevel`,主源码零 logback 依赖 |
| 0012 | `formatMessage` 委托 SLF4J `MessageFormatter` |
| 0013 | 配置属性不用 `@Validated`,构造器兜底 |
| 0014 | 限流令牌桶 + `RateLimiter` SPI(Seam),web 集成分离 `web.ratelimit` 避环 |
| 0015 | 缓存 `CacheUtil` 门面复用 Spring `CacheManager`,Caffeine + spring-context-support 成对 optional |
| 0016 | 分布式锁 `DistributedLock` SPI + 单机 `InMemory`,real seam Redisson 升级示范 |
| 0017 | 完整幂等历史状态机；全站/无界捕获部分由 0028 替代 |
| 0018 | HTTP client `HttpClients` 门面委托 `RestClient` + `Result` 化 |
| 0019 | crypto 加解密门面——安全默认 AES-256-GCM、内管 IV、不透明失败通道、纯 JDK |
| 0020 | 日志脱敏——`LogUtil` 写前集成(`LogPostHandler` 证伪)+ 校验位误伤抑制 + SECRET substring 语义 |
| 0021 | 保留裸列表、无表头ORM与POI optional；CSV政策由0038、Excel预算/公式/临时资源/实际引擎保证由0039部分替代 |
| 0022 | `LogUtil` 门控基于调用方 logger(per-package 生效)+ StackWalker 惰性解析 |
| 0023 | SnowId 回拨:false 无界等待绝不抛;spin 上限随阈值放宽 |
| 0024 | Java 25、固定校验 Wrapper、独立普通 jar 与跨平台入口；Boot3中间版本由0045部分替代 |
| 0025 | Context 注册归实例所有、刷新/关闭隔离；构造器注入为默认，ID/日志兼容入口不跨 context 缓存 Spring bean |
| 0026 | Async：显式执行器、整体 deadline、同步上下文作用域与协作取消；部分替代 0002 |
| 0027 | 安全 RFC 9457 错误策略贯通 Filter/MVC/ERROR，真实状态和必要头；已提交边界、宿主政策与显式 legacy 迁移 |
| 0028 | 普通响应直通、显式有界捕获；repeatable 正预算、流所有权与真实 Servlet 生命周期 |
| 0029 | 默认连接peer/显式可信代理；Servlet与Callable作用域清理身份、恢复宿主trace，部分替代0014来源假设 |
| 0032 | 本地配额正成本/精确余额、有界主体回收、required/Optional政策和可信身份入口计费；部分替代0014 |
| 0034 | 独立claim的可信scope/fingerprint、owner条件完成、结果到期不重授、共享硬预算与永久close；部分替代0017 |
| 0036 | 正数实际字节预算、借用MIME流不关闭、生成存储键与同卷hardlink不覆盖发布；保留0001 optional边界 |
| 0037 | ZIP完整关闭后hardlink不覆盖发布，有界目录/归档结果与真实失败清理；纯JDK普通jar消费 |
| 0038 | Commons CSV明确strict/legacy，有界行消费/便利读取，机器原值与电子表格拒绝政策；部分替代0021 |
| 0039 | POI5.5.1按格式消费；小XLS/HSSF、有界XLSX/SAX、显式公式缓存与SXSSF自有临时预算；部分替代0021 |
| 0040 | 保留旧AES-GCM/PBKDF2协议；安全Result失败、应用输入/并发预算与独立普通jar历史回执消费者 |
| 0041 | 保留Result/领域错误语义；浅引用所有权、必需回调与集合算术边界，纯Java普通jar消费者 |
| 0044 | JSON 应用 Jsons 注入、构建期回调和显式流预算；保留旧入口，冻结消费者金样并登记 22–24 非发布集成门 |
| 0045 | Boot4目标依赖、按技术拆分模块、JUnit6/ArchUnit与独立工具链探针；23关闭Jackson编译、24恢复完整门 |
| 0046 | Jackson3应用mapper/registry所有权、不可变builder、安全错误和正数字段预算；替代0044旧兼容阶段 |
| 0047 | 真实依赖图和普通jar/HTTP平台门；补全Servlet6.1重载与缺任一缓存依赖回退，OS证据分别登记 |
| 0048 | 宿主builder/应用Adapter拥有外部HTTP配置，实际字节预算、有限重试与未知副作用结果；部分替代0018 |
| 0050 | 独立JWT保护MVC模板：应用信任/Actor、标准Security授权、安全401/403/503与上下文所有权；扩展0027/0029接合 |

## 6. 质量门

- **09/14/15跨平台闭合**：集成 `c2f0f6b` 的Windows/Ubuntu完整门、平台门和归档全部通过，[CI37147633803](verification/ticket-09-14-15-ci.md)登记同源证据，三票closed。
- **最新本地完整门（含16与11/25/27）**：被测`99ae71a` Windows `all --fresh`为库1600/0/0/0、模板47/0/0/0、聚合应用14/0/0/0，共92命令全部通过。Excel4种真实引擎依赖图、64MiB400,000行/200失败/恶意XML、独立样本和openpyxl导出oracle通过；原质量门、既有消费者/平台矩阵/资源/负控均PASS，详见[16报告](verification/ticket-16-bounded-excel.md)。Windows CI模板失败仍由重跑诊断，新的Excel组合尚待Linux/CI；11/16/25/27均保持verification-pending，不混用本地与CI状态。
- **当前目标平台（2026-10-04）**：票24的 `31e7765` Windows空仓库 `all --fresh` 为1449/0/0/0；instruction92.8076%、line93.3576%、branch85.2258%，原5架构及依赖门通过，见 [票24证据](verification/ticket-24-platform-integration.md)。普通jar/core/crypto、JSON双应用、3Web、5依赖图11JVM、Tika有无上传、5次资源周期及3工具链负控PASS。Servlet6.1新重载在本机实际通过；同产品集成`80670fa`现已通过Windows/Ubuntu完整CI，详见[平台闭合](verification/ticket-24-ci.md)；各环境精确值以各自artifact为准。
- **旧平台参照（Windows / Java25 / Boot3.5.16）**：`5a59d2f` 为1323项、0失败/错误/跳过，含5条ArchUnit及原覆盖率/依赖门；同产品的 `2304a57` 已通过 Windows/Ubuntu `all --fresh`，见 [票05 CI证据](verification/ticket-05-ci.md)。旧平台绿色不外推到当前Boot4；Servlet6.1新重载已由24在目标平台复验关闭。
- **覆盖率**:JaCoCo check 绑 `verify`,BUNDLE 级 INSTRUCTION/LINE ≥0.88、BRANCH ≥0.75
  (旧平台快照 instruction92.9939% / line93.3940% / branch86.1614%)，当前目标实测见上，门槛保持。
- **依赖账目**:`maven-dependency-plugin` `analyze-only` 绑 `verify` 且 `failOnWarning` ——
  used-undeclared / unused-declared 必须清零(运行时 SPI / 聚合传递依赖显式 ignore 并注明理由)。

## 7. 一致性宪法(2026-07-06,九项拍板,评审批次 5)

> 把全库隐性惯例升格为成文条款;新代码必须遵守,存量按「触碰即对齐」渐进。
> 三个 breaking 修正(B1/B2/B3)已在 0.1.0-SNAPSHOT 窗口内落地。

**C1 null 契约**:数据参数 null → null-safe(按返回类型语义回退:null / 空容器 / 回退值);
函数型与必需依赖参数 null → fail-fast(`requireNonNull`);IO/解析/外部世界交互 → `Result`
通道。存量差异已被测试锁定、不改行为,各类级 javadoc 如实自述(`Numbers` setScale(null)→null、
`NumberFormat` format(null)→""、`MimeTyping` detect(byte[]) 仅 null/空数组前置回退
FALLBACK；其旧 detect(InputStream,String) 的IO失败现抛UncheckedIOException，不再静默回退（ADR0036）、`Patterns` 全员 null-safe 且
`compile` 底层原语刻意 fail-fast)。

**C2 「无限制」拼法**:统一为「**≤0 = 不限制**」(properties javadoc/USAGE/注释同一拼法);
ADR-0046 的 JSON InputStream 字段是正预算例外：显式 ≤0 拒绝，无参注解入口固定1MiB。
ADR-0036 的上传预算也必须为正数，≤0 经Result拒绝，便利入口固定10MiB；无无界上传路径。
ADR-0037 的ZIP/目录与ADR-0038的CSV预算也全部正数，旧便利入口采用已登记有限默认；显式增大预算不等于宿主并发准入。
ADR-0032 的限流capacity/rate/cost/maxBuckets均必须正且rate有限；注解capacity/rate=0仅表示继承默认，绝不表示无限制。
不引入公共常量。**已批准例外（ADR-0028）**：启用 repeatable body 与选定响应捕获必须为正预算，0/负数拒绝；`RepeatableRequestWrapper` 便利构造器使用 10 MiB。禁用 repeatable 使用 `enabled=false`，不得用无界预算替代。

**C3 降级日志政策**:装配期一次性动作、低频防护动作、配置故障信号 → **WARN**;每请求
高频路径的预期降级 → **静默**(政策依据:信号须可见,噪音须抑制)。现状审计(2026-07-06,
全部符合):WARN 侧——锁 executeWithLock 无 bean 降级执行、锁/幂等溢出 fail-closed 拒绝、
cache ConcurrentMap 回退(装配期)、幂等响应失配(配置故障)、
CopyUtil null key drop;静默侧——LockUtil.tryLock/unlock 无 bean、CacheUtil 无 CacheManager、HttpClients 无定制 bean 回退默认。
ADR-0032已替代限流clear-all与默认无Bean放行：新key只回收补满桶或拒绝；普通门面不可用抛异常，Optional显式降级仍不逐请求记日志。

**C4 门面命名双家族**:`XxxUtil` = 静态门面(可能有状态/参与 Spring 边缘/装配交互);
复数名词 = 纯函数无状态工具。新组件按此归家族,存量零改名。历史例外:`HttpClients`
复数名但依赖 `RestClient` bean,按门面对待(如实记载,不粉饰)。

**C5 可空性标注**:公共 API 可空参数/返回值用 `jakarta.annotation.Nullable`;首批已补
result/structure 簇(`Result`/`Tuple`/`Triple`);**error 包例外**——ADR-0010 纯 JDK 边界
(ArchUnit `error_package_depends_only_on_jdk` 锁定)禁止 jakarta 依赖,`WrappedError`
以 javadoc 散文表达可空性。其余存量触碰即补。

**Breaking 记录(用户拍板 2026-07-06)**:B1 `ErrorTypeInterface.formatFallback`
public default → 接口 private(契约面收缩);B2 删除 `getSeverity()`/`ErrorSeverity`
(零消费 YAGNI),`getDetailedDescription` 改三段格式;B3 `NullSafe.allNotNull(空数组)`
false → true(vacuous truth 对齐业界惯例;null 入参仍 false)。
