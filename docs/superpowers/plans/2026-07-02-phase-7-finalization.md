# Phase 7 计划:收口(文档 / 覆盖率 / 工程配置 / 对账)

> **For agentic workers:** 沿用 P2-P6 全部硬条款——RED/GREEN 为原始 mvn 粘贴、行号与提交文件交叉核对、迁移源测试失败=实施者的错、改源=任务失败(rework/补测任务除外,范围以本计划为准)、git 提交一律 PowerShell 工具且提交信息不含 ASCII 双引号、迁移提交与 rework 提交分开。

**Goal:** 把 server-facility 从"迁移完成"收口为"可发布":质量门达标、文档三件套齐备、文档零谎言、依赖账目清洁、§5 逐包判定对账闭合。

**Architecture:** 非迁移 phase。四条工作线——①代码修复(安全/一致性,TDD)②文档真实性勘误(红线)③覆盖率补测 + 质量门 ④文档撰写与对账。文档类真实性是本工程红线,凡 javadoc/package-info 声明与代码实况不符即 Critical。

**Tech Stack:** Java 21、Spring Boot 3.5.10 BOM、JaCoCo 0.8.12、maven-dependency-plugin、surefire 3.5.2。

## Global Constraints(每个任务隐含包含)

- 包根 `cn.code91.facility.*`;配置前缀 `facility.*`;类前缀 `Facility*`。
- 0.1.0-SNAPSHOT 未发布态——行为对齐/语义修正可断,但须有测试锁定。
- 基线:master @ P6 合并后,**679 绿 = 675 `@Test` + 4 `@ArchTest`**(grep 实数,勿手算连锁)。
- 全局覆盖率实测(P6 后):INSTRUCTION 73.8% / BRANCH 61.2% / LINE 73.3%。
- 文档真实性红线:任何 javadoc/package-info/ADR 的事实性声明必须与代码实况一致;"看起来对"不够,须 grep/读码实证。

## 分支
`feat/phase-7-finalization`(自 master)。计划先 commit 到 master。

## 0. 证据清单(计划作者已核,实施者复核)

| 事实 | 出处 |
|---|---|
| Filenames.DANGEROUS_EXTENSIONS = {exe,bat,cmd,sh,ps1,vbs,js,jar,msi,dll,com,scr,pif} —— 无 jsp/php/asp 等服务端脚本 | Filenames.java:17-20 |
| filename* 大小写不一:HttpFileResponses 2 处 `utf-8''`(L48/L97),ResponseUtil 1 处 `UTF-8''`(L90) | grep 实测 |
| LogUtil 缓存 `LOGGER_CACHE = new ConcurrentHashMap<>()`(强引用),但 javadoc L23/L43 称"弱引用避免内存泄漏"——谎言 | LogUtil.java:23/43/45 |
| FacilityErrorType 26 模板**全无** {0} 占位;format(args) 走 MessageFormat,无占位时 args 静默丢弃;javadoc L269 称"支持 MessageFormat 占位符" | FacilityErrorType.java + ErrorTypeInterface.format |
| FILE_* 错误类型混合调用:传 args(~23 处)与不传 args(~15 处,Hashing/PathIo/Zipping/MimeTyping/Filenames/HttpFileResponses/SafeUpload)并存 → 加占位符会让无参调用渲染字面 {0} | grep 全量实测 |
| facilityMessageSource basename `i18n/facility-messages`,`setUseCodeAsDefaultMessage(false)`;i18n 目录仅 _en/_zh_CN/_zh_TW,**无无后缀 base** → 非中英 locale 经 fallbackToSystemLocale 回落中文 | FacilityCoreAutoConfiguration:41/44 + ls |
| pom 无 JaCoCo check gate、无 maven-dependency-plugin、jar plugin 走默认 2.4(P6 merge 输出实证) | pom.xml 全文 |
| .editorconfig 已完善(charset/lf/indent 4;xml/yml/json indent 2;md 不 trim)——P7 篮此项已满足 | .editorconfig 实读 |
| .gitattributes 缺失;README.md 仅 10 行 stub;无 DESIGN.md/USAGE.md | ls + wc |
| ADR 0000-0013 + INDEX 齐;ADR-0002 补记把 Boot OnExecutorCondition 简写为 @ConditionalOnMissingBean(Executor.class)(控评已认领待精确化) | ls docs/adr + P6 终审 |
| 低覆盖包:date 29%、json 40%、copy 51%、web.util 55%、web.filter 61% | jacoco.csv 按包实测 |

