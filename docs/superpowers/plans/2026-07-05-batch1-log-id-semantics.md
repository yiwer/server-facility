# 批次 1 fix/log-id-semantics 实施计划(F1 + F2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复全库评审 P0 榜首两项功能 bug——F1(LogUtil 的 DEFAULT_LOGGER 门控使 per-package 日志级别失效,顺带 StackWalker 换全栈捕获)与 F2(SnowIdGenerator `throwOnClockBackwardsExceedThreshold=false` 在大幅回拨时仍抛,决策 a:无界等待直到追上)。

**Architecture:** 两处都是"承诺与行为矛盾"的语义修复:LogUtil 删除 9 个公共方法的 DEFAULT_LOGGER 早退预检,级别门控完全落在调用方 logger 上,并把 `getCallerClassName()` 从 `Thread.currentThread().getStackTrace()`(全栈快照)换成 `StackWalker`(惰性遍历,RETAIN_CLASS_REFERENCE)抵消热路径成本;SnowIdGenerator 把回拨分支重排为「true:超阈抛/阈内有界 spin(上限随阈值放宽)——false:一律无界等待追上,绝不抛」。

**Tech Stack:** Java 21(StackWalker / LockSupport,零新依赖)、JUnit 5 + AssertJ、logback ListAppender(既有测试模式)、注入时钟 Supplier<Long>(既有测试模式)。

**评审依据:** docs/superpowers/2026-07-05-whole-code-review-findings.md F1/F2;F2 决策已定 a(用户确认)。

## Global Constraints

- 一切 Maven 命令带 `-o`(离线);全量基线 **1147 绿**(1142 @Test + 5 @ArchTest,master 1cccf49)。
- JaCoCo gate:INSTRUCTION/LINE 0.88、BRANCH 0.75,`mvn -o clean verify` 必须 met;ArchUnit **5/5**。
- doc-truth 红线:一切文档声称(javadoc/README/DESIGN/USAGE/ADR)必须与代码实况逐条相符;计数用构建输出实数,禁止手数。
- TDD:先写测试,RED/GREEN 均为**原始 mvn 输出粘贴**(拼凑视为任务失败);行号与提交文件交叉核对。
- 只许改各 Task 点名的文件;修改其他文件 = 任务失败(发现计划盲点→停下汇报,不许自作主张)。
- 提交一律 `git commit -F <消息文件>`(PowerShell 5.1 会把含 ASCII 双引号的 -m 切成 pathspec);消息文件写 UTF-8。
- 静态状态毒化纪律:测试内操纵的任何 logger 级别(root 之外的)必须 finally 复位为 `null`(重新继承);root 级别由既有 @AfterEach 复原。
- 无新第三方依赖:StackWalker、LockSupport 均为 JDK。

---

### Task 1: F1 — LogUtil 级别门控改调用方 logger + StackWalker

**Files:**
- Modify: `src/main/java/cn/code91/facility/log/LogUtil.java`
- Test: `src/test/java/cn/code91/facility/log/LogUtilTest.java`

**Interfaces:**
- Consumes: 无(独立任务)。
- Produces: LogUtil 公共 API 签名零变化;行为变化 = per-package `logging.level` 配置对 LogUtil 通道生效。Task 3 的 ADR-0022 描述本任务的实现(STACK_WALKER 常量、getCallerClassName 惰性遍历)。

**背景(评审发现 F1):** 9 个公共方法首行 `if (!DEFAULT_LOGGER.isXxxEnabled()) return;`,`DEFAULT_LOGGER` 绑定 `cn.code91.facility.log.LogUtil` 自身(继承 root)。root=INFO 而业务配 `logging.level.com.myapp=DEBUG` 时,`LogUtil.debug()` 在第一道门短路——业务包 DEBUG 配置对 LogUtil 通道完全无效。既有测试只操纵 root,恰好测不到。

- [ ] **Step 1: 写两个失败测试(per-package 级别生效)**

在 `LogUtilTest.java` 中追加(放在 `loggerName_isCallerClass` 测试之后;`Logger` 已 import 为 `ch.qos.logback.classic.Logger`):

