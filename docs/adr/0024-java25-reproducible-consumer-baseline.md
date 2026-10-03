# ADR-0024: Java 25 构建与普通 jar 消费边界

## Status

Accepted，2026-10-03，已批准 ticket 01。

## Context

原构建运行于 JDK 25 时仍编译 Java 21 字节码，旧 JaCoCo / ArchUnit 不提供 Java 25 字节码的完整支持。
库内测试带全部 optional 依赖，不能证明发布 jar 可在独立消费方 classpath 启动。

本决策替代 README 的 Java 21+ 构建基线；没有旧平台 ADR 需要撤销。
对 ADR-0014 / ADR-0017 只落实其“非 Web 可用”的既有承诺：Web bean 方法移入条件化嵌套配置，
避免 Spring 反射顶层配置时提前链接缺失的 Servlet 类型，业务政策及 Bean 名不变。

## Decision

- Wrapper 3.3.4 的 only-script 发行物固定 Maven 3.10.0；下载包 SHA-256 随版本入库，生成时另核对官方 SHA-512。
- 根项目只使用 `maven.compiler.release=25`，不启用 preview；Enforcer 在 validate 要求 JDK 25 和固定 Maven。
- Boot 3.5.16 是中间平台，最终 Boot 4 / Jackson 3 仍由票 21–24 完成，不能以本票宣称迁移完成。
- 版本由 Boot BOM 或显式 pin 归属；Lombok 编译依赖及处理器共同取 BOM；字节码工具升级，原质量门不变。
- `verification/consumer` 不继承根 POM、不进 reactor、不引用库源码/测试；只声明普通库 Maven 坐标。
  JDK-only 验证入口用隔离仓库安装库，编译消费者并在独立 JVM 验证产物、配置和 Bean 覆盖。
- fast / integration / resources / all / prerequisites 入口均有明确失败和证据目录；缺先决条件不能静默跳过。
  `all --fresh` 固定专属空 Maven 仓库和 Wrapper 缓存，不继承个人 settings；运行日志、测试发现、覆盖率、
  effective POM、依赖树与产物哈希归档。CI Windows/Linux 矩阵执行同一个入口。

## Consequences

Java 21 消费方必须升级至 Java 25。Maven 不再需要预安装；首次运行需要 Central HTTPS 和平台自带下载工具。
负向 JDK 验证额外需要真实 JDK 21，仅测试用途，不改变生产基线。缺失时验证失败并给出所需环境变量。
独立消费者会发现被 test classpath 遮掩的装配缺陷；更完整的可选依赖组合矩阵归票 24。

## References

- [Maven Wrapper 校验](https://maven.apache.org/wrapper/)
- [Maven 3.10.0 SHA-512](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip.sha512)
- [Boot 3.5.16 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom)
- [本票版本账本](../building/java25-baseline.md)
