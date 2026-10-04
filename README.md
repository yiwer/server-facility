# server-facility

> Spring Boot server 端基础设施脚手架 —— 一个 **deep module**（窄接口、宽实现）：
> 用少量静态门面、值类型与自动装配 bean，封装 server 开发反复要写的 Result 错误建模、
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
| 理解设计动机、包依赖结构、翻历史决策 | [DESIGN](docs/DESIGN.md) → [ADR 索引](docs/adr/INDEX.md)（49 条） |
| 查术语定义（deep module / Seam / Result-style …） | [CONTEXT](CONTEXT.md) |
| 追溯某特性的需求与实施过程 | `docs/superpowers/specs/` 与 `docs/superpowers/plans/`（过程档案，只读） |

## 硬事实

- **坐标**：`cn.code91:server-facility:0.1.0-SNAPSHOT`，单模块 jar。
- **环境**：JDK25、Spring Boot4.1.1、Jackson3.1.5；Maven Wrapper固定3.10.0并校验下载。目标平台普通jar和跨平台消费者已通过票24，见[平台账本](docs/building/boot4-platform.md)。其他票继续在集成分支实施，尚未发布制品。
- **命名**：包根 `cn.code91.facility.*`；类前缀 `Facility*`；配置前缀 `facility.*`；i18n bundle `i18n/facility-messages_*`。
- **命令**：`./mvnw verify`（Windows `mvnw.cmd verify`）= 库质量门；`java verification/Verify.java all --fresh` = 干净依赖仓库、库质量门、独立消费者、资源及先决条件检查。integration/resources/all另需`PG_BIN`指向PostgreSQL18.6原生工具；库fast/verify不需数据库。完整准备命令和第二个测试 JDK 要求见 [Java 25 构建说明](docs/building/java25-baseline.md)。
- **质量门**（不达即构建失败，禁止以调低门槛的方式通过）：
  - JaCoCo BUNDLE 级：INSTRUCTION / LINE ≥ 0.88，BRANCH ≥ 0.75；
  - `maven-dependency-plugin` `analyze-only` + `failOnWarning`：依赖账目必须干净；
  - ArchUnit 5 条架构红线（随测试套运行，见[维护须知](#维护须知)）。
- **当前验证边界（2026-10-04，Boot4.1.1/Jackson3.1.5）**：`80670fa`的Windows/Ubuntu `all --fresh`和独立平台控制全部通过，[同源CI与artifact](docs/verification/ticket-24-ci.md)已登记。普通jar/core/crypto、JSON双应用、3Web、5依赖图11JVM、有/无Tika上传、资源周期及负控均已执行；03/05/06/13/17/24适用平台项关闭。本地完整门为1449/0/0/0，各CI精确数值见对应原报告；尚未实施的业务协议不在此通过范围。
- **09/14/15跨平台闭合**：集成 `c2f0f6b` 已通过Windows/Ubuntu完整门、平台门与归档，见[同源CI37147633803](docs/verification/ticket-09-14-15-ci.md)。各环境精确数值以其artifact为准，三票已closed。
- **前次本地完整门（含16与11/25/27）**：被测`99ae71a` Windows `all --fresh`为库1600/0/0/0、模板47/0/0/0、聚合应用14/0/0/0，共92命令全部通过，含4种Excel实际依赖图、64MiB400,000行与恶意XML/200失败清理、独立格式样本及openpyxl导出oracle。原覆盖率/5架构/依赖、既有消费者/平台矩阵/资源/负控均PASS，详见[16报告](docs/verification/ticket-16-bounded-excel.md)。同源CI12已通过Windows/Ubuntu完整门、平台门和归档，11/16/27 closed，详见[CI37156503739](docs/verification/ticket-11-16-27-ci.md)。25后加Inventory `[null]` 修复已随[CI13](docs/verification/ticket-25-26-ci.md)跨平台闭合；本段精确计数仅为原本地来源。
- **前次本地完整门（26）**：冻结`72a37b6` Windows `all --fresh`为库1614/0/0/0、模板52/0/0/0、聚合应用14/0/0/0，92命令与原质量门/负控全部PASS，见[26报告](docs/verification/ticket-26-host-observability.md)。含25库存null修复的联合候选已通过[CI13](docs/verification/ticket-25-26-ci.md)，25/26 closed。
- **持久业务本地门（28）**：冻结`4a5ad5d`完整92步为库1614/partner15/模板74均零失败；随后整秒JDBC预算修复在`e0fd5b3`完成模板76项、原质量门、真实PostgreSQL可执行包两线程模式CRUD/重启与coverage负控。两次来源和范围分别记录在[28报告](docs/verification/ticket-28-persistent-business.md)。CI14同源Ubuntu通过、Windows打包数据库启动失败，见[CI记录](docs/verification/ticket-28-ci.md)；28保持verification-pending；[原生进程修复](docs/verification/ticket-28-ci14-fix.md)已合入，CI15 Ubuntu通过、Windows并发迁移测试失败；[并发测试宿主修复](docs/verification/ticket-28-ci15-fix.md)已完成当前库1712与完整模板78项，等待CI16联合验证。
- **本地互斥门（07）**：冻结`df7f788`的Windows完整97步通过，库1637/0/0/0；原SPI兼容、64MiB轮转与Async观察结束后仍持锁均已执行。之后与28合并的候选等待CI，精确范围见[07报告](docs/verification/ticket-07-local-lock.md)。
- **授权重放本地门（12）**：`3563d92`的Windows `all`通过，库1667/0/0/0、模板76/0/0/0及独立Security重放消费者、PostgreSQL打包重启、原质量门与负控全部通过。含07；精确来源见[12报告](docs/verification/ticket-12-authorized-replay.md)。12仍待Linux，28的CI14失败独立保留。
- **Cookie/HTML本地门（32）**：`1f307a3`的Windows `all --fresh`100命令PASS，库1655/0/0/0、模板76/0/0/0；jsoup有/无两个普通jar图、64MiB深度10000与10000次成功/拒绝及原质量门/负控通过，见[32报告](docs/verification/ticket-32-cookie-html.md)。该冻结来源不含12或28 CI修复；合并后联合候选及Linux仍待CI，不能拼接计数冒充新来源通过。
- **显式映射本地门（19）**：修复Map key与value回调间中断检查后的`4bcad87`完成Windows `all --fresh`110命令PASS，库1712/0/0/0、模板76/partner15及原门全部通过；具名DTO业务/编译负控、旧binary和64MiB资源消费者通过，见[19报告](docs/verification/ticket-19-explicit-mapping.md)。Linux仍待CI，此本地结果不覆盖CI15 Windows模板并发迁移失败。
- **旧平台参照**：Boot3.5.16 的 `5a59d2f` 在Windows为1323项全绿、instruction92.9939% / line93.3940% / branch86.1614%；包含相同产品的 `2304a57` 已通过两OS `all --fresh`，见 [票05 CI证据](docs/verification/ticket-05-ci.md)。这些结果不能视为当前目标平台全绿。

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
docs/adr/                                 49 条架构决策记录（INDEX.md 索引；0000 为模板）
docs/superpowers/                         specs / plans / 评审 findings（SDD 过程档案）
CHANGELOG.md                              行为与破坏性变更 + 消费方迁移指引
CONTEXT.md                                域术语权威
```

## 引入

```xml
<dependency>
    <groupId>cn.code91</groupId>
    <artifactId>server-facility</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

放到 classpath 即通过 Spring Boot 自动装配生效（11 个 `@AutoConfiguration`：Core / Id / Json / Locale / Async / Web / RateLimit / Cache / Lock / Http / Idempotency）。装配模型三句话（细节见 USAGE「消费方须知」）：

- 核心 bean（`messageSource`、异步执行器、全局异常处理器等）`@ConditionalOnMissingBean` 兜底——你声明同类 / 同名 bean 即让位，facility 只填空缺。
- Web 过滤器 / 拦截器**不靠竞争 bean**，由 `facility.web.*.enabled` 开关控制；声明同类型 filter 会双重入链而非顶替。
- `hash` / `crypto` / `masking` / `csv` / `excel` 五簇是零装配静态门面：恒可用、无开关、无 properties。

Web / XSS / MIME 探测 / Excel / Caffeine 等重依赖声明为 Maven `optional`，按需自行引入（全表见 USAGE「optional 依赖矩阵」）；未引入时对应装配因 `@ConditionalOnClass` 不生效，或运行时探测降级返 `err`（`ExcelUtil`，ADR-0021），均不影响其余簇。

## 5 分钟上手

```java
// 1) Result<T,E>：显式错误通道，取代 try/catch 与 null
Result<User, WrappedError> r = userService.findById(id);
String name = r.map(User::getName).orElse("unknown");

// 2) ID 生成：雪花 ID 与 UUID
Long id = IdUtil.snowId();
String uuid = IdUtil.uuidSimpleStr();

// 3) JSON：序列化返回 Result，不抛异常
Result<String, WrappedError> json = JsonUtil.serialize(user);

// 4) 日志：应用自己的 SLF4J Logger，只输出审核过的元数据
var log = org.slf4j.LoggerFactory.getLogger(OrderQueries.class);
log.info("Order query completed; outcome={}", outcome.name());

// 5) 异步：惰性 pipeline，结果落到 Result
Result<String, Throwable> out = Async.supply(() -> httpGet(url))
        .map(Response::body)
        .recover(e -> "fallback")
        .await();
```

## API 约定

读写任何 facility API 前先掌握四条全局约定（成文依据：DESIGN §7 一致性宪法）：

- **失败走 `Result`**：可预期失败（IO / 解析 / 序列化 / 外部交互）一律返回 `Result<T,E>`，不抛受检异常、不以 null 表示失败；`Result.empty()` 表达「成功但无值」（ADR-0007）。
- **命名双家族（C4）**：`XxxUtil` = 静态门面（可能有状态、参与 Spring 装配交互）；复数名词类（`Numbers` / `Patterns` / `Filenames` / `Collects` / `Hashing` …）= 纯函数无状态工具。历史例外：`HttpClients` 复数名但按门面对待。
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
| `id` | `IdUtil` | 雪花 ID（可配 worker/dataCenter）+ UUID 多形态 |
| `json` | `Jsons` / `JsonConfig` | 应用 mapper 注入、不可变 builder、安全 Result 错误通道；JsonUtil 保留 standalone 静态预设 |
| `log` | 应用 SLF4J；旧 LogUtil | 新路径标准日志与字段白名单；旧二次分发只保留兼容，不是审计 |
| `date` | `DateUtil` | 日期格式化/解析（返回 Result）、区间规范化 |
| `number` | `Numbers` / `NumberFormat` / `NumberUnits` | 数值解析、大小格式化、单位换算 |
| `hash` | `Hashing` | 文件/字节哈希 |
| `io` | `PathIo` / `Zipping` | 有界完整目录统计/逐项删除；ZIP完整关闭后不覆盖发布，实际读写/条目/深度预算和清理失败可见 |
| `path` | `Filenames` | 文件名清洗、路径穿越防御、危险扩展名拦截 |
| `mime` | `MimeTyping` | 基于魔数的 MIME 探测（optional：tika-core） |
| `pattern` | `Patterns` | 常用正则校验 |
| `copy` | `CopyUtil` | 有界旧复制；新路径见[显式DTO示例](examples/order-mapping/README.md) |
| `locale` | 应用 MessageSource；旧 LocaleUtil | 宿主优先的明确 bundle 顺序，静态入口保留兼容并弃用 |
| `async` | `Async<T>` | 惰性组合、整体 deadline 与协作取消；有界平台线程默认，应用显式注入 Executor |
| `web.*` | filter / interceptor / exception / session / response / argument / util / download / upload | Servlet 栈：traceId、可重复读请求体、访问日志、全局异常、统一响应、安全上传下载、完整Cookie scope与显式有限HTML片段（optional：jsoup；ADR0055） |
| `ratelimit` | `RateLimiterUtil` / `RateLimiter`（SPI） | 本地令牌桶：合法成本、精确扣费与有界准入；必需门面 + 显式 Optional 降级 |
| `web.ratelimit` | `@RateLimit` | 方法级入口限流；IP / Principal / Global；429 + `Retry-After`，不可用默认 503 |
| `cache` | `CacheUtil` | 缓存门面委托 Spring `CacheManager`；`@Cacheable` 自然可用；Caffeine optional 支持 TTL/maxSize |
| `lock` | `LocalKeyedMutex`；旧锁入口兼容 | 实例内同步互斥、严格活动键预算和安全回收；缺少所需实现不执行 action，等待与实际持有分开 |
| `http` | `HttpClients` | HTTP client 门面：委托 RestClient，`get`/`post`/`put`/`delete`→`Result`；超时可配 |
| `idempotency` | `IdempotencyStore`（SPI） | 执行资格、指纹绑定与有界回执；lease/retention分离，旧入口保留迁移（ADR0034） |
| `web.idempotency` | `@Idempotent` | 当前授权/可信身份/操作隔离的有界同步响应重放；必须提供宿主授权 Adapter（ADR0035） |
| `crypto` | `CryptoUtil` | AES-256-GCM 对称加解密 + HMAC + 密钥派生/管理 + Base64/Hex（静态门面，纯 JDK，无需配置） |
| `masking` | `MaskUtil` | 纯函数脱敏（旧 LogUtil 默认集成）：秘密/JWT/身份证/银行卡/邮箱/手机号六规则，校验位（mod11-2/Luhn）抑误伤；`LogUtil` 写前集成，`setMaskingEnabled(false)` 可关（静态门面，纯 JDK，无需配置） |
| `csv` | `CsvUtil` | 有界 CSV 读写（Commons CSV required）：strict/legacy 方言、逐行消费、UTF-8 字节/行列/字段预算；机器与电子表格导出政策分离 |
| `excel` | `ExcelUtil` | Excel（xls/xlsx）读写（POI optional）：有界XLS/HSSF与XLSX/SAX，显式Locale/公式缓存，SXSSF一行窗口及自有临时预算；成对引擎缺失返回err |

## 装配开关

所有开关前缀 `facility.*`，均可在 `application.yml` 调整；`enabled=false` 关闭对应组件（所有 `enabled` 均 `matchIfMissing=true`：不写 = 开）。下表为速查，**权威全表（含默认值）见 USAGE「装配开关全表」**；可直接复制的带详注样例见 `src/main/resources/application.example.yaml`。

| 前缀 | 作用 |
|---|---|
| `facility.id` | 雪花 ID：`worker-id` / `data-center-id` / `clock-backwards-threshold-millis` |
| `facility.web.trace` | 旧 TraceId 过滤器，默认禁用；仅显式兼容 opt-in，新应用使用标准 Micrometer tracing |
| `facility.web.repeatable-request` | 可重复读请求体：`max-body-bytes` / `include-content-types` / `exclude-paths` |
| `facility.web.access-log` | 访问日志拦截器：`slow-threshold-millis`（超阈升 WARN 标记 slow；0=禁用） |
| `facility.web.cors` | CORS：`allowed-origins`（默认空 = 不开）/ `allowed-methods` / `allow-credentials` |
| `facility.web.exception` | 安全 HTTP 错误：默认 RFC 9457 ProblemDetail；`use-problem-detail=false` 显式旧 envelope（ADR-0027） |
| `facility.ratelimit` | 限流：`default-capacity` / `default-permits-per-second` / `max-buckets` / `fail-open=false` |
| `facility.cache` | 缓存：`default-ttl` / `maximum-size`（仅 Caffeine 后端生效） |
| `facility.lock` | 分布式锁：`max-locks`（锁集合无界防护上限） |
| `facility.http` | HTTP client：`connect-timeout` / `read-timeout` |
| `facility.idempotency` | 幂等：`lease` / `result-retention` / `max-entries` / 请求、响应与合计回执字节预算；`default-ttl` 仅兼容回退 |

## 消费方陷阱速查

一行版；细节与处置全在 USAGE「消费方须知」：

- **应用拥有 i18n**：Boot `spring.messages.*` 或应用名为 `messageSource` 的 bean 优先；仅在两者缺席时提供设施 bundle。需要设施文案时，将 `i18n/facility-messages` 明确列在应用 basename 之后，见[迁移说明](docs/building/application-observability.md)。
- **JsonUtil 进程级单例**：`JsonsRegistry` 是静态单例，同一 JVM 内多个 ApplicationContext 共享同一套 ObjectMapper 命名空间。
- **SpringContextHolder 已弃用**：新路径构造器注入所需服务。兼容门面只发布首个成功刷新 context，只有发布者能撤销；被拒绝的 context 不自动接管，关闭不会影响 owner（ADR-0025）。
- **两类让位机制勿混淆**：`@ConditionalOnMissingBean` 真回退（声明即让位） vs Web 过滤器/拦截器仅认 `enabled` 开关（声明同类 bean 会并存双重入链）。
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

**一致性宪法速览**（C1–C5，新代码必须遵守，存量「触碰即对齐」；全文 DESIGN §7）：C1 null 契约（数据参数 null-safe / 依赖参数 fail-fast / 外部交互走 Result）、C2「≤0 = 不限制」统一拼法、C3 降级日志政策（装配期与低频防护 WARN / 每请求高频预期降级静默）、C4 命名双家族、C5 `jakarta.annotation.Nullable` 标注（error 包例外）。

**新增能力范式**（对齐存量形态，勿发明新形态）：

- 需要 bean / 配置属性 → 新建 `Facility*AutoConfiguration` 并注册进 `AutoConfiguration.imports`，properties 前缀 `facility.*`、不用 `@Validated`（构造器兜底，ADR-0013）；可替换点用 `@ConditionalOnMissingBean` 落 Seam，**不为单一实现预先抽象接口**（hypothetical seam，见 CONTEXT）。
- 无状态纯门面 → 零装配、零 properties、恒可用（对齐 `crypto` / `masking` / `csv`）。
- 重依赖 → Maven `optional`；有装配的用 `@ConditionalOnClass` 条件化，静态门面用运行时探测降级（ADR-0021 范式）；成对依赖成对声明（ADR-0015 范式：caffeine + spring-context-support、poi + poi-ooxml）。
- Web 集成与通用能力分包（`ratelimit` / `web.ratelimit`，`idempotency` / `web.idempotency`），避免反向依赖成环。

**文档同步义务**（改完代码没同步文档 = 任务未完成）：

- 行为变更 → 同步 USAGE 对应小节；破坏性 / 行为变更 → CHANGELOG 补迁移指引；
- 新架构决策 → 复制 `docs/adr/0000-adr-template.md` 顺延编号，更新 `docs/adr/INDEX.md` 与 DESIGN §5；
- 新术语 → CONTEXT.md；本 README 的快照计数随之刷新。
