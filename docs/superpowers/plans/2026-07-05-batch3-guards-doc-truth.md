# 批次 3 fix/guards-doc-truth 实施计划(F3+F13/F4/F5/F11/F12/F14/F15)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 守卫兑现 + doc-truth 清账七项:F3+F13 三簇构造器守卫兑现 ADR-0013 承诺(TokenBucketRateLimiter/InMemoryDistributedLock/InMemoryIdempotencyStore 的 `<=0` 参数 IllegalArgumentException);F4 Filenames.sanitize 按路径段检测穿越(`report..final.pdf` 不再误伤);F5 CopyUtil null key entry 丢弃补 WARN+javadoc(决策 a,用户确认);F11/F12 Collects/NullSafe 类注释如实收窄;F14 AccessLog 死配置处置(决策 a,用户确认:实现 slowThresholdMillis+删 logHeaders);F15 Cache ConcurrentMap 回退 WARN。

**Architecture:** 全部为局部修复:守卫对齐 SnowIdGenerator 先例(构造器 fail-fast,任何 classpath 下生效);F4 把 `contains("..")` 换成 cleanPath 后按 `/` 拆段等值判 `..`(攻击面不缩小——真穿越必以独立 `..` 段存在,cleanPath 已归一分隔符);F14/F15 消费既有 props 字段并在装配/拦截器路径补运维信号。

**Tech Stack:** Java 21、JUnit 5 + AssertJ、logback ListAppender(WARN 断言)、ApplicationContextRunner + FilteredClassLoader(cache 装配测试既有习语)。

**评审依据:** docs/superpowers/2026-07-05-whole-code-review-findings.md;决策已定:F5=a(drop+WARN+javadoc)、F14=a(实现 slow+删 logHeaders),用户确认 2026-07-05。

## Global Constraints

- 一切 Maven 命令带 `-o`;基线 **1159 绿**(master 4f3164e);gate 0.88/0.75 must met;ArchUnit 5/5。
- doc-truth 红线:javadoc 声称与代码逐条相符;计数用构建实数。
- TDD:RED/GREEN 原始 mvn 输出粘贴(拼凑=任务失败);既有测试**除 Task 4 明示的 logHeaders 相关外**零改动零回归(已核:无测试引用 logHeaders,预期零既有测试改动)。
- 只许改各 Task 点名文件;提交 `git commit -F <消息文件>`(UTF-8);零新依赖。
- WARN 消息不得含 MaskUtil SECRET 关键词(password/passwd/pwd/token/secret/api-key/authorization/access-token);本计划各 WARN 文案已预核安全,照抄勿改写。
- 测试内 ListAppender 必须 finally detach;禁真实 sleep(AccessLog 慢请求用回填 startTime 属性模拟)。

---

### Task 1: F3+F13 — 三簇构造器守卫兑现

**Files:**
- Modify: `src/main/java/cn/code91/facility/ratelimit/TokenBucketRateLimiter.java`(构造器)
- Modify: `src/main/java/cn/code91/facility/ratelimit/FacilityRateLimitProperties.java`(仅类 javadoc 一句)
- Modify: `src/main/java/cn/code91/facility/lock/InMemoryDistributedLock.java`(构造器)
- Modify: `src/main/java/cn/code91/facility/idempotency/InMemoryIdempotencyStore.java`(构造器)
- Test: `src/test/java/cn/code91/facility/ratelimit/TokenBucketRateLimiterTest.java`(追加)
- Test: `src/test/java/cn/code91/facility/lock/InMemoryDistributedLockTest.java`(追加)
- Test: `src/test/java/cn/code91/facility/idempotency/InMemoryIdempotencyStoreTest.java`(追加)

**Interfaces:**
- Produces: 三构造器对非法参数抛 `IllegalArgumentException`(消息含参数名与实际值);合法参数行为零变化。ADR-0013 无需改(兑现承诺即可)。

