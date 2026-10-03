# Java 25 与 Spring Boot 4 基线研究及迁移方案

研究日期：2026-10-03（Asia/Shanghai）。代码基线：`0ee9d547022371ad31f885605e999de17ec22777`。状态：**研究与拟议方案，尚未实施升级**。

本文读取了 README、完整 pom、DESIGN、ADR 索引，以及 JSON、MIME、自动装配、架构测试的有关源码。遵循现有质量门，不调低覆盖率、不删除架构规则、不用宽泛 dependency ignore 掩盖升级问题。负责版本调查的子任务没有执行构建；主任务执行的原始基线结果见下，不代表目标升级组合已经通过验证。

同日主任务使用 Maven3.9.16 + Oracle JDK25.0.4.1 完成原pom的 `verify`；研究者复核了 `target/research/baseline-verify.log` 结尾：1196 tests、0 failure/error/skipped，含5条ArchUnit，覆盖率门与依赖分析通过。主任务读取的JaCoCo计数换算为 instruction93.60%、line93.34%、branch86.95%。**当时两处compiler release仍为21**：这只证明原项目产出的Java21字节码可在该JDK25环境构建/运行测试，不证明旧JaCoCo/ArchUnit能处理release25字节码。

## 1. 建议与证据边界

建议将长期基线定为 **Java 25 + Spring Boot 4.1.1 BOM + Spring Framework 7.0.9 + Jackson 3**。保留 Maven 构建，优先交付可独立验证的升级批次；Boot 3.5.16 是定位 JDK/工具链问题的短期中间站。当前仓库是一份供消费方引用的库，不是可直接运行的应用，因此其兼容承诺必须通过实际消费方应用验证，不能只有库内部测试。

这不是因为 Java 25 必须搭配 Boot 4：官方明确 Boot 3.5.16 兼容 Java 17–25，Boot 4.1.1 兼容 Java 17–26。迁到 Boot 4 的主要依据是受支持的平台、Jackson 3 和 Jakarta EE 11 生态。Framework 官方说明 6.2 开源支持于 2026-06 结束，7.0 是当前生产线；商业支持与公开 Maven 工件是否继续出现是不同问题。[Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Boot 当前系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Framework 支持线](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions)

版本选择经过两层检查：官方发布文档，以及 Maven Central 的确切 POM / metadata。动态文档可能滞后，例如 ArchUnit news 只列到 1.5.0，但官方 GitHub 和 Central 已发布 1.5.1；搜索摘要中的 compiler 3.15.0 也落后于可读取的 3.16.0。表中数字是本次读取的、用户日期前可核实的发布版本，**不是无限期有效的“最新版本”承诺**。Central metadata 的 `<release>` 也可能是 beta / RC / milestone，不能直接拿它自动升级生产依赖。[ArchUnit 1.5.1](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1)、[Compiler metadata](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-compiler-plugin/maven-metadata.xml)、[Boot metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml)

