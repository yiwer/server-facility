# Java 25 中间构建基线与验证入口

本文件对应 ticket 01 / ADR-0024。Boot 3.5.16 仅是 Java 25 中间平台；最终 Boot 4、Jackson 3 和完整可选依赖矩阵由票 21–24 负责。

## 先决条件与命令

JDK 25（含 java/javac）、Git、Central HTTPS 网络可用。Wrapper 固定 Maven 3.10.0，无需预装 Maven。
Windows 使用系统 PowerShell，Linux 使用 sh、curl 或 wget，以及 unzip、sha256sum 或 shasum。
CI 固定 Temurin `25.0.4+101.0.LTS`（供应商版本 `25.0.4.1+1-LTS`）；Windows 本地记录为 Oracle `25.0.4.1+1-LTS-5`。
更改固定版本必须同时更新校验和、账本和构建证据，不能只改 URL。

从仓库根目录运行：

```text
java verification/Verify.java fast
java verification/Verify.java integration
java verification/Verify.java resources
java verification/Verify.java all --fresh
java verification/Verify.java prerequisites
```

| 入口 | 实际执行 | 失败条件 |
|---|---|---|
| fast | clean verify：所有库测试、JaCoCo、五条架构规则、依赖分析；归档 effective POM/依赖树 | 构建失败、测试为零、任意失败/跳过、既有五条架构规则任一未发现（允许新增规则） |
| integration | clean install + 独立消费者 configured/override/invalid + prerequisites | 普通 jar/69 字节码/无 preview/metadata/配置/覆盖/失败诊断任何断言不成立 |
| resources | clean install + 三种消费场景，再重复五次配置应用启动/使用/关闭 | 每个独立 JVM 上限 256 MiB、45 秒；超时终止本次子进程树且失败 |
| all | 合并上述入口；库质量门仅执行一次 | 任一子步骤失败 |
| prerequisites | 校验损坏下载、缺失 JAVA_HOME、真实错误 JDK 拒绝 | 负向用例意外成功、诊断不匹配或所需 JDK 缺失 |

integration / all / prerequisites 需要设置 `VERIFY_WRONG_JAVA_HOME` 为**真实的非 Java 25 JDK**（CI 使用 Temurin 21）。
这不是可选跳过项；缺少变量时入口会失败，日志解释需要安装测试用 JDK。日常仅跑 fast 不需要第二个 JDK。

```powershell
$env:VERIFY_WRONG_JAVA_HOME='C:\tools\jdk-21'
java verification/Verify.java all --fresh
```

```sh
VERIFY_WRONG_JAVA_HOME=/opt/jdk-21 java verification/Verify.java all --fresh
```

`--fresh` 为本次运行创建空本地依赖仓库；不传则复用当前 worktree 的 `.verification-results/repository`，
从不借用别的 worktree 的 SNAPSHOT。Wrapper 缓存始终按运行隔离。Maven 使用仓库内空 settings，
不会悄悄依赖个人镜像或激活 profile；离线/受限网络须先明确提供自己的构建环境，不能把下载失败记作成功。

普通快速开发可运行 `./mvnw verify`（Windows `mvnw.cmd verify`），但该命令本身不包含独立消费者及负向先决条件测试。

## 消费边界与资源预算

消费者是独立 Maven 项目，唯一直接依赖为 `cn.code91:server-facility`；运行 classpath 除消费者自身 classes
外全部必须是本次隔离仓库中的 jar。它验证每个发布 class major=69/minor=0、无 BOOT-INF、imports 和
configuration metadata 存在，并通过真实 Boot 非 Web 启动验证配置影响 ID 输出、用户 Bean 让位和非法配置诊断。
无 Servlet、Hibernate Validator、POI、Tika、Caffeine，防止 test/optional classpath 泄漏。

资源入口只承诺本票的有界应用生命周期冒烟：五个顺序独立 JVM，各 256 MiB、45 秒，必须自然退出；
它不替代票 03 的单 JVM executor 资源契约或票 33 的长稳测试。所有下载和报告在受控测试目录，不删除用户目录。

## 证据约定

每次写入 `.verification-results/<时间>-<入口>/`（git ignored），含命令、Git SHA/工作区状态、JDK/OS/架构、
时区/Locale、本地仓库是否 fresh、完整日志、Surefire XML、JaCoCo XML/HTML、effective POM、依赖树及 jar SHA-256。
失败也保留日志和 summary，不用重跑覆盖失败记录。版本控制内的 `docs/verification/ticket-01-windows.md` 摘要
引用这些证据；CI 以 `java25-<OS>-<SHA>` artifact 保存 30 天。CI 配置不是已执行证据。
原基线为 1196 个测试（含 5 条架构规则），**不把总数写死**；新增/删除/跳过按票解释。

## 直接依赖、BOM 与 processor 账本