```java
@Test
@DisplayName("F1:root=WARN + 调用方 logger DEBUG → LogUtil.debug 必须产出事件(per-package 级别生效)")
void perPackageDebugLevel_effectiveThroughLogUtil_whenRootIsWarn() {
    Logger callerLogger = (Logger) LoggerFactory.getLogger(LogUtilTest.class);
    root.setLevel(ch.qos.logback.classic.Level.WARN);
    callerLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    try {
        LogUtil.debug("per-package gating {}", "works");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("per-package gating works");
        assertThat(appender.list.get(0).getLoggerName()).isEqualTo(LogUtilTest.class.getName());
    } finally {
        callerLogger.setLevel(null); // 复位为继承 root,防毒化
    }
}

@Test
@DisplayName("F1:root=WARN + 调用方 logger TRACE → LogUtil.trace 必须产出事件(修复须覆盖全部方法)")
void perPackageTraceLevel_effectiveThroughLogUtil_whenRootIsWarn() {
    Logger callerLogger = (Logger) LoggerFactory.getLogger(LogUtilTest.class);
    root.setLevel(ch.qos.logback.classic.Level.WARN);
    callerLogger.setLevel(ch.qos.logback.classic.Level.TRACE);
    try {
        LogUtil.trace("per-package trace {}", "works");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.TRACE);
    } finally {
        callerLogger.setLevel(null);
    }
}
```

说明:appender 挂在 root 上(@BeforeEach),logback additivity 会把 callerLogger 接受的事件传播到 root 的 appender——root 自身级别不再过滤已被子 logger 接受的事件,所以断言成立的唯一条件就是"门控看的是调用方 logger"。

- [ ] **Step 2: 跑测试确认 RED**

```
mvn -o test -Dtest=LogUtilTest
```

预期:恰好上述 2 个新测试失败,失败信息含 `Expected size: 1 but actual size: 0`(旧代码在 DEFAULT_LOGGER 预检处早退,appender 为空);其余既有测试全绿。报告中粘贴原始输出。

- [ ] **Step 3: 实现——删 9 处预检 + StackWalker**

对 `LogUtil.java` 做以下修改(公共 API 签名不变):

**3a. 删除全部 9 个公共方法的首行早退块**(trace / debug / info / warn(String,Object...) / warn(String,Throwable) / warn(String,Throwable,Object...) / error(String,Object...) / error(String,Throwable) / error(String,Throwable,Object...)):

```java
// 删除每个方法开头的这 3 行(isXxxEnabled 随方法级别变化):
if (!DEFAULT_LOGGER.isXxxEnabled()) {
    return;
}
```

删除后每个方法直接以 `final String callerClassName = getCallerClassName();` 开头,其余方法体不动。

**3b. 新增 STACK_WALKER 常量**(放在 DEFAULT_LOGGER 字段之后):

```java
/**
 * 惰性栈遍历器(JDK 保证线程安全,可静态共享);RETAIN_CLASS_REFERENCE 使帧携带
 * Class 引用,以引用比较精确跳过 LogUtil 自身帧(ADR-0022)。
 */
private static final StackWalker STACK_WALKER =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
```

**3c. 重写 getCallerClassName()**(替换整个方法,含 javadoc):

```java
/**
 * <b>获取调用者的类名</b>
 * <p>
 * 基于 {@link StackWalker} 惰性遍历:按 Class 引用跳过 LogUtil 自身帧,返回第一个
 * 外部调用方类名。相比 {@code Thread.currentThread().getStackTrace()} 的全栈快照,
 * 惰性遍历只实体化前几帧;反射帧默认隐藏,反射调用方也能正确解析(ADR-0022)。
 * </p>
 *
 * @return 调用者的完整类名
 */
private static String getCallerClassName() {
    return STACK_WALKER.walk(frames -> frames
            .filter(frame -> frame.getDeclaringClass() != LogUtil.class)
            .findFirst()
            .map(StackWalker.StackFrame::getClassName)
            // 防御回退:理论不可达(公共方法帧之外必有调用方)
            .orElse(LogUtil.class.getName()));
}
```

**3d. DEFAULT_LOGGER 字段 javadoc 如实化**(字段保留——doInvokePostHandler 的 catch 仍用它):

```java
/**
 * 内部兜底 Logger:仅用于 post handler 失败时的错误日志。
 * 不参与级别门控——门控完全基于调用方 logger(per-package 级别生效,ADR-0022)。
 */
private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(LogUtil.class);
```

