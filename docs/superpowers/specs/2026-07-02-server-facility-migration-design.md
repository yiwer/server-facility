# server-facility 迁移与优化设计(spec)

> **日期**:2026-07-02
> **状态**:已批准(2026-07-02,用户确认全部决策与假设 D1-D2 / A1-A7 / C1-C3 / §5 判定表)
> **源项目**:`D:\STELE\beacon\beacon-support\beacon-facility`(`cn.hbads:beacon-facility`,phase-14 后稳定态:23 子包 / 117 主源文件 / 355 测试全绿 / REVIEW-2 全部 23 条处置完毕)
> **目标项目**:`D:\Yiwer\code\server-facility`(`cn.code91:server-facility`,独立仓库,当前仅空 pom)

---

## §1 背景与目标

beacon-facility 是 beacon 单体仓库内经 7 轮 SDD phase(评审→迁移→精炼→收口→hardening×2)打磨的
Spring Boot 基础设施 deep module。本工程将其**提取为独立的 server 脚手架项目**,脱离 beacon 父 pom
与 `cn.hbads` 命名空间,同时**逐包重审**——迁移不是复制,而是一次带质量门的再评审:

1. **代码质量**:延续源项目已达成的并发正确性/安全纵深防御水位,消化两条 wontfix 重估与新发现;
2. **架构整洁**:保持"窄接口、宽承载"的 deep module 形态与 `@ConditionalOnMissingBean` Seam 纪律;
3. **接口易用**:静态门面 + Spring bean 双入口;配置前缀收敛为 `facility.*`;
4. **可扩展**:新组件(限流/脱敏/幂等等)有明确的接入范式(子包 + optional 依赖 + AutoConfiguration + properties);
5. **SDD 驱动**:spec → plan → TDD 实施 → retrospective;决策记 ADR;
6. **测试全面**:迁移 355 测试基线全绿,补齐无测试包,JaCoCo 全局 line ≥ 80%。

**不在本次范围(out of scope)**:发布 Maven Central、CI 流水线、多模块拆分、新功能组件实做(见 §10 roadmap)。

---

## §2 已确认决策与待确认假设

### 已确认(用户拍板,2026-07-02)

| # | 决策 |
|---|---|
| D1 | 包名根 `cn.code91.facility.*`;保留 `Facility*` 类前缀;配置前缀 `beacon.facility.*` → `facility.*` |
| D2 | 组件取舍采用"逐项审核定夺":本 spec §5 给出逐包 keep/rework/drop 建议,用户审阅时一次性定夺 |

### 假设(默认值;审阅本 spec 时可逐条推翻)

| # | 假设 | 依据 |
|---|---|---|
| A1 | **本次不实做新组件**,先把存量 23 包做扎实;新组件列入 §10 roadmap | 长程任务质量优先;架构扩展点在 §4 保证 |
| A2 | 版本 `0.1.0-SNAPSHOT` 起步(semver);Java 21 + Spring Boot **3.5.10** BOM(与源一致) | 独立项目自管 dependencyManagement |
| A3 | 依赖收敛按 §6 执行:移除 hutool-core / commons-io / spring-jdbc / postgresql(均零使用),commons-lang3 待 date 簇 rework 后移除,hibernate-validator 降为 test scope | 逐条 import 扫描证据(2026-07-02) |
| A4 | 测试标准:355 基线迁移全绿 + 无测试包各补行为测试 + JaCoCo line ≥ 80% 全局 gate | 用户要求"测试用例覆盖全面"的量化 |
| A5 | 新仓库自带完整文档基线:README/DESIGN/USAGE 三件套 + `docs/adr/`(8 条 facility ADR 以 inherited 登记出处)+ `CONTEXT.md` 精简版 + `docs/superpowers/` 工作流 | 独立仓库须自洽,不能引用 beacon 内部路径 |
| A6 | **wontfix 重估授权**:RV2-17(LogUtil 手写格式化)、RV2-18(Result/Tuple 别名)当年 wontfix 的理由是"改动 churn 已有消费方"——新项目**零消费方**,该理由消失,在迁移窗口重估;若翻案,各记一条新 ADR | REVIEW-2 §4 wontfix 理由随迁移失效 |
| A7 | 引入 **ArchUnit**(test scope)守护架构约束:包依赖无环、门面/SPI 可见性纪律;随 P1 落地并逐簇追加规则 | §4.4 断环成果需要回归保障,否则会随演进复发 |

