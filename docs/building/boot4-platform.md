# Boot 4 目标平台账本与中间验证边界

票 22，2026-10-04；决策 [ADR-0045](../adr/0045-boot4-platform-toolchain.md)。这里记录实际解析过的目标平台，不表示主库已完成 Jackson 迁移。22/23 仅进入 `codex/server-facility-next`，24 闭合完整平台门后才允许进入主线/候选发布。

## 依赖归属

官方 Boot 4.1.1 BOM 及相关模块 POM/JAR 已从 Central 实际取得。无独立版本的依赖由 BOM 管理；执行证据的 effective POM/tree 为完整传递依赖依据。

| 依赖 | 目标实际版本 | 声明 / 迁移责任 |
|---|---|---|
| Boot BOM / core / autoconfigure / jackson | 4.1.1 | BOM import；根不继承 parent，显式添加实际使用的 Jackson 技术模块 |
| Spring context / beans / core / web / webmvc / context-support | 7.0.9 | BOM；Web 和 context-support 继续 optional |
| Jackson core / databind | 3.1.5 | `tools.jackson.core`；旧公开 Java 类型与 serializer/builder 行为归 23 |
| Jackson annotations | 2.21 | 保留 `com.fasterxml.jackson.core`，不能盲目替换组名 |
| JDK8 / JSR310 / parameter-names 模块 | 不再独立声明 | 功能已合并至 Jackson 3 databind；具体类型迁移归 23 |
| Jakarta annotation / validation / Servlet API | 3.0.0 / 3.1.1 / 6.1.0 | BOM；Servlet 继续 optional；C5 `Nullable` 策略不在本票改动 |
| Lombok | 1.18.46 | 编译依赖和 processor 共同由 BOM 管理 |
| Boot configuration processor | 4.1.1 | 显式 processor path，与 Boot 属性同步 |
| Jupiter / JUnit Platform | 6.0.3 | starter-test 传递；真实发现由独立探针验证 |
| junit-jupiter-params | 6.0.3 | 保留 05 新增的显式 test-scope 声明，由目标 BOM 管理 |
| ArchUnit JUnit 6 adapter/api/engine | 1.5.1 | 显式 pin；注解仍在 `com.tngtech.archunit.junit` |
| Mockito / AssertJ | 5.23.0 / 3.27.7 | BOM，test scope |
| Logback / Hibernate Validator | 1.5.38 / 9.1.3.Final | BOM，test scope；provider 不进入库生产依赖 |
| Tomcat | 11.0.24 | BOM；仅测试 fixture 和独立 Web 应用选择容器 |
| Tika / jsoup / POI | 4.1.0 / 1.18.3 / 5.3.0 | Tika 由票 13 升级并验证有界探测/所有权；jsoup / POI 业务升级归对应票 |

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

独立 Web consumer 改用 `spring-boot-starter-webmvc`，旧 `Jackson2ObjectMapperBuilderCustomizer` 的签名/策略随 mapper 交 23，不通过 Jackson 2 兼容 starter 掩盖。需要 MockMvc 技术自动装配时应使用 `spring-boot-starter-webmvc-test`，本项目现有 context runner 并不因此自动迁包或必须引入该 starter。

04 的共享实际 Servlet fixture 直接使用 `spring-boot-web-server`、`spring-boot-tomcat`、`spring-boot-webmvc`，所以根 POM 显式声明这三个 test-scope 模块；保留 04 的 BOM 管理 `tomcat-embed-core` test 依赖。探针实际编译、加载并核对上表14种类型的来源 JAR，不假设导入 BOM 会自动提供拆分后的类。

## 执行和证据

```text
java verification/Verify.java platform --fresh
java verification/Verify.java all --fresh
```

`platform` 为独立工具链子集，真实跑 Jupiter / ArchUnit 正向与各自故意失败控制、Lombok/配置处理器、69.0 classfile、JaCoCo 指令探针和 dependency analyzer，并解析根依赖。日志与输入在 `.verification-results/<时间>-platform`；CI 分别保留完整 `all` 与 `platform` 的状态。

`all` 继续执行库全量质量门。22 的 Jackson 编译错误必须真实报红并在 [精确交接清单](../verification/ticket-22-jackson-diagnostics.md) 记录；不修改 test includes、skip、旧测试断言或质量阈值来得到绿色。主库测试暂未执行与测试被跳过是不同状态，不把探针的 5 项算入主库测试总数。Windows/Linux 的主库同产物保证、独立普通 jar/JSON consumer、缺类和用户覆盖组合由 24 闭合。

## 官方依据

- [Boot 4 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)：技术模块及测试支持拆分。
- [Boot 4.1.1 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)：实际目标版本归属。
- [Jackson 3 发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)：不可变 mapper、合并模块和 annotations 保留。
- [ArchUnit 1.5.1](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1) / [JUnit 6.0.3](https://docs.junit.org/6.0.3/overview.html)：目标引擎。
