> **inherited-from**: beacon ADR-0001(原仓库 docs/adr/0001-rp-02-jsoup-tika-optional.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0001: RP-02 jsoup / tika-core compile → optional

## Status

Accepted

日期：2026-05-21

## Context

- 评审来源：`docs/facility/REVIEW.md` §7 RP-02 + §2.1 评审发现 + §6.D.1 `web/` + §6.B.14 `mime/`
- beacon 现状：`beacon-facility/pom.xml` 中 `jsoup`（XSS 清洗用，`web/util/XssUtil`）与 `tika-core`（MIME 探测用，`mime/MimeTyping`）声明为 compile 强依赖。所有 consumer——包括不使用 Web XSS / 文件上传场景的纯工具类 consumer——均被迫引入约 5 MB 依赖，接口面（classpath 污染面）偏宽。
- 行业现状：
  - Maven 官方文档 *Introduction to Optional and Excludes Dependencies* 推荐对"非必需 transitive 引入"的 dep 用 `<optional>true</optional>`，阻断 transitive 传递；consumer 若需要功能可显式声明该 dep
  - Spring Boot 自身的 `spring-boot-starter-web` 将 servlet container 声明为 optional，consumer 选择 tomcat / jetty / undertow
  - Spring Boot `spring-boot-autoconfigure` 通过 `@ConditionalOnClass(X.class)` 守卫激活路径，仅 classpath 有该类时才装配相关 bean
- deep module 角度：facility 是 deep module，"接口面（classpath 污染面）窄" 是其本质特征之一；compile 强 dep 5 MB 污染违反此原则

## Decision

将 jsoup 与 tika-core 在 `beacon-facility/pom.xml` 中改为 `<optional>true</optional>`：

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <optional>true</optional>
</dependency>
```

**范围边界**：
- jsoup：`FacilityWebAutoConfiguration` 已通过 `@ConditionalOnClass(Servlet.class)` 守卫 XssUtil 装配路径（Web 场景下 jsoup 通常已被 Web consumer 显式引入；非 Web consumer 不触发 XssUtil 使用）。本 ADR 不新增 condition guard。
- tika-core：`mime/MimeTyping` 是 facility 内部 static util，非 AutoConfig 装配。consumer 不调 `MimeTyping` 即不会触发类加载，无需新增 `@ConditionalOnClass` 守卫。consumer 若调 MimeTyping 时需显式声明 tika-core 依赖；ADR 留 carry-forward "若实际遇到 ClassNotFoundException 报错信息友好性问题，phase-4+ 加 lazy load + 友好提示"。

**不引入新 AutoConfiguration**：保持 phase-3 范围可控，不建 `FacilityMimeAutoConfiguration`。

## Consequences

**Positive**：
- facility consumer 引入依赖体积减少约 5 MB（jsoup ~2 MB + tika-core ~1 MB + transitive）
- 接口面（classpath 污染面）变窄，符合 deep module 哲学
- 与 Spring Boot starter 业界标准实践对齐

**Negative**：
- consumer 若调 `XssUtil` 或 `MimeTyping` 而未显式声明 jsoup / tika-core，运行时 `ClassNotFoundException`（错误信息不友好，需 stack trace 才能定位）
- 现有调用链中已隐式依赖 jsoup / tika-core 的 consumer 升级 beacon-facility 后需检查并显式声明（潜在 break 但 consumer 通常已经引入；本 phase 无真实 consumer，影响为 0）

**Carry-forward**：
- 若 phase-4+ 有 consumer 报告 ClassNotFoundException 友好性问题，新建 `FacilityMimeAutoConfiguration` + `@ConditionalOnClass(org.apache.tika.detect.Detector.class)` 守卫 + log warning（"调用 MimeTyping 需显式依赖 tika-core"）

## References

1. *Introduction to Optional and Excludes Dependencies*. Maven Documentation. https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html
2. Spring Boot Reference Documentation §3.1.3 (Starters). https://docs.spring.io/spring-boot/docs/3.5.x/reference/htmlsingle/#using.build-systems.starters
3. Spring Boot `spring-boot-starter-web` 源码 (servlet container as optional dep)
4. *A Philosophy of Software Design* (John Ousterhout, 2018) Chapter 4 (Modules Should Be Deep)

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
