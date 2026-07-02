# server-facility P3 数值与 ID 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 迁移 date/number/id 三簇与首个 AutoConfiguration:date 去 commons-lang3(4 处 DateUtils 调用 JDK 等价替换)、ChineseNumbers drop 执行、C3 断环第一步(FacilityIdProperties 归位 id 包 + 配置前缀 `beacon.facility.id`→`facility.id`)、`META-INF/spring` 装配注册落地。

**Architecture:** 依赖拓扑:date(→error/result/structure)、number(纯 JDK)互不依赖;id(→context)+ properties 归位后自洽;autoconfigure 只向下依赖 id。顺序:T1 依赖 → T2 date → T3 number → T4 id+C3 → T5 装配 → T6 ArchUnit → T7 docs。TDD:测试先落(红)→ 源码 → 绿 → 提交。

**Tech Stack:** 同 P2;本阶段新增依赖:spring-boot-autoconfigure(compile)、hibernate-validator(**test**——`@Validated` 属性校验用,Spring Boot 的 MessageInterpolatorFactory 无 EL 亦可兜底,源项目实证)。

## Global Constraints

- 继承 P1/P2 计划全部约束:`$SRC`=`D:\STELE\beacon\beacon-support\beacon-facility`、`$DST`=`D:\Yiwer\code\server-facility`、包名映射 `cn.hbads.beacon.facility`→`cn.code91.facility`(ordinal)、UTF-8 无 BOM、每任务 `mvn test` 全绿才 commit、commit 尾行 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。
- **git 提交一律用 PowerShell 工具执行**(P2 教训:here-string 在 POSIX shell 下会把 `@` 写进提交信息)。
- **标准迁移命令**(PowerShell,同 P1/P2):
  ```powershell
  $s = '<源绝对路径>'; $d = '<目标绝对路径>'
  New-Item -ItemType Directory -Force (Split-Path $d) | Out-Null
  $t = [IO.File]::ReadAllText($s, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText($d, $t.Replace('cn.hbads.beacon.facility', 'cn.code91.facility'), [Text.UTF8Encoding]::new($false))
  ```
- **C3 二次替换**(仅 T4/T5 标注的文件,在标准迁移命令产出后执行):
  ```powershell
  $t = [IO.File]::ReadAllText($d, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText($d, $t.Replace('cn.code91.facility.autoconfigure.properties.FacilityIdProperties', 'cn.code91.facility.id.FacilityIdProperties'), [Text.UTF8Encoding]::new($false))
  ```
- **测试计数纪律**(grep 实数,已核):迁移 DateUtilTest 4 / NumberFormatSizeTest 2 / IdUtilTest 12 / IdUtilSpringFallbackTest 1 / ClockBackwardsExceptionTest 1 / SnowIdGeneratorTest 9 / FacilityIdAutoConfigurationTest 3 / ConfigurationPropertiesBindingTest **裁剪后 3**(第 4 例属 web 簇,P6 迁回);新增 date pins 7 / NumbersTest 16 / NumberFormatTest 10 / NumberUnitsTest 5 / ArchUnit +1。链:**T2 后 314 → T3 后 347 → T4 后 370 → T5 后 376 → T6 后 377**。不符即 STOP(先查计数再查代码)。
- **报告纪律**:RED/GREEN 必须原始 mvn 粘贴,行号与提交文件交叉核对,拼凑视为任务失败。
- **禁止事项**:不迁移 `ChineseNumbers.java`(drop,spec D2 已批);不迁移 web 相关 properties(P6);`DateUtil` 除 lang3 替换外零改动;`SnowIdGenerator` 除 C3 import/javadoc 前缀修正外零改动。

## 复核结论(spec §5 遗留 review 项,本计划定案)

- **SnowIdGenerator 位宽([0..3])**:保持 2+2+10 布局不变。理由:55 位有效位是有意设计(类 javadoc 明示),每毫秒 1024 ID、16 节点上限对脚手架规模足够;扩位(如 5+5)会改变位移与 parse 语义,属破坏性决策,列入 pre-1.0 roadmap 决策点(一旦有部署的 ID 存量就永远不能改)。
- **date 去 lang3 单 commit 执行**(不同于 P2 formatMessage 的分离):4 处 `DateUtils` 调用是机械等价替换且中间态无法编译(迁移原样需临时引入 lang3 再移除,纯 churn);行为由 7 个新增 pin 用例 + 4 个迁移用例保护;null 行为契约选 NPE(源测试从未覆盖 null 入参,lang3 3.20 的 truncate 亦抛 NPE,保真成立)。

---

### Task 1: P3 依赖落 pom

**Files:**
- Modify: `D:\Yiwer\code\server-facility\pom.xml`

**Interfaces:**
- Consumes: P2 的 pom
- Produces: compile 含 spring-boot-autoconfigure;test 含 hibernate-validator(`@Validated` 属性校验)