**3e. 类 javadoc「重构改进」列表更新**——把
`<li><b>性能优化</b>：先检查日志级别再获取调用者信息</li>`
替换为:

```java
 *     <li><b>per-package 级别生效</b>:级别门控基于调用方 logger,业务包的
 *         {@code logging.level.*} 配置对 LogUtil 通道生效;调用方经 {@link StackWalker}
 *         惰性解析(ADR-0022)</li>
```

- [ ] **Step 4: 跑 LogUtilTest 确认 GREEN**

```
mvn -o test -Dtest=LogUtilTest
```

预期:全绿(既有约 32+ 测试 + 新 2 测)。特别注意既有 `loggerName_isCallerClass`、root=WARN/ERROR/OFF 抑制系列、TRACE/DEBUG 系列必须全绿——它们是 StackWalker 换栈与门控迁移的回归网。

- [ ] **Step 5: 全量测试**

```
mvn -o test
```

预期:BUILD SUCCESS,总数 = 1147 + 2 = 1149(以输出实数为准,写进报告)。

- [ ] **Step 6: 提交**

```
git add src/main/java/cn/code91/facility/log/LogUtil.java src/test/java/cn/code91/facility/log/LogUtilTest.java
git commit -F <消息文件>
```

消息文件内容(UTF-8):

```
fix: LogUtil 级别门控改调用方 logger + StackWalker 惰性解析(F1)

删除 9 个公共方法的 DEFAULT_LOGGER 早退预检——该预检绑定 LogUtil 自身 logger
(继承 root),使业务包 per-package logging.level 配置对 LogUtil 通道失效;
门控现完全落在调用方 logger。getCallerClassName 换 StackWalker
(RETAIN_CLASS_REFERENCE,惰性遍历+Class 引用比较)抵消去预检后的热路径成本。
测试:root=WARN + 调用方 DEBUG/TRACE 两向锁定 per-package 生效。
```

---

### Task 2: F2 — SnowIdGenerator 回拨语义(false = 无界等待,绝不抛)

**Files:**
- Modify: `src/main/java/cn/code91/facility/id/support/SnowIdGenerator.java`
- Modify: `src/main/java/cn/code91/facility/id/FacilityIdProperties.java`(仅两行 javadoc)
- Test: `src/test/java/cn/code91/facility/id/support/SnowIdGeneratorTest.java`

**Interfaces:**
- Consumes: 无(独立任务)。
- Produces: `nextId()` 签名不变;新私有方法 `awaitClockCatchUp(long target)`;`spinUntil` 上限改 `Math.max(SPIN_TIMEOUT_MILLIS, clockBackwardsThresholdMillis)`。Task 3 的 ADR-0023 描述本语义。

**背景(评审发现 F2,决策 a):** 用户显式配置「超阈值回拨不抛」(`throwOnClockBackwardsExceedThreshold=false`),但 delta>1s 时 `spinUntil` 的硬编码 1s 超时仍抛 `ClockBackwardsException`——配置承诺自相矛盾。决策 a:false 时无界等待直到时钟追上,绝不抛;文档如实记载无界等待风险。连带:`spinUntil` 的 1s 上限对「阈值配 >1s 且 true」同样违约(阈内回拨本应被吸收却在 1s 抛),上限改为随阈值放宽。

- [ ] **Step 1: 写三个测试(两红一锁)**

在 `SnowIdGeneratorTest.java` 追加(顶层,`InstanceParseMethodTests` 之前;文件已有 `AtomicLong`/`assertThatThrownBy` import,无需新增 import):

