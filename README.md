# server-facility

> Spring Boot server 端基础设施脚手架 —— 一个 **deep module**（窄接口、宽实现）：
> 用应用拥有的服务实例、值类型与纯函数入口，封装 server 开发反复要写的 Result 错误建模、
> ID 生成、JSON、i18n、Web 过滤链、限流 / 缓存 / 锁 / 幂等 / 加解密 / 脱敏等横切能力。

**读者定位**：本仓库的第一读者是 **coding agent**。本 README 是唯一入口，阅读约定三条：

1. **权威链**：代码 + `docs/adr/` ＞ `docs/USAGE.md` / `docs/DESIGN.md` ＞ 本文 ＞ `CONTEXT.md`（术语基准）。文档与代码冲突时以代码 + ADR 为准，并回头修订文档。
2. **按任务路由**：先查下表，只加载与当前任务相关的文档，不要全量通读。
3. **快照数据**：本文标注「快照」的计数允许滞后，权威取 `./mvnw verify` 实际输出与对应源文件。

## 任务路由

| 你的任务 | 去处 |
|---|---|
| 在应用中使用某能力（API 语义、示例、返回约定） | 本文[特性矩阵](#特性矩阵)定位簇 → [USAGE](docs/USAGE.md) 同名小节 |
| 接合多个外部HTTP服务与失败政策 | [真实聚合应用](examples/partner-aggregation/README.md)（类型化Adapter、自有client与有限预算） |
| 创建独立的JWT保护MVC应用 | [应用模板](templates/secured-api/README.md)（独立POM/Wrapper，应用自有信任与Actor） |
| 配置或关闭某个自动装配组件 | 本文[装配开关](#装配开关) → USAGE「装配开关全表」（权威、含默认值） |
| 用自己的 bean 替换 facility 默认实现 | USAGE「消费方须知 · 两类让位机制」 |
| 排查「配置不生效 / bean 不是我的 / 意外降级」 | 本文[消费方陷阱速查](#消费方陷阱速查) → USAGE「消费方须知」 |
| 消费方升级 facility 版本 | [CHANGELOG](CHANGELOG.md)（破坏性 / 行为变更的迁移指引） |
| 修改本仓库代码 | 本文[维护须知](#维护须知) → [DESIGN §7 一致性宪法](docs/DESIGN.md) |
| 理解设计动机、包依赖结构、翻历史决策 | [DESIGN](docs/DESIGN.md) → [ADR 索引](docs/adr/INDEX.md)（56 条） |
| 查术语定义（deep module / Seam / Result-style …） | [CONTEXT](CONTEXT.md) |
| 追溯某特性的需求与实施过程 | `docs/superpowers/specs/` 与 `docs/superpowers/plans/`（过程档案，只读） |

## 硬事实

- **坐标**：`cn.code91:server-facility:0.2.0-SNAPSHOT`，单模块 jar。
- **环境**：JDK25、Spring Boot4.1.1、Jackson3.1.5；Maven Wrapper固定3.10.0并校验下载；候选`all`/`platform`另需Python3.11+。当前依赖与支持边界见[平台账本](docs/building/boot4-platform.md)，尚未发布制品。
- **命名**：包根 `cn.code91.facility.*`；类前缀 `Facility*`；配置前缀 `facility.*`；i18n bundle `i18n/facility-messages_*`。
- **命令**：`./mvnw verify`（Windows `mvnw.cmd verify`）= 库质量门；`java verification/Verify.java all --fresh` = 干净依赖仓库、库质量门、独立消费者、资源及先决条件检查。integration/resources/all另需`PG_BIN`指向PostgreSQL18.6原生工具；库fast/verify不需数据库。完整准备命令和第二个测试 JDK 要求见 [Java 25 构建说明](docs/building/java25-baseline.md)。
- **质量门**（不达即构建失败，禁止以调低门槛的方式通过）：
  - JaCoCo BUNDLE 级：INSTRUCTION / LINE ≥ 0.88，BRANCH ≥ 0.75；
  - `maven-dependency-plugin` `analyze-only` + `failOnWarning`：依赖账目必须干净；
  - ArchUnit 5 条架构红线（随测试套运行，见[维护须知](#维护须知)）。
- **当前验收**：01–33共33票已closed；被测实现`2d14f6a`完成本机fresh all/platform、Windows/Linux同候选四角色JAR身份、历史78→80升级及最终Standards/Spec双轴审查（各0 findings），见[票33报告](docs/verification/ticket-33-release-evidence.md#final-repaired-candidate-qualification)、[契约账本](docs/verification/ticket-33-contract-ledger.md)与[终审原文](docs/verification/ticket-33-final-review.md)。当前开发坐标未发布。
- **最近完整候选门**：`2d14f6a` / [CI25 attempt1](https://github.com/yiwer/server-facility/actions/runs/37203623059)在Windows/Ubuntu完成all、platform、归档及四角色比较，实际正向测试1790/15/132/153全部通过；[安全证据摘要](docs/verification/ticket33/final-evidence-summary.json)保留身份、环境、覆盖率与边界。后续文档提交不重标为被测source。各历史source、失败与修正保留在[执行账本](docs/superpowers/plans/2026-10-03-server-facility-next-execution.md)；[票31历史升级与benchmark](docs/verification/ticket-31-template-upgrade.md)保持原始身份。

## 仓库地图

```
pom.xml                                   版本管理、optional 依赖、三道质量门配置（含各处 ignore 的理由注释）
src/main/java/cn/code91/facility/         29 个功能子包（见特性矩阵）；autoconfigure/ 是唯一装配入口
src/main/resources/
  META-INF/spring/…AutoConfiguration.imports   11 个 @AutoConfiguration 注册表
  i18n/facility-messages*.properties           内置文案（base / en / zh_CN / zh_TW 四份）
  application.example.yaml                     facility.* 全配置项带详注样例（Spring 不加载，供复制）
src/test/java/cn/code91/facility/         测试；architecture/ArchitectureTest.java 为 5 条 ArchUnit 红线
docs/USAGE.md                             消费方 API 手册（用法权威）
docs/DESIGN.md                            设计文档；§7 一致性宪法 = 修改本仓库的成文规则
docs/adr/                                 56 条架构决策记录（INDEX.md 索引；0000 为模板）
docs/superpowers/                         specs / plans / 评审 findings（SDD 过程档案）
CHANGELOG.md                              行为与破坏性变更 + 消费方迁移指引
CONTEXT.md                                域术语权威
```

## 引入

```xml
<dependency>
    <groupId>cn.code91</groupId>
    <artifactId>server-facility</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

放到 classpath 即通过 Spring Boot 自动装配生效（11 个 `@AutoConfiguration`：Core / Id / Json / Locale / Async / Web / RateLimit / Cache / Lock / Http / Idempotency）。装配模型三句话（细节见 USAGE「消费方须知」）：

- 默认服务按各自类型或名称条件让位。新代码构造器注入应用的 `Jsons`、`MessageSource`、`Executor`、`CacheManager` 与业务 Adapter；旧静态查容器入口仅供迁移。
- Trace、Repeatable、Idempotency filter 按类型让位并通过各自接线单次注册；AccessLogInterceptor 仍需先关闭开关再替换。默认不启用旧 trace 与重复读，具体注册名称见 USAGE。
- `hash` / `crypto` / `masking` / `csv` / `excel` 无自动装配开关；格式引擎可用性、显式预算和调用方资源所有权仍适用。

Web / XSS / MIME 探测 / Excel / Caffeine 等依赖声明为 Maven `optional`，按能力引入完整依赖图（见 USAGE「optional 依赖矩阵」）。缺类行为按能力区分：未选缓存不装配 manager，显式选缓存却缺任一配套依赖会启动失败；缺 Excel 引擎通过公开 Result 返回错误。不要把 optional 理解为已选择能力可以静默降低保证。

## 5 分钟上手

```java
// 1) Result<T,E>：显式错误通道，取代 try/catch 与 null
Result<User, WrappedError> r = userService.findById(id);
String name = r.map(User::getName).orElse("unknown");

// 2) 新业务 ID：JDK UUID；旧 SnowId 须显式节点和有限等待政策
UUID id = UUID.randomUUID();
String uuid = id.toString();

// 3) JSON：构造器注入本应用的 Jsons，复用其 mapper 政策
// OrderExport(Jsons jsons) 中保存依赖；预期解析/序列化失败走 Result
Result<String, WrappedError> json = jsons.serialize(user);

// 4) 日志：应用自己的 SLF4J Logger，只输出审核过的元数据
var log = org.slf4j.LoggerFactory.getLogger(OrderQueries.class);
log.info("Order query completed; outcome={}", outcome.name());

// 5) 异步：显式传入应用注入的 Executor，并选择整体预算
Result<String, Throwable> out = Async.supply(() -> httpGet(url), applicationTaskExecutor)
        .map(Response::body)
        .timeout(Duration.ofSeconds(2))
        .recover(e -> "fallback")
        .await();
```

## API 约定

读写任何 facility API 前先掌握四条全局约定（成文依据：DESIGN §7 一致性宪法）：

- **显式失败通道**：IO / 解析等 Result 型入口返回 `Result<T,E>`，`Result.empty()` 表达「成功但无值」（ADR-0007）。必需依赖和程序故障立即失败；Spring 标准 SPI、qualified claim 与 HTTP 使用各自公开结果/异常协议，不能假定所有 API 都包装异常。
- **命名双家族（C4）**：`XxxUtil` = 静态门面（可能有状态、参与 Spring 装配交互）；复数名词类（`Numbers` / `Patterns` / `Filenames` / `Collects` / `Hashing` …）= 以值操作为主的工具（Patterns保留明确有界的编译缓存）。历史例外：`HttpClients` 复数名但按门面对待。
- **null 契约（C1）**：数据参数 null → null-safe 语义回退；函数型与必需依赖参数 null → `requireNonNull` fail-fast；公共 API 可空性以 `jakarta.annotation.Nullable` 标注（error 包例外，javadoc 散文表达，C5）。
- **「≤0 = 不限制」（C2）**：表达「无限制」的统一拼法；ADR-0028/0036/0037/0038/0046 明确例外：启用 repeatable body、响应捕获、上传、ZIP/目录、CSV及显式 JSON InputStream 字段预算必须为正数，0/负数拒绝；上传便利入口固定10 MiB，JSON无参注解入口固定1 MiB。

## 特性矩阵

**实现任何通用能力前先查此表**——已有的直接用，不要在消费方重复造轮子。各簇完整方法与语义见 USAGE 同名小节。

| 簇 | 入口 | 能力 |
|---|---|---|
| `result` | `Result<T,E>`（sealed） | 显式错误通道：`ok`/`err`/`empty`/`map`/`flatMap`/`orElse`/`fromOptional` |
| `error` | `WrappedError` / `FacilityErrorType` / `ErrorTypeInterface` | 错误码 + i18n 消息键 + 可扩展错误类型；error 包纯 JDK（C1 断环） |
| `structure` | `Tuple` / `Triple` | 轻量二/三元值容器 |
| `common` | `NullSafe` / `Collects` | 空安全与集合便捷 |
| `context` | 构造器注入；兼容 `SpringContextHolder` | 默认注入应用自己的服务；旧门面按实例归属发布/撤销 context |
| `id` | JDK UUID / 显式 `SnowIdGenerator` | UUID 默认；旧55位协议、显式节点与有界等待，[迁移](docs/building/identifier-policy.md) |
| `json` | `Jsons` / `JsonConfig` | 应用 mapper 注入、不可变 builder、安全 Result 错误通道；JsonUtil 保留 standalone 静态预设 |
| `log` | 应用 SLF4J；旧 LogUtil | 新路径标准日志与字段白名单；旧二次分发只保留兼容，不是审计 |
| `date` | `DateUtil` | legacy SMART/调用时Locale、无动态缓存；[显式业务时间](examples/export-input/README.md) |
| `number` | `Numbers` / `NumberFormat` / `NumberUnits` | 精确有界容量解析、legacy显示；NumberUnits弃用迁移 |
| `hash` | `Hashing` | 文件/字节哈希 |
| `io` | `PathIo` / `Zipping` | 有界完整目录统计/逐项删除；ZIP完整关闭后不覆盖发布，实际读写/条目/深度预算和清理失败可见 |
| `path` | `Filenames` | 文件名清洗、路径穿越防御、危险扩展名拦截 |
| `mime` | `MimeTyping` | 基于魔数的 MIME 探测（optional：tika-core） |
| `pattern` | `Patterns` | 有界开发者模式缓存与legacy形状判断；不保证任意regex时限 |
| `copy` | `CopyUtil` | 有界旧复制；新路径见[显式DTO示例](examples/order-mapping/README.md) |
| `locale` | 应用 MessageSource；旧 LocaleUtil | 宿主优先的明确 bundle 顺序，静态入口保留兼容并弃用 |
| `async` | `Async<T>` | 惰性组合、整体 deadline 与协作取消；有界平台线程默认，应用显式注入 Executor |
| `web.*` | filter / interceptor / exception / session / response / argument / util / download / upload | Servlet 栈：traceId、可重复读请求体、访问日志、全局异常、统一响应、安全上传下载、完整Cookie scope与显式有限HTML片段（optional：jsoup；ADR0055） |
| `ratelimit` | `RateLimiterUtil` / `RateLimiter`（SPI） | 本地令牌桶：合法成本、精确扣费与有界准入；必需门面 + 显式 Optional 降级 |
| `web.ratelimit` | `@RateLimit` | 方法级入口限流；IP / Principal / Global；429 + `Retry-After`，不可用默认 503 |
| `cache` | Spring `CacheManager` / `Cache` | 显式选用 Caffeine，固定名称及正 TTL/条目容量；用户 manager 优先；旧 `CacheUtil` 弃用。见[政策](docs/building/local-cache.md) |
| `lock` | `LocalKeyedMutex`；旧锁入口兼容 | 实例内同步互斥、严格活动键预算和安全回收；缺少所需实现不执行 action，等待与实际持有分开 |
| `http` | 应用 `RestClient.Builder` / 具名 Adapter | 宿主 HTTP 配置与有限业务政策；旧 `HttpClients` 保留弃用兼容，见[聚合示例](examples/partner-aggregation/README.md) |
| `idempotency` | `IdempotencyStore`（SPI） | 执行资格、指纹绑定与有界回执；lease/retention分离，旧入口保留迁移（ADR0034） |
| `web.idempotency` | `@Idempotent` | 当前授权/可信身份/操作隔离的有界同步响应重放；必须提供宿主授权 Adapter（ADR0035） |
| `crypto` | `CryptoUtil` | AES-256-GCM 对称加解密 + HMAC + 密钥派生/管理 + Base64/Hex（静态门面，纯 JDK，无需配置） |
| `masking` | `MaskUtil` | 纯函数脱敏（旧 LogUtil 默认集成）：秘密/JWT/身份证/银行卡/邮箱/手机号六规则，校验位（mod11-2/Luhn）抑误伤；`LogUtil` 写前集成，`setMaskingEnabled(false)` 可关（静态门面，纯 JDK，无需配置） |
| `csv` | `CsvUtil` | 有界 CSV 读写（Commons CSV required）：strict/legacy 方言、逐行消费、UTF-8 字节/行列/字段预算；机器与电子表格导出政策分离 |
| `excel` | `ExcelUtil` | Excel（xls/xlsx）读写（POI optional）：有界XLS/HSSF与XLSX/SAX，显式Locale/公式缓存，SXSSF一行窗口及自有临时预算；成对引擎缺失返回err |

## 装配开关

配置前缀为 `facility.*`；开关默认值与关闭范围按能力区分。缓存、重复读与旧 trace 默认关闭；限流/幂等关闭默认 provider 后仍保留 HTTP 注解守卫。下表为速查，**权威全表（含默认值）见 USAGE「装配开关全表」**；可直接复制的带详注样例见 `src/main/resources/application.example.yaml`。

| 前缀 | 作用 |
|---|---|
| `facility.id` | 默认关闭；显式节点/epoch/回拨政策及 `wait-timeout` |
| `facility.web.trace` | 旧 TraceId 过滤器，默认禁用；仅显式兼容 opt-in，新应用使用标准 Micrometer tracing |
| `facility.web.repeatable-request` | 可重复读请求体：`max-body-bytes` / `include-content-types` / `exclude-paths` |
| `facility.web.access-log` | 访问日志拦截器：`slow-threshold-millis`（超阈升 WARN 标记 slow；0=禁用） |
| `facility.web.cors` | CORS：`allowed-origins`（默认空 = 不开）/ `allowed-methods` / `allow-credentials` |
| `facility.web.exception` | 安全 HTTP 错误：默认 RFC 9457 ProblemDetail；`use-problem-detail=false` 显式旧 envelope（ADR-0027） |
| `facility.ratelimit` | 限流：`default-capacity` / `default-permits-per-second` / `max-buckets` / `fail-open=false` |
| `facility.cache` | 默认关闭；`enabled` / `cache-names` / 正 `default-ttl` / 正 `maximum-size`；选中须有 Caffeine + context-support |
| `facility.lock` | 本地互斥：正 `max-locks` 活动键预算；不默认提供分布式 adapter |
| `facility.http` | 兼容 client：仅宿主 builder 缺席时使用 `connect-timeout` / `read-timeout` |
| `facility.idempotency` | 幂等：`lease` / `result-retention` / `max-entries` / 请求、响应与合计回执字节预算；`default-ttl` 仅兼容回退 |

## 消费方陷阱速查

一行版；细节与处置全在 USAGE「消费方须知」：

- **应用拥有 i18n**：Boot `spring.messages.*` 或应用名为 `messageSource` 的 bean 优先；仅在两者缺席时提供设施 bundle。需要设施文案时，将 `i18n/facility-messages` 明确列在应用 basename 之后，见[迁移说明](docs/building/application-observability.md)。
- **JSON 实例归属**：注入的 `Jsons`/`JsonsRegistry` 属于本应用；`JsonUtil` 的 standalone 静态 registry 独立，Spring 启停不覆盖它。多应用不要用静态预设代替自己的 HTTP mapper 政策。
- **SpringContextHolder 已弃用**：新路径构造器注入所需服务。兼容门面只发布首个成功刷新 context，只有发布者能撤销；被拒绝的 context 不自动接管，关闭不会影响 owner（ADR-0025）。
- **按组件接管**：服务与 Trace/Repeatable/Idempotency filter 使用各自类型/名称条件；AccessLogInterceptor 先关开关再替换。不要给同一 filter 增添重复容器注册，接线细节见 USAGE。
- **无校验 provider 也能启动**：properties 类不用 `@Validated`（ADR-0013），取值约束在组件构造器兜底。

## 维护须知

修改本仓库代码时的成文规则。完整条款：DESIGN §7 一致性宪法；工作流：SDD（spec → plan → TDD 实施，档案在 `docs/superpowers/`）。

**完成判定**：`./mvnw verify` 库质量门全绿，发布/集成另运行 `java verification/Verify.java all --fresh`。门槛失败修代码、补测试，**不得调低 pom 门槛值或随手加 ignore**（依赖账目确需 ignore 时必须注明理由，样例见 pom 注释）。

票22/23是已批准的非发布迁移例外：只在 `codex/server-facility-next` 记录明确归属的暂时失败，独立 `platform` 子集不能代替 `all`。票24恢复完整门后才进入主线或候选发布。

**架构红线**（ArchUnit，`src/test/java/cn/code91/facility/architecture/ArchitectureTest.java`，违反即测试红）：

1. `packages_are_cycle_free` —— 包依赖无环、自底向上单向流动（依赖地图见 DESIGN §2）；
2. `error_package_depends_only_on_jdk` —— error 包纯 JDK（C1 断环，ADR-0010；故 error 包禁用 `jakarta.annotation.Nullable`）；
3. `main_code_does_not_depend_on_logback` —— 主源码零 logback（ADR-0011）；
4. `autoconfigure_is_not_depended_on_by_main_packages` —— 组件包不感知装配；配置属性类与消费组件同包；
5. `excel_facade_does_not_depend_on_poi` —— POI 类型只允许出现在包私有读写实现（ADR-0039，保留0021类型隔离理由）。

**一致性宪法速览**（C1–C5，新代码必须遵守，存量「触碰即对齐」；全文 DESIGN §7）：C1 null 契约（数据参数 null-safe / 依赖参数 fail-fast / 外部交互走 Result）、C2 历史无限制拼法及逐项正预算例外、C3 降级日志政策（装配期与低频防护 WARN / 每请求高频预期降级静默）、C4 命名双家族、C5 `jakarta.annotation.Nullable` 标注（error 包例外）。

**新增能力范式**（对齐存量形态，勿发明新形态）：

- 需要 bean / 配置属性 → 新建 `Facility*AutoConfiguration` 并注册进 `AutoConfiguration.imports`，properties 前缀 `facility.*`、不用 `@Validated`（构造器兜底，ADR-0013）；可替换点用 `@ConditionalOnMissingBean` 落 Seam，**不为单一实现预先抽象接口**（hypothetical seam，见 CONTEXT）。
- 无状态纯门面 → 零装配、零 properties、恒可用（对齐 `crypto` / `masking` / `csv`）。
- 重依赖 → Maven `optional`，隔离缺类加载并记录每个实际依赖图。显式选择能力必须兑现保证或明确失败；不要用低保证后端静默替代。缓存成对依赖见 ADR0031，Excel 完整格式图见 ADR0039。
- Web 集成与通用能力分包（`ratelimit` / `web.ratelimit`，`idempotency` / `web.idempotency`），避免反向依赖成环。

**文档同步义务**（改完代码没同步文档 = 任务未完成）：

- 行为变更 → 同步 USAGE 对应小节；破坏性 / 行为变更 → CHANGELOG 补迁移指引；
- 新架构决策 → 复制 `docs/adr/0000-adr-template.md` 顺延编号，更新 `docs/adr/INDEX.md` 与 DESIGN §5；
- 新术语 → CONTEXT.md；本 README 的快照计数随之刷新。