- [ ] **Step 1: pom.xml 插入依赖** —— old:

```xml
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
        </dependency>
```

new:

```xml
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
        </dependency>

        <!-- ===== P3 装配层所需 ===== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
```

- [ ] **Step 2: pom.xml 插入 test 依赖** —— old:

```xml
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>test</scope>
        </dependency>
```

new:

```xml
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.hibernate.validator</groupId>
            <artifactId>hibernate-validator</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 3: 验证** Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test` → Expected: `BUILD SUCCESS`,`Tests run: 303`

- [ ] **Step 4: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add pom.xml
git -C D:\Yiwer\code\server-facility commit -m @'
build: P3 依赖(spring-boot-autoconfigure compile;hibernate-validator test)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 2: date 簇迁移 + 去 commons-lang3

**Files:**
- Create(迁移+追加): `$DST\src\test\java\cn\code91\facility\date\DateUtilTest.java`
- Create(迁移+编辑): `$DST\src\main\java\cn\code91\facility\date\DateUtil.java`
- Create(全新内容): `$DST\src\main\java\cn\code91\facility\date\package-info.java`

**Interfaces:**
- Consumes: P1 的 error/result/structure(Tuple)
- Produces: `DateUtil` 全 API(format/parseDate Result-style、nowDay/isSameDay/yesterday/tomorrow、long↔LocalDateTime 等);**零 lang3 依赖**
- 行为契约:nowDay/yesterday/tomorrow 返回零点截断;isSameDay 按日等价;null 入参 → `NullPointerException("date must not be null")`

- [ ] **Step 1: 迁移 DateUtilTest.java(标准命令)**

`$SRC\src\test\java\cn\hbads\beacon\facility\date\DateUtilTest.java` → `$DST\src\test\java\cn\code91\facility\date\DateUtilTest.java`

- [ ] **Step 2: DateUtilTest 追加 7 个 pin 用例(类收尾 `}` 前插入;用 FQN 避免动 import 区)**

```java

    private static java.util.Date at(int y, int mo, int d, int h, int mi) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.clear();
        cal.set(y, mo - 1, d, h, mi, 0);
        return cal.getTime();
    }

    @Test @DisplayName("nowDay 截断到本日零点")
    void nowDay_truncatesToMidnight() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(DateUtil.nowDay());
        assertThat(cal.get(java.util.Calendar.HOUR_OF_DAY)).isZero();
        assertThat(cal.get(java.util.Calendar.MINUTE)).isZero();
        assertThat(cal.get(java.util.Calendar.SECOND)).isZero();
        assertThat(cal.get(java.util.Calendar.MILLISECOND)).isZero();
    }

    @Test @DisplayName("isSameDay 同日不同时刻为 true")
    void isSameDay_sameDayDifferentTimes_true() {
        assertThat(DateUtil.isSameDay(at(2026, 7, 2, 1, 0), at(2026, 7, 2, 23, 59))).isTrue();
    }

    @Test @DisplayName("isSameDay 跨日为 false")
    void isSameDay_differentDays_false() {
        assertThat(DateUtil.isSameDay(at(2026, 7, 2, 23, 59), at(2026, 7, 3, 0, 0))).isFalse();
    }

    @Test @DisplayName("isSameDay null 入参抛 NPE")
    void isSameDay_nullArg_throwsNPE() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DateUtil.isSameDay(null, new java.util.Date()));
    }

    @Test @DisplayName("yesterday 为前一日零点")
    void yesterday_isPreviousDayAtMidnight() {
        assertThat(DateUtil.yesterday(at(2026, 7, 2, 15, 30))).isEqualTo(at(2026, 7, 1, 0, 0));
    }

    @Test @DisplayName("tomorrow 为后一日零点")
    void tomorrow_isNextDayAtMidnight() {
        assertThat(DateUtil.tomorrow(at(2026, 7, 2, 15, 30))).isEqualTo(at(2026, 7, 3, 0, 0));
    }

    @Test @DisplayName("yesterday null 入参抛 NPE")
    void yesterday_nullArg_throwsNPE() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DateUtil.yesterday(null));
    }
```

- [ ] **Step 3: 验证"红"** Run mvn test → Expected: `BUILD FAILURE`,`cannot find symbol: class DateUtil`

- [ ] **Step 4: 迁移 DateUtil.java(标准命令),然后执行 6 处编辑**

`$SRC\src\main\java\cn\hbads\beacon\facility\date\DateUtil.java` → `$DST\src\main\java\cn\code91\facility\date\DateUtil.java`

编辑 1(删 lang3 import)—— old:
```java
import org.apache.commons.lang3.time.DateUtils;
```
new:(整行删除,无替换内容——用 Edit 把该行与其换行符替换为空)
```java
```