```java
@Test
@DisplayName("F2:throwOnExceedThreshold=false + 大幅回拨 2s → 无界等待追上,绝不抛(决策 a)")
void falseConfig_largeBackwards_waitsUntilCaughtUp_neverThrows() {
    FacilityIdProperties props = defaultProps();
    props.setThrowOnClockBackwardsExceedThreshold(false);

    // 步进时钟:首读 BASE_MS 建立 lastTimestamp;此后从回拨 2000ms 起每读前进 400ms,封顶 BASE_MS。
    // 旧实现 spinUntil 以注入时钟测流逝,>1000ms 即抛——本测试在旧代码上必红。
    AtomicLong reads = new AtomicLong();
    SnowIdGenerator gen = new SnowIdGenerator(props, () -> {
        long n = reads.getAndIncrement();
        if (n == 0) {
            return BASE_MS;
        }
        return Math.min(BASE_MS, BASE_MS - 2_000L + (n - 1) * 400L);
    });

    long first = gen.nextId();   // lastTimestamp = BASE_MS
    long second = gen.nextId();  // 回拨 2000ms → 等待追上 → 不抛

    assertThat(second).isGreaterThan(first);
    assertThat(gen.parseTimestamp(second)).isEqualTo(BASE_MS);
}

@Test
@DisplayName("F2:throwOnExceedThreshold=false + 阈值内回拨 → 同样等待追上,不抛(锁定,旧新行为一致)")
void falseConfig_smallBackwards_waitsUntilCaughtUp() {
    FacilityIdProperties props = defaultProps();
    props.setThrowOnClockBackwardsExceedThreshold(false);

    AtomicLong reads = new AtomicLong();
    SnowIdGenerator gen = new SnowIdGenerator(props, () -> {
        long n = reads.getAndIncrement();
        if (n == 0) {
            return BASE_MS;
        }
        return Math.min(BASE_MS, BASE_MS - 3L + (n - 1)); // 回拨 3ms(≤阈值 5),每读 +1ms
    });

    long first = gen.nextId();
    long second = gen.nextId();

    assertThat(second).isGreaterThan(first);
}

@Test
@DisplayName("F2 连带:true + 阈值 3s,阈内回拨 2.5s 的 spin 须越过旧 1s 硬上限追上(cap 随阈值放宽)")
void trueConfig_withinLargeThreshold_spinOutlastsLegacyOneSecondCap() {
    FacilityIdProperties props = defaultProps();
    props.setClockBackwardsThresholdMillis(3_000L);
    props.setThrowOnClockBackwardsExceedThreshold(true);

    AtomicLong reads = new AtomicLong();
    SnowIdGenerator gen = new SnowIdGenerator(props, () -> {
        long n = reads.getAndIncrement();
        if (n == 0) {
            return BASE_MS;
        }
        return Math.min(BASE_MS, BASE_MS - 2_500L + (n - 1) * 600L); // 回拨 2.5s ≤ 阈值 3s,每读 +600ms
    });

    long first = gen.nextId();
    long second = gen.nextId();  // 旧实现:spin 流逝 1200ms>1000 抛;新实现 cap=max(1000,3000) 追上

    assertThat(second).isGreaterThan(first);
}
```

注意:三个测试都用步进时钟模拟流逝,**零真实 sleep**;supplier 单调不减且封顶 BASE_MS,对实现读钟次数不敏感(不许把断言写成依赖精确读钟次数)。

- [ ] **Step 2: 跑测试确认 RED(1、3 红,2 绿)**

```
mvn -o test -Dtest=SnowIdGeneratorTest
```

预期:`falseConfig_largeBackwards_waitsUntilCaughtUp_neverThrows` 与 `trueConfig_withinLargeThreshold_spinOutlastsLegacyOneSecondCap` 以 `ClockBackwardsException` 失败;`falseConfig_smallBackwards_waitsUntilCaughtUp` 绿(锁定测试,旧新行为一致,如实记录);其余既有测试绿。粘贴原始输出。

- [ ] **Step 3: 实现**

**3a. nextId() 回拨分支重排**(替换 `if (now < lastTimestamp) {...}` 整块):

```java
        if (now < lastTimestamp) {
            long delta = lastTimestamp - now;
            if (throwOnExceedThreshold) {
                if (delta > clockBackwardsThresholdMillis) {
                    throw new ClockBackwardsException(delta);
                }
                now = spinUntil(lastTimestamp);
            } else {
                // throw-on-clock-backwards-exceed-threshold=false 的承诺是"不抛":
                // 无论回拨多大都等待时钟追上(无界,阻塞 ID 生成整个回拨时长;
                // 风险见 FacilityIdProperties javadoc 与 ADR-0023)
                now = awaitClockCatchUp(lastTimestamp);
            }
        }
```

**3b. spinUntil 上限随阈值放宽**(替换整个方法,含新 javadoc):