**背景:** ADR-0013 决策 2 明示「守卫由构造器兜底」(引 SnowIdGenerator 先例),三簇落空:`permitsPerSecond=0` 除零使 retryAfter=Long.MAX_VALUE(F3);`maxLocks=0` 现状是「每次调用都先 clear 再建」的荒谬行为;`maxEntries=0` 同理。properties 只有声明性「>0」不校验。

- [ ] **Step 1: 写失败测试(三个测试类各追加一组边界测试)**

`TokenBucketRateLimiterTest.java` 追加(沿用文件既有 import;如缺则补 `assertThatThrownBy`/`assertThatCode` 静态导入):

```java
    @Test
    @DisplayName("F3/F13:构造器守卫——capacity/permitsPerSecond/maxBuckets 非正数抛 IAE,合法最小值 1 通过")
    void constructorGuards_rejectNonPositive() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0, 10, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultCapacity");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, 0, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultPermitsPerSecond");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, -1.5, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultPermitsPerSecond");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, Double.NaN, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultPermitsPerSecond");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, 10, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxBuckets");
        assertThatCode(() -> new TokenBucketRateLimiter(1, 0.001, 1)).doesNotThrowAnyException();
    }
```

`InMemoryDistributedLockTest.java` 追加:

```java
    @Test
    @DisplayName("F13:构造器守卫——maxLocks 非正数抛 IAE(0 的旧行为是每次先 clear 再建,守卫后消除)")
    void constructorGuard_rejectsNonPositiveMaxLocks() {
        assertThatThrownBy(() -> new InMemoryDistributedLock(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxLocks");
        assertThatThrownBy(() -> new InMemoryDistributedLock(-5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxLocks");
        assertThatCode(() -> new InMemoryDistributedLock(1)).doesNotThrowAnyException();
    }
```

`InMemoryIdempotencyStoreTest.java` 追加:

```java
    @Test
    @DisplayName("F13:构造器守卫——maxEntries 非正数抛 IAE")
    void constructorGuard_rejectsNonPositiveMaxEntries() {
        assertThatThrownBy(() -> new InMemoryIdempotencyStore(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxEntries");
        assertThatThrownBy(() -> new InMemoryIdempotencyStore(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxEntries");
        assertThatCode(() -> new InMemoryIdempotencyStore(1)).doesNotThrowAnyException();
    }
```

- [ ] **Step 2: RED**

```
mvn -o test -Dtest=TokenBucketRateLimiterTest,InMemoryDistributedLockTest,InMemoryIdempotencyStoreTest
```

预期:三个新测试均失败(当前无守卫,构造成功→「期望异常未抛」);既有测试绿。粘贴原始输出。

- [ ] **Step 3: 实现守卫**

**3a. TokenBucketRateLimiter 构造器**(体首插入,原赋值保留):

```java
    public TokenBucketRateLimiter(long defaultCapacity, double defaultPermitsPerSecond, int maxBuckets) {
        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be > 0, got " + defaultCapacity);
        }
        if (!(defaultPermitsPerSecond > 0)) {   // 反向写法同时拦 NaN(与 NaN 的任何比较为 false)
            throw new IllegalArgumentException("defaultPermitsPerSecond must be > 0, got " + defaultPermitsPerSecond);
        }
        if (maxBuckets <= 0) {
            throw new IllegalArgumentException("maxBuckets must be > 0, got " + maxBuckets);
        }
        this.defaultCapacity = defaultCapacity;
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        this.maxBuckets = maxBuckets;
    }
```

构造器 javadoc(如无则补)注明:「参数范围守卫(F13/ADR-0013):非正数启动期快速失败,消除 permitsPerSecond=0 的除零→retryAfter=Long.MAX_VALUE(F3)」。

**3b. InMemoryDistributedLock 构造器**:

```java
    public InMemoryDistributedLock(int maxLocks) {
        if (maxLocks <= 0) {
            throw new IllegalArgumentException("maxLocks must be > 0, got " + maxLocks);
        }
        this.maxLocks = maxLocks;
    }
```

**3c. InMemoryIdempotencyStore 构造器**:

```java
    public InMemoryIdempotencyStore(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0, got " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }
```