编辑 2(nowDay)—— old:
```java
        return DateUtils.truncate(new Date(), Calendar.DATE);
```
new:
```java
        return truncateToDay(new Date());
```

编辑 3(isSameDay)—— old:
```java
        return DateUtils.truncatedEquals(date1, date2, Calendar.DATE);
```
new:
```java
        return truncateToDay(date1).equals(truncateToDay(date2));
```

编辑 4(yesterday)—— old:
```java
        return DateUtils.addDays(DateUtils.truncate(date, Calendar.DATE), -1);
```
new:
```java
        return truncatedPlusDays(date, -1);
```

编辑 5(tomorrow)—— old:
```java
        return DateUtils.addDays(DateUtils.truncate(date, Calendar.DATE), 1);
```
new:
```java
        return truncatedPlusDays(date, 1);
```

编辑 6(在 tomorrow 方法后插入两个私有 helper)—— old:
```java
    public static Date tomorrow(Date date) {
        return truncatedPlusDays(date, 1);
    }
```
new:
```java
    public static Date tomorrow(Date date) {
        return truncatedPlusDays(date, 1);
    }

    /**
     * 截断到当日零点(默认时区)。镜像原 lang3 {@code DateUtils.truncate(date, Calendar.DATE)} 语义;
     * null 抛 NPE(与 lang3 3.20 一致)。
     */
    private static Date truncateToDay(Date date) {
        if (date == null) {
            throw new NullPointerException("date must not be null");
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * 截断到零点后加减天数。镜像原 lang3 {@code DateUtils.addDays(DateUtils.truncate(...), n)} 组合。
     */
    private static Date truncatedPlusDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(truncateToDay(date));
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
```

- [ ] **Step 5: 写入 date/package-info.java(全新——源文件"Depends on: nothing"失真,实际 import error/result/structure)**

```java
/**
 * <h2>cn.code91.facility.date</h2>
 *
 * <p><b>Purpose:</b> Result-style date parsing/formatting (never throws checked
 * exceptions), cached {@code DateTimeFormatter}s, legacy {@code java.util.Date}
 * day-level helpers (truncate / same-day / adjacent-day), and epoch conversions.</p>
 *
 * <p><b>Entry classes:</b> {@code DateUtil}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (parse failures surface as
 * {@code Result<T, WrappedError>}) and {@code structure} ({@code Tuple} for range
 * normalization). No third-party date library — lang3 usage was replaced with JDK
 * equivalents at migration (P3).</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.date;
```

- [ ] **Step 6: 验证"绿"** Run mvn test → Expected: `BUILD SUCCESS`,`Tests run: 314`(303 + 11),0 失败

- [ ] **Step 7: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 date 簇并去除 commons-lang3(4 处 DateUtils 调用 JDK 等价替换)

7 个新增 pin 用例钉住截断/同日/邻日语义(null→NPE 与 lang3 3.20 一致);
迁移用例断言零改动;主 classpath 自此无 commons-lang3(spec §6 A3)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 3: number 簇迁移(ChineseNumbers drop 执行)

**Files:**
- Create(迁移): `$DST\src\test\java\cn\code91\facility\number\NumberFormatSizeTest.java`
- Test(新写): `NumbersTest.java` / `NumberFormatTest.java` / `NumberUnitsTest.java`(同目录)
- Create(迁移+1 处编辑): `$DST\src\main\java\cn\code91\facility\number\Numbers.java`
- Create(迁移): `NumberFormat.java`
- Create(迁移): `NumberUnits.java`
- Create(全新内容): `package-info.java`
- **不迁移**:`ChineseNumbers.java`(spec §5 drop)

**Interfaces:**
- Consumes: 无(纯 JDK + lombok)
- Produces:`Numbers`(parse×5/OrDefault/equals/isPositive/isNegative/isZero/isNonNegative/max/min/nullToZero/nullToDefault×2/setScale×2)、`NumberFormat`(format/formatInt/format2/format4/formatSmart/formatMoney/formatPercent/formatSize/parseSize)、`NumberUnits`(mmToPx/pxToMm)

- [ ] **Step 1: 新写 NumbersTest.java(16 用例)**