## 1. 决策记录(计划级,终审对账)

1. **filename* 统一为大写 `UTF-8''`**:对齐 Spring `ContentDisposition` 惯例与 IANA charset 规范名。改 HttpFileResponses 2 处 + 其测试断言(ResponseUtil 已是大写,不动)。
2. **Filenames 补服务端脚本扩展名**:加 jsp/jspx/jspf/php/php3/php4/php5/phtml/asp/aspx/aspx/jsf/cgi/pl/py/rb;保留既有 13 个。纵深防御继承缺口(终审 P7-安全)。
3. **FacilityErrorType {0} 收窄表述,不改行为**:混合调用使加占位符高风险(15 处无参调用会渲染字面 {0});且面向用户错误消息嵌入服务器路径是信息泄漏面。修 FacilityErrorType.defaultMessage javadoc(删"支持 MessageFormat 占位符")、ErrorTypeInterface.format(args)/WrappedError.of(args) javadoc,明确:内置模板均无参,args 供日志/调试与消费方自定义 ErrorType 使用,不进内置面向用户消息(呼应 ADR-0010 边界文案稳定)。此为文档真实性修复,零行为改动。
4. **LogUtil 弱引用谎言 → 诚实描述**:改 L23/L43 javadoc 为 ConcurrentHashMap 强引用,说明 logger 实例数量有界(与类/名称一一对应,非用户输入驱动),不构成泄漏。零代码改动。
5. **base bundle 补齐**:新建 `i18n/facility-messages.properties`(无后缀,内容 = 英文键值,国际中立兜底);FacilityCoreAutoConfiguration 显式 `setDefaultLocale(Locale.ENGLISH)` 或 `setFallbackToSystemLocale(false)` 二选一(执行时读 facilityMessageSource 实现类型定夺),使非中英 locale 确定性回落英文而非系统中文。
6. **JaCoCo gate 务实门 + roadmap**:P7 补 date/json 盲区后按实测设**防退化门**(line/instruction 取实测值向下取整到 0.05,如 78%→0.75),branch 取实测保守值;spec §8 的 0.80 降为 §10 roadmap 显式目标并记 errata(date/json/copy/web 过滤链完整覆盖需专门测试工程,超收口范围)。防退化门优于虚设 0.80 让 verify 永久红。
7. **README/DESIGN/USAGE 三件套 + 消费方须知**:README(快速上手/坐标/特性矩阵/装配开关表),DESIGN(deep module 哲学/包簇地图/断环 C1-C3/装配范式/ADR 索引),USAGE(各 facade 用法 + 消费方须知:spring.messages.* 失效模型、JsonUtil 单例×多上下文、@ConditionalOnMissingBean 回退、i18n 聚合、properties 前缀表)。

## 2. 任务列表

### T1 安全与一致性代码修复(sonnet;TDD;2 提交)

**提交 1:Filenames 补服务端脚本扩展名**
- **Files**:Modify `src/main/java/cn/code91/facility/path/Filenames.java:17-20`;Test `src/test/java/cn/code91/facility/path/FilenamesTest.java`(既有,追加)
- RED:FilenamesTest 追加 `isDangerousExtension_serverScripts_returnsTrue`(参数化或多断言):`a.jsp/x.jspx/y.php/z.phtml/w.asp/v.aspx/u.jsf/t.cgi/s.pl/r.py/q.rb` 全 true;`normal.txt/report.xlsx` 仍 false。跑 `mvn test -Dtest=FilenamesTest` 原始 RED 粘贴(新扩展名当前 false)。
- GREEN:DANGEROUS_EXTENSIONS Set.of 追加 `"jsp","jspx","jspf","php","php3","php4","php5","phtml","asp","aspx","jsf","cgi","pl","py","rb"`(保留原 13 个,共 28 个)。跑 `mvn verify` 全绿。提交信息摘要:`fix: Filenames 危险扩展名补服务端脚本类型(纵深防御,jsp/php/asp 等)`。