---

## §3 方案比较

### 方案 A:单模块 deep module 直迁 + 逐簇质量门(**推荐**)

保持源项目验证过的形态:单 jar,重依赖 optional,`@ConditionalOnClass`/`@ConditionalOnMissingBean`
条件装配实现"按 classpath 自动裁剪"。迁移按依赖拓扑分簇推进(核心类型 → 运行基座 → … → web → 装配),
每簇一个 phase:重审 → 移植改名 → rework → 测试全绿。

- ✅ 355 测试基线可整体继承,回归风险最低;
- ✅ deep module 哲学(接口窄/承载宽)已被 14 个 phase 验证,单模块即是其物理形态;
- ✅ spring-boot-autoconfigure 本身就是此模式的最佳先例;
- ⚠️ 消费方无法按模块挑选 jar——但 optional 依赖 + 条件装配已达成同等 classpath 裁剪效果。

### 方案 B:多模块拆分(facility-core / facility-web / facility-json + BOM + starter)

- ✅ 分发粒度细,"框架感"强;
- ❌ 117 个源文件的体量撑不起多模块的构建/版本/发布复杂度;
- ❌ 跨模块依赖(json→log→…)会催生 facility-common 传染;
- ❌ 与"接口面窄"的 deep module 定位相悖。**否决**。

### 方案 C:白纸重写(以旧代码为参考重设计 API)

- ✅ 审美自由度最大(如全面 DI-first 去静态门面);
- ❌ 丢弃 355 测试回归基线与 14 个 phase 的 hardening 沉淀,风险/收益严重倒挂。**否决**。

---

## §4 目标架构(方案 A 展开)

### 4.1 命名映射

| 维度 | 源 | 目标 |
|---|---|---|
| Maven 坐标 | `cn.hbads:beacon-facility`(父 pom 管版本) | `cn.code91:server-facility:0.1.0-SNAPSHOT`(自管) |
| 包根 | `cn.hbads.beacon.facility.*` | `cn.code91.facility.*` |
| 类前缀 | `Facility*` | `Facility*`(不变) |
| 配置前缀 | `beacon.facility.*` | `facility.*` |
| i18n bundle | `i18n/facility-messages_*` | 不变 |
| 装配注册 | `META-INF/spring/....AutoConfiguration.imports`(6 条) | 同机制,新包名 |

### 4.2 deep module 形态(继承)

```
        应用 / 上层模块
              │  接口面【窄】
   ┌──────────┼──────────────────────┐
   ▼          ▼                      ▼
静态门面    Spring 装配(Seam)      值类型 / SPI
IdUtil      6 × Facility*AutoConfig  Result<T,E>(sealed)
JsonUtil    @ConditionalOnMissingBean ErrorTypeInterface
LogUtil     (应用 bean 即可覆盖)     LogPostHandler(SPI)
DateUtil                             Tuple / Triple
LocaleUtil
   └──── 承载面【宽】:~21 个子包(§5 定夺后),按责任聚类 ────┘
```

Seam 纪律不变:每个默认实现都可被应用声明同类型 bean 替换;hypothetical seam 不预先抽象
Strategy,第二实现出现再升 real seam。

### 4.3 新组件接入范式(可扩展性的架构保证)

新组件 = 新子包 + optional 依赖 + `Facility<X>AutoConfiguration`(带 `@ConditionalOnClass` +
`@ConditionalOnMissingBean`)+ `Facility<X>Properties`(`facility.<x>.*`)+ imports 注册 + 三件套文档补一行。
§10 roadmap 各项均按此范式接入,不需要动既有代码。

### 4.4 包依赖拓扑与断环决策(本次审核新发现,2026-07-02 全量 import 扫描)

源项目存在 **三组包级循环依赖**(单 jar 内可编译,但破坏"每簇可独立理解/测试"):

