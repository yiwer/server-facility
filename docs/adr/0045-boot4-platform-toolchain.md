# ADR-0045: Boot 4 技术模块与独立工具链验证

## Status

Accepted，2026-10-04。票 22；部分替代 ADR-0024 的 Boot 3.5.16 中间版本，保留 Java 25、固定 Wrapper、普通 jar 和原质量门。落实 ADR-0044 的 22→23→24 非发布迁移边界。

## Context

Boot 4 把 Jackson、MVC、容器等技术拆入独立模块，测试支持亦按技术拆分。BOM 本身不声明这些依赖，也不管理本项目没有继承的 parent 插件配置。JUnit 平台主版本和 ArchUnit 引擎必须共同迁移；仅得到 Maven 成功退出不能证明两个引擎发现了测试。

官方 4.1.1 BOM 实际管理 Framework 7.0.9、Jackson 3.1.5、JUnit 6.0.3、Tomcat 11.0.24。Boot 4 的类迁移不是统一的包前缀替换：FilterRegistrationBean、任务和消息自动装配仍在原包，Jackson、MVC、Tomcat 和 Servlet 容器 context 分属不同包/模块。

## Decision

1. 根 POM 和独立 Web consumer 固定 Boot 4.1.1；保持 Java 25/release 25、原插件版本、88% 指令/行和 75% 分支质量门。Boot configuration processor 与 BOM 同版本；Lombok 继续由 BOM 管理。
2. 按实际使用声明技术模块。根库显式引用 Jackson 自动装配，因此声明 `spring-boot-jackson`；普通库不引入 Web starter 或生产容器。测试/独立 Web 应用按所需技术引入 MVC 支持，容器只属于测试或应用。保持 optional 能力边界，缺席组合的真实启动验收由 24 完成。
3. Jackson core/databind 依赖转为 `tools.jackson.core`，annotations 保留 `com.fasterxml.jackson.core`，已合并的 JDK8/JSR310/parameter-names 模块移除。票 22 不伪装公共类型已迁移：尚存 Jackson 2 Java 类型、mapper/serializer 签名和相关测试/消费者逐项交 23。不得为绕过编译继续引入兼容 Jackson 2 starter。
4. `archunit-junit6:1.5.1` 替换 JUnit 5 适配器，其 `com.tngtech.archunit.junit` 注解包保持不变。已有五条主工程规则和测试文件保留。依赖账目仅随确切坐标调整，不增加宽泛忽略。
5. `verification/platform-probe` 是无 facility 依赖、无父 POM 的工具链探针。真实运行 Jupiter 与 ArchUnit 的正向及故意失败控制，验证 classfile 69.0、配置处理器和 JaCoCo 处理 Java 25。该探针只证明工具链，不能代替主库完整测试、主库 metadata、普通 jar 或两类 consumer。`all` 仍执行原完整质量门，迁移期间必须如实失败。

## Consequences

**Positive**：依赖、引擎和包归属拥有独立证据，23 可集中处理 Jackson 行为与公开类型，24 有明确的完整平台关闭条件。

**Negative**：22 是不可发布的中间提交，主工程暂因精确登记的 Jackson 编译缺口失败。不会提供同一 jar 对 Boot/Jackson 两个主版本的二进制兼容层。

**Carry-forward**：23 清空 Jackson 类型/行为缺口；24 恢复完整质量门、Windows/Linux 同产物消费者、缺类/覆盖/注册顺序/metadata 验证。Servlet 6.1 新增 `sendRedirect` 重载与 `setCharacterEncoding(Charset)` 会绕过旧包装器覆盖，05 的响应捕获能力须在 24 补齐并用真实 Servlet 6.1 测试关闭；仅依赖解析不能证明它正确。

## References

- [Boot 4 官方迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Boot 4.1.1 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)
- [JUnit 6.0.3 文档](https://docs.junit.org/6.0.3/overview.html)
- [ArchUnit 1.5.1 发布](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1)
- [Servlet 6.1 response wrapper](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletresponsewrapper)
- [迁移影响登记](../building/platform-migration-inventory.md)
