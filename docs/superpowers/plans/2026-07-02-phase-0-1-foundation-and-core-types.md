# server-facility P0 奠基 + P1 核心类型簇 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 server-facility 独立仓库的构建/文档基线(P0),并迁移+重审核心类型簇 result/error/common/structure(P1),含 RV2-18 别名精简与 C1 断环(error 去 locale 依赖)。

**Architecture:** 单模块 deep module(见已批准 spec §3 方案 A)。P1 迁移顺序按依赖拓扑:structure(纯值)→ common(→structure)→ result(纯)→ error(C1 断环后纯 JDK),最后 ArchUnit 规则锁定拓扑。每个迁移任务 TDD:测试先落地(红:编译失败)→ 源码迁移+rework → 全绿 → 提交。

**Tech Stack:** Java 21 / Maven / Spring Boot 3.5.10 BOM(仅依赖管理,P1 不引 Spring 运行时)/ JUnit 5 + AssertJ(经 spring-boot-starter-test)/ ArchUnit 1.3.0 / JaCoCo 0.8.12 / Lombok(optional)

## Global Constraints

- **源仓库根**:`D:\STELE\beacon\beacon-support\beacon-facility`(下称 `$SRC`);**目标仓库根**:`D:\Yiwer\code\server-facility`(下称 `$DST`)。
- **包名映射**(唯一字符串替换,ordinal 非正则):`cn.hbads.beacon.facility` → `cn.code91.facility`。
- **坐标**:`cn.code91:server-facility:0.1.0-SNAPSHOT`;Java `21`;Spring Boot BOM `3.5.10`;ArchUnit `1.3.0`;JaCoCo `0.8.12`;surefire `3.5.2`;compiler-plugin `3.13.0`;Lombok `1.18.42`。
- **编码**:一切文件 UTF-8(无 BOM)。迁移复制必须用本计划的"标准迁移命令"(显式 UTF-8 读写),不得用 `Copy-Item` + 手改。
- **标准迁移命令**(PowerShell;每次替换 `$s`/`$d` 为该步骤给出的绝对路径):
  ```powershell
  $s = '<源绝对路径>'; $d = '<目标绝对路径>'
  New-Item -ItemType Directory -Force (Split-Path $d) | Out-Null
  $t = [IO.File]::ReadAllText($s, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText($d, $t.Replace('cn.hbads.beacon.facility', 'cn.code91.facility'), [Text.UTF8Encoding]::new($false))
  ```
- **依赖纪律**:依赖按簇随 phase 增量声明(本计划只声明 P1 所需);终态以 spec §6 收敛表为准;P7 用 `mvn dependency:analyze` 清账。
- **TDD 纪律**:每个迁移任务先落测试(编译失败即"红"),再落源码到"绿";rework 的行为断言必须先存在于测试中。
- **任务出口**:`mvn test` 全绿(BUILD SUCCESS)才允许 commit;commit message 用下方给定文案,结尾带 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。
- **禁止事项**:不迁移 `coordinate`/`ChineseNumbers`/`validate`(spec §5 drop);不迁移 `WrappedContainer`/`WrappedDataType`(留待 P4,C2);不新增 spec 之外的 API。
- 所有 `mvn` 命令在 `$DST` 目录执行:`mvn -f D:\Yiwer\code\server-facility\pom.xml <goals>`。

---

### Task 1: P0 — 构建基线(pom + 编码/编辑器/Lombok 配置)

**Files:**
- Modify: `D:\Yiwer\code\server-facility\pom.xml`(整体替换为下方内容)
- Create: `D:\Yiwer\code\server-facility\.editorconfig`
- Create: `D:\Yiwer\code\server-facility\lombok.config`

**Interfaces:**
- Consumes: 无(首任务)
- Produces: 可构建的 Maven 工程;后续任务的 `mvn test` 命令、JaCoCo `target/site/jacoco/index.html` 报告、`@UtilityClass` 等 Lombok 注解可用