| 环 | 证据 | 推荐断法 |
|---|---|---|
| C1 `error → locale → context → error` | `ErrorTypeInterface`→`LocaleUtil`;`LocaleUtil`→`SpringContextHolder`;`SpringContextHolder`→`WrappedError`/`FacilityErrorType`/`Result` | **error 去 locale 依赖**:错误类型只承载 code+args 纯数据,本地化解析反转到 locale 侧(如 `locale` 提供针对 `ErrorTypeInterface` 的消息解析);形成锥形单向依赖 `result/error(纯) ← context ← locale`(记 ADR) |
| C2 `common → structure → copy → common` | `Collects`→`Tuple`;`WrappedContainer`→`CopyTrait`/`DateUtil`/`NumberFormat`;`CopyUtil`→`Collects`/`NullSafe` | `WrappedContainer`/`WrappedDataType` 迁出 structure(随 §5 复核决定去向:并入 copy 簇或独立子包或 drop);断后 `structure(纯值) ← common ← copy` 单向 |
| C3 `autoconfigure ⇄ id/web`(经 properties) | `SnowIdGenerator`→`autoconfigure.properties.FacilityIdProperties`;`TraceIdFilter`/`AccessLogInterceptor`/`AbstractGlobalExceptionHandler` 等→`autoconfigure.properties.FacilityWeb*Properties` | **properties 各归其组件包**(`FacilityIdProperties`→`id`,`FacilityWeb*Properties`→`web` 相应子包);`autoconfigure` 只剩装配类,依赖单向向下;配置前缀(字符串)不受包位置影响 |

备选:保留现状环(spring-boot-autoconfigure 自身也有类似集中放置)。**不推荐**——本次迁移是唯一低成本断环窗口,
且断环后分簇迁移的 phase 拓扑(§8)才严格可行。断环成果由 A7 ArchUnit 规则守护。

---

## §5 逐包审核结论(23 包;D2 待用户定夺)

> 判定含义:**keep** 原样迁移(仅改包名);**keep+rework** 迁移时执行列明的重构(TDD);
> **keep+review** 迁移时复核列明疑点,复核结论可改判(改判记入该簇 plan);
> **keep(拆)** 包保留但列明成员剔除;**drop** 不迁移(代码留在源库,需要时可取回)。

