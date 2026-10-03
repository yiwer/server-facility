# Boot 4 目标平台账本与消费者验证边界

2026-10-04；票 22 的工具链决策见 [ADR-0045](../adr/0045-boot4-platform-toolchain.md)，票 23 已完成 Jackson 迁移并清空全部预期编译错误。票 24 按 [ADR-0047](../adr/0047-boot4-consumer-integration.md) 验证完整质量门、真实缺类/覆盖/Servlet 与普通 jar 消费。支持结论以 [24 执行报告](../verification/ticket-24-platform-integration.md) 的实际 OS 证据为准；目标平台现已在`80670fa`完成[Windows/Ubuntu同源CI](../verification/ticket-24-ci.md)，平台门已闭合；本批其他能力仍在集成分支实施，未发布制品。

## 依赖归属

官方 Boot 4.1.1 BOM 及相关模块 POM/JAR 已从 Central 实际取得。无独立版本的依赖由 BOM 管理；执行证据的 effective POM/tree 为完整传递依赖依据。

| 依赖 | 目标实际版本 | 声明 / 迁移责任 |
|---|---|---|
| Boot BOM / core / autoconfigure / jackson | 4.1.1 | BOM import；根不继承 parent，显式添加实际使用的 Jackson 技术模块 |
| Boot starter-test / web-server / tomcat / webmvc | 4.1.1 | BOM，根 test scope；独立 Web 应用自行选择 starter-webmvc，容器不进入库生产传递图 |
| SLF4J API | 2.0.18 | BOM；主源码不依赖 Logback |
| Caffeine | 3.2.4 | BOM，optional；与 context-support 成对；24 实际缺类图验证已有回退，不代替 08 TTL/容量政策 |
| Spring context / beans / core / web / webmvc / context-support | 7.0.9 | BOM；Web 和 context-support 继续 optional |
| Jackson core / databind | 3.1.5 | `tools.jackson.core`；23 已迁移公开类型与 serializer/builder 行为，独立旧金样及实际 HTTP 保留 |
| Jackson annotations | 2.21 | 保留 `com.fasterxml.jackson.core`，不能盲目替换组名 |
| JDK8 / JSR310 / parameter-names 模块 | 不再独立声明 | 功能已合并至 Jackson 3 databind；无兼容 Jackson 2 模块或双主版本 classpath |
| Jakarta annotation / validation / Servlet API | 3.0.0 / 3.1.1 / 6.1.0 | BOM；Servlet 继续 optional；C5 `Nullable` 策略不在本票改动 |
| Lombok | 1.18.46 | 编译依赖和 processor 共同由 BOM 管理 |
| Boot configuration processor | 4.1.1 | 显式 processor path，与 Boot 属性同步 |
| Jupiter / JUnit Platform | 6.0.3 | starter-test 传递；真实发现由独立探针验证 |
| junit-jupiter-params | 6.0.3 | 保留 05 新增的显式 test-scope 声明，由目标 BOM 管理 |
| ArchUnit JUnit 6 adapter/api/engine | 1.5.1 | 显式 pin；注解仍在 `com.tngtech.archunit.junit` |
| Mockito / AssertJ | 5.23.0 / 3.27.7 | BOM，test scope |
| Logback / Hibernate Validator | 1.5.38 / 9.1.3.Final | BOM，test scope；provider 不进入库生产依赖 |
| Tomcat | 11.0.24 | BOM；仅测试 fixture 和独立 Web 应用选择容器 |
| Commons CSV | 1.14.1 | 票15 required；Apache 独占解析语法。Windows普通jar消费者已验证无Tika/POI图的Commons IO2.20.0 / Codec1.19.0；库测试图因其他依赖为IO2.22.0 / Codec1.21.0。两份完整tree见票15证据 |
| Tika / jsoup / POI | 4.1.0 / 1.23.2 / 5.5.1 | Tika由13；POI由16升级，optional完整格式图含Commons Compress1.28.0，完整图真实tree为poi/poi-ooxml/lite5.5.1、XMLBeans5.3.0、Compress1.28.0、IO2.21.0、Curves1.08、Log4j API2.24.3、Collections4 4.5.0；jsoup由32升级，历史样本与显式HTML预算同票交付 |

构建工具保持 Wrapper 3.3.4 / Maven 3.10.0、compiler 3.16.0、Surefire 3.6.0、JaCoCo 0.8.15、dependency 3.11.0、jar 3.5.1；其余固定版本见 [Java 25 入口](java25-baseline.md)。未降低根 JaCoCo 88/88/75、五条 ArchUnit 或 failOnWarning。

## 实际技术模块归属

下表来自目标 JAR 的 class entries，不从包名猜测模块。仅变更实际引用；不引入生产 Web starter 或容器。