- [ ] **Step 1: 写入完整 pom.xml**(整文件替换现有占位 pom)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>cn.code91</groupId>
    <artifactId>server-facility</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>server-facility</name>
    <description>Server 开发基础设施脚手架(deep module):Result 错误类型、ID 生成、JSON、Web 过滤链、i18n 聚合等</description>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

        <spring-boot.version>3.5.10</spring-boot.version>
        <lombok.version>1.18.42</lombok.version>
        <archunit.version>1.3.0</archunit.version>

        <maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>
        <maven-surefire-plugin.version>3.5.2</maven-surefire-plugin.version>
        <jacoco.version>0.8.12</jacoco.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- ===== P1 核心类型簇所需 ===== -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
        </dependency>

        <!-- ===== test ===== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${maven-compiler-plugin.version}</version>
                <configuration>
                    <release>21</release>
                    <parameters>true</parameters>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-configuration-processor</artifactId>
                            <version>${spring-boot.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${maven-surefire-plugin.version}</version>
                <configuration>
                    <argLine>@{argLine} -Dfile.encoding=UTF-8</argLine>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

> 说明:`@{argLine}` 引用 JaCoCo `prepare-agent` 注入的 agent 参数,两者叠加;JaCoCo `check` 门(line ≥ 0.80)按 spec §7 在 P7 才挂,本阶段只出报告。

- [ ] **Step 2: 创建 .editorconfig**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 4
trim_trailing_whitespace = true

[*.{xml,yml,yaml,json}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 3: 创建 lombok.config**

```ini
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
```

> `addLombokGeneratedAnnotation` 让 JaCoCo 自动排除 Lombok 生成代码(spec §7 第 6 条)。

- [ ] **Step 4: 验证构建**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`(尚无源码与测试;surefire 报告 No tests to run 属正常)

- [ ] **Step 5: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add pom.xml .editorconfig lombok.config
git -C D:\Yiwer\code\server-facility commit -m @'
build: P0 构建基线(Boot 3.5.10 BOM/JaCoCo/surefire/Lombok/编码配置)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 2: P0 — 文档基线(CONTEXT + ADR 继承 + README stub)

**Files:**
- Create: `D:\Yiwer\code\server-facility\CONTEXT.md`
- Create: `D:\Yiwer\code\server-facility\README.md`(覆盖:当前无此文件)
- Create: `D:\Yiwer\code\server-facility\docs\adr\INDEX.md`
- Create: `D:\Yiwer\code\server-facility\docs\adr\0000-adr-template.md` + `0001..0008`(自 beacon 复制,加 inherited 头)

**Interfaces:**
- Consumes: beacon ADR 源文件 `D:\STELE\beacon\docs\adr\0000..0008-*.md`
- Produces: `docs/adr/` 编号空间(0009 起为本工程新 ADR,Task 8 使用);CONTEXT.md 域术语(后续所有 phase 的 javadoc/文档用语基准)

- [ ] **Step 1: 创建 CONTEXT.md**

```markdown
# CONTEXT — server-facility 域术语权威

> 本文件是本仓库文档与 javadoc 的用语基准。与代码不一致时,以代码 + ADR 为准并回头修订此文件。
> 源头:beacon 仓库 CONTEXT.md 的 facility 相关子集,随迁移精简。

## 架构术语

- **deep module**:接口面窄(静态门面 + Spring bean + 值类型)、承载面宽(20+ 子包)的模块形态
  (John Ousterhout)。判据:删掉它,横切职责会以重复、各自为政的形式散落到各消费方。
- **接口面 / 承载面**:消费方可见的入口集合 / 支撑入口的内部实现集合。接口面 ≪ 承载面 ⇒ deep module。
- **Seam**:`@ConditionalOnMissingBean` 落地的可替换点——应用声明同类型 bean 即可替换默认实现,
  无需改 facility 代码。
- **hypothetical seam / real seam**:只有一个默认实现的 Seam(声明可替换性,不预先抽象 Strategy)/
  出现第二实现后升格的 Seam。不为单一实现预先抽象接口。
- **静态门面**:`XxxUtil` 形态的零配置入口(IdUtil/JsonUtil/LogUtil/DateUtil/LocaleUtil),
  Spring 就绪后与容器装配的实例共享状态。
- **Result-style**:`sealed Result<T,E>`(`Ok`/`Err` record)替代受检异常;switch 编译期穷尽两条路径;
  `Result.empty()` 表达"成功但无值"(ADR-0007)。
- **RFC 7807 双轨**:同一异常处理器支持 `BaseResponse`(默认)与 `ProblemDetail`(opt-in),
  配置切换不换类(ADR-0003)。
- **错误消息边界本地化**:错误类型(`ErrorTypeInterface`)只承载 code/messageKey/defaultMessage 纯数据,
  `format()` 仅渲染默认模板;i18n 解析发生在展示边界(locale 包),error 包不依赖 Spring(ADR-0010,C1)。

## 工作流术语

- **SDD**:spec(docs/superpowers/specs)→ plan(docs/superpowers/plans)→ TDD 实施 → retrospective。
- **ADR**:docs/adr/,0000 为模板;0001-0008 为 inherited(源:beacon);0009 起为本工程决策。
- **迁移三判定**:keep(原样迁移)/ keep+rework(迁移时重构,TDD)/ drop(不迁移)。
- **断环 C1/C2/C3**:spec §4.4 的三组包级循环依赖及其断法;ArchUnit 规则守护。

## 命名与配置

- 包根 `cn.code91.facility.*`;类前缀 `Facility*`;配置前缀 `facility.*`;
  i18n bundle `i18n/facility-messages_*`。
```

- [ ] **Step 2: 复制 ADR 模板与 8 条 inherited ADR**

```powershell
$srcAdr = 'D:\STELE\beacon\docs\adr'
$dstAdr = 'D:\Yiwer\code\server-facility\docs\adr'
New-Item -ItemType Directory -Force $dstAdr | Out-Null
Copy-Item "$srcAdr\0000-adr-template.md" "$dstAdr\0000-adr-template.md"
foreach ($n in 1..8) {
  $file = Get-ChildItem $srcAdr -Filter ("{0:d4}-*.md" -f $n)
  $text = [IO.File]::ReadAllText($file.FullName, [Text.UTF8Encoding]::new($false))
  $header = "> **inherited-from**: beacon ADR-{0:d4}(原仓库 docs/adr/{1})。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。`r`n`r`n" -f $n, $file.Name
  [IO.File]::WriteAllText("$dstAdr\$($file.Name)", ($header + $text), [Text.UTF8Encoding]::new($false))
}
Get-ChildItem $dstAdr | Select-Object -ExpandProperty Name
```

Expected: 列出 `0000-adr-template.md` 与 `0001..0008` 共 9 个文件。

- [ ] **Step 3: 创建 docs/adr/INDEX.md**

```markdown
# ADR 索引

> 0001-0008 为 inherited(源:beacon 仓库,facility 专属决策);0009 起为 server-facility 本工程决策。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](0001-rp-02-jsoup-tika-optional.md) | inherited | jsoup / tika-core 声明为 Maven optional |
| [0002](0002-rp-04-async-bean-type-matching.md) | inherited | 异步线程池注入按类型匹配,不按 bean 名 |
| [0003](0003-rp-06-rfc-7807-problem-details.md) | inherited | RFC 7807 ProblemDetail 双轨(use-problem-detail 开关) |
| [0004](0004-rp-07-facility-exception-interface.md) | inherited | FacilityException 接口解耦异常层次 |
| [0005](0005-rp-08-slf4j-throwable-position.md) | inherited | LogUtil Throwable 参数对齐 SLF4J 末位 |
| [0006](0006-rp-13-cas-compare-and-exchange.md) | inherited | LogUtil 内部状态 compareAndExchange 消除 ABA |
| [0007](0007-rp-10-result-empty-factory.md) | inherited | Result.empty() 表达"成功但无值" |
| [0008](0008-rp-15-snowid-parsetimestamp-instance.md) | inherited | SnowId parseTimestamp/parseInfo 改 instance |
```

- [ ] **Step 4: 覆盖 README.md(P7 收口前的最小 stub)**

```markdown
# server-facility

> Spring Boot server 基础设施脚手架(deep module)。坐标 `cn.code91:server-facility`。
> 源项目:beacon-facility(cn.hbads,phase-14 稳定态),逐簇迁移+重审中。

- 设计(spec):[docs/superpowers/specs/2026-07-02-server-facility-migration-design.md](docs/superpowers/specs/2026-07-02-server-facility-migration-design.md)
- 决策记录:[docs/adr/INDEX.md](docs/adr/INDEX.md)
- 域术语:[CONTEXT.md](CONTEXT.md)

完整 README / DESIGN / USAGE 三件套在 P7 收口时撰写。
```

- [ ] **Step 5: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add CONTEXT.md README.md docs\adr
git -C D:\Yiwer\code\server-facility commit -m @'
docs: P0 文档基线(CONTEXT 域术语/8 条 inherited ADR/README stub)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 3: P1 — structure 簇迁移 + 别名精简(Tuple/Triple)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\structure\TupleTest.java`
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\structure\TripleTest.java`
- Create(迁移+编辑): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\structure\Tuple.java`
- Create(迁移+编辑): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\structure\Triple.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\structure\package-info.java`

**Interfaces:**
- Consumes: 无(P1 第一个代码任务)
- Produces(后续任务依赖的精确 API):
  - `record Tuple<L, R>(L left, R right)`:`static of(L,R)`、`static fromEntry(Map.Entry<K,V>)`、`mapLeft(Function)`、`mapRight(Function)`、`bimap(Function,Function)`、`swap()`、`merge(BiFunction)`、`toEntry()`、`toNullableEntry()`(Task 4 的 Collects 用 `Tuple.of`)
  - `record Triple<L, M, R>(L left, M middle, R right)`:`static of`、`static fromTuple×2`、`mapLeft/mapMiddle/mapRight`、`trimap`、`rotateLeft/rotateRight/reverse`、`merge(TriFunction)`、`dropRight/dropLeft/dropMiddle`
  - **已删除(勿引用)**:`Tuple.first/second/key/value`;`Triple.first/second/third/toLeftMiddle/toMiddleRight/toLeftRight`

- [ ] **Step 1: 新写 TupleTest.java(15 用例)**

```java
package cn.code91.facility.structure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Tuple - 二元元组")
class TupleTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        void of_createsTupleWithBothValues() {
            Tuple<String, Integer> t = Tuple.of("a", 1);
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.right()).isEqualTo(1);
        }

        @Test
        void of_allowsNullValues() {
            Tuple<String, Integer> t = Tuple.of(null, null);
            assertThat(t.left()).isNull();
            assertThat(t.right()).isNull();
        }

        @Test
        void fromEntry_copiesKeyAndValue() {
            Tuple<String, Integer> t = Tuple.fromEntry(Map.entry("k", 9));
            assertThat(t.left()).isEqualTo("k");
            assertThat(t.right()).isEqualTo(9);
        }

        @Test
        void fromEntry_null_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> Tuple.fromEntry(null));
        }
    }

    @Nested
    @DisplayName("转换操作")
    class Transformations {

        @Test
        void mapLeft_transformsOnlyLeft() {
            Tuple<Integer, String> t = Tuple.of("ab", "x").mapLeft(String::length);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.right()).isEqualTo("x");
        }

        @Test
        void mapRight_transformsOnlyRight() {
            Tuple<String, Integer> t = Tuple.of("x", "abc").mapRight(String::length);
            assertThat(t.left()).isEqualTo("x");
            assertThat(t.right()).isEqualTo(3);
        }

        @Test
        void bimap_transformsBothSides() {
            Tuple<Integer, Integer> t = Tuple.of("ab", "abc").bimap(String::length, String::length);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.right()).isEqualTo(3);
        }

        @Test
        void mapLeft_nullMapper_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> Tuple.of("a", "b").mapLeft(null));
        }

        @Test
        void swap_exchangesSides() {
            Tuple<Integer, String> t = Tuple.of("a", 1).swap();
            assertThat(t.left()).isEqualTo(1);
            assertThat(t.right()).isEqualTo("a");
        }

        @Test
        void merge_combinesBothValues() {
            String merged = Tuple.of("a", 1).merge((l, r) -> l + r);
            assertThat(merged).isEqualTo("a1");
        }
    }

    @Nested
    @DisplayName("Map.Entry 互操作")
    class EntryInterop {

        @Test
        void toEntry_returnsEntryWithBothValues() {
            Map.Entry<String, Integer> e = Tuple.of("k", 1).toEntry();
            assertThat(e.getKey()).isEqualTo("k");
            assertThat(e.getValue()).isEqualTo(1);
        }

        @Test
        void toEntry_withNullSide_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Tuple.of(null, 1).toEntry())
                    .withMessageContaining("left");
        }

        @Test
        void toNullableEntry_allowsNulls() {
            Map.Entry<String, Integer> e = Tuple.<String, Integer>of(null, null).toNullableEntry();
            assertThat(e.getKey()).isNull();
            assertThat(e.getValue()).isNull();
        }
    }

    @Nested
    @DisplayName("record 语义")
    class RecordSemantics {

        @Test
        void equals_sameValues_equal() {
            assertThat(Tuple.of("a", 1)).isEqualTo(Tuple.of("a", 1));
        }

        @Test
        void toString_isParenthesizedPair() {
            assertThat(Tuple.of("a", 1).toString()).isEqualTo("(a, 1)");
        }
    }
}
```

- [ ] **Step 2: 新写 TripleTest.java(16 用例)**

```java
package cn.code91.facility.structure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Triple - 三元组")
class TripleTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        void of_createsTripleWithAllValues() {
            Triple<String, Integer, Boolean> t = Triple.of("a", 1, true);
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void fromTuple_appendsRight() {
            Triple<String, Integer, Boolean> t = Triple.fromTuple(Tuple.of("a", 1), true);
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void fromTuple_prependsLeft() {
            Triple<String, Integer, Boolean> t = Triple.fromTuple("a", Tuple.of(1, true));
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void fromTuple_nullTuple_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Triple.fromTuple((Tuple<String, Integer>) null, true));
        }
    }

    @Nested
    @DisplayName("转换操作")
    class Transformations {

        @Test
        void mapLeft_transformsOnlyLeft() {
            Triple<Integer, Integer, Boolean> t = Triple.of("ab", 1, true).mapLeft(String::length);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void mapMiddle_transformsOnlyMiddle() {
            Triple<String, String, Boolean> t = Triple.of("a", 12, true).mapMiddle(Object::toString);
            assertThat(t.middle()).isEqualTo("12");
        }

        @Test
        void mapRight_transformsOnlyRight() {
            Triple<String, Integer, String> t = Triple.of("a", 1, true).mapRight(Object::toString);
            assertThat(t.right()).isEqualTo("true");
        }

        @Test
        void trimap_transformsAllThree() {
            Triple<Integer, String, String> t = Triple.of("ab", 1, true)
                    .trimap(String::length, Object::toString, Object::toString);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.middle()).isEqualTo("1");
            assertThat(t.right()).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("旋转与反转")
    class Rotations {

        @Test
        void rotateLeft_shiftsLeftward() {
            Triple<Integer, Boolean, String> t = Triple.of("a", 1, true).rotateLeft();
            assertThat(t.left()).isEqualTo(1);
            assertThat(t.middle()).isEqualTo(true);
            assertThat(t.right()).isEqualTo("a");
        }

        @Test
        void rotateRight_shiftsRightward() {
            Triple<Boolean, String, Integer> t = Triple.of("a", 1, true).rotateRight();
            assertThat(t.left()).isEqualTo(true);
            assertThat(t.middle()).isEqualTo("a");
            assertThat(t.right()).isEqualTo(1);
        }

        @Test
        void reverse_swapsEnds() {
            Triple<Boolean, Integer, String> t = Triple.of("a", 1, true).reverse();
            assertThat(t.left()).isEqualTo(true);
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("合并与投影")
    class MergeAndProjections {

        @Test
        void merge_combinesAllThree() {
            String merged = Triple.of("a", 1, true).merge((l, m, r) -> l + m + r);
            assertThat(merged).isEqualTo("a1true");
        }

        @Test
        void dropRight_keepsLeftMiddle() {
            assertThat(Triple.of("a", 1, true).dropRight()).isEqualTo(Tuple.of("a", 1));
        }

        @Test
        void dropLeft_keepsMiddleRight() {
            assertThat(Triple.of("a", 1, true).dropLeft()).isEqualTo(Tuple.of(1, true));
        }

        @Test
        void dropMiddle_keepsLeftRight() {
            assertThat(Triple.of("a", 1, true).dropMiddle()).isEqualTo(Tuple.of("a", true));
        }
    }

    @Test
    @DisplayName("toString 为括号三元组")
    void toString_isParenthesizedTriple() {
        assertThat(Triple.of("a", 1, true).toString()).isEqualTo("(a, 1, true)");
    }
}
```

- [ ] **Step 3: 运行验证"红"(编译失败:Tuple/Triple 不存在)**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,报 `cannot find symbol: class Tuple`(测试先行,红状态确认)

- [ ] **Step 4: 标准迁移命令复制 3 个源文件**

分别以下列 (源, 目标) 执行 Global Constraints 的标准迁移命令:
1. `$SRC\src\main\java\cn\hbads\beacon\facility\structure\Tuple.java` → `$DST\src\main\java\cn\code91\facility\structure\Tuple.java`
2. `$SRC\src\main\java\cn\hbads\beacon\facility\structure\Triple.java` → `$DST\src\main\java\cn\code91\facility\structure\Triple.java`
3. `$SRC\src\main\java\cn\hbads\beacon\facility\structure\package-info.java` → `$DST\src\main\java\cn\code91\facility\structure\package-info.java`

- [ ] **Step 5: Tuple.java 删除 4 个纯别名(ADR-0009)**

对目标 `Tuple.java` 执行精确替换——old:

```java
    // ==================== 便捷别名 ====================

    /**
     * 左值别名：first
     */
    public L first() {
        return left;
    }

    /**
     * 右值别名：second
     */
    public R second() {
        return right;
    }

    /**
     * 左值别名：key（当作为键值对使用时）
     */
    public L key() {
        return left;
    }

    /**
     * 右值别名：value（当作为键值对使用时）
     */
    public R value() {
        return right;
    }

    @Override
```

new:

```java
    @Override
```

- [ ] **Step 6: Triple.java 删除 6 个纯别名(ADR-0009)**

对目标 `Triple.java` 执行精确替换——old:

```java
    /**
     * 取左中值作为 Tuple
     */
    public Tuple<L, M> toLeftMiddle() {
        return dropRight();
    }

    /**
     * 取中右值作为 Tuple
     */
    public Tuple<M, R> toMiddleRight() {
        return dropLeft();
    }

    /**
     * 取左右值作为 Tuple
     */
    public Tuple<L, R> toLeftRight() {
        return dropMiddle();
    }

    // ==================== 便捷别名 ====================

    /**
     * 左值别名：first
     */
    public L first() {
        return left;
    }

    /**
     * 中值别名：second
     */
    public M second() {
        return middle;
    }

    /**
     * 右值别名：third
     */
    public R third() {
        return right;
    }

    @Override
```

new:

```java
    @Override
```

- [ ] **Step 7: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 8: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src pom.xml
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 structure 簇(Tuple/Triple)并删除 10 个纯别名(ADR-0009)

31 个新行为测试;WrappedContainer/WrappedDataType 留待 P4(C2 断环)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 4: P1 — common 簇迁移(NullSafe/Collects)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\common\NullSafeTest.java`
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\common\CollectsTest.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\common\NullSafe.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\common\Collects.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\common\package-info.java`

**Interfaces:**
- Consumes: Task 3 的 `Tuple.of(L,R)`(Collects.extractCompareTuple 返回 `Map<K, Tuple<V,V>>`)
- Produces(P2+ 大量使用):`NullSafe.isNull/nonNull/allNotNull/equals/isEmpty(Collection|Map|T[])/isNotEmpty/isBlank/isNotBlank/getOrDefault/computeOrElse/asList`;`Collects.toMap×2/safeExtractFromMap/extractCompareTuple/safelyJoin/safelyMappingAndJoin/mapNonNull/listDiff/calculateCapacity/longListToLongArray`

- [ ] **Step 1: 新写 NullSafeTest.java(25 用例)**

```java
package cn.code91.facility.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("NullSafe - null/空检查与默认值")
class NullSafeTest {

    @Nested
    @DisplayName("null 检查")
    class NullChecks {

        @Test
        void isNull_null_true() {
            assertThat(NullSafe.isNull(null)).isTrue();
        }

        @Test
        void isNull_nonNull_false() {
            assertThat(NullSafe.isNull("x")).isFalse();
        }

        @Test
        void nonNull_mirrorsIsNull() {
            assertThat(NullSafe.nonNull("x")).isTrue();
            assertThat(NullSafe.nonNull(null)).isFalse();
        }

        @Test
        void allNotNull_nullArray_false() {
            assertThat(NullSafe.allNotNull((Object[]) null)).isFalse();
        }

        @Test
        void allNotNull_emptyArray_false() {
            assertThat(NullSafe.allNotNull()).isFalse();
        }

        @Test
        void allNotNull_containsNull_false() {
            assertThat(NullSafe.allNotNull("a", null, "b")).isFalse();
        }

        @Test
        void allNotNull_allPresent_true() {
            assertThat(NullSafe.allNotNull("a", 1, true)).isTrue();
        }

        @Test
        void equals_nullSafe() {
            assertThat(NullSafe.equals(null, null)).isTrue();
            assertThat(NullSafe.equals("a", null)).isFalse();
            assertThat(NullSafe.equals("a", "a")).isTrue();
        }
    }

    @Nested
    @DisplayName("空检查")
    class EmptyChecks {

        @Test
        void isEmpty_collection_nullOrEmpty_true() {
            assertThat(NullSafe.isEmpty((List<String>) null)).isTrue();
            assertThat(NullSafe.isEmpty(List.of())).isTrue();
        }

        @Test
        void isEmpty_collection_nonEmpty_false() {
            assertThat(NullSafe.isEmpty(List.of("a"))).isFalse();
        }

        @Test
        void isNotEmpty_collection_mirrorsIsEmpty() {
            assertThat(NullSafe.isNotEmpty(List.of("a"))).isTrue();
            assertThat(NullSafe.isNotEmpty((List<String>) null)).isFalse();
        }

        @Test
        void isEmpty_map_nullOrEmpty_true() {
            assertThat(NullSafe.isEmpty((Map<String, String>) null)).isTrue();
            assertThat(NullSafe.isEmpty(Map.of())).isTrue();
        }

        @Test
        void isNotEmpty_map_nonEmpty_true() {
            assertThat(NullSafe.isNotEmpty(Map.of("k", "v"))).isTrue();
        }

        @Test
        void isEmpty_array_nullOrEmpty_true() {
            assertThat(NullSafe.isEmpty((String[]) null)).isTrue();
            assertThat(NullSafe.isEmpty(new String[0])).isTrue();
        }

        @Test
        void isNotEmpty_array_nonEmpty_true() {
            assertThat(NullSafe.isNotEmpty(new String[]{"a"})).isTrue();
        }

        @Test
        void isBlank_nullEmptyWhitespace_true() {
            assertThat(NullSafe.isBlank(null)).isTrue();
            assertThat(NullSafe.isBlank("")).isTrue();
            assertThat(NullSafe.isBlank("  \t\n")).isTrue();
        }

        @Test
        void isBlank_nonBlank_false() {
            assertThat(NullSafe.isBlank(" a ")).isFalse();
        }

        @Test
        void isNotBlank_mirrorsIsBlank() {
            assertThat(NullSafe.isNotBlank("a")).isTrue();
            assertThat(NullSafe.isNotBlank("  ")).isFalse();
        }
    }

    @Nested
    @DisplayName("默认值")
    class DefaultValues {

        @Test
        void getOrDefault_nonNull_returnsData() {
            assertThat(NullSafe.getOrDefault("v", "d")).isEqualTo("v");
        }

        @Test
        void getOrDefault_null_returnsDefault() {
            assertThat(NullSafe.getOrDefault(null, "d")).isEqualTo("d");
        }

        @Test
        void computeOrElse_supplierYieldsValue_returnsIt() {
            assertThat(NullSafe.computeOrElse(() -> "v", "d")).isEqualTo("v");
        }

        @Test
        void computeOrElse_supplierYieldsNull_returnsDefault() {
            assertThat(NullSafe.computeOrElse(() -> null, "d")).isEqualTo("d");
        }

        @Test
        void computeOrElse_nullSupplier_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> NullSafe.computeOrElse(null, "d"));
        }
    }

    @Nested
    @DisplayName("asList")
    class AsList {

        @Test
        void asList_null_returnsEmptyModifiableList() {
            List<String> list = NullSafe.asList((String[]) null);
            assertThat(list).isEmpty();
            list.add("a");
            assertThat(list).containsExactly("a");
        }

        @Test
        void asList_values_returnsModifiableCopy() {
            List<String> list = NullSafe.asList("a", "b");
            assertThat(list).containsExactly("a", "b");
            list.add("c");
            assertThat(list).containsExactly("a", "b", "c");
        }
    }
}
```

- [ ] **Step 2: 新写 CollectsTest.java(22 用例)**

```java
package cn.code91.facility.common;

import cn.code91.facility.structure.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Collects - 集合与 Map 操作")
class CollectsTest {

    record User(Long id, String name) {}

    @Nested
    @DisplayName("toMap")
    class ToMap {

        @Test
        void toMap_nullCollection_returnsEmptyMap() {
            assertThat(Collects.toMap((List<User>) null, User::id)).isEmpty();
        }

        @Test
        void toMap_skipsNullElementsAndNullKeys() {
            List<User> users = Arrays.asList(new User(1L, "a"), null, new User(null, "no-key"));
            Map<Long, User> map = Collects.toMap(users, User::id);
            assertThat(map).hasSize(1);
            assertThat(map.get(1L).name()).isEqualTo("a");
        }

        @Test
        void toMap_duplicateKeys_keepsFirstInserted() {
            List<User> users = List.of(new User(1L, "first"), new User(1L, "second"));
            Map<Long, User> map = Collects.toMap(users, User::id);
            assertThat(map.get(1L).name()).isEqualTo("first");
        }

        @Test
        void toMap_kv_extractsBoth() {
            List<User> users = List.of(new User(1L, "a"), new User(2L, "b"));
            Map<Long, String> map = Collects.toMap(users, User::id, User::name);
            assertThat(map).containsEntry(1L, "a").containsEntry(2L, "b");
        }

        @Test
        void toMap_kv_skipsNullValues() {
            List<User> users = List.of(new User(1L, null), new User(2L, "b"));
            Map<Long, String> map = Collects.toMap(users, User::id, User::name);
            assertThat(map).hasSize(1).containsEntry(2L, "b");
        }
    }

    @Nested
    @DisplayName("safeExtractFromMap")
    class SafeExtract {

        @Test
        void hit_correctType_returnsValue() {
            Map<String, Object> map = Map.of("k", 42);
            Optional<Integer> v = Collects.safeExtractFromMap(map, "k", Integer.class);
            assertThat(v).contains(42);
        }

        @Test
        void typeMismatch_returnsEmpty() {
            Map<String, Object> map = Map.of("k", "string");
            assertThat(Collects.safeExtractFromMap(map, "k", Integer.class)).isEmpty();
        }

        @Test
        void missingKeyOrNullInputs_returnsEmpty() {
            Map<String, Object> map = Map.of("k", 1);
            assertThat(Collects.safeExtractFromMap(map, "absent", Integer.class)).isEmpty();
            assertThat(Collects.safeExtractFromMap(null, "k", Integer.class)).isEmpty();
            assertThat(Collects.safeExtractFromMap(map, null, Integer.class)).isEmpty();
            assertThat(Collects.safeExtractFromMap(map, "k", null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractCompareTuple")
    class ExtractCompareTuple {

        @Test
        void commonKeys_pairedAsTuple() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("b", 20, "c", 30);
            Map<String, Tuple<Integer, Integer>> result = Collects.extractCompareTuple(m1, m2);
            assertThat(result).hasSize(1);
            assertThat(result.get("b")).isEqualTo(Tuple.of(2, 20));
        }

        @Test
        void noCommonKeys_returnsEmpty() {
            assertThat(Collects.extractCompareTuple(Map.of("a", 1), Map.of("b", 2))).isEmpty();
        }

        @Test
        void nullOrEmptyInput_returnsEmpty() {
            assertThat(Collects.extractCompareTuple(null, Map.of("a", 1))).isEmpty();
            assertThat(Collects.extractCompareTuple(Map.of("a", 1), Map.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("列表聚合与映射")
    class ListOps {

        @Test
        void safelyJoin_skipsNullListsKeepsOrder() {
            List<String> joined = Collects.safelyJoin(List.of("a"), null, List.of("b", "c"));
            assertThat(joined).containsExactly("a", "b", "c");
        }

        @Test
        void safelyJoin_nullVarargs_returnsEmpty() {
            assertThat(Collects.safelyJoin((List<String>[]) null)).isEmpty();
        }

        @Test
        void safelyMappingAndJoin_filtersNullElementsAndNullResults() {
            List<String> result = Collects.safelyMappingAndJoin(
                    s -> "x".equals(s) ? null : s.toUpperCase(),
                    Arrays.asList("a", null, "x"), List.of("b"));
            assertThat(result).containsExactly("A", "B");
        }

        @Test
        void mapNonNull_basicMapping() {
            assertThat(Collects.mapNonNull(List.of("ab", "c"), String::length)).containsExactly(2, 1);
        }

        @Test
        void mapNonNull_nullList_returnsEmpty() {
            assertThat(Collects.mapNonNull(null, Object::toString)).isEmpty();
        }
    }

    @Nested
    @DisplayName("listDiff(多重集差)")
    class ListDiff {

        @Test
        void multisetSemantics_respectsCounts() {
            List<Integer> diff = Collects.listDiff(List.of(1, 1, 2), List.of(1));
            assertThat(diff).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        void list2Empty_returnsCopyOfList1() {
            assertThat(Collects.listDiff(List.of(1, 2), null)).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        void list1Empty_returnsEmpty() {
            assertThat(Collects.listDiff(null, List.of(1))).isEmpty();
        }
    }

    @Nested
    @DisplayName("容量与数组")
    class CapacityAndArray {

        @Test
        void calculateCapacity_nonPositive_defaults16() {
            assertThat(Collects.calculateCapacity(0)).isEqualTo(16);
            assertThat(Collects.calculateCapacity(-5)).isEqualTo(16);
        }

        @Test
        void calculateCapacity_loadFactorFormula() {
            assertThat(Collects.calculateCapacity(12)).isEqualTo(17);
        }

        @Test
        void longListToLongArray_nullAndValues() {
            assertThat(Collects.longListToLongArray(null)).isNull();
            assertThat(Collects.longListToLongArray(List.of(1L, 2L))).containsExactly(1L, 2L);
        }
    }
}
```

- [ ] **Step 3: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,报 `cannot find symbol: variable NullSafe / Collects`

- [ ] **Step 4: 标准迁移命令复制 3 个源文件(无内容 rework)**

1. `$SRC\src\main\java\cn\hbads\beacon\facility\common\NullSafe.java` → `$DST\src\main\java\cn\code91\facility\common\NullSafe.java`
2. `$SRC\src\main\java\cn\hbads\beacon\facility\common\Collects.java` → `$DST\src\main\java\cn\code91\facility\common\Collects.java`
3. `$SRC\src\main\java\cn\hbads\beacon\facility\common\package-info.java` → `$DST\src\main\java\cn\code91\facility\common\package-info.java`

- [ ] **Step 5: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 78`(31 + 47),0 失败

- [ ] **Step 6: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 common 簇(NullSafe/Collects),补 47 个行为测试(原零测试盲区)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 5: P1 — result 簇迁移 + 别名精简(Result)

**Files:**
- Create(迁移+编辑): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\result\ResultTest.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\result\ResultSwapTest.java`
- Create(迁移+编辑): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\result\Result.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\result\package-info.java`

**Interfaces:**
- Consumes: 无(Result 纯 JDK)
- Produces(全项目错误通道):`sealed interface Result<T,E>`——工厂 `ok(T)/ok()/empty()/err(E)/of(ThrowableSupplier)/ofRunnable/fromOptional/fromNullable/collectShortCircuit/collectAll`;查询 `isOk/isErr/isOkAnd/isErrAnd`;取值 `get/getErr/orElse/orElseGet/orElseMap/orElseThrow×2/toOptional/toOptionalErr/stream`;变换 `map/mapErr/bimap/flatMap/ensure/recover/recoverWith/swap`;组合 `and/andThen/or/orElseSupplier`;副作用 `peek/peekErr/ifOk/ifErr/match/fold`;内部类型 `Ok<T,E>`/`Err<T,E>`(record)、`ThrowableSupplier`/`ThrowableRunnable`
- **已删除(勿引用)**:`filter(Predicate,Supplier)`(用 `ensure`)、`unwrap()`(用 `get`)、`unwrapErr()`(用 `getErr`)

- [ ] **Step 1: 标准迁移命令复制 2 个测试文件**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\result\ResultTest.java` → `$DST\src\test\java\cn\code91\facility\result\ResultTest.java`
2. `$SRC\src\test\java\cn\hbads\beacon\facility\result\ResultSwapTest.java` → `$DST\src\test\java\cn\code91\facility\result\ResultSwapTest.java`

- [ ] **Step 2: ResultTest.java 删除 3 个别名用例**

编辑目标 `ResultTest.java`,执行两处精确替换。

替换 1 —— old:

```java
        @Test
        void unwrap_isAliasForGet() {
            assertThat(Result.ok("v").unwrap()).isEqualTo("v");
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> Result.err("e").unwrap());
        }

        @Test
        void unwrapErr_isAliasForGetErr() {
            assertThat(Result.err("e").unwrapErr()).isEqualTo("e");
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> Result.ok("v").unwrapErr());
        }

        @Test
        void orElse_onOk_returnsValue() {
```

new:

```java
        @Test
        void orElse_onOk_returnsValue() {
```

替换 2 —— old:

```java
        @Test
        void filter_isSameAsEnsure() {
            Result<String, String> result = Result.<String, String>ok("").filter(s -> !s.isEmpty(), () -> "empty");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isEqualTo("empty");
        }

        @Test
        void recover_onErr_recoversToOk() {
```

new:

```java
        @Test
        void recover_onErr_recoversToOk() {
```

- [ ] **Step 3: 运行验证"红"(Result 类不存在)**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,报 `cannot find symbol: class Result`

- [ ] **Step 4: 标准迁移命令复制 Result 源码与 package-info**

1. `$SRC\src\main\java\cn\hbads\beacon\facility\result\Result.java` → `$DST\src\main\java\cn\code91\facility\result\Result.java`
2. `$SRC\src\main\java\cn\hbads\beacon\facility\result\package-info.java` → `$DST\src\main\java\cn\code91\facility\result\package-info.java`

- [ ] **Step 5: Result.java 删除 3 个纯别名(ADR-0009)**

编辑目标 `Result.java`,执行两处精确替换。

替换 1 —— old:

```java
    /**
     * 类似 Rust 的 unwrap，获取值或抛出异常
     * <p>语义：我确信这是成功的，否则就崩溃</p>
     */
    default T unwrap() {
        return get();
    }

    /**
     * 类似 Rust 的 unwrap_err，获取错误或抛出异常
     */
    default E unwrapErr() {
        return getErr();
    }

    /**
     * 对成功值进行转换
     */
    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);
```

new:

```java
    /**
     * 对成功值进行转换
     */
    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);
```

替换 2 —— old:

```java
    /**
     * filter 的别名，功能同 {@link #ensure}
     */
    default Result<T, E> filter(Predicate<? super T> predicate, Supplier<? extends E> errorSupplier) {
        return ensure(predicate, errorSupplier);
    }

    // ==================== 组合操作 ====================
```

new:

```java
    // ==================== 组合操作 ====================
```

- [ ] **Step 6: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 179`(78 + ResultTest 99 + ResultSwapTest 2),0 失败
> 勘误(2026-07-02 执行时发现):源 ResultTest 实有 102 用例,删 3 别名后余 99;计划初版误记为 96,连锁预期 176/212/214 相应改为 179/215/217。

- [ ] **Step 7: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 result 簇(sealed Result<T,E>)并删除 filter/unwrap/unwrapErr 别名(ADR-0009)

101 个迁移测试(含 RV2-03 swap 守卫);别名用例同步删除。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 6: P1 — error 簇迁移 + C1 断环(错误消息边界本地化)

**Files:**
- Create(迁移): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\error\ErrorTypeInterfaceTest.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\error\WrappedErrorTest.java`
- Create(全新内容,见 Step 3): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\error\ErrorTypeInterface.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\error\FacilityErrorType.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\error\WrappedError.java`
- Create(全新内容,见 Step 4): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\error\package-info.java`

**Interfaces:**
- Consumes: 无(C1 断环后 error 只依赖 JDK + Lombok 编译期注解)
- Produces(P2+ 错误通道基石):
  - `interface ErrorTypeInterface`:`getModule()/getCode()/getMessageKey()/getDefaultMessage()/getFullCode()/format(Object...)/formatFallback(Object[],IllegalArgumentException)/validateCodeRange(int,int)/getSeverity()/getDetailedDescription()`;内嵌 `enum ErrorSeverity {FATAL,ERROR,WARN,INFO}`
  - **行为契约(C1 后)**:`format()` 只渲染 `getDefaultMessage()` 模板——无参返回模板原文(不经 MessageFormat,单引号不被吞);有参走 `MessageFormat.format`;模板为 null 返回 `getMessageKey()`;格式化失败走 `formatFallback`。i18n 解析在边界由 locale 包完成(P5 接线,ADR-0010)
  - `enum FacilityErrorType`(25 个常量,500000-500699)与 `final class WrappedError`(工厂 `of×3/ofWithArgs`;`getErrorType/getException/getArgs/getArgsList/getArgCount/getArg×2/hasException/hasArgs/isErrorType/isErrorCode/getFormattedMessage/getFullMessage`)

- [ ] **Step 1: 标准迁移命令复制 2 个测试文件(断言无需改动——现有断言本就是无 Spring 环境的 fallback 行为)**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\error\ErrorTypeInterfaceTest.java` → `$DST\src\test\java\cn\code91\facility\error\ErrorTypeInterfaceTest.java`
2. `$SRC\src\test\java\cn\hbads\beacon\facility\error\WrappedErrorTest.java` → `$DST\src\test\java\cn\code91\facility\error\WrappedErrorTest.java`

- [ ] **Step 2: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,报 `cannot find symbol`(ErrorTypeInterface/FacilityErrorType/WrappedError)

- [ ] **Step 3: 写入 C1 断环后的 ErrorTypeInterface.java(完整文件)**

> 相对源文件的变化:删除 `import cn.hbads.beacon.facility.locale.LocaleUtil`;`format()` 改为纯默认模板渲染(私有 `formatDefault` 精确镜像旧 `LocaleUtil.renderFallback` 语义);类 javadoc 的 i18n 条目改述边界本地化。其余成员逐字保留。

```java
package cn.code91.facility.error;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * <b>错误类型接口</b>
 * <p>
 * 定义错误信息的标准接口，所有错误类型枚举应实现此接口。
 * 用于统一错误码和错误消息的定义，便于错误处理和国际化。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *     <li><b>模块隔离</b>：通过 {@link #getModule()} 区分错误来源模块</li>
 *     <li><b>唯一标识</b>：{@code module + code} 组合保证全局唯一</li>
 *     <li><b>纯数据契约</b>：本接口只承载 code / messageKey / defaultMessage，不做 i18n 解析；
 *         {@link #getMessageKey()} 是给边界（locale 包）解析用的数据（ADR-0010，C1 断环）</li>
 *     <li><b>参数化消息</b>：通过 {@link #format(Object...)} 渲染默认模板的动态参数</li>
 *     <li><b>安全降级</b>：格式化失败时提供降级方案</li>
 * </ul>
 *
 * <h3>错误码规范：</h3>
 * <pre>
 * 模块前缀（3位） + 错误序号（3位）
 * 例如：FACILITY 模块 = 500xxx
 *       USER 模块     = 100xxx
 *       ORDER 模块    = 200xxx
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public enum UserErrorType implements ErrorTypeInterface {
 *     USER_NOT_FOUND(100001, "用户 {0} 不存在"),
 *     INVALID_PASSWORD(100002, "密码格式错误：{0}");
 *
 *     private final int code;
 *     private final String message;
 *
 *     UserErrorType(int code, String message) {
 *         this.code = code;
 *         this.message = message;
 *     }
 *
 *     @Override
 *     public String getModule() {
 *         return "USER";
 *     }
 *
 *     @Override
 *     public int getCode() {
 *         return code;
 *     }
 *
 *     @Override
 *     public String getDefaultMessage() {
 *         return message;
 *     }
 *
 *     @Override
 *     public String getMessageKey() {
 *         return "user." + name().toLowerCase();
 *     }
 * }
 * }</pre>
 *
 * @author yvvb
 * @see WrappedError
 * @since 2.0.0
 * @apiNote C1 断环版本：i18n 解析移交展示边界（locale 包），本接口不依赖 Spring（ADR-0010）
 */
public interface ErrorTypeInterface {

    // ==================== 核心属性 ====================

    /**
     * 获取模块标识
     * <p>用于区分错误来源模块，建议使用大写字母</p>
     *
     * @return 模块标识，如 "FACILITY", "USER", "ORDER"
     */
    default String getModule() {
        return "UNKNOWN";
    }

    /**
     * 获取错误码
     * <p>在模块内唯一，建议使用模块前缀+序号的方式</p>
     *
     * @return 错误码，用于唯一标识错误类型
     */
    int getCode();

    /**
     * 获取 i18n 消息键
     * <p>纯数据：由展示边界（locale 包的 MessageSource 解析）使用；本接口自身不做解析</p>
     *
     * @return 消息键，格式建议：{module}.{error_name}
     */
    String getMessageKey();

    /**
     * 获取默认错误消息（i18n 未命中或不可用时的兜底模板）
     * <p>支持 {@link MessageFormat} 占位符，如 "用户 {0} 不存在"</p>
     *
     * @return 默认错误消息模板
     */
    String getDefaultMessage();

    // ==================== 派生方法 ====================

    /**
     * 获取完整错误码
     * <p>格式：MODULE-CODE，如 FACILITY-5001</p>
     *
     * @return 完整错误码
     */
    default String getFullCode() {
        return getModule() + "-" + getCode();
    }

    /**
     * 渲染默认消息模板
     * <p>
     * 只使用 {@link #getDefaultMessage()}，不做 i18n 解析（C1 断环，ADR-0010）：
     * 无参时返回模板原文（不经 {@link MessageFormat}，模板中的单引号不会被吞）；
     * 有参时用 {@link MessageFormat#format} 渲染；模板为 null 时返回 {@link #getMessageKey()}。
     * 需要本地化消息的场景在边界调用 locale 包的解析入口。
     * </p>
     *
     * @param args 消息参数
     * @return 渲染后的消息
     */
    default String format(Object... args) {
        try {
            return formatDefault(args);
        } catch (IllegalArgumentException e) {
            return formatFallback(args, e);
        }
    }

    private String formatDefault(Object[] args) {
        String pattern = getDefaultMessage();
        if (pattern == null) {
            return getMessageKey();
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    /**
     * 格式化失败时的降级处理
     * <p>
     * 当 MessageFormat 格式化失败时（如占位符数量不匹配），
     * 提供一个友好的降级消息而不是抛出异常。
     * </p>
     *
     * @param args 原始参数
     * @param e    格式化异常
     * @return 降级后的消息
     */
    default String formatFallback(Object[] args, IllegalArgumentException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(getDefaultMessage());
        sb.append(" [格式化失败: ").append(e.getMessage());
        sb.append(", 参数: ").append(Arrays.toString(args));
        sb.append("]");
        return sb.toString();
    }

    /**
     * 验证错误码是否符合规范
     * <p>
     * 检查错误码是否在模块分配的范围内。
     * </p>
     *
     * @param minCode 最小错误码
     * @param maxCode 最大错误码
     * @return true 如果在范围内
     */
    default boolean validateCodeRange(int minCode, int maxCode) {
        int code = getCode();
        return code >= minCode && code <= maxCode;
    }

    /**
     * 获取错误严重程度（可选扩展）
     * <p>
     * 默认实现返回 INFO 级别，子类可以根据错误码范围返回不同级别。
     * </p>
     *
     * @return 错误级别
     */
    default ErrorSeverity getSeverity() {
        return ErrorSeverity.INFO;
    }

    /**
     * 获取详细的错误描述（用于调试）
     *
     * @return 包含完整错误码、消息键和默认消息的描述
     */
    default String getDetailedDescription() {
        return String.format(
                "ErrorType{fullCode='%s', messageKey='%s', defaultMessage='%s', severity='%s'}",
                getFullCode(),
                getMessageKey(),
                getDefaultMessage(),
                getSeverity()
        );
    }

    // ==================== 错误严重程度枚举 ====================

    /**
     * 错误严重程度
     */
    enum ErrorSeverity {
        /**
         * 致命错误 - 系统级错误，需要立即处理
         */
        FATAL,

        /**
         * 错误 - 业务逻辑错误，影响功能正常运行
         */
        ERROR,

        /**
         * 警告 - 可能存在问题但不影响主流程
         */
        WARN,

        /**
         * 信息 - 正常的业务提示
         */
        INFO
    }
}
```

- [ ] **Step 4: 标准迁移命令复制 FacilityErrorType.java 与 WrappedError.java,并写入新 package-info.java**

复制(无内容 rework):
1. `$SRC\src\main\java\cn\hbads\beacon\facility\error\FacilityErrorType.java` → `$DST\src\main\java\cn\code91\facility\error\FacilityErrorType.java`
2. `$SRC\src\main\java\cn\hbads\beacon\facility\error\WrappedError.java` → `$DST\src\main\java\cn\code91\facility\error\WrappedError.java`

写入 package-info.java(全新内容,更正依赖声明):

```java
/**
 * <h2>cn.code91.facility.error</h2>
 *
 * <p><b>Purpose:</b> Module-prefixed error-code SPI ({@code ErrorTypeInterface}) and
 * immutable {@code WrappedError} container used as the error channel in
 * {@code Result} and {@code Async} pipelines.</p>
 *
 * <p><b>Entry classes:</b> {@code ErrorTypeInterface}, {@code WrappedError},
 * {@code FacilityErrorType}.</p>
 *
 * <p><b>Depends on:</b> nothing outside the JDK. {@code format()} renders the default
 * template only; i18n resolution happens at the boundary via the {@code locale} package
 * (ADR-0010, C1 cycle break).</p>
 *
 * <p><b>Depended on by:</b> {@code context}, {@code hash}, {@code date}, {@code mime},
 * {@code path}, {@code io}, {@code json}, {@code web}, and most other packages that
 * propagate typed errors.</p>
 */
package cn.code91.facility.error;
```

- [ ] **Step 5: 运行验证"绿"(断言不变即证明 C1 行为保持)**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 216`(179 + ErrorTypeInterfaceTest 13 + WrappedErrorTest 24),0 失败
> 勘误 2:源 WrappedErrorTest 实有 24 用例(初版漏数 getArgsList);连锁预期 215/217 → 216/218。
关键佐证:`format_noArgs_returnsDefaultMessage`、`format_withArgs_substitutesPlaceholders`、`format_invalidPattern_fallbackGracefully` 三条迁移断言原样通过。

- [ ] **Step 6: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 error 簇并执行 C1 断环——错误消息边界本地化(ADR-0010)

ErrorTypeInterface.format() 纯默认模板渲染(镜像旧 renderFallback 语义),
error 包零外部依赖;36 个迁移测试断言零改动通过。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 7: P1 — ArchUnit 架构守护(包无环 + error 纯度)

**Files:**
- Create: `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\architecture\ArchitectureTest.java`

**Interfaces:**
- Consumes: Task 3-6 产出的全部 main 类
- Produces: 常驻架构回归测试;后续 phase 在此类中**追加**规则(C2/C3 断环守护在 P4/P3 各自 plan 中添加),不新建测试类

- [ ] **Step 1: 新写 ArchitectureTest.java(2 条规则)**

```java
package cn.code91.facility.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 架构守护(spec §4.4 / §7 第 5 条):
 * 规则随 phase 演进追加——P1 落地包无环 + error 纯度(C1);
 * C2/C3 的守护规则随 P4/P3 迁移任务补充。
 */
@AnalyzeClasses(packages = "cn.code91.facility", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule packages_are_cycle_free =
            slices().matching("cn.code91.facility.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule error_package_depends_only_on_jdk =
            classes().that().resideInAPackage("cn.code91.facility.error..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "cn.code91.facility.error..");
}
```

> 说明:Lombok 注解(`@Getter`)是 SOURCE retention,不进字节码,不影响 error 纯度规则;
> ArchUnit 分析的是编译产物。

- [ ] **Step 2: 运行验证(守护型测试,落地即绿;若红则说明 Task 3-6 有依赖泄漏,必须回查)**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 218`,0 失败

- [ ] **Step 3: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
test: ArchUnit 架构守护(包依赖无环 + error 包纯 JDK)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 8: P1 — 决策沉淀(ADR-0009 / ADR-0010 / INDEX 更新)

**Files:**
- Create: `D:\Yiwer\code\server-facility\docs\adr\0009-alias-trimming.md`
- Create: `D:\Yiwer\code\server-facility\docs\adr\0010-error-message-boundary-localization.md`
- Modify: `D:\Yiwer\code\server-facility\docs\adr\INDEX.md`(表尾追加 2 行)

**Interfaces:**
- Consumes: Task 3/5(别名精简事实)、Task 6(C1 事实)
- Produces: 可溯源决策记录(spec §12 成功标准第 5 条)

- [ ] **Step 1: 写 ADR-0009**

```markdown
# ADR-0009: Result/Tuple/Triple 纯别名精简

- **状态**:Accepted(2026-07-02)
- **源起**:REVIEW-2 RV2-18(wontfix-for-now)+ spec A6 重估授权

## 背景

源项目 REVIEW-2 认定 `filter`≡`ensure`、`unwrap`≡`get` 等纯别名为表面冗余(RV2-18),
当年 wontfix 理由是"无生产消费方,删除仅 churn facility 自身测试"。迁移到零消费方的新仓库后,
该理由消失,而"每个语义一个名字"的 API 收敛收益永久化。
迁移前全仓 import 扫描证实:所有别名在源码内部零使用(仅 3 处测试用例覆盖别名自身)。

## 决策

迁移时删除以下纯别名(委托实现、无语义差异):

| 类 | 删除 | 保留(语义主名) |
|---|---|---|
| `Result` | `filter(Predicate,Supplier)`、`unwrap()`、`unwrapErr()` | `ensure`、`get`、`getErr` |
| `Tuple` | `first()`、`second()`、`key()`、`value()` | `left()`、`right()` |
| `Triple` | `first()`、`second()`、`third()`、`toLeftMiddle()`、`toMiddleRight()`、`toLeftRight()` | `left()`、`middle()`、`right()`、`dropRight()`、`dropLeft()`、`dropMiddle()` |

**非别名不删**:`and`(急切)/`andThen`(惰性)、`or`/`orElseSupplier`、`match`(消费)/`fold`(映射)
语义各自独立,保留。

## 后果

- API 面收敛 13 个方法;新消费方不再面临"两个名字选哪个"。
- 自 beacon 迁移代码的消费方按上表映射改名即可(编译期报错,机械替换)。
```

- [ ] **Step 2: 写 ADR-0010**

```markdown
# ADR-0010: 错误消息边界本地化(C1 断环)

- **状态**:Accepted(2026-07-02)
- **源起**:spec §4.4 C1——包级循环 `error → locale → context → error`

## 背景

源项目 `ErrorTypeInterface.format()` 内联调用 `LocaleUtil.translateMessageWithFallback(...)`,
使核心错误类型编译期依赖 locale,进而经 `SpringContextHolder` 与 Spring 运行时纠缠,
构成三包循环(证据:ErrorTypeInterface.java:3 / LocaleUtil.java:4 / SpringContextHolder.java:16-18)。
循环导致 error/locale/context 三簇无法独立理解、测试与迁移。

## 决策

1. **error 包纯数据化**:`ErrorTypeInterface` 只承载 `code/messageKey/defaultMessage`;
   `format()` 只渲染默认模板,语义精确镜像旧 `LocaleUtil.renderFallback`:
   - 无参 → 返回 `getDefaultMessage()` 原文(不经 MessageFormat,单引号不被吞);
   - 有参 → `MessageFormat.format(defaultMessage, args)`;
   - 模板 null → 返回 `getMessageKey()`;
   - `IllegalArgumentException` → `formatFallback`(原降级逻辑不变)。
2. **i18n 解析移交展示边界**:locale 包保留 `translateMessageWithFallback` 能力;
   P5 提供针对 `ErrorTypeInterface` 的便捷解析入口并在 P6 异常处理器接线,
   等价旧行为:`LocaleUtil.translateMessageWithFallback(et.getMessageKey(), args, et.getDefaultMessage(), locale)`。
3. 依赖方向变为锥形单向:`error(纯) ← context ← locale`;ArchUnit 规则
   `error_package_depends_only_on_jdk` 常驻守护。

## 行为影响

- **无 Spring / MessageSource 未命中场景**:行为完全不变(旧实现本就落入 renderFallback 路径),
  36 个迁移测试断言零改动通过是直接证据。
- **Spring + i18n 命中场景**:`format()` 不再隐式返回本地化消息——需要本地化的调用点
  (源项目中实际只有 web 异常出口)改为在边界显式解析(P6 落地)。

## 备选(否决)

- 保留循环:三簇永久绑定,分簇迁移拓扑不可行。
- error 内嵌 ResourceBundle 解析:重新发明 MessageSource,且仍需 Locale 上下文,复杂度高于边界解析。
```

- [ ] **Step 3: INDEX.md 表尾追加 2 行**

old:

```markdown
| [0008](0008-rp-15-snowid-parsetimestamp-instance.md) | inherited | SnowId parseTimestamp/parseInfo 改 instance |
```

new:

```markdown
| [0008](0008-rp-15-snowid-parsetimestamp-instance.md) | inherited | SnowId parseTimestamp/parseInfo 改 instance |
| [0009](0009-alias-trimming.md) | Accepted | Result/Tuple/Triple 纯别名精简(13 个方法) |
| [0010](0010-error-message-boundary-localization.md) | Accepted | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
```

- [ ] **Step 4: 全量回归 + Commit**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 218`,0 失败

```powershell
git -C D:\Yiwer\code\server-facility add docs\adr
git -C D:\Yiwer\code\server-facility commit -m @'
docs: ADR-0009 别名精简 + ADR-0010 错误消息边界本地化(C1)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## 验收清单(P0+P1 出口)

- [ ] `mvn test` 全绿,218 个用例(structure 31 + common 47 + result 101 + error 37 + arch 2),0 失败 0 跳过
- [ ] `mvn verify` 生成 `target/site/jacoco/index.html`,P1 四簇 line coverage 目测 ≥ 90%(gate 在 P7 挂)
- [ ] ArchUnit 两规则绿:包无环、error 纯 JDK
- [ ] `docs/adr/` 共 11 个文件(0000 模板 + 0001-0008 inherited + 0009/0010)
- [ ] git log 含 8 个任务提交,迁移与 rework 变更可独立回溯
- [ ] 未迁移文件(coordinate/ChineseNumbers/validate/WrappedContainer/WrappedDataType)未出现在 `$DST\src`