| # | 包 | 判定 | 理由与 rework 内容 |
|---|---|---|---|
| 1 | `result` | keep+rework | 核心值类型(sealed `Result<T,E>`),质量高。rework:A6 重估 RV2-18 纯别名(`filter`≡`ensure`、`unwrap`≡`get`),零消费方窗口精简 API,保留语义独立方法(记 ADR) |
| 2 | `error` | keep+rework | `ErrorTypeInterface`/`FacilityErrorType`/`WrappedError`,Result-style 与异常体系的错误类型支撑。rework:执行 C1 断环(§4.4)——错误类型收敛为 code+args 纯数据,本地化解析移交 locale 侧(记 ADR) |
| 3 | `structure` | keep+review | `Tuple`/`Triple` keep(别名精简同上);`WrappedContainer`/`WrappedDataType` 复核必要性与去向(C2 断环要求其迁出 structure:并入 copy 簇 / 独立子包 / drop,复核时定) |
| 4 | `common` | keep | `Collects`/`NullSafe`,跨包基础 |
| 5 | `context` | keep | `SpringContextHolder`(AtomicReference+CAS 单次发布,REVIEW-2 认证的并发亮点) |
| 6 | `id` | keep | `IdUtil`+`SnowIdGenerator`:完整时钟回拨处理、instance parse(ADR-0008)、RV2-06 已修;顺带复核 workerId/dataCenterId 位宽([0..3] 偏窄是否够用) |
| 7 | `date` | keep+rework | Result-style parse;**唯一 commons-lang3 使用点**——用 JDK 等价替换后清除该依赖 |
| 8 | `number` | keep(拆) | `Numbers`/`NumberFormat`/`NumberUnits` keep(RV2-04 已修);**`ChineseNumbers` 建议 drop**(中文数字转换业务色彩浓,通用脚手架不背) |
| 9 | `hash` | keep | 零外部依赖(JDK MessageDigest;已核实无 codec/hutool import——USAGE"封装 commons-codec/hutool"说法失实,文档修正) |
| 10 | `pattern` | keep | `Patterns`/`CommonPatterns` 编译缓存 |
| 11 | `path` | keep | `Filenames.sanitize`(上传安全纵深防御一环) |
| 12 | `io` | keep | `PathIo`/`Zipping`;RV2-09/10 已修;复核 Zipping 的 zip-slip 防护 |
| 13 | `mime` | keep | `MimeTyping` magic-bytes 检测;tika optional(ADR-0001) |
| 14 | `json` | keep | 三层设计(`Jsons` 实例 / `JsonsRegistry` / `JsonUtil` 门面)复用 Spring `ObjectMapper`,序列化一致性亮点;RV2-20 已修,迁移时以并行 ApplicationContextRunner 复核 |
| 15 | `convert` | keep+review | 基于 Spring ConversionService;以"脚手架 API"标准复核 `TypeConverter`/`BidirectionalConverter` 的必要性与易用性 |
| 16 | `copy` | keep+rework | `CopyUtil` 体量过大(约 700 行 UtilityClass),审拆分;修正 USAGE"封装 MapStruct"失实描述(实为反射实现,无 mapstruct 依赖);复核 carry-forward 的 `processMapEntry` null-put |
| 17 | `locale` | keep+rework | `AggregatedMessageSource` 聚合模式(六大架构模式之一);RV2-21 已修;i18n 资源(en/zh_CN/zh_TW)同迁。rework:承接 C1 断环后的错误消息解析职责(locale→error 单向) |
| 18 | `log` | keep+rework | ①A6 重估 RV2-17:手写 `{}` 格式化 → SLF4J `MessageFormatter`(零消费方,兼容顾虑消失);②`LogUtil` 直接 import `ch.qos.logback.classic.LoggerContext`(门面耦合实现)——隔离为条件能力或明确文档化;`LogPostHandler` SPI keep |
| 19 | `async` | keep | 虚拟线程 executor + 拦截器责任链 + `AsyncContext` 钩子;"MDC 不自动透传"文档已对齐(RV2-D1) |
| 20 | `coordinate` | **drop 建议** | 经纬度 Point/距离计算,领域特定;通用 server 脚手架不背,需要时取回或引专业库(JTS) |
| 21 | `validate` | **drop** | 空包(phase-13 删除 3 个零使用 marker 后仅剩 package-info);待真实校验组件出现时随组件重建 |
| 22 | `web` | keep 全簇 | 脚手架核心价值:`exception`(RFC 7807 双轨,ADR-0003)、`filter`(TraceId/RepeatableRequest)、`interceptor`(AccessLog/SessionUserClear)、`response`(BaseResponse/PageBaseResponse)、`session`、`upload`(SafeUpload 纵深防御)、`download`、`util`(Cookie/Request/Response/Xss)、`argument`(PageQuery,RV2-16 已修)。逐类复核安全默认值(RV2-07/08/14 修复效果);servlet API 依赖评估 tomcat-embed-core → `jakarta.servlet-api` |
| 23 | `autoconfigure` | keep+rework | 6 AutoConfiguration + imports;前缀迁移 `beacon.facility.*`→`facility.*`;修正 USAGE"5 个 AutoConfiguration"漂移(实为 6)。rework:执行 C3 断环——6 个 properties 类各归其组件包,本包只留装配类 |

**drop 汇总(默认建议)**:`coordinate` 全包、`number/ChineseNumbers` 单类、`validate` 空包。
若审阅时翻案改 keep,并入对应簇的 phase,不影响其余结构。

---

## §6 依赖收敛(证据:2026-07-02 全量 import 扫描)