**3d. FacilityRateLimitProperties 类 javadoc**——把
`真正的范围守卫留给具体限流实现(如 {@link TokenBucket} 对 capacity/rate 的运行期语义)。`
替换为:

```
真正的范围守卫在 {@link TokenBucketRateLimiter} 构造器兜底(启动期快速失败,任何 classpath 下生效——F13)。
```

**3e. 同型声称对齐检查**:`grep -n "范围守卫\|绑定不校验" src/main/java/cn/code91/facility/lock/*.java src/main/java/cn/code91/facility/idempotency/*.java` 及两簇 properties 文件——若有「守卫留给实现/运行期」类措辞与新构造器守卫矛盾,同步为「构造器兜底」;仅声明「绑定不校验(ADR-0013)」的保持原样(仍为真)。报告中列出 grep 命中与处置。

- [ ] **Step 4: GREEN**(同 Step 2 命令)

- [ ] **Step 5: 全量 `mvn -o test`**(预期 1159+3=1162,以实数为准)

- [ ] **Step 6: 提交**

```
git add <上述 7 文件>
git commit -F <消息文件>
```

消息:

```
fix: 三簇构造器守卫兑现 ADR-0013(F3/F13)

TokenBucketRateLimiter/InMemoryDistributedLock/InMemoryIdempotencyStore
构造器对非正参数抛 IllegalArgumentException(对齐 SnowIdGenerator 先例,
启动期快速失败)。消除 permitsPerSecond=0 除零(retryAfter=Long.MAX_VALUE,
F3)与 maxLocks=0「每次先清后建」荒谬态;NaN 经反向比较拦截。properties
javadoc 守卫指向如实化。
```

---

### Task 2: F4 — Filenames.sanitize 按路径段检测穿越(安全敏感)

**Files:**
- Modify: `src/main/java/cn/code91/facility/path/Filenames.java`(sanitize 方法)
- Test: `src/test/java/cn/code91/facility/path/FilenamesTest.java`(追加)

**Interfaces:**
- Produces: `sanitize("report..final.pdf")` → `ok("report..final.pdf")`;一切含独立 `..` 段的输入仍拒绝。签名与错误类型零变化。

**背景:** `cleaned.contains("..")` 过粗——文件名内连续点(非路径段)被误判穿越。**安全论证(审查者必须独立验证拦截面没有缩小)**:真实穿越必以独立 `..` **路径段**存在;`StringUtils.cleanPath` 已把 `\` 归一为 `/` 并折叠可解析的 `a/..` 序列,残留 `..` 只能以独立段存在(如首段);按段等值判定后,后续 basename 截取+不安全字符替换进一步兜底。行为变化仅是「名内双点不再误拒」——该集合恰为 F4 修复目标,非攻击向量。

- [ ] **Step 1: 失败测试**

`FilenamesTest.java` 追加:

```java
    @Test
    @DisplayName("F4:文件名内连续点不是穿越——report..final.pdf 等放行")
    void sanitize_doubleDotsWithinName_allowed() {
        assertThat(Filenames.sanitize("report..final.pdf").get()).isEqualTo("report..final.pdf");
        assertThat(Filenames.sanitize("..hidden").get()).isEqualTo("..hidden");
        assertThat(Filenames.sanitize("a..b.txt").get()).isEqualTo("a..b.txt");
    }

    @Test
    @DisplayName("F4:各种穿越形态仍全部拦截(拦截面不缩小)")
    void sanitize_traversalForms_stillRejected() {
        assertThat(Filenames.sanitize("../etc/passwd").isErr()).isTrue();
        assertThat(Filenames.sanitize("..\\evil.txt").isErr()).isTrue();
        assertThat(Filenames.sanitize("x/../../y.txt").isErr()).isTrue();   // cleanPath 折叠后残留首段 ..
        assertThat(Filenames.sanitize("..").isErr()).isTrue();
        assertThat(Filenames.sanitize("./..").isErr()).isTrue();
    }