**提交 2:filename* 大小写统一**
- **Files**:Modify `src/main/java/cn/code91/facility/web/download/HttpFileResponses.java:48,97`;Test `src/test/java/cn/code91/facility/web/download/HttpFileResponsesTest.java`(既有断言从 `utf-8''` 改 `UTF-8''`)
- RED:先改 HttpFileResponsesTest 里断言 filename* 的期望串 `utf-8''`→`UTF-8''`(2 处),跑 `-Dtest=HttpFileResponsesTest` 原始 RED(主源仍小写)。
- GREEN:HttpFileResponses L48/L97 `filename*=utf-8''` → `filename*=UTF-8''`。跑 `mvn verify` 全绿。摘要:`fix: filename* 统一大写 UTF-8''(对齐 Spring ContentDisposition 与 IANA 规范名)`。
- 验收:本任务 `@Test` 增量按 grep 实数(提交 1 视 FilenamesTest 追加数;提交 2 不新增 @Test 仅改断言)。

### T2 文档真实性勘误(sonnet;严格审查;1 提交)

零代码逻辑改动,纯 javadoc/ADR 文字修正;每处修正须 grep/读码实证(改前后各贴证据)。
- **LogUtil.java:23,43**:删"弱引用"表述,改为 ConcurrentHashMap 强引用 + logger 数量有界不泄漏说明。
- **FacilityErrorType.java:269**(defaultMessage 字段 javadoc):删"（支持 MessageFormat 占位符）",改为"默认错误消息(内置模板均为无参描述文案)"。
- **ErrorTypeInterface.format(Object...)** 与 **WrappedError.of(..., Object...)** / **ofWithArgs** javadoc:补一句语义澄清——args 仅当模板含 MessageFormat 占位符时注入;facility 内置 FacilityErrorType 模板无占位符,args 用于日志/调试上下文与消费方自定义 ErrorType,不进内置面向用户消息(见 ADR-0010)。
- **docs/adr/0002-rp-04-async-bean-type-matching.md**:P6 补记里"@ConditionalOnMissingBean(Executor.class)" 精确化为 Boot 3.5.10 实际机制(`TaskExecutorConfigurations$TaskExecutorConfiguration` 类级 `@Conditional(OnExecutorCondition.class)`,其 AnyNestedCondition 内含 ExecutorBeanCondition 走 @ConditionalOnMissingBean(Executor.class));结论(@AutoConfigureAfter 让位)不变。
- **web/download/package-info.java、web/upload/package-info.java**:核实依赖故事(grep import),与实况不符则修(参照 web.util 已修范式,补全 Depends on/Depended on by)。
- **javadoc 疑点核实(核实后修或判无问题)**:io/Zipping.java 的"容差"表述、number/formatSize 的 PB 非对称、locale/LocaleUtil translateMessage 穿透措辞——逐个 grep 定位,读码判真伪,真谎言则修,无问题则在汇报中标注"核实无误"。
- 跑 `mvn verify` 全绿(纯注释改动不应动测试数)。摘要:`docs: P7 文档真实性勘误(LogUtil 弱引用谎言/FacilityErrorType 占位符表述/ADR-0002 机制精确化/package-info)`。
- 汇报须逐处列"改前证据→改后",便于审查零漂移。

### T3 覆盖率补测(sonnet;补已有行为的盲区测试;1-2 提交)

目标:补 date + json 两个最低覆盖包的盲区行为测试,把全局 line 覆盖率拉向 ≥78%。**补测非 TDD 红绿**(行为已存在),直接写、跑 verify 全绿、看覆盖率提升。

- **date 包(29%,128 行)**:读 `src/main/java/cn/code91/facility/date/*.java` 找未测公开方法,`DateUtilTest`(既有或新建)补:格式化/解析往返、边界(null、非法串→err)、range 规范化、时区/Locale 变体。断言值一律探针实证(先跑出实际返回再固化),禁止凭想象。
- **json 包(40%,161 行)**:`Jsons`/`JsonUtil` 的盲区——序列化/反序列化往返、TypeRef 泛型、错误通道(非法 JSON→err WrappedError)、JsonNode 操作、边界(null/空)。同样探针实证。
- 每提交后跑 `mvn verify`,用 `awk` 从 jacoco.csv 算全局 line 覆盖率并在汇报中报增量(基线 73.3%)。
- 硬条款:补测发现某公开行为实际是 bug(与 javadoc 不符),**不许改主源迁就**,把实况写进测试并在汇报中列为观察(交 P7 后续或 roadmap)。
- 摘要:`test: date/json 簇盲区补测(覆盖率 XX%→YY%)`。
- 验收:全局 line 覆盖率较基线显著提升(目标 ≥78%);`@Test` 增量 grep 实数。