版本读取自 2026-10-03 可实际获取的官方 Central POM。无显式版本项由 Boot 3.5.16 BOM 管理，optional/test 语义保留。
执行时 `effective-pom.xml` 和 `dependency-tree.txt` 是本次解析结果，下面是本票固定配置的归属说明。

| 依赖（group:artifact） | 版本 | 归属 / 语义 |
|---|---|---|
| org.springframework.boot:spring-boot-dependencies | 3.5.16 | import BOM，中间站 |
| org.projectlombok:lombok | 1.18.46 | BOM，optional；processor 同一管理来源 |
| jakarta.annotation:jakarta.annotation-api | 2.1.1 | BOM |
| org.slf4j:slf4j-api | 2.0.18 | BOM |
| org.springframework:spring-context | 6.2.19 | BOM |
| org.springframework:spring-beans | 6.2.19 | BOM |
| org.springframework:spring-core | 6.2.19 | BOM |
| org.springframework.boot:spring-boot | 3.5.16 | BOM |
| org.springframework.boot:spring-boot-autoconfigure | 3.5.16 | BOM |
| jakarta.validation:jakarta.validation-api | 3.0.2 | BOM；provider 不进入生产依赖 |
| com.fasterxml.jackson.core:jackson-core | 2.21.4 | BOM，Jackson 3 迁移归票 21–23 |
| com.fasterxml.jackson.core:jackson-annotations | 2.21 | Jackson BOM（2.20 起 annotations 使用两段版本） |
| com.fasterxml.jackson.core:jackson-databind | 2.21.4 | BOM |
| com.fasterxml.jackson.datatype:jackson-datatype-jdk8 | 2.21.4 | BOM |
| com.fasterxml.jackson.datatype:jackson-datatype-jsr310 | 2.21.4 | BOM |
| com.fasterxml.jackson.module:jackson-module-parameter-names | 2.21.4 | BOM |
| org.apache.tika:tika-core | 4.1.0 | 显式、optional；票 13 有界 core 探测与流所有权，见 ADR-0036 |
| jakarta.servlet:jakarta.servlet-api | 6.0.0 | BOM、optional |
| org.springframework:spring-web | 6.2.19 | BOM、optional |
| org.springframework:spring-webmvc | 6.2.19 | BOM、optional |
| org.jsoup:jsoup | 1.18.3 | 显式、optional；行为升级归票 32 |
| com.github.ben-manes.caffeine:caffeine | 3.2.4 | BOM、optional |
| org.springframework:spring-context-support | 6.2.19 | BOM、optional |
| org.apache.poi:poi | 5.3.0 | 显式、optional；行为升级归票 16 |
| org.apache.poi:poi-ooxml | 5.3.0 | 显式、optional |
| org.springframework.boot:spring-boot-starter-test | 3.5.16 | BOM、test |
| ch.qos.logback:logback-classic | 1.5.34 | BOM、test |
| org.hibernate.validator:hibernate-validator | 8.0.3.Final | BOM、test |
| com.tngtech.archunit:archunit-junit5 | 1.5.1 | 显式、test，Java 25 字节码支持 |
| org.springframework.boot:spring-boot-configuration-processor | 3.5.16 | processor，与 Boot 同步；metadata 入 jar |

## 构建插件与执行工具账本

根项目不继承 Boot parent，构建插件不由 import BOM 管理；以下全部显式固定。

| 工具 / 插件 | 版本 | 用途 |
|---|---|---|
| Maven Wrapper / distribution generator | 3.3.4 | 官方 only-script；不包含二进制 wrapper jar |
| Maven | 3.10.0 | Wrapper 下载；Enforcer 限定版本 |
| maven-enforcer-plugin | 3.6.3 | validate 拒绝错误 JDK / Maven |
| maven-clean-plugin | 3.4.1 | 干净编译 |
| maven-resources-plugin | 3.3.1 | 生产和测试资源 |
| maven-compiler-plugin | 3.16.0 | release 25，parameters，显式 annotation processor paths |
| maven-surefire-plugin | 3.6.0 | JUnit Jupiter 5 / ArchUnit 引擎发现 |
| maven-jar-plugin | 3.5.1 | 普通库 jar，无 Boot repackage |
| maven-install-plugin | 3.1.4 | 安装至验证隔离仓库 |
| maven-help-plugin | 3.5.1 | effective POM 归档 |
| maven-dependency-plugin | 3.11.0 | analyze-only + failOnWarning、tree、consumer classpath |
| jacoco-maven-plugin | 0.8.15 | Java 25 字节码；原 88/88/75 门槛 |

consumer 实际执行 clean/compile/dependency:build-classpath，显式插件与根项目相同版本（独立 POM 有意重复，不能继承根项目）。
未新增框架/测试库；验证 runner 只用 JDK。CI Actions 使用官方仓库的已核对 commit SHA：checkout v4、setup-java v5、upload-artifact v4。

Maven zip SHA-256：`1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff`。
官方 SHA-512：`22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337`。