```

注:若 `Result` 取值访问器非 `get()`/判错非 `isErr()`,以 `Filenames.java`/`Result.java` 实际 API 为准(既有 FilenamesTest 用法照抄),并在报告披露。

- [ ] **Step 2: RED**

```
mvn -o test -Dtest=FilenamesTest
```

预期:`sanitize_doubleDotsWithinName_allowed` 三断言全失败(旧 contains 误拒);`sanitize_traversalForms_stillRejected` 绿(锁定,旧新一致);既有测试绿。粘贴原始输出。

- [ ] **Step 3: 实现按段检测**

`Filenames.java` sanitize 中,把

```java
        String cleaned = StringUtils.cleanPath(fileName);
        if (cleaned.contains("..")) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_NAME_INVALID, null, new Object[]{fileName}));
        }
```

替换为:

```java
        String cleaned = StringUtils.cleanPath(fileName);
        // 按路径段检测穿越(F4):cleanPath 已归一 \ 为 / 并折叠可解析的 a/.. 序列,
        // 残留 .. 只能以独立段存在;段等值判定不误伤文件名内连续点(report..final.pdf)
        for (String segment : cleaned.split("/")) {
            if ("..".equals(segment)) {
                return Result.err(WrappedError.of(
                        FacilityErrorType.FILE_NAME_INVALID, null, new Object[]{fileName}));
            }
        }
```

方法 javadoc 的「检测 {@code ..}」改为「按路径段检测 {@code ..} 穿越(文件名内连续点不误伤,F4)」。

- [ ] **Step 4: GREEN**(同 Step 2)

- [ ] **Step 5: 全量 `mvn -o test`**(预期 +2)

- [ ] **Step 6: 提交**(消息:)

```
fix: Filenames.sanitize 按路径段检测穿越(F4)

contains("..") 过粗,把 report..final.pdf 等名内连续点误判为穿越。改为
cleanPath 后按 / 拆段等值判 ..——真穿越必以独立段存在(cleanPath 已归一
分隔符并折叠可解析序列),拦截面不缩小:../、..\、x/../../y、裸 ..、./..
全部仍拒(锁定测试);名内双点放行。
```

---

### Task 3: F5+F11+F12 — CopyUtil null key WARN + 两处类注释如实化

**Files:**
- Modify: `src/main/java/cn/code91/facility/copy/CopyUtil.java`(processMapEntry + 相关公共方法 javadoc + LogUtil import)
- Modify: `src/main/java/cn/code91/facility/common/Collects.java`(仅类 javadoc)
- Modify: `src/main/java/cn/code91/facility/common/NullSafe.java`(仅类 javadoc)
- Test: `src/test/java/cn/code91/facility/copy/CopyUtilTest.java`(追加)

**Interfaces:**
- Produces: F5(决策 a):null key entry 在 throwOnNullCopy=false 下仍丢弃(行为不变)但补 WARN;条目数语义测试锁定。F11/F12 纯 javadoc。

- [ ] **Step 1: 失败测试(F5)**

`CopyUtilTest.java` 追加(照该文件既有 CopyTrait 测试类型与 options 构造习语改造;下面骨架里的类型/工厂以文件实际为准并在报告披露):

```java
    @Test
    @DisplayName("F5:宽容模式下 null key entry 丢弃但必须 WARN(不再静默),条目数 3 进 2 出")
    void mapCopy_nullKeyEntry_droppedWithWarn() {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            // 构造含 null key 的 map(HashMap 允许 null key),throwOnNullCopy=false 宽容模式
            // <以 CopyUtilTest 既有 map 拷贝用例的类型与调用形态为准,3 个 entry 其一 key 为 null>
            // Map<K,V> source = ...; source.put(null, someValue);
            // Map<K,V> result = CopyUtil.<既有 map 拷贝入口>(source, <lenient options>);
            // assertThat(result).hasSize(2);
            assertThat(appender.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(e.getFormattedMessage()).contains("null map key entry dropped");
            });
        } finally {
            root.detachAppender(appender);
        }
    }