### T4 质量门 pom(sonnet;1 提交)

- **Files**:Modify `pom.xml`
- **JaCoCo check gate**:jacoco-maven-plugin 增 `check` execution(bind 到 verify,haltOnFailure=true),rule COUNTER LINE/INSTRUCTION/BRANCH minimum 按 **T3 完成后实测值向下取整到 0.05**(执行 T4 时先 `mvn verify` 读实际覆盖率再填,不许写死 0.80 让 verify 红)。excludes 加显式无逻辑类(package-info、纯 record/enum 若 JaCoCo 误算)。
- **maven-dependency-plugin pin + analyze**:pin 版本(如 3.7.1),`analyze-only` execution 或文档化 `dependency:analyze` 结论;对 BOM 传递但字节码不直接引用的(hibernate-validator test、spring-boot-starter-test 等)配 `ignoredUnusedDeclaredDependencies` / `ignoredNonTestScopedDependencies`,使 `mvn dependency:analyze` 无 warning。执行时先跑 `mvn dependency:analyze` 看实际 used-undeclared / unused-declared 清单,逐条消化(补声明或加 ignore + 注释理由)。
- **生命周期插件 pin**:显式 pin maven-jar-plugin(3.4.x)、maven-surefire 已 pin、maven-resources/install 等按 `mvn help:effective-pom` 实际用到的 pin,消除"用默认版本"警告。
- 跑 `mvn clean verify`(clean 去 ZzzProbe 陈旧报告)全绿 + `mvn dependency:analyze` 无 warning,原始输出粘贴。摘要:`build: P7 质量门(JaCoCo check gate 防退化 + dependency:analyze 清账 + 生命周期插件 pin)`。

### T5 .gitattributes 行尾归一(控制器;1 提交)

- **Files**:Create `.gitattributes`
- 内容:`* text=auto eol=lf` 基线;`*.java/*.xml/*.properties/*.md/*.yml text eol=lf`;`*.jar/*.png binary`;bat/cmd(若无)略。
- 提交后 `git add --renormalize .` 检查是否有既有文件行尾漂移(有则单独说明,本 phase 不强制重写历史)。摘要:`build: 补 .gitattributes 行尾归一(text eol=lf)`。

### T6 base bundle 回落修复(sonnet;TDD;1 提交)

- **Files**:Create `src/main/resources/i18n/facility-messages.properties`(无后缀,内容 = en bundle 全部 33 键值);Modify `src/main/java/cn/code91/facility/autoconfigure/FacilityCoreAutoConfiguration.java`(setDefaultLocale/setFallbackToSystemLocale);Test 新建 `src/test/java/cn/code91/facility/autoconfigure/LocaleFallbackIntegrationTest.java`
- RED:LocaleFallbackIntegrationTest(Core+Locale runner,参照 FacilityMessageSourceIntegrationTest):请求 `Locale.FRENCH` 解析 `facility.json.serialize_error` → 断言等于英文文案 "Failed to serialize object to JSON"(当前无 base + fallbackToSystemLocale 会回落系统 locale;在中文系统上 RED 得中文)。原始 RED 粘贴。
- GREEN:①建 base bundle(cp en 内容);②读 FacilityCoreAutoConfiguration 的 facilityMessageSource 实现,加 `setDefaultLocale(Locale.ENGLISH)` 或 `setFallbackToSystemLocale(false)`(依实现类型择一,注释理由)。跑 `mvn verify` 全绿。摘要:`fix: 补 i18n base bundle + 确定性回落英文(非中英 locale 不再落系统中文)`。
- 验收:`@Test` +1;4 bundle(base+3 locale)键集一致。

### T7 README / DESIGN / USAGE 三件套(控制器亲自;1 提交)