```java
package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Numbers - 数字核心工具")
class NumbersTest {

    @Test
    void parseBigDecimal_valid_trimmed() {
        assertThat(Numbers.parseBigDecimal(" 1.50 ")).contains(new BigDecimal("1.50"));
    }

    @Test
    void parseBigDecimal_nullBlankGarbage_empty() {
        assertThat(Numbers.parseBigDecimal(null)).isEmpty();
        assertThat(Numbers.parseBigDecimal("  ")).isEmpty();
        assertThat(Numbers.parseBigDecimal("abc")).isEmpty();
    }

    @Test
    void parseBigDecimalOrZero_garbage_zero() {
        assertThat(Numbers.parseBigDecimalOrZero("x")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void parseInt_valid() {
        assertThat(Numbers.parseInt(" 42 ")).contains(42);
    }

    @Test
    void parseIntOrDefault_garbage_default() {
        assertThat(Numbers.parseIntOrDefault("4.2", 7)).isEqualTo(7);
    }

    @Test
    void parseLong_valid() {
        assertThat(Numbers.parseLong("9000000000")).contains(9_000_000_000L);
    }

    @Test
    void parseDouble_valid() {
        assertThat(Numbers.parseDouble("-3.14")).contains(-3.14);
    }

    @Test
    void equals_nullNullTrue_andScaleInsensitive() {
        assertThat(Numbers.equals(null, null)).isTrue();
        assertThat(Numbers.equals(new BigDecimal("1.0"), new BigDecimal("1.00"))).isTrue();
        assertThat(Numbers.equals(new BigDecimal("1"), null)).isFalse();
    }

    @Test
    void isPositive_nullFalse() {
        assertThat(Numbers.isPositive(BigDecimal.ONE)).isTrue();
        assertThat(Numbers.isPositive(BigDecimal.ZERO)).isFalse();
        assertThat(Numbers.isPositive(null)).isFalse();
    }

    @Test
    void isNegative() {
        assertThat(Numbers.isNegative(new BigDecimal("-0.01"))).isTrue();
        assertThat(Numbers.isNegative(BigDecimal.ZERO)).isFalse();
    }

    @Test
    void isZero_scaleInsensitive() {
        assertThat(Numbers.isZero(new BigDecimal("0.00"))).isTrue();
        assertThat(Numbers.isZero(null)).isFalse();
    }

    @Test
    void isNonNegative() {
        assertThat(Numbers.isNonNegative(BigDecimal.ZERO)).isTrue();
        assertThat(Numbers.isNonNegative(new BigDecimal("-1"))).isFalse();
    }

    @Test
    void max_nullSafe() {
        assertThat(Numbers.max(null, BigDecimal.ONE)).isEqualTo(BigDecimal.ONE);
        assertThat(Numbers.max(BigDecimal.TEN, BigDecimal.ONE)).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void min_nullSafe() {
        assertThat(Numbers.min(BigDecimal.TEN, null)).isEqualTo(BigDecimal.TEN);
        assertThat(Numbers.min(BigDecimal.TEN, BigDecimal.ONE)).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void nullToZero_andDefault() {
        assertThat(Numbers.nullToZero(null)).isEqualTo(BigDecimal.ZERO);
        assertThat(Numbers.nullToDefault(null, BigDecimal.TEN)).isEqualTo(BigDecimal.TEN);
        assertThat(Numbers.nullToDefault(null, () -> BigDecimal.ONE)).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void setScale_halfUpDefault_andNullPassthrough() {
        assertThat(Numbers.setScale(new BigDecimal("1.005"), 2)).isEqualTo(new BigDecimal("1.01"));
        assertThat(Numbers.setScale(new BigDecimal("1.004"), 2, RoundingMode.CEILING))
                .isEqualTo(new BigDecimal("1.01"));
        assertThat(Numbers.setScale(null, 2)).isNull();
    }
}
```

- [ ] **Step 2: 新写 NumberFormatTest.java(10 用例)**

```java
package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumberFormat - 数字格式化(补充 formatSize 之外的盲区)")
class NumberFormatTest {

    @Test
    void format_scale2_halfUp() {
        assertThat(NumberFormat.format(new BigDecimal("1.005"), 2)).isEqualTo("1.01");
    }

    @Test
    void format_scaleZeroOrNegative_integerForm() {
        assertThat(NumberFormat.format(new BigDecimal("3.7"), 0)).isEqualTo("4");
        assertThat(NumberFormat.format(new BigDecimal("3.7"), -1)).isEqualTo("4");
    }

    @Test
    void formatInt_roundsHalfUp() {
        assertThat(NumberFormat.formatInt(2.5)).isEqualTo("3");
    }

    @Test
    void formatSmart_stripsTrailingZeros() {
        assertThat(NumberFormat.formatSmart(new BigDecimal("1.2300"))).isEqualTo("1.23");
        assertThat(NumberFormat.formatSmart(new BigDecimal("5.000"))).isEqualTo("5");
    }

    @Test
    void formatSmart_capsAtEightDecimals() {
        assertThat(NumberFormat.formatSmart(new BigDecimal("0.1234567891")))
                .isEqualTo("0.12345679");
    }

    @Test
    void formatMoney_thousandsGrouping() {
        assertThat(NumberFormat.formatMoney(new BigDecimal("1234567.891"))).isEqualTo("1,234,567.89");
    }

    @Test
    void formatPercent_multipliesBy100() {
        assertThat(NumberFormat.formatPercent(new BigDecimal("0.1234"), 2)).isEqualTo("12.34%");
    }

    @Test
    void nullInputs_yieldEmptyString() {
        assertThat(NumberFormat.format(null, 2)).isEmpty();
        assertThat(NumberFormat.formatMoney(null)).isEmpty();
        assertThat(NumberFormat.formatSmart(null)).isEmpty();
        assertThat(NumberFormat.formatPercent(null, 2)).isEmpty();
    }

    @Test
    void parseSize_units() {
        assertThat(NumberFormat.parseSize("10KB")).contains(10L * 1024);
        assertThat(NumberFormat.parseSize("1.5MB")).contains((long) (1.5 * 1024 * 1024));
        assertThat(NumberFormat.parseSize("2GB")).contains(2L * 1024 * 1024 * 1024);
        assertThat(NumberFormat.parseSize("512B")).contains(512L);
        assertThat(NumberFormat.parseSize("77")).contains(77L);
    }

    @Test
    void parseSize_invalidOrBlank_empty() {
        assertThat(NumberFormat.parseSize("abcMB")).isEmpty();
        assertThat(NumberFormat.parseSize("   ")).isEmpty();
        assertThat(NumberFormat.parseSize(null)).isEmpty();
    }
}
```