```

**注意**:上面注释掉的构造段是形态示意——实施者必须用 CopyUtilTest 既有 map 深拷贝用例的真实类型(CopyTrait 实现类)与真实公共入口改写为可编译代码,断言 `hasSize(2)` 与 WARN 两者都要;做不到(如找不到宽容模式 map 用例可仿)→ BLOCKED 汇报,不许自造类型体系。

- [ ] **Step 2: RED**(`mvn -o test -Dtest=CopyUtilTest`;新测失败于 anySatisfy 无 WARN;粘贴输出)

- [ ] **Step 3: 实现**

**3a. CopyUtil**:import `cn.code91.facility.log.LogUtil;`;processMapEntry 的 null key 分支:

```java
        if (key == null) {
            if (options.throwOnNullCopy) {
                throw new CopyException("Map key cannot be null");
            }
            // 决策 F5-a(2026-07-05):宽容模式保持丢弃语义(行为不变),但不再静默
            LogUtil.warn("[CopyUtil] null map key entry dropped (throwOnNullCopy=false)");
            return;
        }
```

**3b. 公共入口 javadoc**:`grep -n "processMapEntry" src/main/java/cn/code91/facility/copy/CopyUtil.java` 找到所有到达该私有方法的公共 map 拷贝 API,在其 javadoc 补一句:
「宽容模式(throwOnNullCopy=false)下,null key 的 entry 会被**丢弃并记 WARN**(F5,决策 a);严格模式抛 CopyException。」

**3c. Collects 类 javadoc**(F11)——替换第 17 行声称:

```java
 * <p>null 安全：null 输入视为空集合处理;除 {@code longListToLongArray}(null 入参返回
 * null,行为已被测试锁定)外,返回值不为 null。</p>
```

**3d. NullSafe 类 javadoc**(F12)——替换第 16 行声称:

```java
 * <p>null 安全:<b>数据</b>参数 null 不抛 NPE,集合返回方法不返回 null(返回空集合);
 * <b>函数型</b>参数除外——{@code computeOrElse} 的 {@code dataSupplier} 为 null 时
 * fail-fast 抛 NPE(全类唯一 requireNonNull,行为已被测试锁定)。</p>
```

(实施前 `grep -n "requireNonNull" NullSafe.java` 复核仍仅 computeOrElse 一处;若有出入按实况改写并披露。)

- [ ] **Step 4: GREEN** → **Step 5: 全量 `mvn -o test`**(预期 +1) → **Step 6: 提交**(消息:)

```
fix: CopyUtil null key entry 丢弃补 WARN(F5 决策 a)+ Collects/NullSafe 类注释如实收窄(F11/F12)

F5:宽容模式 null key entry 保持丢弃语义但不再静默(WARN+javadoc+条目数
3 进 2 出锁定)。F11:「返回值永远不为 null」与 longListToLongArray(null)
→null 矛盾,类注释收窄如实(行为已被测试锁定,不改行为)。F12:「永远不抛
NPE」与 computeOrElse 的 requireNonNull 矛盾,同收窄(标明函数参数 fail-fast)。
```

---

### Task 4: F14+F15 — AccessLog slow 实现+logHeaders 删除 + Cache 回退 WARN

**Files:**
- Modify: `src/main/java/cn/code91/facility/web/interceptor/AccessLogInterceptor.java`
- Modify: `src/main/java/cn/code91/facility/web/interceptor/FacilityWebAccessLogProperties.java`(删 logHeaders 字段)
- Modify: `src/main/java/cn/code91/facility/autoconfigure/FacilityCacheAutoConfiguration.java`(ConcurrentMap 分支 WARN)
- Create: `src/test/java/cn/code91/facility/web/interceptor/AccessLogInterceptorTest.java`
- Test: `src/test/java/cn/code91/facility/autoconfigure/FacilityCacheAutoConfigurationTest.java`(追加/增强 1 测)

**Interfaces:**
- Produces: F14(决策 a):`slow-threshold-millis`(默认 1000,>0 生效)超阈请求日志升 WARN 并追加 ` slow` 标记;`log-headers` 配置删除(轻 breaking,0.1.0 窗口,用户已裁)。F15:ConcurrentMap 回退分支装配期一次 WARN。
- Consumes: `LogUtil.warn/info`;AccessLogInterceptor 的 startTime 属性名 `"accessLog_startTime"`(私有常量,测试用字面量,与实现一致)。

**已核事实**:全测试源零处引用 logHeaders/log-headers——删除字段不碰任何既有测试。

- [ ] **Step 1: 失败测试**

新建 `AccessLogInterceptorTest.java`:

```java
package cn.code91.facility.web.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccessLogInterceptor - 访问日志与慢请求标记(F14 决策 a)")
class AccessLogInterceptorTest {