```java
    /**
     * 有界自旋(true 模式,阈值内回拨):上限取 {@link #SPIN_TIMEOUT_MILLIS} 与配置阈值的
     * 较大者,保证「≤ 阈值的回拨被吸收」的承诺对大于 1s 的阈值同样成立;流逝以注入时钟测量,
     * 病理时钟(自旋期间继续倒退)下超限抛出——true 模式允许抛。
     */
    private long spinUntil(long target) {
        long spinStart = clock.get();
        long cap = Math.max(SPIN_TIMEOUT_MILLIS, clockBackwardsThresholdMillis);
        long now = spinStart;
        while (now < target) {
            if (now - spinStart > cap) {
                throw new ClockBackwardsException(target - now);
            }
            now = clock.get();
        }
        return now;
    }
```

**3c. 新增 awaitClockCatchUp**(放在 spinUntil 之后):

```java
    /**
     * 无界等待(false 模式):以 1ms park 步进直到时钟追上,绝不抛出。
     * 等待发生在 synchronized 内——回拨期间本生成器的所有 nextId() 调用整体阻塞;
     * 中断不打断等待(park 被中断唤醒后循环重查,中断标志保留)。
     */
    private long awaitClockCatchUp(long target) {
        long now = clock.get();
        while (now < target) {
            LockSupport.parkNanos(1_000_000L); // 1ms;虚假/中断唤醒无害,循环重查
            now = clock.get();
        }
        return now;
    }
```

新增 import:`java.util.concurrent.locks.LockSupport`。

**3d. 类 javadoc 特性列表更新**——把
`<li>时钟回拨检测：小幅回拨自旋等待，大幅回拨抛出 {@link ClockBackwardsException}</li>`
替换为:

```java
 *     <li>时钟回拨:≤ 阈值自旋等待;超阈值时 throw-on-clock-backwards-exceed-threshold=true
 *         抛出 {@link ClockBackwardsException},false 则无界等待直到时钟追上——绝不抛,
 *         但阻塞 ID 生成整个回拨时长(ADR-0023)</li>
```

**3e. FacilityIdProperties 两行 javadoc 如实化**:

```java
    /** Clock-backwards ≤ this many ms is absorbed by bounded spin (cap = max(1s, threshold)). Above it, behavior follows throw-on-clock-backwards-exceed-threshold. Default 5. */
    @Min(0)
    private long clockBackwardsThresholdMillis = 5L;

    /** true: backwards above threshold throws ClockBackwardsException. false: NEVER throws — nextId() waits (unbounded, ~1ms park steps) until the clock catches up; ID generation blocks for the whole backwards span (ADR-0023). Default true. */
    private boolean throwOnClockBackwardsExceedThreshold = true;
```

- [ ] **Step 4: 跑 SnowIdGeneratorTest 确认 GREEN**

```
mvn -o test -Dtest=SnowIdGeneratorTest
```

预期:全绿(既有 9 测 + 新 3 测 = 12)。既有 `largeClockBackwardsThrows`(true+delta 100>5 立抛)与 `smallClockBackwardsSpinsAndCatchesUp`(true+delta 3 spin)是回归网,必须绿。

- [ ] **Step 5: 全量测试**

```
mvn -o test
```

预期:BUILD SUCCESS,总数 = Task 1 后基线 + 3(以输出实数为准,写进报告)。

- [ ] **Step 6: 提交**

```
git add src/main/java/cn/code91/facility/id/support/SnowIdGenerator.java src/main/java/cn/code91/facility/id/FacilityIdProperties.java src/test/java/cn/code91/facility/id/support/SnowIdGeneratorTest.java
git commit -F <消息文件>
```

消息文件内容:

```
fix: SnowIdGenerator 回拨语义忠实化——false 无界等待绝不抛(F2,决策 a)

throwOnClockBackwardsExceedThreshold=false 原在 delta>1s 时仍因 spinUntil
硬编码 1s 超时抛 ClockBackwardsException,配置承诺自相矛盾。重排回拨分支:
true=超阈立抛/阈内有界 spin(上限 max(1s,阈值),阈值>1s 不再违约);
false=awaitClockCatchUp 无界等待(1ms park 步进)追上,绝不抛。
注入步进时钟三测锁定(大回拨不抛/阈内不抛/大阈值 spin 越过旧 1s 上限)。
```

---

### Task 3: 文档收口 — ADR-0022/0023 + 计数与声称同步 + 全量 verify