- [ ] **Step 3: 新写 NumberUnitsTest.java(5 用例)**

```java
package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumberUnits - 毫米/像素换算")
class NumberUnitsTest {

    @Test
    void mmToPx_oneInchAt96Dpi_is96px() {
        assertThat(NumberUnits.mmToPx(new BigDecimal("25.4"), 96)).isEqualTo(new BigDecimal("96"));
    }

    @Test
    void mmToPx_null_zero() {
        assertThat(NumberUnits.mmToPx(null, 96)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void pxToMm_96pxAt96Dpi_is25_40mm() {
        assertThat(NumberUnits.pxToMm(new BigDecimal("96"), 96)).isEqualTo(new BigDecimal("25.40"));
    }

    @Test
    void pxToMm_dpiZero_zero() {
        assertThat(NumberUnits.pxToMm(BigDecimal.ONE, 0)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void pxToMm_null_zero() {
        assertThat(NumberUnits.pxToMm(null, 96)).isEqualTo(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 4: 迁移 NumberFormatSizeTest.java(标准命令)**

`$SRC\src\test\java\cn\hbads\beacon\facility\number\NumberFormatSizeTest.java` → `$DST\src\test\java\cn\code91\facility\number\NumberFormatSizeTest.java`

- [ ] **Step 5: 验证"红"** Run mvn test → Expected: `BUILD FAILURE`,`cannot find symbol`(Numbers/NumberFormat/NumberUnits)

- [ ] **Step 6: 迁移 3 个主源文件(标准命令;ChineseNumbers 不迁),Numbers.java 执行 1 处编辑**

1. `$SRC\...\number\Numbers.java` → `$DST\src\main\java\cn\code91\facility\number\Numbers.java`
2. `$SRC\...\number\NumberFormat.java` → 同目录
3. `$SRC\...\number\NumberUnits.java` → 同目录

Numbers.java 编辑(去 ChineseNumbers javadoc 引用)—— old:
```java
 * 格式化能力在 {@link NumberFormat}，单位换算在 {@link NumberUnits}，中文数字在 {@link ChineseNumbers}。
```
new:
```java
 * 格式化能力在 {@link NumberFormat}，单位换算在 {@link NumberUnits}。
