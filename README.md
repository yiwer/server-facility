# server-facility

> Spring Boot server 端基础设施脚手架 —— 一个 **deep module**（窄接口、宽实现）：
> 用少量静态门面、值类型与自动装配 bean，封装 server 开发反复要写的 Result 错误建模、
> ID 生成、JSON、i18n、Web 过滤链、限流 / 缓存 / 锁 / 幂等 / 加解密 / 脱敏等横切能力。

**读者定位**：本仓库的第一读者是 **coding agent**。本 README 是唯一入口，阅读约定三条：

1. **权威链**：代码 + `docs/adr/` ＞ `docs/USAGE.md` / `docs/DESIGN.md` ＞ 本文 ＞ `CONTEXT.md`（术语基准）。文档与代码冲突时以代码 + ADR 为准，并回头修订文档。
2. **按任务路由**：先查下表，只加载与当前任务相关的文档，不要全量通读。
3. **快照数据**：本文标注「快照」的计数允许滞后，权威取 `mvn verify` 实际输出与对应源文件。

## 任务路由

| 你的任务 | 去处 |
|---|---|
| 在应用中使用某能力（API 语义、示例、返回约定） | 本文[特性矩阵](#特性矩阵)定位簇 → [USAGE](docs/USAGE.md) 同名小节 |
| 配置或关闭某个自动装配组件 | 本文[装配开关](#装配开关) → USAGE「装配开关全表」（权威、含默认值） |
| 用自己的 bean 替换 facility 默认实现 | USAGE「消费方须知 · 两类让位机制」 |
| 排查「配置不生效 / bean 不是我的 / 意外降级」 | 本文[消费方陷阱速查](#消费方陷阱速查) → USAGE「消费方须知」 |
| 消费方升级 facility 版本 | [CHANGELOG](CHANGELOG.md)（破坏性 / 行为变更的迁移指引） |
| 修改本仓库代码 | 本文[维护须知](#维护须知) → [DESIGN §7 一致性宪法](docs/DESIGN.md) |
| 理解设计动机、包依赖结构、翻历史决策 | [DESIGN](docs/DESIGN.md) → [ADR 索引](docs/adr/INDEX.md)（23 条） |
| 查术语定义（deep module / Seam / Result-style …） | [CONTEXT](CONTEXT.md) |
| 追溯某特性的需求与实施过程 | `docs/superpowers/specs/` 与 `docs/superpowers/plans/`（过程档案，只读） |

## 硬事实

- **坐标**：`cn.code91:server-facility:0.1.0-SNAPSHOT`，单模块 jar。
- **环境**：Java 21+；Spring Boot 3.5.x（依赖版本经 `spring-boot-dependencies` BOM 收敛）。
- **命名**：包根 `cn.code91.facility.*`；类前缀 `Facility*`；配置前缀 `facility.*`；i18n bundle `i18n/facility-messages_*`。
- **命令**：`mvn verify` = 全部质量门（测试 + 覆盖率 + 依赖账目）；`mvn test` = 仅测试。
- **质量门**（不达即构建失败，禁止以调低门槛的方式通过）：
  - JaCoCo BUNDLE 级：INSTRUCTION / LINE ≥ 0.88，BRANCH ≥ 0.75；
  - `maven-dependency-plugin` `analyze-only` + `failOnWarning`：依赖账目必须干净；
  - ArchUnit 5 条架构红线（随测试套运行，见[维护须知](#维护须知)）。
- **快照（2026-07-10）**：测试 1190 项全绿（含 5 条 ArchUnit）；覆盖率实测约 instruction 93.6% / line 93.4% / branch 87.0%。

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
docs/adr/                                 23 条架构决策记录（INDEX.md 索引；0000 为模板）
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

// 4) 日志：SLF4J 风格静态门面（Throwable 显式置于 msg 后、占位符参数前）
LogUtil.info("user {} logged in", userId);
LogUtil.error("load failed: {}", ex, resourceId);   // ex 在 msg 与参数之间，不被当占位符实参吞掉

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
- **「≤0 = 不限制」（C2）**：所有配置项中表达「无限制」的统一拼法。

## 特性矩阵

**实现任何通用能力前先查此表**——已有的直接用，不要在消费方重复造轮子。各簇完整方法与语义见 USAGE 同名小节。

| 簇 | 入口 | 能力 |
|---|---|---|
| `result` | `Result<T,E>`（sealed） | 显式错误通道：`ok`/`err`/`empty`/`map`/`flatMap`/`orElse`/`fromOptional` |
| `error` | `WrappedError` / `FacilityErrorType` / `ErrorTypeInterface` | 错误码 + i18n 消息键 + 可扩展错误类型；error 包纯 JDK（C1 断环） |
| `structure` | `Tuple` / `Triple` | 轻量二/三元值容器 |
| `common` | `NullSafe` / `Collects` | 空安全与集合便捷 |
| `context` | `SpringContextHolder` | 静态持有 ApplicationContext（AtomicReference + CAS 单次发布） |
| `id` | `IdUtil` | 雪花 ID（可配 worker/dataCenter）+ UUID 多形态 |
| `json` | `JsonUtil` | 多命名空间（DEFAULT/GENERIC/CANONICAL/PRETTY）Jackson；序列化返回 Result |
| `log` | `LogUtil` | SLF4J 风格门面；带异常签名固定 `(msg, t, args...)`，Throwable 显式居中（ADR-0005），主源零 logback 依赖（ADR-0011） |
| `date` | `DateUtil` | 日期格式化/解析（返回 Result）、区间规范化 |
| `number` | `Numbers` / `NumberFormat` / `NumberUnits` | 数值解析、大小格式化、单位换算 |
| `hash` | `Hashing` | 文件/字节哈希 |
| `io` | `PathIo` / `Zipping` | 路径读写、压缩 |
| `path` | `Filenames` | 文件名清洗、路径穿越防御、危险扩展名拦截 |
| `mime` | `MimeTyping` | 基于魔数的 MIME 探测（optional：tika-core） |
| `pattern` | `Patterns` | 常用正则校验 |
| `copy` | `CopyUtil` | Bean 属性拷贝 |
| `locale` | `LocaleUtil` | i18n 消息翻译 + 聚合 MessageSource |
| `async` | `Async<T>` | 惰性异步计算，结果落 `Result`；虚拟线程默认执行器 |
| `web.*` | filter / interceptor / exception / session / response / argument / util / download / upload | Servlet 栈：traceId、可重复读请求体、访问日志、全局异常、统一响应、安全上传下载、XSS（optional：jsoup） |
| `ratelimit` | `RateLimiterUtil` / `RateLimiter`（SPI） | 令牌桶限流：纯 JDK 默认实现 + SPI 可替换（Redis）；编程门面 + 无 bean 降级放行 |
| `web.ratelimit` | `@RateLimit` | 方法级声明式限流（拦截器）；超限 429 + `Retry-After` |
| `cache` | `CacheUtil` | 缓存门面委托 Spring `CacheManager`；`@Cacheable` 自然可用；Caffeine optional 支持 TTL/maxSize |
| `lock` | `LockUtil` / `DistributedLock`（SPI） | 分布式锁：高阶 `executeWithLock` 自动获取释放 + `tryLock`/`unlock`；默认单机 ReentrantLock，SPI 可替换 Redisson |
| `http` | `HttpClients` | HTTP client 门面：委托 RestClient，`get`/`post`/`put`/`delete`→`Result`；超时可配 |
| `idempotency` | `IdempotencyStore`（SPI） | 幂等存储：PROCESSING/DONE 状态机 + TTL，默认内存，SPI 可替换 Redis |
| `web.idempotency` | `@Idempotent` | 完整幂等：同 key 返首次响应，拦截器 + Filter 捕获响应，PROCESSING→409 |
| `crypto` | `CryptoUtil` | AES-256-GCM 对称加解密 + HMAC + 密钥派生/管理 + Base64/Hex（静态门面，纯 JDK，无需配置） |
| `masking` | `MaskUtil` | 日志脱敏（默认开启）：秘密/JWT/身份证/银行卡/邮箱/手机号六规则，校验位（mod11-2/Luhn）抑误伤；`LogUtil` 写前集成，`setMaskingEnabled(false)` 可关（静态门面，纯 JDK，无需配置） |
| `csv` | `CsvUtil` | RFC 4180 CSV 读写（纯 JDK，零依赖恒可用）：UTF-8+BOM 写出、CRLF、最小引号；读容忍 CR/LF/CRLF 并剥 BOM |
| `excel` | `ExcelUtil` | Excel（xls/xlsx）读写（POI optional）：写 SXSSF 恒定内存 xlsx，读 usermodel 全字符串化；POI 缺失时运行时探测降级返 err，不崩溃 |

## 装配开关

所有开关前缀 `facility.*`，均可在 `application.yml` 调整；`enabled=false` 关闭对应组件（所有 `enabled` 均 `matchIfMissing=true`：不写 = 开）。下表为速查，**权威全表（含默认值）见 USAGE「装配开关全表」**；可直接复制的带详注样例见 `src/main/resources/application.example.yaml`。

| 前缀 | 作用 |
|---|---|
| `facility.id` | 雪花 ID：`worker-id` / `data-center-id` / `clock-backwards-threshold-millis` |
| `facility.web.trace` | TraceId 过滤器：`header-name` / `mdc-key` / `generate-if-absent` |
| `facility.web.repeatable-request` | 可重复读请求体：`max-body-bytes` / `include-content-types` / `exclude-paths` |
| `facility.web.access-log` | 访问日志拦截器：`slow-threshold-millis`（超阈升 WARN 标记 slow；0=禁用） |
| `facility.web.cors` | CORS：`allowed-origins`（默认空 = 不开）/ `allowed-methods` / `allow-credentials` |
| `facility.web.exception` | 全局异常：`include-trace-profiles` / `use-problem-detail`（RFC 7807） |
| `facility.ratelimit` | 限流：`default-capacity` / `default-permits-per-second` / `max-buckets` |
| `facility.cache` | 缓存：`default-ttl` / `maximum-size`（仅 Caffeine 后端生效） |
| `facility.lock` | 分布式锁：`max-locks`（锁集合无界防护上限） |
| `facility.http` | HTTP client：`connect-timeout` / `read-timeout` |
| `facility.idempotency` | 幂等：`default-ttl` / `max-entries` |

## 消费方陷阱速查

一行版；细节与处置全在 USAGE「消费方须知」：

- **i18n 抢注**：facility 抢注 `@Primary` 的 `messageSource`，`spring.messages.*` **不影响** facility 自带文案；要完全接管，声明名为 `messageSource` 的 bean 即可让位。
- **JsonUtil 进程级单例**：`JsonsRegistry` 是静态单例，同一 JVM 内多个 ApplicationContext 共享同一套 ObjectMapper 命名空间。
- **SpringContextHolder 先到先得**：静态 CAS 只注入首个 context；多 context 测试中「拿到别的上下文的 bean / 降级分支被意外触发」先查此语义。
- **两类让位机制勿混淆**：`@ConditionalOnMissingBean` 真回退（声明即让位） vs Web 过滤器/拦截器仅认 `enabled` 开关（声明同类 bean 会并存双重入链）。
- **无校验 provider 也能启动**：properties 类不用 `@Validated`（ADR-0013），取值约束在组件构造器兜底。

## 维护须知

修改本仓库代码时的成文规则。完整条款：DESIGN §7 一致性宪法；工作流：SDD（spec → plan → TDD 实施，档案在 `docs/superpowers/`）。

**完成判定**：`mvn verify` 全绿。门槛失败修代码、补测试，**不得调低 pom 门槛值或随手加 ignore**（依赖账目确需 ignore 时必须注明理由，样例见 pom 注释）。

**架构红线**（ArchUnit，`src/test/java/cn/code91/facility/architecture/ArchitectureTest.java`，违反即测试红）：

1. `packages_are_cycle_free` —— 包依赖无环、自底向上单向流动（依赖地图见 DESIGN §2）；
2. `error_package_depends_only_on_jdk` —— error 包纯 JDK（C1 断环，ADR-0010；故 error 包禁用 `jakarta.annotation.Nullable`）；
3. `main_code_does_not_depend_on_logback` —— 主源码零 logback（ADR-0011）；
4. `autoconfigure_is_not_depended_on_by_main_packages` —— 组件包不感知装配；配置属性类与消费组件同包；
5. `excel_facade_does_not_depend_on_poi` —— POI 类型只允许出现在包私有 `ExcelSupport`（ADR-0021）。

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