    /** 与 AccessLogInterceptor 私有常量一致 */
    private static final String ATTR_START_TIME = "accessLog_startTime";

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void attach() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
    }

    private AccessLogInterceptor interceptorWithThreshold(long thresholdMillis) {
        FacilityWebAccessLogProperties props = new FacilityWebAccessLogProperties();
        props.setSlowThresholdMillis(thresholdMillis);
        return new AccessLogInterceptor(props);
    }

    @Test
    @DisplayName("快请求:INFO 且无 slow 标记")
    void fastRequest_logsInfo_withoutSlowMarker() {
        AccessLogInterceptor interceptor = interceptorWithThreshold(1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fast");
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());   // 刚开始,耗时≈0
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).contains("/api/fast").doesNotContain("slow");
        });
    }

    @Test
    @DisplayName("慢请求(回填 startTime 模拟,零真实 sleep):WARN + slow 标记")
    void slowRequest_logsWarn_withSlowMarker() {
        AccessLogInterceptor interceptor = interceptorWithThreshold(1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/slow");
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis() - 5_000);   // 5s 前"开始"
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("/api/slow").contains("slow");
        });
    }

    @Test
    @DisplayName("阈值 0 = 禁用慢标记:超长耗时仍 INFO")
    void zeroThreshold_disablesSlowMarking() {
        AccessLogInterceptor interceptor = interceptorWithThreshold(0);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis() - 60_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).anySatisfy(e ->
                assertThat(e.getLevel()).isEqualTo(Level.INFO));
    }
}
```

`FacilityCacheAutoConfigurationTest.java`:找到既有「无 Caffeine 回退 ConcurrentMap」测试(FilteredClassLoader 隐藏 Caffeine 的那个),追加一个同构测试(或在其基础上复制改名)断言装配期 WARN:

```java
    @Test
    @DisplayName("F15:回退 ConcurrentMap 时装配期 WARN(default-ttl/maximum-size 被忽略的信号)")
    void concurrentMapFallback_emitsWarn() {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            <照既有回退测试的 ContextRunner + FilteredClassLoader 形态> .run(context -> {
                assertThat(context).hasSingleBean(org.springframework.cache.concurrent.ConcurrentMapCacheManager.class);
                assertThat(appender.list).anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                    assertThat(e.getFormattedMessage()).contains("ConcurrentMapCacheManager");
                });
            });
        } finally {
            root.detachAppender(appender);
        }
    }