```

- [ ] **Step 7: 写入 number/package-info.java(全新;去 ChineseNumbers,依赖声明属实"纯 JDK")**

```java
/**
 * <h2>cn.code91.facility.number</h2>
 *
 * <p><b>Purpose:</b> Numeric core utilities — safe parsing ({@code Optional}-returning),
 * null-safe comparison, scale setting ({@code Numbers}); formatting to plain / money /
 * percent / human-readable byte-size strings ({@code NumberFormat}); and mm ↔ px unit
 * conversion ({@code NumberUnits}).</p>
 *
 * <p><b>Entry classes:</b> {@code Numbers}, {@code NumberFormat}, {@code NumberUnits}.</p>
 *
 * <p><b>Depends on:</b> nothing outside the JDK.
 * ({@code ChineseNumbers} was dropped at migration — spec §5, domain-specific.)</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.number;
```

- [ ] **Step 8: 验证"绿"** Run mvn test → Expected: `BUILD SUCCESS`,`Tests run: 347`(314 + 33),0 失败

- [ ] **Step 9: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 number 簇(Numbers/NumberFormat/NumberUnits),ChineseNumbers 按 spec §5 drop

补 31 个行为测试(原仅 formatSize 2 例);javadoc 与 package-info 同步去除 drop 类引用。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 4: id 簇迁移 + C3 断环(FacilityIdProperties 归位)

**Files:**
- Create(迁移): 4 个测试 → `$DST\src\test\java\cn\code91\facility\id\{IdUtilTest,IdUtilSpringFallbackTest}.java`、`...\id\support\{ClockBackwardsExceptionTest,SnowIdGeneratorTest}.java`(SnowIdGeneratorTest 需 C3 二次替换)
- Create(迁移): `$DST\src\main\java\cn\code91\facility\id\IdUtil.java`、`...\id\support\{SnowIdGenerator,ClockBackwardsException}.java`(SnowIdGenerator 需 C3 二次替换 + 2 处 javadoc 编辑)
- Create(迁移+编辑): `$DST\src\main\java\cn\code91\facility\id\FacilityIdProperties.java`(源:`$SRC\...\autoconfigure\properties\FacilityIdProperties.java`;包声明改 id + prefix 改)
- Create(全新内容): `$DST\src\main\java\cn\code91\facility\id\package-info.java`

**Interfaces:**
- Consumes: P2 的 SpringContextHolder;P1 的 error(ClockBackwardsException 所在簇约定)
- Produces:`IdUtil.snowId()/uuid()×5/parseTimestamp/parseWorkerId/parseDataCenterId/parseSequence/parseInfo/getGeneratorType/isUsingSpringGenerator/resetGenerator/setGenerator`;`SnowIdGenerator`(3 构造器 + clock seam + nextId + parse 实例/静态方法 + MAX_* 常量);**`cn.code91.facility.id.FacilityIdProperties`**(prefix `facility.id`,T5 装配引用此新家)
- **C3 效果**:id 簇不再 import autoconfigure 任何类(T6 规则锁定)

- [ ] **Step 1: 迁移 4 个测试文件(标准命令);SnowIdGeneratorTest 追加 C3 二次替换**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\id\IdUtilTest.java` → `$DST\src\test\java\cn\code91\facility\id\IdUtilTest.java`
2. `$SRC\...\id\IdUtilSpringFallbackTest.java` → 同布局
3. `$SRC\...\id\support\ClockBackwardsExceptionTest.java` → 同布局
4. `$SRC\...\id\support\SnowIdGeneratorTest.java` → 同布局,**然后对其执行 Global Constraints 的 C3 二次替换命令**

- [ ] **Step 2: 验证"红"** Run mvn test → Expected: `BUILD FAILURE`,`cannot find symbol`(IdUtil/SnowIdGenerator/FacilityIdProperties)

- [ ] **Step 3: 迁移 3 个主源文件;SnowIdGenerator 执行 C3 二次替换 + 2 处 javadoc 编辑**

1. `$SRC\...\id\IdUtil.java` → `$DST\src\main\java\cn\code91\facility\id\IdUtil.java`
2. `$SRC\...\id\support\ClockBackwardsException.java` → `$DST\...\id\support\ClockBackwardsException.java`
3. `$SRC\...\id\support\SnowIdGenerator.java` → `$DST\...\id\support\SnowIdGenerator.java`,随后执行 **C3 二次替换命令**(import 行归位),再做以下两处 javadoc 编辑。

SnowIdGenerator javadoc 编辑 A(yaml 示例前缀)—— old:

```java
 * # application.yml
 * beacon:
 *   facility:
 *     id:
 *       data-center-id: 1  # 数据中心ID (0-3)
 *       worker-id: 0       # 工作节点ID (0-3)
 *       clock-backwards-threshold-millis: 5
 *       throw-on-clock-backwards-exceed-threshold: true
```

new:

```java
 * # application.yml
 * facility:
 *   id:
 *     data-center-id: 1  # 数据中心ID (0-3)
 *     worker-id: 0       # 工作节点ID (0-3)
 *     clock-backwards-threshold-millis: 5
 *     throw-on-clock-backwards-exceed-threshold: true
```

SnowIdGenerator javadoc 编辑 B(stele 残留)—— old:

```java
     * @param startTimestamp 自定义纪元（Unix 毫秒），与 {@code stele.facility.id.startTimestamp} 配置对应
```

new:

```java
     * @param startTimestamp 自定义纪元（Unix 毫秒），与 {@code facility.id.start-timestamp} 配置对应
```

- [ ] **Step 4: 迁移 FacilityIdProperties 到新家(标准命令后 2 处编辑)**

`$SRC\src\main\java\cn\hbads\beacon\facility\autoconfigure\properties\FacilityIdProperties.java` → `$DST\src\main\java\cn\code91\facility\id\FacilityIdProperties.java`

编辑 1(包声明归位)—— old:
```java
package cn.code91.facility.autoconfigure.properties;
```
new:
```java
package cn.code91.facility.id;
```

编辑 2(配置前缀,D1 决策)—— old:
```java
@ConfigurationProperties(prefix = "beacon.facility.id")
```
new:
```java
@ConfigurationProperties(prefix = "facility.id")
```

- [ ] **Step 5: 写入 id/package-info.java(全新——源文件"Depends on: error"失真;C3 后 properties 落户本包)**

```java
/**
 * <h2>cn.code91.facility.id</h2>
 *
 * <p><b>Purpose:</b> Snowflake ID generation — static facade {@code IdUtil} (snow id +
 * UUID variants + id parsing), {@code SnowIdGenerator} (41+2+2+10 bit layout, clock-backwards
 * handling, test clock seam), and its configuration knobs {@code FacilityIdProperties}
 * (prefix {@code facility.id}; re-homed here from autoconfigure — C3 cycle break, spec §4.4).</p>
 *
 * <p><b>Entry classes:</b> {@code IdUtil}, {@code SnowIdGenerator}, {@code FacilityIdProperties}.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code IdUtil} resolves the Spring-managed
 * generator via {@code SpringContextHolder}, with non-latching DEFAULT fallback — RV2-06),
 * Spring Boot configuration-properties annotations, Jakarta validation annotations.</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityIdAutoConfiguration}
 * wires {@code SnowIdGenerator} from {@code FacilityIdProperties}), downstream application code.</p>
 */