截至本次读取，Boot stable 文档为 4.1.1，4.0 维护文档为 4.0.8；4.2.0-M2、Framework 7.1.0-M2、Maven 4.0.0-rc-7 是预发布，本方案不采用。Oracle 已公开 JDK 25.0.4.1（2026-08-18）的发行说明；实际 CI/容器应另固定选定供应商的 Java 25 补丁和镜像摘要，不能把 Oracle 的补丁号自动套到所有 OpenJDK 发行版。[Boot 4.0](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、[JDK 25 汇总发布说明](https://www.oracle.com/java/technologies/javase/25all-relnotes.html)、[Maven 下载页](https://maven.apache.org/download.cgi)

## 2. 完整依赖与插件账本

“当前 BOM”数字直接来自 [Boot 3.5.10 POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.10/spring-boot-dependencies-3.5.10.pom)；“目标 BOM”数字直接来自 [Boot 4.1.1 POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)。所有原先 optional 的能力在保守迁移方案中继续 optional，test scope 不进入生产依赖。方案 B 的显式 starter 会另行定义依赖语义。

### 2.1 pom 中的全部直接依赖

| 依赖 / 坐标 | 当前 | 目标及管理方式 | 迁移判断 |
|---|---|---|---|
| `spring-boot-dependencies`（导入 BOM） | 3.5.10 | 4.1.1；3.5.16 为中间站 | 平台整体升级，不能单独强升 Framework |
| `org.projectlombok:lombok` | 显式 1.18.42 | 删除独立版本，目标 BOM 1.18.46；处理器同步 BOM | 1.18.42 已支持 JDK 25，不是硬阻断；不为“更大数字”偏离 BOM |
| `jakarta.annotation:jakarta.annotation-api` | BOM 2.1.1 | BOM 3.0.0 | 现有 `jakarta.annotation.Nullable` 仍存在；JSpecify 迁移是另一个设计决定 |
| `org.slf4j:slf4j-api` | BOM 2.0.17 | BOM 2.0.18 | 保留直接声明；生产不引入 Logback 实现 |
| `org.springframework:spring-context` | BOM 6.2.15 | BOM 7.0.9 | 统一随平台升级 |
| `org.springframework:spring-beans` | BOM 6.2.15 | BOM 7.0.9 | 统一随平台升级 |
| `org.springframework:spring-core` | BOM 6.2.15 | BOM 7.0.9 | 同时影响反射、字节码读取、空值契约 |
| `org.springframework.boot:spring-boot` | BOM 3.5.10 | BOM 4.1.1 | 按模块重组核对所用类型归属 |
| `org.springframework.boot:spring-boot-autoconfigure` | BOM 3.5.10 | BOM 4.1.1 | 不再承载所有技术的自动装配 |
| `jakarta.validation:jakarta.validation-api` | BOM 3.0.2 | BOM 3.1.1 | 继续遵守 ADR-0013；库核心不要求消费者引入 provider |
| `com.fasterxml.jackson.core:jackson-core` | BOM 2.19.4 | **改坐标** `tools.jackson.core:jackson-core`，BOM 3.1.5 | 包、异常类型改变 |
| `com.fasterxml.jackson.core:jackson-annotations` | Jackson 2 BOM | 保留坐标；目标 Jackson 3 BOM 管理为 **2.21** | 不要机械改成 tools.jackson 或 version 3.x |
| `com.fasterxml.jackson.core:jackson-databind` | BOM 2.19.4 | **改坐标** `tools.jackson.core:jackson-databind`，BOM 3.1.5 | 公共 JSON 类型造成源代码/二进制不兼容 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | BOM 2.19.4 | **删除** | 功能并入 Jackson 3 databind |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | BOM 2.19.4 | **删除** | 时间能力并入 databind；自定义时间序列化器仍需迁移 |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | BOM 2.19.4 | **删除** | 功能并入 databind；保留 javac `-parameters` |
| `org.apache.tika:tika-core` | 显式 3.2.3 | 中间站 **3.3.2**，最终 **4.1.0**，显式 pin | 4.x 有流位置契约改变，不能无测试直升 |
| `jakarta.servlet:jakarta.servlet-api` | BOM 6.0.0 | BOM 6.1.0 | Boot 4 Servlet 基线；消费方容器一并升级 |
| `org.springframework:spring-web` | BOM 6.2.15 | BOM 7.0.9 | 保留 optional；RestClient/HTTP 适配核验 |
| `org.springframework:spring-webmvc` | BOM 6.2.15 | BOM 7.0.9 | 保留 optional；异常映射及过滤链完整集成测试 |
| `org.jsoup:jsoup` | 显式 1.18.3 | **1.23.2**，显式 pin | 清洗策略回归，不把库升级当 XSS 策略替代 |
| `com.github.ben-manes.caffeine:caffeine` | BOM 3.2.3 | BOM 3.2.4 | 保留与 context-support 成对 optional |
| `org.springframework:spring-context-support` | BOM 6.2.15 | BOM 7.0.9 | 不将 Caffeine 后端变成隐式必需 |
| `org.apache.poi:poi` | 显式 5.3.0 | **5.5.1**，显式 pin | 与 poi-ooxml 一起升级 |
| `org.apache.poi:poi-ooxml` | 显式 5.3.0 | **5.5.1**，显式 pin | 继续在 ExcelSupport 隔离类型；验证临时文件、读取限制、公式行为 |
| `org.springframework.boot:spring-boot-starter-test` | BOM 3.5.10 | BOM 4.1.1 | JUnit Jupiter 5.12.2 → 6.0.3；技术测试模块按需追加 |
| `ch.qos.logback:logback-classic` | BOM 1.5.25，test | BOM 1.5.38，test | 维持 test scope，生产 SLF4J 抽象不变 |
| `org.hibernate.validator:hibernate-validator` | BOM 8.0.3.Final，test | BOM 9.1.3.Final，test | 保留“provider 缺席也可启动”测试 |
| `com.tngtech.archunit:archunit-junit5` | 显式 1.3.0，test | JDK25 中间站 `archunit-junit5:1.5.1`；Boot4 目标改 **`archunit-junit6:1.5.1`** | 引擎名称和依赖分析 ignore 白名单必须相应精确调整 |

补充来源：[Jackson 3 BOM](https://repo.maven.apache.org/maven2/tools/jackson/jackson-bom/3.1.5/jackson-bom-3.1.5.pom)、[Lombok changelog](https://projectlombok.org/changelog)、[Jakarta Nullable 3.0](https://jakarta.ee/specifications/annotations/3.0/apidocs/jakarta.annotation/jakarta/annotation/nullable)、[Tika 下载与支持线](https://tika.apache.org/download.html)、[jsoup 发布](https://jsoup.org/news/)、[POI 发布](https://poi.apache.org/download.html)、[ArchUnit 1.5.0 引入 JUnit6](https://github.com/TNG/ArchUnit/releases/tag/v1.5.0)、[ArchUnit 1.5.1 发布](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1)。

Lombok 截至研究日另有 1.18.48；目标 BOM 的 1.18.46 已包含 JDK25 支持和 Jackson3 修正。本方案有意选择 BOM 版本。只有确认本库遇到 1.18.48 修复的问题时，才在独立升级批次覆盖它，并保持编译依赖与 annotation processor 一致。[Lombok changelog](https://projectlombok.org/changelog)

### 2.2 全部显式插件与 annotation processors

| 项目 | 当前 | 目标 | 证据与要点 |
|---|---|---|---|
| Java release | property=21，compiler 内又硬编码 21 | **唯一来源 `maven.compiler.release=25`** | 不能只改 property；编译、测试、Javadoc、消费样例都要用 Java25 |
| Maven runtime / Wrapper | pom 不锁运行版本 | **3.10.0 Wrapper**；3.9.16 可作过渡环境 | 官方推荐稳定 3.10.0；Maven4仍RC。[下载页](https://maven.apache.org/download.cgi) |
| `maven-compiler-plugin` | 3.13.0 | **3.16.0** | 使用 javac `release`；保留 `parameters=true` 与显式 processor path。[目标插件](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html) |
| `maven-surefire-plugin` | 3.5.2 | **3.6.0** | JUnit Platform provider 变化；确认全部测试及 ArchUnit 被发现，不能只看 exit 0。[官方说明](https://maven.apache.org/surefire/index.html) |
| `maven-jar-plugin` | 3.4.2 | **3.5.1** | Central 的 4.0.0-beta-1 不选；保持普通库 jar，不添加 Boot repackage。[metadata](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml) |
| `maven-dependency-plugin` | 3.7.1 | **3.11.0** | 目标 POM 引入 dependency-analyzer 1.17.1；该 analyzer 使用 ASM9.9.1，可读取 Java25。[插件 POM](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-dependency-plugin/3.11.0/maven-dependency-plugin-3.11.0.pom)、[analyzer POM](https://repo.maven.apache.org/maven2/org/apache/maven/shared/maven-dependency-analyzer/1.17.1/maven-dependency-analyzer-1.17.1.pom) |
| `jacoco-maven-plugin` | 0.8.12 | **0.8.15** | Java25 正式支持从0.8.14开始；0.8.15已发布，不能用trunk 0.8.16快照。[正式发布](https://www.jacoco.org/jacoco/)、[变更](https://www.jacoco.org/jacoco/trunk/doc/changes.html) |
| Lombok processor | 1.18.42 | BOM **1.18.46** | compiler3.16可从 dependencyManagement 推断 path 版本，无需复制属性 |
| `spring-boot-configuration-processor` | 3.5.10 | BOM **4.1.1** | 与Boot同步；生成配置metadata必须进入jar |

本库仅 import BOM，**没有继承 Boot parent，因此 BOM 不会替本库管理构建插件**；维持显式插件版本是必要的。也不要误以为声明一个同名 `<spring-framework.version>` 属性就能改 imported BOM 的内部版本。annotation processor path 的顶层版本可以省略并取 dependencyManagement；`annotationProcessorPathsUseDepMgmt` 只控制其传递依赖，不能混为一谈。[Boot Maven 管理规则](https://docs.spring.io/spring-boot/maven-plugin/using.html)、[Compiler processor 规则](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html)

字节码工具是 JDK25 的真实前置条件：Java25 class major=69；ArchUnit1.4.1 才正式支持它，ASM9.8 加入 V25。应用 BOM 中升一个 ASM，不能修复 Maven plugin class realm、ArchUnit 内部/打包 ASM 或 JaCoCo agent 的旧解析器；应升级拥有它的工具。[ArchUnit 1.4.1](https://github.com/TNG/ArchUnit/releases/tag/v1.4.1)、[ASM 版本](https://asm.ow2.io/versions.html)

现有 Surefire 的 `@{argLine}` 拼接和 Windows `jdk.net.unixdomain.tmpdir` 解释是有依据的本地设置；升级时先保留，在独立环境复现后再评估删除。覆盖率仍要求 INSTRUCTION/LINE≥0.88、BRANCH≥0.75；工具版本变化可能改变生成代码过滤方式，应说明差异，不用调门槛补偿。

## 3. Boot 3.5 → 4 的项目专属断点

### 3.1 JSON 是最大兼容面，不能搜索替换结束

代码证据：`Jsons.java:37–41` 公开 ObjectMapper；同文件公开 TypeReference、JsonNode；`JsonUtil` 也公开 Jackson 类型。`JsonConfig.java:591` 接受 `Consumer<ObjectMapper>`，`:662` build 后还 `setTimeZone`、`setDateFormat`、`:688` 起修改 mapper features。这意味着 Jackson3 迁移同时改变公共 Interface 和内部构建生命周期。

Jackson3 把三个 Java8 模块并入 databind，并使 mapper 走不可变 builder；核心异常变为 unchecked `JacksonException`。因此应将 `JsonConfig` 的最终配置移入 builder，customizer 改接受 builder；删除已合并模块的“启用/禁用”旋钮；逐个迁移自定义 serializer/deserializer。公共 Jackson2 类型签名必须在 CHANGELOG 明确破坏性变化，不能承诺同一jar兼容两种 ObjectMapper。[Jackson3 发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)

建议用一次升级机会收缩 JSON Interface：保留 `serialize(value)`、`deserialize(text, Class)`、必要的泛型类型输入与明确逃生口；只保留具有真实用户场景的 namespace。DEFAULT 必须复用消费应用注入的 **JsonMapper**；不能让库私有 mapper 与 MVC 输出默默分叉。框架默认行为变更必须由契约样例确认，尤其枚举、时间、long ID、null、Optional、未知字段、尾随数据和稳定字段顺序。CANONICAL 若用于签名，必须有独立规范，不能把“字段排序”当规范化算法。

`Jsons` 的 IO / parse 失败继续进入 `Result`。Jackson3异常不再是受检异常，并不意味着可以删除错误包装；捕获范围应限于 Jackson/IO 可预期失败，编程错误仍暴露。构建时完成 mapper 配置，运行期不变更共享实例，能同时减少线程问题和测试污染。

Boot4 默认 Jackson3，自动装配更关注格式专用 JsonMapper。旧 `ObjectMapper` bean 不再等价于替换 JSON mapper。`spring-boot-jackson2` 是官方临时兼容路径，且已弃用；它可以帮助消费应用分期迁移，但不应成为新脚手架长期默认。[Boot4 JSON](https://docs.spring.io/spring-boot/4.0/reference/features/json.html)、[Spring Jackson3 设计说明](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)

### 3.2 自定义自动装配：逐类核验模块归属

Boot4 模块拆分影响 `spring-boot-autoconfigure` 曾经“什么都含”的假设。需要新增技术模块或starter依赖并核验条件，而非把全部包名统一替换。[Boot4 迁移说明](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

| 本库引用 | 目标处理 |
|---|---|
| `FacilityJsonAutoConfiguration` 的 `org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration` | 改为 `org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration`，所需技术模块 `spring-boot-jackson` 显式声明；条件和注入对象针对 Jackson3 JsonMapper。[目标包](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/package-summary.html) |
| `FacilityAsyncAutoConfiguration` 的 `org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration` | Boot4.1.1 仍是该包；保留按类型让位与顺序验证。[目标类](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/task/TaskExecutionAutoConfiguration.html) |
| `FacilityLocaleAutoConfiguration` 的 `org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration` | 仍是该包；顺序/Primary语义回归，不盲改。[目标包](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/context/package-summary.html) |
| Web / Idempotency 装配的 `org.springframework.boot.web.servlet.FilterRegistrationBean` | 公共包名仍存在；核验并声明 `spring-boot-servlet` 模块，保持 optional 或归入显式web starter。[目标类](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/web/servlet/FilterRegistrationBean.html) |
| `AutoConfiguration` / conditions / configuration properties | 核心注解仍使用相应公共包；11项imports必须完整、无旧目标类名。[AutoConfiguration](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/AutoConfiguration.html) |

有 `@ConditionalOnClass` 不等于整份配置在缺类时一定安全：配置解析、bean方法签名和字段初始化必须一起考虑。验收要涵盖“无Servlet”“无Jackson技术模块”“无Tika”“无POI”“仅Caffeine/仅context-support”等消费方 classpath，确保缺一项不会使无关能力失败。现有 FilteredClassLoader 测试应保留并扩展成真实jar消费测试。

### 3.3 Servlet、验证、nullness 与测试引擎

Framework7基线是 Jakarta EE11：Servlet6.1、Validation3.1、JUnit6；这是消费方容器和测试栈变化，不是从 `javax` 到 `jakarta` 的再次全量改名。Boot4支持的嵌入容器是 Tomcat11 / Jetty12.1，原有Undertow消费场景须另评估，不能继续暗示任意Boot3容器都可用。[Framework7 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)、[Boot4 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)

本库 C5 规定 `jakarta.annotation.Nullable`，Framework7偏向JSpecify。Jakarta Nullable 并未消失；建议独立提出修订C5的ADR，以 `@NullMarked` + 精确 Nullable 提高泛型/数组表达力，但不与机械版本升级揉成一批。error纯JDK规则仍生效，是否接受仅注解依赖也必须作为显式架构变更，不能悄悄例外。[Jakarta Nullable](https://jakarta.ee/specifications/annotations/3.0/apidocs/jakarta.annotation/jakarta/annotation/nullable)、[Spring null safety](https://docs.spring.io/spring-framework/reference/core/null-safety.html)

测试特别检查五条 ArchUnit 规则确实执行。ArchUnit1.5.x开始更完整地计入 catch 异常类型的依赖，旧测试可能揭示过去漏报的包循环；应分析源码中的真实依赖，不因工具变严格就关闭规则。[ArchUnit1.5.1](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1)

### 3.4 Tika 的“编译通过但语义变化”

`MimeTyping.java:34` 持有 Tika，`:81` / `:101` 把调用方 InputStream直接传给detect。Tika4 的 Tika facade保留这类签名，但官方明确detect后调用方流位置可能前移，内部读前缓冲不会交还；流也不由它关闭。故升级目标虽为4.1.0，必须先定义输入流所有权和后续读取契约，必要时用调用方可见的可重放来源或由本Module拥有的TikaInputStream；不可只在内部包一层然后丢掉。[Tika4迁移](https://tika.apache.org/docs/4.1.x/migration-to-4x/migrating-to-4x.html)

真实场景是“嗅探上传类型后保存原始文件”。验收要求保存后的hash等于输入hash，覆盖支持/不支持mark的流、探测失败、短内容、大内容和读取上限。在这个测试成立前先升3.3.2维护线，并在计划中保留4.1.0明确任务，不以“其他依赖已更新”掩盖未完成的Tika切换。

### 3.5 Security / Testcontainers：属于消费方样例，当前库未直接依赖

本pom没有Spring Security或Testcontainers直接依赖，不应该为了版本清单把它们塞进核心。若提供受保护的Web消费样例，采用Boot BOM的Security7.1.1；采用SecurityFilterChain、Lambda DSL、authorizeHttpRequests、PathPatternRequestMatcher。Security7删除旧and()/authorizeRequests及旧matcher，应对真实路径规则和错误dispatch做行为测试。[Security7变更](https://docs.spring.io/spring-security/reference/7.0/whats-new.html)、[目标BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)

只有Redis/数据库Adapter等实际外部交互才引入Testcontainers。目标BOM为2.0.5，2.x模块改为 `testcontainers-*`，容器类也移至按模块命名的包，并移除JUnit4支持。不能从旧模板直接复制 `org.testcontainers:junit-jupiter`、`org.testcontainers:postgresql` 再只改版本。[Testcontainers2发布说明](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0)

## 4. Java 25：利用稳定能力，不扩散预览依赖

本库已有虚拟线程，不需要为“采用Java25”再封装一套线程框架。稳定的虚拟线程適合大量等待IO的任务；不池化虚拟线程，连接池、上游并发额度和CPU任务仍需独立有界控制。JDK24起synchronized导致的主要pinning已改善，但不能把这解读为任何native调用都没有pinning风险。[Java25 虚拟线程指南](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)

Boot可以通过 `spring.threads.virtual.enabled=true` 启用虚拟线程；启用后许多线程池大小参数不再控制它。虚拟线程为daemon，依赖调度器维持存活的应用需要考虑 `spring.main.keep-alive=true`。[Boot 虚拟线程](https://docs.spring.io/spring-boot/4.0/reference/features/spring-application.html)

本库的 `FacilityAsyncAutoConfiguration` 默认按 `TaskExecutor` 让位Boot，再创建自己的virtual-thread Executor。升级验收不仅看“是否是虚拟线程”，还应看 executor归属、关闭等待、拒绝/取消/超时、MDC/trace传播以及应用停止后的未完成任务。不要让Async静态默认与应用TaskExecutor各自占一套无法统一关闭的生命周期。

Java25的ScopedValue已正式化；StructuredTaskScope仍是第五次预览，StableValue也在预览列表。建议默认构建无 `--enable-preview`，不在已发布的公共Interface中暴露预览类型。ScopedValue可用于受控的不可变调用上下文，但不应机械替换Spring事务、SecurityContext或任意异步线程间的ThreadLocal；它的跨线程继承存在结构化约束。[Java25预览列表](https://docs.oracle.com/en/java/javase/25/docs/api/preview-list.html)、[ScopedValue规范](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html)

现有Maven工程无需切Gradle。若未来提供Gradle消费样例，Java25 toolchain及运行Gradle的官方支持起点都是9.1.0；研究日已发布9.8.0。Boot3.5文档明确支持Gradle7/8而Boot4支持8.14+/9，这使“Boot3.5 + 全流程Java25 + Gradle”不宜作为默认过渡方案。[Gradle兼容矩阵](https://docs.gradle.org/current/userguide/compatibility.html)、[Gradle发布](https://gradle.org/releases/)

## 5. 推荐执行顺序与验收

| 批次 | 改动范围 | 必须形成的证据 |
|---|---|---|
| 0. 冻结现状 | 保存当前依赖树、测试发现数量、覆盖率、真实消费样例 | 记录源码SHA、JDK/Maven版本、测试是否全部执行；README旧快照不能替代本次输出 |
| 1. 工具链 | Wrapper、compiler、Surefire、JaCoCo、ArchUnit；仍先编译release21 | 当前契约不变下质量门通过，定位工具升级新增发现 |
| 2. JDK25中间站 | release唯一化为25、Boot3.5.16、独立依赖维护更新 | Java25编译/测试/分析全部成功；产物class69；无preview；五条架构规则执行 |
| 3. Boot4/JSON3 | Boot4.1.1 BOM、技术模块、JsonConfig/Jsons、JUnit6/ArchUnit6 | JSON黄金样例与错误通道、MVC和JsonUtil一致、装配override/缺类矩阵、容器启动验证 |
| 4. Tika4及语义收紧 | Tika4.1.0流契约、文档处理行为；独立提案修订C5 | 嗅探后内容hash一致，ownership/上限/失败语义有证明 |
| 5. 发布兼容声明 | 新版本与消费指南、ADR、USAGE、CHANGELOG | 公布最低JDK/Boot、破坏性签名变化、替换示例及不能混用的旧适配器 |

Boot官方建议从最新3.5补丁开始迁4；这里分阶段为了归因，不是发布两条永久维护主线。如果0.1.0-SNAPSHOT尚无需要保留的生产消费者，可在同一发布窗口完成阶段2/3，再统一发布新基线；若确有Boot3消费者，则用明确的旧维护分支和截止目标隔离。[Boot4迁移指导](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

最低验收清单：

1. `mvn verify`全质量门通过，保留阈值与依赖分析；对变更前后测试数量、ArchUnit引擎、跳过数量做核对。
2. 一个真实Boot4 MVC消费应用从本地产物启动，以HTTP验证JSON、错误处理、过滤顺序、限流、幂等，以及覆盖默认bean后的行为。
3. 核心无Web消费应用、缺可选库应用都可启动；有意选择的能力缺必需依赖时，按方案A/B的明确Interface失败，不靠偶然NoClassDefFoundError。
4. JSON外部契约黄金样例；Tika流hash；POI大文件/临时文件释放；并发/取消/关闭的Async契约。
5. Windows与Linux至少各跑一次JDK25质量门；artifact包含imports和configuration metadata；普通jar可以被消费，未误repackage。
6. 文档不宣称Boot3/4同jar兼容、分布式exactly-once、无界线程吞吐提升等未验证保证；支持线和环境列表可由CI证据追溯。

## 6. 独立方案 B：按能力显式选择 Spring Boot starters

本节是独立架构提案，需新ADR替换现有“单jar + optional自动发现”的部分决策；不是对现行ADR的默默解释。借用codebase-design词汇：依赖声明、配置、能力范围、顺序和错误模式同样是 **Interface**。消费者要记住“缺哪个库就怎样降级”“静态registry先由谁初始化”等隐藏知识，会降低 **Depth**，即使静态方法看起来很短。

目标不是把29个包机械变成29个jar，而是按真实消费选择与替换点形成少量发布物：

| 发布物（拟名） | Interface与场景 | 隐藏的Implementation / 真正Seam | 兼容与依赖承诺 |
|---|---|---|---|
| `facility-core` | Result/error、纯值与确有收益的纯JDK能力；无Spring上下文也能用 | 局部算法和错误建模；不预造万能策略接口 | Java25普通jar；不依赖Boot/Jackson/Servlet/POI，不因应用classpath改变语义 |
| `facility-spring-boot-starter-webmvc` | 选择MVC接入：标准错误输出、trace、JSON策略、明确请求上限 | Boot自动装配、HttpMessageConverters、filter注册顺序、context注入；复用Spring提供的Seam | Boot4.1支持线、Framework7、Servlet6.1、Jackson3；required依赖正常传递，缺失时启动诊断 |
| `facility-policies` | 限流、锁、幂等的算法/协议Interface；明确scope与失败语义 | 本地或Redis Adapter可以改变实现，不能改变保证范围 | 不要求Web；本地Adapter只保证单进程；共享状态Adapter另发jar |
| `facility-spring-boot-starter-policies-local` | 显式选择单进程策略，适合单副本工具/测试 | 时钟、清理、并发状态机为内部Seam，外部Interface少且稳定 | 依赖本地Adapter；不会因为没有Redis就偷偷从集群语义退化 |
| `facility-spring-boot-starter-policies-redis` | 显式选择共享限流/幂等/锁能力及所需Redis配置 | Redis协议、原子操作、TTL、token、失败恢复；共用Adapter契约测试 | required Redis依赖；关键配置或Bean缺失启动失败；运行时断连依策略明确失败，不能无条件放行 |
| `facility-documents` | CSV及文档处理的受限输入/输出Interface：限额、ownership、错误 | 流/文件生命周期与资源限制集中管理；格式能力按真实复用决定Seam | CSV保持无外部依赖；POI、Tika实现可置独立Adapter jar，防止核心被重库拖入 |
| `facility-documents-poi` / `facility-documents-tika` | 消费者明确选择Excel与MIME能力；需要Spring时才加薄starter | POI/Tika类型尽量不泄漏到门面；两者不是同一Seam的“互替实现” | 正常传递所需库；模块载入时缺库是配置错误；可单独固定测试矩阵 |
| `facility-bom` | 所有facility发布物同一版本 | 集中收敛第三方未被Boot管理的版本 | 一条发布列车；不在consumer里重复复制版本表 |

Starter应避免附带第二套全局框架：Spring已经有CacheManager、RestClient.Builder、TaskExecutor等成熟Seam时直接使用。可放薄 `*-autoconfigure` 内部发布物隔离装配，但只有维护或消费收益明确时才拆；不必为每个门面制造starter。[Spring自定义starter指导](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)

### Interface 的不变量、顺序与错误

- **显式选择与诚实保证**：`policies-local`保证范围是当前进程，`policies-redis`保证范围按具体算法、存储持久性与故障模型声明。锁不能替代数据库唯一约束；幂等不能凭“重放HTTP响应”承诺外部副作用exactly-once。
- **初始化顺序**：调用者只需要构造/注入一个已就绪对象；移除进程静态registry的隐式发布次序。Web内部filter顺序固定且有集成证明，例如追踪与安全链、幂等捕获和异常转换的前后关系，不要求每个业务调用者复述实现细节。
- **必需能力缺席**：选择了starter就是请求该能力。无required类/Bean、缺关键配置应由明确诊断使启动失败，不能`@ConditionalOnClass`使整项能力静默消失。未选择starter则不注册、不触发、不发“降级”警告。
- **可替换性**：用明确的override Bean替换Adapter并接受同一契约测试；`@ConditionalOnMissingBean`仅表示合法override，不是静默降级策略。多个候选Adapter应启动失败并指出歧义。
- **错误通道**：调用期间可预期业务拒绝/IO失败进入结果类型或标准HTTP错误；启动配置错误fail-fast；取消/中断保留语义。上限、超时、容量、是否重放失败响应都属于Interface。

### 拟议消费示例

下面只表达候选Interface，不是已经存在的可执行代码。消费者选择所需能力一次，版本由facility-bom与Boot BOM收敛：

```xml
<dependency>
  <groupId>cn.code91</groupId>
  <artifactId>facility-spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
  <groupId>cn.code91</groupId>
  <artifactId>facility-spring-boot-starter-policies-redis</artifactId>
</dependency>
```

```java
final class ExportService {
    private final DocumentExporter documents;

    ExportService(DocumentExporter documents) {
        this.documents = documents;
    }

    Result<ExportReceipt, ExportError> export(ExportRequest request) {
        return documents.export(request);
    }
}
```

这里一个窄的文档Interface可以隐藏输出流ownership、临时文件清理、行数/字节预算、格式限制与失败清理，才形成Depth。若DocumentExporter只把POI每个参数改名再转发，就没有拆Module的价值；CSV与Excel差异也不应被一个“format=任意值”的万能请求抹平。只有存在调用者共享流程、且两种Adapter能兑现同一不变量时，才保留这个外部Seam。

### Adapter 契约测试与 Locality

同一策略测试通过外部Seam执行本地和Redis Adapter，覆盖拿不到配额、相同key并发、租约过期、错误token释放、处理中重试、已完成重放和存储失败。需要网络/重启语义时用真实Redis容器，不靠mock假装原子性。每个Adapter可以有额外故障测试，但不能把“单机fake通过”写成分布式保证。只有本地实现且没有现实替换需求的算法，应保留内部类，避免hypothetical Seam。

Web装配的契约则通过可运行消费应用验证：依赖选择→Bean图→真实请求→结果与副作用。版本兼容测试分别落在各starter/Adapter附近，形成Locality：JSON3变化主要落在web/json实现；POI更新只动文档Adapter；Redis协议变更不迫使核心值类型升级认知成本。

代价必须承认：多jar增加发布顺序、依赖收敛、测试矩阵、包可见性与文档路由成本。用同一仓库、同一版本、一个根命令、统一BOM和少量标准消费样例控制成本；按“消费者真正需要选择什么”拆分，而不是按包数量拆。迁移初期可提供聚合兼容starter给旧用户，但它必须有清晰退出目标，不再作为新应用唯一入口。

方案A（原单jar保守升级）交付快、兼容面小，适合先解除JDK25/Boot4阻断。方案B更贴近agent构建应用：依赖声明即能力清单、缺必需能力明确失败、失败模式可读、替换点可测试。推荐先完成独立的基线升级证据，再通过单一Web消费样例验证B的深度；当样例证明调用者知识负担下降，再拆发布物，避免大规模移动文件先于Interface设计。