**Files:**
- Create: `docs/adr/0022-logutil-caller-gating-stackwalker.md`
- Create: `docs/adr/0023-snowid-clock-backwards-nothrow-wait.md`
- Modify: `docs/adr/INDEX.md`(追加两行)
- Modify: `docs/DESIGN.md`(ADR 计数 21→23、ADR 表两行、测试计数)
- Modify: `README.md`(测试计数)
- Modify: `docs/USAGE.md`(装配开关表 `0..31`→`0..3` 两处勘误、`throw-on-clock-backwards-exceed-threshold` 行尾语义注释)

**Interfaces:**
- Consumes: Task 1/2 的实现与提交(ADR 描述其行为);全量 verify 输出的真实测试数。
- Produces: 文档三件套与 ADR 索引一致态。

- [ ] **Step 1: 写 ADR-0022**

`docs/adr/0022-logutil-caller-gating-stackwalker.md`(对齐 0012 的房屋格式):

```markdown
# ADR-0022: LogUtil 级别门控基于调用方 logger + StackWalker 惰性解析

- **状态**:Accepted(2026-07-05)
- **源起**:全库评审 F1(P0);P2 轮以来的「DEFAULT_LOGGER 门控 caveat」升格为修复

## 背景

LogUtil 全部 9 个公共方法首行以 `DEFAULT_LOGGER.isXxxEnabled()` 早退预检。
`DEFAULT_LOGGER` 绑定 `cn.code91.facility.log.LogUtil` 自身 logger(继承 root):
root=INFO 而业务配 `logging.level.com.myapp=DEBUG` 时,业务包经 LogUtil 的 DEBUG
调用在第一道门被短路——per-package 日志级别配置对 LogUtil 通道完全无效。
预检的本意是省去禁用级别下的调用方解析成本(当时用
`Thread.currentThread().getStackTrace()` 全栈快照,成本高)。

## 决策

1. **删除 DEFAULT_LOGGER 预检**,级别门控完全基于调用方 logger——per-package 配置生效,
   语义与直接持有 `LoggerFactory.getLogger(自身类)` 一致。DEFAULT_LOGGER 保留,仅作
   post handler 失败时的兜底错误日志。
2. **getCallerClassName 换 StackWalker**(`getInstance(RETAIN_CLASS_REFERENCE)` 静态共享
   实例):惰性遍历只实体化前几帧(LogUtil 帧 + 首个外部帧),以 Class 引用比较跳过自身帧,
   抵消"每次调用都解析 caller"的热路径成本;反射帧默认隐藏,反射调用方解析更准。
3. 性能不做测试断言(微基准不进单测);选型理由记于此与方法 javadoc。

## 后果

- 每次调用(含禁用级别)都解析调用方——成本从"全栈快照"降为"惰性 2~3 帧",
  换来 per-package 级别语义正确;
- 锁定测试:root=WARN + 调用方 DEBUG/TRACE → 事件必须产出(LogUtilTest 两向);
- 类 javadoc「先检查日志级别再获取调用者信息」的旧声称随之删除(doc-truth)。
```

- [ ] **Step 2: 写 ADR-0023**

`docs/adr/0023-snowid-clock-backwards-nothrow-wait.md`:

```markdown
# ADR-0023: SnowIdGenerator 回拨语义——false 无界等待绝不抛

- **状态**:Accepted(2026-07-05)
- **源起**:全库评审 F2(P0);决策 a(用户确认,2026-07-05)

## 背景

`facility.id.throw-on-clock-backwards-exceed-threshold=false` 承诺"超阈值回拨不抛",
但实现里 false 分支复用 `spinUntil`(硬编码 1s 超时),delta>1s 时仍抛
`ClockBackwardsException`——配置承诺自相矛盾;`false` 分支的大回拨路径无测试。
连带:`spinUntil` 的 1s 硬上限对「阈值配 >1s 且 true」同样违约(阈内回拨本应被吸收)。

## 决策

候选:a) false 时无界等待直到追上;b) 有界等待+特殊失败返回(nextId 返 long,签名不容,
等于删配置);c) 保留 1s 上限、文档收窄"false 仅对 ≤1s 生效"。**取 a(语义最忠实)**:

- true:超阈值立抛;阈值内有界 spin,上限 `max(1s, 阈值)`(阈值>1s 不再违约;
  病理时钟下超限抛出——true 模式允许抛);
- false:`awaitClockCatchUp` 无界等待(1ms `LockSupport.parkNanos` 步进),**绝不抛**。

## 后果(无界等待风险,如实记载)

- false 模式大幅回拨时,`nextId()` 阻塞整个回拨时长且不可被中断打断(中断标志保留);
  等待发生在 synchronized 内,本生成器所有调用方整体停顿——这是"不抛"承诺的代价,
  选 false 前须理解;
- 三测锁定(注入步进时钟,零真实 sleep):false+2s 回拨不抛、false+阈内不抛、
  true+3s 阈值下 2.5s 回拨越过旧 1s 上限;
- `waitForNextMillis`(同毫秒序列耗尽)本就无界等待,新语义与之一致。
```