| 类型 | Boot 4 包 | 所属模块 |
|---|---|---|
| FilterRegistrationBean | `org.springframework.boot.web.servlet`（保持） | spring-boot |
| ErrorPage / ErrorPageRegistrar / ErrorPageRegistry | `org.springframework.boot.web.error` | spring-boot |
| TaskExecutionAutoConfiguration | `org.springframework.boot.autoconfigure.task`（保持） | spring-boot-autoconfigure |
| MessageSourceAutoConfiguration | `org.springframework.boot.autoconfigure.context`（保持） | spring-boot-autoconfigure |
| JacksonAutoConfiguration / JsonMapperBuilderCustomizer | `org.springframework.boot.jackson.autoconfigure` | spring-boot-jackson |
| ServletWebServerApplicationContext | `org.springframework.boot.web.server.servlet.context` | spring-boot-web-server |
| TomcatServletWebServerFactory | `org.springframework.boot.tomcat.servlet` | spring-boot-tomcat |
| TomcatServletWebServerAutoConfiguration | `org.springframework.boot.tomcat.autoconfigure.servlet` | spring-boot-tomcat；替换 fixture 旧通用 ServletWebServerFactoryAutoConfiguration |
| DispatcherServletRegistrationBean | `org.springframework.boot.webmvc.autoconfigure` | spring-boot-webmvc |
| ErrorMvcAutoConfiguration | `org.springframework.boot.webmvc.autoconfigure.error` | spring-boot-webmvc |
| ApplicationContextRunner / WebApplicationContextRunner / FilteredClassLoader | `org.springframework.boot.test.context…`（保持） | spring-boot-test |

独立 Web consumer 使用 `spring-boot-starter-webmvc`，旧 customizer 已迁为 `JsonMapperBuilderCustomizer`；保留 JSON 独立字面金样，不通过 Jackson 2 兼容 starter 掩盖。需要 MockMvc 技术自动装配时应使用 `spring-boot-starter-webmvc-test`，本项目现有 context runner 并不因此自动迁包或必须引入该 starter。

04 的共享实际 Servlet fixture 直接使用 `spring-boot-web-server`、`spring-boot-tomcat`、`spring-boot-webmvc`，所以根 POM 显式声明这三个 test-scope 模块；保留 04 的 BOM 管理 `tomcat-embed-core` test 依赖。探针实际编译、加载并核对上表14种类型的来源 JAR，不假设导入 BOM 会自动提供拆分后的类。

## 执行和证据

```text
java verification/Verify.java platform --fresh
java verification/Verify.java all --fresh
```

`platform` 为独立工具链子集，真实跑 Jupiter / ArchUnit 正向与各自故意失败控制、Lombok/配置处理器、69.0 classfile、JaCoCo 指令探针和 dependency analyzer，并解析根依赖。日志与输入在 `.verification-results/<时间>-platform`；CI 分别保留完整 `all` 与 `platform` 的状态。

票28起，`integration/resources/all` 还需 `PG_BIN` 指向 PostgreSQL18.6 原生工具，按[准备说明](java25-baseline.md)设置；缺失不跳过，库fast和platform子集仍不需数据库。

`all` 执行库全量质量门、纯 Java 与 Spring 普通 jar 消费、旧 JSON 金样/两应用真实 HTTP、五种非 Web 实际依赖图和三种 Web 覆盖/关闭场景，以及 Tika 缺席/选用的实际上传消费者。票15新增 `CsvConsumer`，在原普通 jar consumer 的必需依赖图中执行 CSV，明确排除 Tika/POI；归档其 POM、tree、classpath 与源文件。输入、各图 effective POM/tree/classpath 与结果保存在报告中；安装 jar 必须等于本次根构建 jar。所有 JVM、HTTP 和临时文件范围有界，详见 [矩阵说明](../../verification/platform-consumer/README.md)。

票 22 的 68 个 Jackson 诊断仅是保留的历史迁移证据：[精确交接清单](../verification/ticket-22-jackson-diagnostics.md)。当前没有预期编译红灯或迁移专用 skip；不能把工具链探针的 5 项加入主库总数。非 BOM Tika、jsoup、POI 的全部业务与格式保证分别归 13、32、16；24 的类缺席与普通产物接合不等于验证了所有文件能力，最终由 33 汇总。

## 官方依据

- [Boot 4 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)：技术模块及测试支持拆分。
- [Boot 4.1.1 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)：实际目标版本归属。
- [Jackson 3 发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)：不可变 mapper、合并模块和 annotations 保留。
- [ArchUnit 1.5.1](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1) / [JUnit 6.0.3](https://docs.junit.org/6.0.3/overview.html)：目标引擎。