- **Files**:Modify `README.md`;Create `docs/DESIGN.md`、`docs/USAGE.md`
- **README.md**:项目定位(deep module 脚手架)、Maven 坐标、Java 21/Boot 3.5 要求、特性矩阵(21 子包一句话)、5 分钟上手(引入依赖→装配自动生效→Result/IdUtil/JsonUtil 示例)、装配开关表(facility.web.* / facility.id.* 前缀)、指向 DESIGN/USAGE/ADR。
- **docs/DESIGN.md**:deep module 哲学(窄接口宽实现)、包簇依赖地图、三组断环 C1/C2/C3(spec §4.4 对齐)、装配范式(@ConditionalOnMissingBean 兜底/imports 文件/@AutoConfigureAfter)、i18n 聚合(AggregatedMessageSource)、ADR 索引表(0001-0013 一句话)。
- **docs/USAGE.md**:各 facade 用法(Result/IdUtil/JsonUtil/LogUtil/DateUtil/LocaleUtil/Async/web 各簇)+ **消费方须知**:spring.messages.* 失效模型(facility 抢注 primary messageSource)、JsonUtil 单例×多上下文、@ConditionalOnMissingBean 全量回退点、properties 前缀总表、optional 依赖矩阵(哪个功能需要引哪个 optional dep)。
- 真实性:所有代码示例、前缀、类名 grep/读码实证;所有装配开关与 FacilityWebAutoConfiguration/各 properties 实际前缀一致。摘要:`docs: README/DESIGN/USAGE 三件套 + 消费方须知`。

### T8 §5 verdict 对账 + roadmap(控制器亲自;1 提交)

- **Files**:Create `docs/superpowers/P7-verdict-reconciliation.md`;Modify spec §8/§10(P7 行标记完成 + roadmap 落 JaCoCo 0.80 目标与 errata)
- §5 逐包判定对账:读 spec §5 判定表(23 包 keep/rework/drop),逐包核对目标仓实况(grep 包存在性 + drop 项确实不存在 + rework 项 ADR 落地),产出对账表(包|判定|落实状态|证据)。
- spec §8 P7 行标记完成;§10 roadmap 补 JaCoCo 0.80 aspirational 目标 + T4 实测门 errata。
- 摘要:`docs: §5 逐包判定对账闭合 + P7 收口 roadmap/errata`。

### 收尾(控制器)
- fable/opus 全分支终审(重点:文档真实性零残留谎言、JaCoCo gate 阈值诚实、§5 对账完整、README/USAGE 示例可编译)。
- ledger + memory 更新;finishing-a-development-branch(先例:本地合并回 master)。
- **迁移工程正式收官**。

## 3. 验收总标准
- `mvn clean verify` 全绿(含 JaCoCo check gate 通过);`mvn dependency:analyze` 无 warning。
- `@Test` 实数较基线 675 有增量(T1+T3+T6 补测);4 `@ArchTest`;ArchUnit 四规则通过。
- 全仓文档零谎言:LogUtil 弱引用、FacilityErrorType 占位符、package-info 依赖故事、ADR-0002 机制——全部与代码实况一致。
- 全局 line 覆盖率 ≥78%(T3 后实测);JaCoCo gate 为诚实防退化门。
- README/DESIGN/USAGE 三件套齐备,示例真实;.gitattributes 就位;base bundle 4 文件键集一致。
- §5 对账表闭合(23 包判定全部有落实证据)。

## 4. 自审(计划 vs spec)
- spec §8 P7 要求:README/DESIGN/USAGE ✓(T7)、JaCoCo gate ✓(T4,诚实门+roadmap errata)、dependency:analyze 清零 ✓(T4)、§5 verdict 对账 ✓(T8)、§10 roadmap 落档 ✓(T8)。
- 终审 P7 移交:Filenames 扩展名 ✓(T1)、FacilityErrorType 占位符 ✓(T2 收窄)、filename* 大小写 ✓(T1)、web/util package-info ✓(P6 已修,T2 连带审 download/upload)、base bundle ✓(T6)、translateMessage 穿透措辞 ✓(T2 核实)、ADR-0002 精确化 ✓(T2)、消费方 README ✓(T7)。
- 既有 P7 篮:.gitattributes ✓(T5)、.editorconfig ✓(已满足,划掉)、生命周期插件 pin ✓(T4)、LogUtil javadoc 谎言 ✓(T2)、Zipping/formatSize javadoc ✓(T2 核实)。
- 无占位符;类型/路径均实证。