| 依赖 | 源项目 | 新项目 | 依据 |
|---|---|---|---|
| spring-boot-dependencies 3.5.10 | 父 pom import | **dependencyManagement import** | 独立项目自管版本 |
| jackson(databind/jdk8/jsr310/parameter-names) | compile | compile | json 簇 |
| slf4j-api | compile | compile | log 门面 |
| jakarta.annotation-api / jakarta.validation-api | compile | compile | 注解 |
| spring-boot-autoconfigure | compile | compile | 装配 |
| spring-web / spring-webmvc | optional | optional | web 簇 |
| tomcat-embed-core | optional | optional→复核 | 仅需 servlet API,评估换 `jakarta.servlet-api`(web phase 定) |
| jsoup | optional | optional | 仅 `XssUtil`/`XssLevel`(ADR-0001 吻合) |
| tika-core | optional | optional | 仅 `MimeTyping`(ADR-0001 吻合) |
| lombok | optional | optional | 主源码广泛使用(`@UtilityClass`/`@Data` 等) |
| logback-classic | optional | **待 log rework 定**(目标:降 test) | 主源码仅 `LogUtil` 一处 import logback 内部类 |
| commons-lang3 | compile | **移除**(P3 date rework 后) | 仅 `DateUtil` 一个文件使用 |
| commons-io | compile | **移除** | **零使用** |
| hutool-core | compile | **移除** | **零使用** |
| spring-jdbc / postgresql | optional | **移除** | phase-5 rowmapper 迁出后遗留,**零使用** |
| hibernate-validator | **runtime** | **test** | runtime scope 会传染消费方 classpath;实际仅测试需要 validator 实现 |
| spring-boot-configuration-processor | optional | optional | `facility.*` 前缀变更后重新生成配置元数据 |
| starter-test / junit-jupiter / mockito×2 / assertj | test | test | 测试五件套 |

出口检查:P7 跑 `mvn dependency:analyze`,unused declared 必须为 0。

---

## §7 质量与测试策略

1. **回归基线**:36 个测试文件(355 用例)随各簇迁移(包名替换),**必须全绿**——这是每个 phase 的地板;
2. **TDD**:所有 rework(别名精简 / date 去 lang3 / LogUtil 格式化 / CopyUtil 拆分等)先写失败测试再动实现;
3. **盲区补齐**(源项目无测试的包,每包至少一个行为测试类):`hash`、`mime`、`path`、`pattern`、
   `io.Zipping`、`convert`、`structure`(Tuple/Triple)、`common`、`web`(download/upload/session/util.Xss 等);
4. **装配测试范式**:`ApplicationContextRunner` + `@Nested`(源项目已确立,7 个 autoconfig 测试类随迁);
5. **架构守护**:ArchUnit 测试(A7)自 P1 起随源码演进——包依赖无环(守护 §4.4 断环成果)、
   `autoconfigure` 单向向下、util 类不可实例化等纪律规则;
6. **覆盖率 gate**:P0 引入 JaCoCo,P7 起 `check` 挂全局 line ≥ 0.80(lombok 生成代码经
   `lombok.config addLombokGeneratedAnnotation` 排除);
7. **phase 出口**:`mvn test` 全绿 + 该簇覆盖达标,才允许进入下一 phase;
8. **Windows 注意项**:surefire 显式 `-Dfile.encoding=UTF-8`;properties 资源 UTF-8。

---

## §8 Phase 划分(依赖拓扑序;每 phase = writing-plans 详细计划 → TDD 实施 → 全绿 → 小结)

> 顺序由 §4.4 断环后的**单向依赖拓扑**严格推导(已逐包核对 import 图):
> `result/error/structure/common` ← `context/log/pattern/hash` ← `date/number/id` ← `io/path/mime/json/convert/copy` ← `locale/async` ← `web` ← 收口。

| Phase | 范围 | 关键动作 |
|---|---|---|
| P0 奠基 | pom / 目录 / 文档骨架 | BOM+插件(compiler/surefire/jacoco)+依赖收敛落 pom;docs 骨架(CONTEXT.md、adr/ 含 8 条 inherited、superpowers/);`.editorconfig`;首次提交 |
| P1 核心类型 | `result` `error` `common` `structure`(Tuple/Triple) | C1 断环之 error 侧(错误类型纯数据化,ADR);RV2-18 别名精简决议(ADR);ArchUnit 无环规则就位 |
| P2 运行基座 | `context` `log` `pattern` `hash` | RV2-17 重估 + logback 耦合隔离(ADR);盲区补测 |
| P3 数值与 ID | `date` `number` `id` + Id 装配 | date 去 lang3;ChineseNumbers 决议执行;`FacilityIdProperties` 归位 id 包(C3);`FacilityIdAutoConfiguration` |
| P4 IO 与序列化 | `io` `path` `mime` `json` `convert` `copy` + Json 装配 | CopyUtil 拆分;zip-slip 复核;C2 收尾(`WrappedContainer`/`WrappedDataType` 安置落位);`FacilityJsonAutoConfiguration` |
| P5 运行期服务 | `locale` `async` + Core/Locale/Async 装配 | C1 断环之 locale 侧(错误消息解析衔接+集成测试);i18n 资源迁移;`FacilityCoreAutoConfiguration` 跨簇 bean 齐装 |
| P6 Web 簇 | `web` 全部 + Web 装配 | 安全默认值逐类复核;servlet API 依赖决议;5 个 web properties 归位(C3) |
| P7 收口 | 文档 / 覆盖率 / 对账 | README/DESIGN/USAGE 全新撰写;JaCoCo gate 生效;`dependency:analyze` 清零;§5 verdict 对账;§10 roadmap 落档 |