package cn.code91.facility.id;
```

- [ ] **Step 6: 验证"绿"** Run mvn test → Expected: `BUILD SUCCESS`,`Tests run: 370`(347 + 23),0 失败

- [ ] **Step 7: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 id 簇并执行 C3 断环第一步——FacilityIdProperties 归位 id 包

配置前缀 beacon.facility.id → facility.id(D1);id 簇不再依赖 autoconfigure;
23 个迁移测试(含 RV2-06 非固化回退、ADR-0008 instance parse、时钟回拨)全绿。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 5: Id 装配迁移(FacilityIdAutoConfiguration + imports 注册)

**Files:**
- Create(迁移+编辑): `$DST\src\test\java\cn\code91\facility\autoconfigure\FacilityIdAutoConfigurationTest.java`(prefix 2 行)
- Create(迁移+编辑): `$DST\src\test\java\cn\code91\facility\autoconfigure\ConfigurationPropertiesBindingTest.java`(C3 二次替换 + web 裁剪 + prefix 4 行)
- Create(迁移+编辑): `$DST\src\main\java\cn\code91\facility\autoconfigure\FacilityIdAutoConfiguration.java`(C3 二次替换 + prefix)
- Create(新写): `$DST\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **不迁移**:`autoconfigure/package-info.java`(源文档描述六个装配,现阶段仅一个,照搬即漂移——P5 装配齐后重写)

**Interfaces:**
- Consumes: T4 的 `cn.code91.facility.id.FacilityIdProperties` / `SnowIdGenerator`
- Produces: classpath 自动装配 `SnowIdGenerator`(`facility.id.enabled` 开关,缺省开;`@ConditionalOnMissingBean` Seam);后续 phase 向 imports 文件逐行追加

- [ ] **Step 1: 迁移 2 个测试并编辑**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\autoconfigure\FacilityIdAutoConfigurationTest.java` → `$DST\src\test\java\cn\code91\facility\autoconfigure\FacilityIdAutoConfigurationTest.java`,然后 2 处编辑:
   - old: `            .withPropertyValues("beacon.facility.id.enabled=false")` → new: `            .withPropertyValues("facility.id.enabled=false")`
   - old: `            .withPropertyValues("beacon.facility.id.worker-id=2")` → new: `            .withPropertyValues("facility.id.worker-id=2")`
2. `$SRC\...\autoconfigure\ConfigurationPropertiesBindingTest.java` → 同布局,然后 **C3 二次替换命令**,再依次执行以下编辑。

Binding 编辑 1(web import 整行删)—— old:

```java
import cn.code91.facility.autoconfigure.properties.FacilityWebRepeatableRequestProperties;
```

new:(空串,即删除该行)

Binding 编辑 2 —— old:

```java
    @EnableConfigurationProperties({FacilityIdProperties.class, FacilityWebRepeatableRequestProperties.class})
```

new:

```java
    @EnableConfigurationProperties(FacilityIdProperties.class)
```

Binding 编辑 3(整个 web 用例删除,P6 迁回 web 侧)—— old:

```java

    @Test
    void repeatableRequestPropertiesParsesByteSize() {
        runner
            .withPropertyValues("beacon.facility.web.repeatable-request.max-body-bytes=20971520")
            .run(ctx -> {
                FacilityWebRepeatableRequestProperties p =
                    ctx.getBean(FacilityWebRepeatableRequestProperties.class);
                assertThat(p.getMaxBodyBytes()).isEqualTo(20_971_520L);
            });
    }