- [ ] **Step 3: INDEX.md 追加两行**(表尾,格式对齐既有行):

```markdown
| [0022](0022-logutil-caller-gating-stackwalker.md) | Accepted | LogUtil 门控基于调用方 logger(per-package 生效)+ StackWalker 惰性解析 |
| [0023](0023-snowid-clock-backwards-nothrow-wait.md) | Accepted | SnowId 回拨:false 无界等待绝不抛;spin 上限随阈值放宽 |
```

- [ ] **Step 4: 全量 verify 取真实计数**

```
mvn -o clean verify
```

预期:BUILD SUCCESS、JaCoCo gate met(0.88/0.75)、dependency:analyze 零 warning、ArchUnit 5/5。从输出取 **真实测试总数**(Tests run 汇总行),预期 1147+5=1152,以实数为准。

- [ ] **Step 5: 计数与声称同步(grep 驱动,禁止手数)**

逐条执行并修正:

```
grep -n "1147" README.md docs/DESIGN.md docs/USAGE.md   # 全部替换为 Step 4 实数
grep -n "0\.\.31" docs/USAGE.md                          # 两处 → 0..3(worker-id/data-center-id 实为 2 bit,@Max(3))
grep -n "21 条\|21 ADR\|ADR.*21" README.md docs/DESIGN.md # ADR 计数 → 23
```

- `docs/DESIGN.md`:ADR 表(0021 行后)追加两行,措辞与 INDEX 一致:
  `| 0022 | LogUtil 门控基于调用方 logger(per-package 生效)+ StackWalker 惰性解析 |`
  `| 0023 | SnowId 回拨:false 无界等待绝不抛;spin 上限随阈值放宽 |`
- `docs/USAGE.md` 装配开关表:`throw-on-clock-backwards-exceed-threshold: true` 行尾加注释
  `# false=回拨不抛,无界等待追上(阻塞,ADR-0023)`;两处 `# 0..31` → `# 0..3`。
- README.md 若有 LogUtil 级别相关旧声称(grep `LogUtil` 逐行看)与新行为矛盾则如实修正;
  仅计数变化时只改计数。

- [ ] **Step 6: 提交**

```
git add docs/adr/0022-logutil-caller-gating-stackwalker.md docs/adr/0023-snowid-clock-backwards-nothrow-wait.md docs/adr/INDEX.md docs/DESIGN.md README.md docs/USAGE.md
git commit -F <消息文件>
```

消息文件内容:

```
docs: 批次 1 收口——ADR-0022/0023 + 计数同步 + USAGE 0..31 勘误

ADR-0022(LogUtil 调用方门控+StackWalker 选型)、ADR-0023(SnowId false
无界等待语义与风险);INDEX/DESIGN 表与计数(ADR 21→23,测试实数回填);
USAGE 装配开关表 worker-id/data-center-id 注释 0..31→0..3(实为 2 bit,
@Max(3),顺带 doc-truth 勘误)+ throw-on 行语义注释。
```

---

## 验收(整分支)

1. `mvn -o clean verify` 全绿:测试实数(≈1152)、JaCoCo gate 0.88/0.75 met、analyze 零 warning、ArchUnit 5/5;
2. 行为验收:root=WARN+包 DEBUG 经 LogUtil 产出事件;false+2s 回拨不抛;true 语义与既有测试零回归;
3. doc-truth:LogUtil/SnowIdGenerator/FacilityIdProperties javadoc、ADR×2、INDEX、DESIGN、README、USAGE 与代码实况逐条相符,计数为构建实数;
4. 整分支 opus 终审 → 修复(如有)→ merge --no-ff master → 合并结果复验 → 删分支 → 台账/记忆更新。