粒度说明:P1-P6 每簇内部"先迁移后 rework",迁移 commit 与 rework commit 分开,保证每个 rework 可独立回溯。

---

## §9 文档与 SDD 基线(新仓库自洽)

```
server-facility/
├── README.md / DESIGN.md / USAGE.md      # 三件套(P7 全新撰写,修正已知漂移)
├── CONTEXT.md                             # 域术语精简版(Seam/deep module/Result-style/RFC 7807)
├── docs/
│   ├── adr/                               # 0000 模板 + 8 条 inherited(登记 beacon 出处)+ 本工程新增
│   └── superpowers/{specs,plans}/         # 本 spec 起,每 phase 一对 spec/plan + retrospective
└── src/...
```

继承的 8 条 ADR(0001-0008)以 `inherited-from: beacon ADR-000N` 头登记,不改写内容;
本工程新决策(别名精简、LogUtil 重估、依赖收敛等)按 0000 模板新增。

---

## §10 新组件 roadmap(本次不实做;按 §4.3 范式接入)

| 候选 | 形态 | 天然挂点 |
|---|---|---|
| 日志脱敏 / masking | `LogPostHandler` 实现 | 现有 SPI,零架构改动 |
| 加解密门面 crypto | 新子包 + JDK/BC optional | 静态门面范式 |
| 限流 ratelimit | web filter + 存储 SPI | web 簇 + Seam |
| 幂等 idempotency | 注解 + 拦截器 + 存储 SPI | web 簇 |
| 分布式锁 lock | SPI + 默认单机实现 | Seam 升 real seam 示范 |
| 缓存门面 cache | 薄封装 Spring Cache | 装配范式 |
| Excel/CSV | 新子包 + poi/easyexcel optional | ADR-0001 optional 范式 |
| HTTP client 门面 | RestClient 薄封装 | 静态门面 + 装配 |

---

## §11 风险

| 风险 | 缓解 |
|---|---|
| 审美修订(别名精简/LogUtil 重写)引入回归 | 355 基线 + rework 先红后绿;每项独立 commit 可回滚 |
| C1 断环触及核心接口行为(`ErrorTypeInterface` 本地化路径) | P1 先设计后动手(ADR 记备选);P5 补 error×locale 集成测试闭环;基线测试适配走 TDD |
| wontfix 翻案沦为"为改而改" | A6 要求每项翻案记 ADR,写明"当年理由为何失效" |
| 文档漂移复制进新库 | 已识别 3 处失实(MapStruct/commons-codec/5 个装配),P7 重写时据码修正 |
| 迁移遗漏文件 | P7 对账:源 117 文件逐一标记 migrated/dropped,数目闭合 |

---

## §12 成功标准

1. §5 全部 keep/rework 包迁移完成,`mvn test` 全绿,用例数 ≥ 355 且盲区包各有行为测试;
2. JaCoCo 全局 line ≥ 80% 且 gate 生效;
3. `mvn dependency:analyze` 无 unused declared;§6 收敛全部执行;
4. 三件套 + CONTEXT + ADR 就位,无 TBD,与代码零漂移;
5. 每个 rework/翻案有 ADR 或 plan 条目可溯源;
6. 源 117 主源文件对账闭合(migrated + dropped = 117);
7. 包依赖无环(§4.4 三环全断),由 ArchUnit 规则挂 `mvn test` 守护。