```

new:(空串,即删除整块)

Binding 编辑 4-7(prefix 4 处,均为唯一单行替换):
- old `"beacon.facility.id.worker-id=2",` → new `"facility.id.worker-id=2",`
- old `"beacon.facility.id.data-center-id=3",` → new `"facility.id.data-center-id=3",`
- old `"beacon.facility.id.clock-backwards-threshold-millis=10")` → new `"facility.id.clock-backwards-threshold-millis=10")`
- old `.withPropertyValues("beacon.facility.id.worker-id=4")` → new `.withPropertyValues("facility.id.worker-id=4")`

- [ ] **Step 2: 验证"红"** Run mvn test → Expected: `BUILD FAILURE`,`cannot find symbol: class FacilityIdAutoConfiguration`

- [ ] **Step 3: 迁移 FacilityIdAutoConfiguration(标准命令 + C3 二次替换 + prefix 编辑)+ 迁移 autoconfigure/package-info + 新写 imports 文件**

`$SRC\...\autoconfigure\FacilityIdAutoConfiguration.java` → `$DST\src\main\java\cn\code91\facility\autoconfigure\FacilityIdAutoConfiguration.java`,C3 二次替换后编辑:
- old: `@ConditionalOnProperty(prefix = "beacon.facility.id", name = "enabled", havingValue = "true", matchIfMissing = true)` → new: `@ConditionalOnProperty(prefix = "facility.id", name = "enabled", havingValue = "true", matchIfMissing = true)`

新写 `$DST\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
cn.code91.facility.autoconfigure.FacilityIdAutoConfiguration
```

- [ ] **Step 4: 验证"绿"** Run mvn test → Expected: `BUILD SUCCESS`,`Tests run: 376`(370 + 6),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 Id 装配(FacilityIdAutoConfiguration + AutoConfiguration.imports 注册)

prefix facility.id;Binding 测试裁剪 web 用例留 P6;6 个装配测试全绿。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 6: ArchUnit 追加规则(autoconfigure 单向)

**Files:**
- Modify: `$DST\src\test\java\cn\code91\facility\architecture\ArchitectureTest.java`

- [ ] **Step 1: 类收尾 `}` 前追加(noClasses import 已在 P2 加过,无需动 import 区)**

```java

    /**
     * C3(spec §4.4):装配层单向向下——业务包不得反向依赖 autoconfigure
     * (properties 各归其组件包后,该规则锁定归位成果)。
     */
    @ArchTest
    static final ArchRule autoconfigure_is_not_depended_on_by_main_packages =
            noClasses().that().resideOutsideOfPackage("cn.code91.facility.autoconfigure..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("cn.code91.facility.autoconfigure..");
```

- [ ] **Step 2: 验证(守护型,落地即绿;红则 C3 有漏,STOP)** Run mvn test → Expected: `BUILD SUCCESS`,`Tests run: 377`(376 + 1)

- [ ] **Step 3: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
test: ArchUnit 追加规则——autoconfigure 单向向下(锁定 C3 归位)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 7: spec §6 状态勘误(lang3 已执行)

**Files:**
- Modify: `$DST\docs\superpowers\specs\2026-07-02-server-facility-migration-design.md`

- [ ] **Step 1: 编辑** —— old:

```markdown
| commons-lang3 | compile | **移除**(P3 date rework 后) | 仅 `DateUtil` 一个文件使用 |
```

new:

```markdown
| commons-lang3 | compile | **已移除**(P3 执行:DateUtils 4 处调用 JDK 等价替换) | 仅 `DateUtil` 一个文件使用 |
```

- [ ] **Step 2: 全量回归 + Commit(PowerShell 工具)**

Run mvn test → Expected: `BUILD SUCCESS`,`Tests run: 377`

```powershell
git -C D:\Yiwer\code\server-facility add docs
git -C D:\Yiwer\code\server-facility commit -m @'
docs: spec §6 勘误——commons-lang3 移除已于 P3 执行

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## 验收清单(P3 出口)

- [ ] `mvn test` 全绿,377 个用例(P2 303 + date 11 + number 33 + id 23 + 装配 6 + arch 1),0 失败 0 跳过
- [ ] 主源码 `grep -r "org.apache.commons" src/main` 零命中(lang3 已去)
- [ ] `grep -r "ChineseNumbers" src` 零命中(drop 执行且无 dangling 引用)
- [ ] `grep -rn "beacon.facility" src` 零命中(前缀迁移完成;含 javadoc yaml 示例)
- [ ] id 簇零 `autoconfigure` import;ArchUnit 四规则绿(含新增 autoconfigure 单向)
- [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 存在且含 1 行
- [ ] date/number/id 三个 package-info 依赖声明与 import 实况一致
- [ ] git log:每任务独立 commit,提交信息无 `@` 包裹(P2 教训)