```

(`<照既有…形态>` 段用该文件真实习语补全为可编译代码;做不到→BLOCKED。)

- [ ] **Step 2: RED**

```
mvn -o test -Dtest=AccessLogInterceptorTest,FacilityCacheAutoConfigurationTest
```

预期:slow 测与 zeroThreshold 测?——注意:旧代码一律 INFO,故 `slowRequest_logsWarn` 红、`fastRequest`/`zeroThreshold` 绿(锁定);cache WARN 测红;AccessLogInterceptorTest 若因 `setSlowThresholdMillis` 不存在而编译失败——不会,字段既有(死配置)。粘贴原始输出。

- [ ] **Step 3: 实现**

**3a. AccessLogInterceptor.afterCompletion 替换日志段**:

```java
        Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;
        String clientIp = RequestUtil.getClientIp(request);

        long slowThreshold = props.getSlowThresholdMillis();
        if (slowThreshold > 0 && duration >= slowThreshold) {
            LogUtil.warn("[ACCESS] {} {} {} {}ms {} slow",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, clientIp);
        } else {
            LogUtil.info("[ACCESS] {} {} {} {}ms {}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, clientIp);
        }
```

同时:props 字段上的 `@SuppressWarnings("unused") // stored for future use...` 注解与注释**删除**(props 已被消费);类 javadoc 补一句「耗时 ≥ {@code slow-threshold-millis}(>0 生效,默认 1000)时升 WARN 并追加 {@code slow} 标记;0 =禁用」。

**3b. FacilityWebAccessLogProperties**:删除 `private boolean logHeaders = false;` 行(决策 F14-a:实现成本高、日志泄漏风险大,0.1.0 窗口内移除);`slowThresholdMillis` javadoc 改为:

```java
    /** 慢请求阈值(毫秒):耗时 ≥ 该值的请求日志升 WARN 并标记 slow;0=禁用。(声明性约束:≥0;绑定不校验——ADR-0013) */
```

**3c. FacilityCacheAutoConfiguration.concurrentMapCacheManager**(import LogUtil):

```java
    public CacheManager concurrentMapCacheManager() {
        // F15:回退分支装配期一次性提示——Caffeine 独有的 TTL/容量配置在此后端不生效
        LogUtil.warn("facility.cache.default-ttl / maximum-size only apply to the Caffeine backend; "
                + "falling back to ConcurrentMapCacheManager, these properties are ignored");
        return new ConcurrentMapCacheManager();
    }
```

- [ ] **Step 4: GREEN** → **Step 5: 全量 `mvn -o test`**(预期 +4) → **Step 6: 提交**(消息:)

```
fix: AccessLog slow 阈值实现+logHeaders 删除(F14 决策 a)+ Cache 回退 WARN(F15)

F14:slowThresholdMillis 从死配置变为消费——耗时超阈升 WARN 并标记 slow
(0=禁用;回填 startTime 模拟慢请求,零真实 sleep);logHeaders 删除(实现
成本高+日志泄漏风险,0.1.0 窗口轻 breaking,用户已裁)。F15:ConcurrentMap
回退分支装配期 WARN(default-ttl/maximum-size 被忽略的运维信号)+装配测试
断言日志。props 的 @SuppressWarnings(unused) 随消费移除。
```

---

### Task 5: 收口 — 全量 verify + 三件套同步(控制器亲自)

**Files:** `README.md`、`docs/USAGE.md`、`docs/DESIGN.md`

**Steps:**
- [ ] `mvn -o clean verify` → 全门达标;取实测总数(预期 1159+10=1169,以实数为准)
- [ ] README L92 装配开关行:`访问日志拦截器:log-headers / slow-threshold-millis` → `访问日志拦截器:slow-threshold-millis(超阈升 WARN 标记 slow)`
- [ ] USAGE 装配开关表:删 `log-headers: false` 行;`slow-threshold-millis: 1000` 行尾补 `# 超阈升 WARN 并标记 slow;0=禁用(F14)`;cache 段(如有 default-ttl/maximum-size 行)补一句「仅 Caffeine 后端生效,ConcurrentMap 回退时忽略(启动 WARN,F15)」
- [ ] `grep -n "1159" README.md docs/DESIGN.md docs/USAGE.md` → 实数回填;`grep -rn "log-headers\|logHeaders" README.md docs/` → 零残留
- [ ] 提交:`docs: 批次 3 收口——计数同步 + 开关表 log-headers 移除与 slow/cache 注释`

---

## 验收(整分支)

1. `mvn -o clean verify` 全绿(≈1169)、gate met、analyze 零 warning、ArchUnit 5/5;
2. 行为验收:三构造器非正参数快速失败;report..final.pdf 放行且五种穿越形态全拒;null key 丢弃有 WARN 且条目数锁定;慢请求 WARN+slow;ConcurrentMap 回退装配 WARN;
3. doc-truth:Collects/NullSafe 类注释与行为一致;log-headers 全库零残留;ADR-0013 承诺兑现(ADR 本身不改);
4. 整分支 opus 终审(F4 拦截面不缩小须独立论证)→ merge --no-ff → 复验 → 删分支 → 台账/记忆。
