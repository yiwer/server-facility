# server-facility P2 运行基座 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 迁移+重审运行基座簇 context/log/pattern/hash,执行 RV2-17 翻案(LogUtil 格式化对齐 SLF4J)与 logback 耦合消除(setLevel 零调用删除),四包补齐零测试盲区(81 个新行为测试)。

**Architecture:** 依赖拓扑:context(→error/result)与 pattern/hash 互不依赖;log 依赖 context。顺序:T1 依赖 → T2 context → T3 log 值类型/SPI → T4 LogUtil 迁移(含 setLevel 删除)→ T5 LogUtil rework(独立 commit,P0+P1 终审要求)→ T6 pattern → T7 hash → T8 ArchUnit 追加 → T9 ADR。每迁移任务 TDD:测试先落(红=编译失败)→ 源码 → 绿 → 提交。

**Tech Stack:** 同 P1;本阶段新增依赖:slf4j-api / spring-context / spring-beans / spring-core(compile,BOM 管版本)、logback-classic(**test**——spec §6 目标达成:主 classpath 零 logback)。

## Global Constraints

- 继承 P1 计划全部约束:源根 `D:\STELE\beacon\beacon-support\beacon-facility`(`$SRC`)、目标根 `D:\Yiwer\code\server-facility`(`$DST`)、包名映射 `cn.hbads.beacon.facility`→`cn.code91.facility`(ordinal)、UTF-8 无 BOM、每任务 `mvn test` 全绿才 commit、commit 尾行 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。
- **标准迁移命令**(同 P1):
  ```powershell
  $s = '<源绝对路径>'; $d = '<目标绝对路径>'
  New-Item -ItemType Directory -Force (Split-Path $d) | Out-Null
  $t = [IO.File]::ReadAllText($s, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText($d, $t.Replace('cn.hbads.beacon.facility', 'cn.code91.facility'), [Text.UTF8Encoding]::new($false))
  ```
- **迁移 commit 与 rework commit 分开**(P0+P1 终审裁定):T4(迁移+删除类改动)与 T5(行为 rework)必须是独立 commit。删除类改动(setLevel)允许在迁移 commit 内执行——P1 别名精简先例(ADR-0009 模式)。
- **测试计数纪律**(P1 教训):本计划新测试文件均为全新授权内容,`@Test` 数以本文代码块为准(T2:14 / T3:12 / T4:8 / T5:+4 / T6:36 / T7:10 / T8:+1 规则);任务出口给出的期望总数由此推导:**T2 后 232 → T3 后 244 → T4 后 252 → T5 后 256 → T6 后 292 → T7 后 302 → T8 后 303**。若实测不符,STOP 报 BLOCKED——先查计数再查代码。
  (勘误:T5 执行时按审查发现追加了第 4 个守卫用例与三参 arrayFormat 修正,见 T5 末尾勘误块。)
- **禁止事项**:不迁移 autoconfigure(P3 起);LogUtil 的 `setLevel` 不迁移(T4 删除,ADR-0011);不改 P1 已交付文件(ArchitectureTest 按 T8 指定块追加除外)。

---

### Task 1: P2 依赖落 pom

**Files:**
- Modify: `D:\Yiwer\code\server-facility\pom.xml`
- Create: `D:\Yiwer\code\server-facility\src\test\resources\logback-test.xml`

**Interfaces:**
- Consumes: P1 的 pom(BOM 已 import)
- Produces: compile classpath 含 slf4j-api/spring-context/spring-beans/spring-core;test classpath 含 logback-classic(供 ListAppender 断言与 SLF4J provider);测试期日志静音配置(输出纯净)

- [ ] **Step 1: 在 pom.xml 依赖区插入 P2 依赖**

对 `pom.xml` 执行精确替换——old:

```xml
        <!-- ===== test ===== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

new:

```xml
        <!-- ===== P2 运行基座簇所需 ===== -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-beans</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
        </dependency>

        <!-- ===== test ===== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>test</scope>
        </dependency>
```

> 与 spec §6 的偏差说明(记录在案):spec 表以 spring-boot-autoconfigure(compile)覆盖 Spring 依赖;
> 本计划按 `dependency:analyze` 清洁原则改为"直接 import 什么就声明什么"
> (context 簇 import spring-context/spring-beans 类,log 簇 import spring-core 的 Ordered/CollectionUtils)。
> spring-boot-autoconfigure 推迟到 P3(首个 AutoConfiguration 迁入)再声明。
> logback-classic 落 test 即 spec §6"目标:降 test"的达成。

- [ ] **Step 2: 创建 src/test/resources/logback-test.xml(测试输出静音;ListAppender 为程序化挂载,不受影响)**

```xml
<configuration>
    <appender name="NOP" class="ch.qos.logback.core.helpers.NOPAppender"/>
    <root level="INFO">
        <appender-ref ref="NOP"/>
    </root>
</configuration>
```

- [ ] **Step 3: 验证构建与依赖解析**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 218`(P1 基线不受影响)

- [ ] **Step 4: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add pom.xml src\test\resources
git -C D:\Yiwer\code\server-facility commit -m @'
build: P2 依赖(slf4j-api/spring-context+beans+core compile;logback-classic test)

含 logback-test.xml 测试静音配置(NOPAppender)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 2: context 簇迁移(SpringContextHolder)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\context\SpringContextHolderTest.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\context\SpringContextHolder.java`
- Create(全新内容): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\context\package-info.java`

**Interfaces:**
- Consumes: P1 的 `Result`/`WrappedError`/`FacilityErrorType`;T1 的 spring-context
- Produces(log/id/locale 等后续簇依赖):`SpringContextHolder.getBean(Class<T>)` / `getBean(String,Class<T>)` / `getBean(String)`(均返回 `Result<T,WrappedError>`)、`isInitialized()/isNotInitialized()`、`getApplicationContext()`、`containsBean(String)`、`getBeanNamesForType(Class<?>)`、`setApplicationContextManually(ApplicationContext)`、package-private `clear()`(测试用)、`getContextInfo()`

- [ ] **Step 1: 新写 SpringContextHolderTest.java(14 用例)**

```java
package cn.code91.facility.context;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpringContextHolder - Spring 上下文静态门面")
class SpringContextHolderTest {

    /**
     * 前后双向复位:LogUtilTest(log 包)无法调用本包私有 clear(),
     * 只能把 holder 置为空上下文——若其先于本类运行,@BeforeEach 兜底保证"未初始化"用例成立。
     */
    @BeforeEach
    @AfterEach
    void resetHolder() {
        SpringContextHolder.clear();
    }

    private static StaticApplicationContext contextWithBean() {
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.registerSingleton("sampleBean", StringBuilder.class);
        ctx.refresh();
        return ctx;
    }

    @Nested
    @DisplayName("未初始化状态")
    class NotInitialized {

        @Test
        void isNotInitialized_beforeInjection_true() {
            assertThat(SpringContextHolder.isNotInitialized()).isTrue();
            assertThat(SpringContextHolder.isInitialized()).isFalse();
            assertThat(SpringContextHolder.getApplicationContext()).isNull();
        }

        @Test
        void getBeanByType_notInitialized_returnsNotInitializedErr() {
            var result = SpringContextHolder.getBean(StringBuilder.class);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isTrue();
        }

        @Test
        void getBeanByNameAndType_notInitialized_returnsNotInitializedErr() {
            var result = SpringContextHolder.getBean("sampleBean", StringBuilder.class);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isTrue();
        }

        @Test
        void getBeanByName_notInitialized_returnsNotInitializedErr() {
            var result = SpringContextHolder.getBean("sampleBean");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isTrue();
        }

        @Test
        void containsBean_notInitialized_false() {
            assertThat(SpringContextHolder.containsBean("sampleBean")).isFalse();
        }

        @Test
        void getBeanNamesForType_notInitialized_emptyArray() {
            assertThat(SpringContextHolder.getBeanNamesForType(StringBuilder.class)).isEmpty();
        }

        @Test
        void getContextInfo_notInitialized_saysSo() {
            assertThat(SpringContextHolder.getContextInfo()).contains("not initialized");
        }
    }

    @Nested
    @DisplayName("已初始化状态")
    class Initialized {

        @Test
        void getBeanByType_present_returnsOk() {
            SpringContextHolder.setApplicationContextManually(contextWithBean());
            var result = SpringContextHolder.getBean(StringBuilder.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isInstanceOf(StringBuilder.class);
        }

        @Test
        void getBeanByNameAndType_present_returnsOk() {
            SpringContextHolder.setApplicationContextManually(contextWithBean());
            var result = SpringContextHolder.getBean("sampleBean", StringBuilder.class);
            assertThat(result.isOk()).isTrue();
        }

        @Test
        void getBeanByType_missing_returnsGetBeanErrWithTypeArg() {
            SpringContextHolder.setApplicationContextManually(contextWithBean());
            var result = SpringContextHolder.getBean(java.util.concurrent.ExecutorService.class);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_GET_BEAN_ERROR)).isTrue();
            assertThat(result.getErr().getArg(0, String.class))
                    .isEqualTo("java.util.concurrent.ExecutorService");
        }

        @Test
        void containsBean_andBeanNamesForType_reflectRegistry() {
            SpringContextHolder.setApplicationContextManually(contextWithBean());
            assertThat(SpringContextHolder.containsBean("sampleBean")).isTrue();
            assertThat(SpringContextHolder.containsBean("absent")).isFalse();
            assertThat(SpringContextHolder.getBeanNamesForType(StringBuilder.class))
                    .containsExactly("sampleBean");
        }

        @Test
        void getContextInfo_initialized_containsBeanCount() {
            SpringContextHolder.setApplicationContextManually(contextWithBean());
            assertThat(SpringContextHolder.getContextInfo()).contains("beanCount");
        }
    }

    @Nested
    @DisplayName("生命周期与 CAS")
    class Lifecycle {

        @Test
        void setApplicationContext_duplicateInjection_keepsFirst() {
            StaticApplicationContext first = contextWithBean();
            StaticApplicationContext second = new StaticApplicationContext();
            second.refresh();

            SpringContextHolder holder = new SpringContextHolder();
            holder.setApplicationContext(first);
            holder.setApplicationContext(second);

            assertThat(SpringContextHolder.getApplicationContext()).isSameAs(first);
        }

        @Test
        void destroy_clearsContext() {
            SpringContextHolder holder = new SpringContextHolder();
            holder.setApplicationContext(contextWithBean());
            assertThat(SpringContextHolder.isInitialized()).isTrue();

            holder.destroy();
            assertThat(SpringContextHolder.isNotInitialized()).isTrue();
        }
    }
}
```

- [ ] **Step 2: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,`cannot find symbol: class SpringContextHolder`

- [ ] **Step 3: 标准迁移命令复制 SpringContextHolder.java;写入新 package-info.java**

复制:`$SRC\src\main\java\cn\hbads\beacon\facility\context\SpringContextHolder.java` → `$DST\src\main\java\cn\code91\facility\context\SpringContextHolder.java`

写入 package-info.java(全新内容——源文件"Depends on: nothing"失真,实际 import error/result,按 P1 教训据实重写):

```java
/**
 * <h2>cn.code91.facility.context</h2>
 *
 * <p><b>Purpose:</b> Static handle to the Spring {@code ApplicationContext}, enabling
 * programmatic bean lookup outside of injection-managed components. Lookups return
 * {@code Result} instead of throwing.</p>
 *
 * <p><b>Entry classes:</b> {@code SpringContextHolder}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (lookup failures surface as
 * {@code Result<T, WrappedError>}), Spring context/beans, SLF4J.</p>
 *
 * <p><b>Depended on by:</b> {@code log} ({@code LogUtil} resolves the post-handler
 * composite), {@code locale} and {@code id} (arriving in later phases),
 * {@code autoconfigure} (registers the holder as an {@code ApplicationContextAware} bean).</p>
 */
package cn.code91.facility.context;
```

- [ ] **Step 4: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 232`(218 + 14),0 失败

- [ ] **Step 5: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 context 簇(SpringContextHolder),补 14 个行为测试(原零测试盲区)

package-info 据实重写(源文件"Depends on: nothing"失真,实际依赖 error/result)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 3: log 簇值类型与 SPI(LogContext / LogPostHandler / LogPostHandlerComposite)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\log\LogContextTest.java`
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\log\LogPostHandlerCompositeTest.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\log\LogContext.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\log\LogPostHandler.java`
- Create(迁移): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\log\LogPostHandlerComposite.java`

**Interfaces:**
- Consumes: T1 的 spring-core(`Ordered`/`CollectionUtils`)、slf4j-api
- Produces(T4 的 LogUtil 依赖):`LogContext.builder().message(String).level(Level).callerClassName(String).throwable(Throwable).build()`;getter `getMessage/getLevel/getCallerClassName/getThrowable/getThreadName/getTimestamp`、`hasThrowable()`、`getStackTrace(int)`;`interface LogPostHandler extends Ordered { void handle(LogContext); default int getOrder() }`;`LogPostHandlerComposite(List<LogPostHandler>)` + `handle`/`getOrder`/`getHandlerCount`

- [ ] **Step 1: 新写 LogContextTest.java(6 用例)**

```java
package cn.code91.facility.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogContext - 日志上下文值对象")
class LogContextTest {

    @Test
    void builder_populatesAllExplicitFields() {
        RuntimeException boom = new RuntimeException("boom");
        LocalDateTime ts = LocalDateTime.of(2026, 7, 2, 12, 0);
        LogContext ctx = LogContext.builder()
                .message("m").level(Level.WARN).callerClassName("com.example.A")
                .throwable(boom).threadName("t-1").timestamp(ts)
                .build();
        assertThat(ctx.getMessage()).isEqualTo("m");
        assertThat(ctx.getLevel()).isEqualTo(Level.WARN);
        assertThat(ctx.getCallerClassName()).isEqualTo("com.example.A");
        assertThat(ctx.getThrowable()).isSameAs(boom);
        assertThat(ctx.getThreadName()).isEqualTo("t-1");
        assertThat(ctx.getTimestamp()).isEqualTo(ts);
    }

    @Test
    void builder_defaultsThreadNameAndTimestamp() {
        LogContext ctx = LogContext.builder().message("m").level(Level.INFO).build();
        assertThat(ctx.getThreadName()).isEqualTo(Thread.currentThread().getName());
        assertThat(ctx.getTimestamp()).isNotNull();
    }

    @Test
    void hasThrowable_reflectsPresence() {
        assertThat(LogContext.builder().build().hasThrowable()).isFalse();
        assertThat(LogContext.builder().throwable(new Exception()).build().hasThrowable()).isTrue();
    }

    @Test
    void getStackTrace_noThrowable_returnsEmpty() {
        assertThat(LogContext.builder().build().getStackTrace(5)).isEmpty();
    }

    @Test
    void getStackTrace_limitsLinesAndIncludesHeader() {
        LogContext ctx = LogContext.builder().throwable(new IllegalStateException("bad")).build();
        String trace = ctx.getStackTrace(2);
        assertThat(trace).startsWith("java.lang.IllegalStateException: bad");
        assertThat(trace.lines().filter(l -> l.startsWith("\tat "))).hasSize(2);
    }

    @Test
    void toString_containsLevelCallerAndMessage() {
        LogContext ctx = LogContext.builder()
                .message("hello").level(Level.ERROR).callerClassName("X").build();
        assertThat(ctx.toString()).contains("ERROR").contains("X").contains("hello");
    }
}
```

- [ ] **Step 2: 新写 LogPostHandlerCompositeTest.java(6 用例)**

```java
package cn.code91.facility.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogPostHandlerComposite - 后处理器组合器")
class LogPostHandlerCompositeTest {

    private record Recording(String name, List<String> sink, int order) implements LogPostHandler {
        @Override
        public void handle(LogContext context) {
            sink.add(name);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @Test
    void nullList_yieldsZeroHandlers_andHandleIsNoOp() {
        LogPostHandlerComposite composite = new LogPostHandlerComposite(null);
        assertThat(composite.getHandlerCount()).isZero();
        composite.handle(LogContext.builder().message("m").build());
    }

    @Test
    void emptyList_yieldsZeroHandlers() {
        assertThat(new LogPostHandlerComposite(List.of()).getHandlerCount()).isZero();
    }

    @Test
    void handlers_invokedInOrderPriority() {
        List<String> sink = new ArrayList<>();
        LogPostHandlerComposite composite = new LogPostHandlerComposite(List.of(
                new Recording("low", sink, 100),
                new Recording("high", sink, -100)
        ));
        composite.handle(LogContext.builder().message("m").build());
        assertThat(sink).containsExactly("high", "low");
    }

    @Test
    void nestedComposite_filteredOut() {
        LogPostHandlerComposite inner = new LogPostHandlerComposite(List.of());
        LogPostHandlerComposite outer = new LogPostHandlerComposite(List.of(
                inner, new Recording("h", new ArrayList<>(), 0)
        ));
        assertThat(outer.getHandlerCount()).isEqualTo(1);
    }

    @Test
    void throwingHandler_doesNotBlockOthers() {
        List<String> sink = new ArrayList<>();
        LogPostHandler throwing = context -> { throw new IllegalStateException("boom"); };
        LogPostHandlerComposite composite = new LogPostHandlerComposite(List.of(
                throwing, new Recording("survivor", sink, Ordered.LOWEST_PRECEDENCE)
        ));
        composite.handle(LogContext.builder().message("m").build());
        assertThat(sink).containsExactly("survivor");
    }

    @Test
    void compositeOrder_isHighestPrecedence() {
        assertThat(new LogPostHandlerComposite(List.of()).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
```

- [ ] **Step 3: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,`cannot find symbol`(LogContext / LogPostHandler)

- [ ] **Step 4: 标准迁移命令复制 3 个源文件(无内容 rework;log/package-info 留待 T4 随 LogUtil 重写)**

1. `$SRC\src\main\java\cn\hbads\beacon\facility\log\LogContext.java` → `$DST\src\main\java\cn\code91\facility\log\LogContext.java`
2. `$SRC\src\main\java\cn\hbads\beacon\facility\log\LogPostHandler.java` → `$DST\src\main\java\cn\code91\facility\log\LogPostHandler.java`
3. `$SRC\src\main\java\cn\hbads\beacon\facility\log\LogPostHandlerComposite.java` → `$DST\src\main\java\cn\code91\facility\log\LogPostHandlerComposite.java`

- [ ] **Step 5: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 244`(232 + 12),0 失败

- [ ] **Step 6: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 log 簇值类型与 SPI(LogContext/LogPostHandler/Composite),补 12 个行为测试

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 4: LogUtil 迁移(含 setLevel 删除,ADR-0011)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\log\LogUtilTest.java`
- Create(迁移+删除编辑): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\log\LogUtil.java`
- Create(全新内容): `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\log\package-info.java`

**Interfaces:**
- Consumes: T2 的 `SpringContextHolder`、T3 的 log 值类型、slf4j-api;测试用 logback ListAppender(test scope)
- Produces:`LogUtil.trace/debug/info/warn/error(String, Object...)`、`warn/error(String, Throwable)`、`warn/error(String, Throwable, Object...)`、`clearHandlerCache()`、`clearLoggerCache()`
- **已删除(勿引用)**:`setLevel(Class<?>, Level)`——全仓零调用,且是主源码唯一 logback 触点(ADR-0011)

- [ ] **Step 1: 新写 LogUtilTest.java(8 用例;仅断言迁移前后不变的行为——`{}` 转义/数组等差异用例在 T5 补)**

```java
package cn.code91.facility.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.code91.facility.context.SpringContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.context.support.StaticApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogUtil - 日志静态门面")
class LogUtilTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAndReset() {
        root.detachAppender(appender);
        LogUtil.clearHandlerCache();
        LogUtil.clearLoggerCache();
        springClear();
    }

    private static void springClear() {
        // clear() 是 context 包私有(RP-12),本测试在 log 包不可见;
        // 用公开 API 把 holder 置为空上下文,防止本类注册的 composite 泄漏到其他测试;
        // SpringContextHolderTest 侧有 @BeforeEach clear() 兜底跨类运行顺序污染。
        StaticApplicationContext empty = new StaticApplicationContext();
        empty.refresh();
        SpringContextHolder.setApplicationContextManually(empty);
    }

    private ILoggingEvent lastEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        return events.get(events.size() - 1);
    }

    @Test
    void info_substitutesPlaceholdersInOrder() {
        LogUtil.info("user {} did {}", "alice", "login");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("user alice did login");
    }

    @Test
    void info_nullArg_rendersNullLiteral() {
        LogUtil.info("value={}", (Object) null);
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("value=null");
    }

    @Test
    void info_extraArgs_ignored() {
        LogUtil.info("only {}", "one", "two");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("only one");
    }

    @Test
    void info_fewerArgs_leavesPlaceholder() {
        LogUtil.info("{} and {}", "first");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("first and {}");
    }

    @Test
    void loggerName_isCallerClass() {
        LogUtil.info("caller check");
        assertThat(lastEvent().getLoggerName()).isEqualTo(LogUtilTest.class.getName());
    }

    @Test
    void warn_withThrowable_capturesStackTrace() {
        LogUtil.warn("failed", new IllegalStateException("boom"));
        ILoggingEvent event = lastEvent();
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void error_withThrowableAndArgs_formatsAndCaptures() {
        LogUtil.error("order {} failed", new RuntimeException("x"), "42");
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("order 42 failed");
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void postHandler_receivesContext_fromSpringWiring() {
        List<LogContext> received = new ArrayList<>();
        LogPostHandler probe = received::add;
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.refresh();
        ctx.getBeanFactory().registerSingleton(
                "composite", new LogPostHandlerComposite(List.of(probe)));
        SpringContextHolder.setApplicationContextManually(ctx);
        LogUtil.clearHandlerCache();

        LogUtil.warn("handler {} test", "wiring");

        assertThat(received).hasSize(1);
        LogContext logCtx = received.get(0);
        assertThat(logCtx.getMessage()).isEqualTo("handler wiring test");
        assertThat(logCtx.getLevel()).isEqualTo(Level.WARN);
        assertThat(logCtx.getCallerClassName()).isEqualTo(LogUtilTest.class.getName());
    }
}
```

- [ ] **Step 2: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,`cannot find symbol: variable LogUtil`

- [ ] **Step 3: 标准迁移命令复制 LogUtil.java,随后删除 setLevel(两处编辑)**

复制:`$SRC\src\main\java\cn\hbads\beacon\facility\log\LogUtil.java` → `$DST\src\main\java\cn\code91\facility\log\LogUtil.java`

编辑 1(删 logback import)—— old:

```java
import ch.qos.logback.classic.LoggerContext;
import cn.code91.facility.context.SpringContextHolder;
```

new:

```java
import cn.code91.facility.context.SpringContextHolder;
```

编辑 2(删 setLevel 方法及其分节注释)—— old:

```java
    // ==================== 日志级别设置 ====================

    /**
     * <b>动态设置指定类的日志级别</b>
     *
     * @param clazz {@link Class} 需要设置日志级别的类
     * @param level {@link Level} 目标日志级别
     */
    public static void setLevel(Class<?> clazz, Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(clazz);
        logger.setLevel(ch.qos.logback.classic.Level.convertAnSLF4JLevel(level));
    }

    // ==================== 日志输出方法 ====================
```

new:

```java
    // ==================== 日志输出方法 ====================
```

- [ ] **Step 4: 写入 log/package-info.java(全新内容——源文件"Depends on: nothing"与"MDC helpers"均失真)**

```java
/**
 * <h2>cn.code91.facility.log</h2>
 *
 * <p><b>Purpose:</b> Unified SLF4J logging facade ({@code LogUtil}) with caller-class
 * detection, a log-event value object ({@code LogContext}), and a composable
 * post-handler chain ({@code LogPostHandler} SPI + {@code LogPostHandlerComposite})
 * for structured log enrichment.</p>
 *
 * <p><b>Entry classes:</b> {@code LogUtil}, {@code LogContext}, {@code LogPostHandler}
 * (SPI), {@code LogPostHandlerComposite}.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code LogUtil} resolves the composite bean via
 * {@code SpringContextHolder}), SLF4J API, Spring core ({@code Ordered}). No logging
 * implementation dependency — logback was removed with {@code setLevel} (ADR-0011).</p>
 *
 * <p><b>Depended on by:</b> {@code json} / {@code io} / {@code web} (arriving in later
 * phases), {@code autoconfigure} (wires {@code LogPostHandlerComposite} bean).</p>
 */
package cn.code91.facility.log;
```

- [ ] **Step 5: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 252`(244 + 8),0 失败

- [ ] **Step 6: Commit(迁移 commit——rework 在 T5 独立提交)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 LogUtil 并删除 setLevel(全仓零调用,主源码唯一 logback 触点,ADR-0011)

补 8 个行为测试(经 logback ListAppender 断言真实输出);
logback-classic 自此仅存在于 test classpath;formatMessage rework 见下一提交。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 5: LogUtil formatMessage rework(RV2-17 翻案,ADR-0012;独立 commit)

**Files:**
- Modify: `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\log\LogUtilTest.java`(追加 3 用例)
- Modify: `D:\Yiwer\code\server-facility\src\main\java\cn\code91\facility\log\LogUtil.java`(formatMessage 换 MessageFormatter)

**Interfaces:**
- Consumes: T4 的 LogUtil
- Produces: `formatMessage` 语义升级为 SLF4J `MessageFormatter.arrayFormat`——新增 `\\{}` 转义支持、数组参数深度格式化、null 模板安全;既有 8 用例断言不变(基础替换语义两实现一致)

- [ ] **Step 1: LogUtilTest.java 追加 3 个边缘用例(插在 `postHandler_receivesContext_fromSpringWiring` 方法之后、类收尾 `}` 之前)**

在文件末尾的类收尾 `}` 前插入:

```java

    @Test
    void info_escapedPlaceholder_rendersLiteralBraces() {
        LogUtil.info("literal \\{} and {}", "value");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("literal {} and value");
    }

    @Test
    void info_arrayArg_deepFormatted() {
        LogUtil.info("ids={}", (Object) new int[]{1, 2, 3});
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("ids=[1, 2, 3]");
    }

    @Test
    void info_nullTemplateWithArgs_doesNotThrow() {
        LogUtil.info(null, "arg");
        // MessageFormatter 对 null 模板返回 null 消息;不抛异常即通过,输出内容不作断言
    }
```

- [ ] **Step 2: 运行验证"红"(手写 formatMessage 对转义/数组的行为与断言不符)**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`(测试失败)——`info_escapedPlaceholder_rendersLiteralBraces` 与 `info_arrayArg_deepFormatted` FAIL(手写实现把 `\\{}` 当普通占位符、数组走 toString 输出 `[I@...`);`info_nullTemplateWithArgs_doesNotThrow` 以 NPE ERROR 失败

- [ ] **Step 3: 替换 formatMessage 实现**

编辑 `LogUtil.java` —— old:

```java
    /**
     * <b>格式化消息</b>
     * <p>使用 SLF4J 风格的占位符格式化</p>
     */
    private static String formatMessage(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }

        // 简单的占位符替换
        String result = template;
        for (Object arg : args) {
            int index = result.indexOf("{}");
            if (index == -1) {
                break;
            }
            String argStr = arg == null ? "null" : arg.toString();
            result = result.substring(0, index) + argStr + result.substring(index + 2);
        }
        return result;
    }
```

new:

```java
    /**
     * <b>格式化消息</b>
     * <p>委托 SLF4J {@link org.slf4j.helpers.MessageFormatter#arrayFormat},与 SLF4J
     * {@code Logger} 的占位符语义完全一致:支持 {@code \\{}} 转义、数组参数深度格式化、
     * null 模板安全(RV2-17 翻案,ADR-0012)。</p>
     */
    private static String formatMessage(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        // 三参变体禁用"尾参 Throwable 自动剥离":所有参数(含 Throwable,经 toString)按占位符填充,
        // 与旧手写实现一致;Throwable 的 stack trace 输出走显式重载位(ADR-0005)。
        return org.slf4j.helpers.MessageFormatter.arrayFormat(template, args, null).getMessage();
    }
```

> 勘误(执行时,commit 22a7a00):初版用双参 `arrayFormat(template, args)`——审查发现该变体会无条件
> 剥离尾参 Throwable(`info("失败: {}", ex)` 渲染 "失败: {}" 且异常静默丢弃)。改用三参传 null 禁用
> 提取,并追加守卫用例 `info_trailingThrowableArg_formattedIntoPlaceholder`(先红后绿)。上方代码为终态。

- [ ] **Step 4: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 255`(252 + 3),0 失败——既有 8 用例不变通过即证明基础语义保持

- [ ] **Step 5: Commit(rework commit)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
refactor: LogUtil.formatMessage 换 SLF4J MessageFormatter(RV2-17 翻案,ADR-0012)

新增转义/数组/null 模板 3 个边缘用例先红后绿;既有 8 用例断言零改动。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 6: pattern 簇迁移(Patterns / CommonPatterns)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\pattern\PatternsTest.java`
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\pattern\CommonPatternsTest.java`
- Create(迁移): `Patterns.java` / `CommonPatterns.java` / `package-info.java` → `$DST\src\main\java\cn\code91\facility\pattern\`

**Interfaces:**
- Consumes: 无(纯 JDK)
- Produces:`Patterns.compile/tryCompile/isValidRegex/matches/matchesIgnoreCase/contains/startsWith/endsWith/count/findFirst×3/findFirstGroups/findFirstAsMap/findAll×2/findAllGroups/findAllAsMap/findDistinct×2/replaceFirst×2/replaceAll×2/remove/removeFirst/split×2/splitNonEmpty/splitTrimmed/stream×2/escape/escapeReplacement/clearCache/cacheSize` + 28 个正则字面量;`CommonPatterns.isEmail/isMobileCN/isIdCard/isUrl/isIpv4/isInteger/isNumber/containsChinese/isUuid`

- [ ] **Step 1: 新写 PatternsTest.java(26 用例)**

```java
package cn.code91.facility.pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Patterns - 正则引擎")
class PatternsTest {

    @AfterEach
    void clearCache() {
        Patterns.clearCache();
    }

    @Nested
    @DisplayName("编译与缓存")
    class CompileAndCache {

        @Test
        void compile_sameRegexAndFlags_returnsCachedInstance() {
            Pattern first = Patterns.compile("a+b");
            Pattern second = Patterns.compile("a+b");
            assertThat(second).isSameAs(first);
            assertThat(Patterns.cacheSize()).isEqualTo(1);
        }

        @Test
        void compile_differentFlags_differentCacheEntries() {
            Patterns.compile("x");
            Patterns.compile("x", Pattern.CASE_INSENSITIVE);
            assertThat(Patterns.cacheSize()).isEqualTo(2);
        }

        @Test
        void compile_null_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> Patterns.compile(null));
        }

        @Test
        void tryCompile_invalidOrNull_returnsEmpty() {
            assertThat(Patterns.tryCompile("([")).isEmpty();
            assertThat(Patterns.tryCompile(null)).isEmpty();
            assertThat(Patterns.tryCompile("ok")).isPresent();
        }

        @Test
        void isValidRegex_distinguishesValidity() {
            assertThat(Patterns.isValidRegex("a{2,3}")).isTrue();
            assertThat(Patterns.isValidRegex("a{2,")).isFalse();
            assertThat(Patterns.isValidRegex(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("校验")
    class Matching {

        @Test
        void matches_fullMatchOnly() {
            assertThat(Patterns.matches("abc123", "[a-z]+\\d+")).isTrue();
            assertThat(Patterns.matches("abc123!", "[a-z]+\\d+")).isFalse();
            assertThat(Patterns.matches(null, "x")).isFalse();
            assertThat(Patterns.matches("x", null)).isFalse();
        }

        @Test
        void matchesIgnoreCase_caseInsensitive() {
            assertThat(Patterns.matchesIgnoreCase("ABC", "[a-z]+")).isTrue();
        }

        @Test
        void contains_findsSubsequence() {
            assertThat(Patterns.contains("xx42yy", "\\d+")).isTrue();
            assertThat(Patterns.contains("xxyy", "\\d+")).isFalse();
        }

        @Test
        void startsWith_anchorsAtZero() {
            assertThat(Patterns.startsWith("42xx", "\\d+")).isTrue();
            assertThat(Patterns.startsWith("xx42", "\\d+")).isFalse();
        }

        @Test
        void endsWith_lastMatchTouchesEnd() {
            assertThat(Patterns.endsWith("xx42", "\\d+")).isTrue();
            assertThat(Patterns.endsWith("42xx", "\\d+")).isFalse();
        }

        @Test
        void count_countsOccurrences() {
            assertThat(Patterns.count("a1b22c333", "\\d+")).isEqualTo(3);
            assertThat(Patterns.count(null, "\\d")).isZero();
        }
    }

    @Nested
    @DisplayName("提取")
    class Extraction {

        @Test
        void findFirst_wholeAndGroupIndex() {
            assertThat(Patterns.findFirst("a=1;b=2", "(\\w)=(\\d)")).contains("a=1");
            assertThat(Patterns.findFirst("a=1;b=2", "(\\w)=(\\d)", 2)).contains("1");
            assertThat(Patterns.findFirst("nope", "\\d")).isEmpty();
        }

        @Test
        void findFirst_namedGroup_andMissingName() {
            assertThat(Patterns.findFirst("a=1", "(?<k>\\w)=(?<v>\\d)", "v")).contains("1");
            assertThat(Patterns.findFirst("a=1", "(?<k>\\w)=(?<v>\\d)", "absent")).isEmpty();
        }

        @Test
        void findFirstGroups_returnsAllCapturedGroups() {
            assertThat(Patterns.findFirstGroups("a=1", "(\\w)=(\\d)")).containsExactly("a", "1");
        }

        @Test
        void findFirstAsMap_mapsNamedGroups() {
            Map<String, String> map = Patterns.findFirstAsMap("a=1", "(?<k>\\w)=(?<v>\\d)", "k", "v");
            assertThat(map).containsEntry("k", "a").containsEntry("v", "1");
        }

        @Test
        void findAll_byIndexAndName() {
            assertThat(Patterns.findAll("a1 b2", "\\w(\\d)", 1)).containsExactly("1", "2");
            assertThat(Patterns.findAll("a1 b2", "\\w(?<n>\\d)", "n")).containsExactly("1", "2");
        }

        @Test
        void findAllGroups_perMatchGroupLists() {
            List<List<String>> groups = Patterns.findAllGroups("a=1;b=2", "(\\w)=(\\d)");
            assertThat(groups).containsExactly(List.of("a", "1"), List.of("b", "2"));
        }

        @Test
        void findDistinct_deduplicatesPreservingOrder() {
            assertThat(Patterns.findDistinct("1 2 1 3", "\\d")).containsExactly("1", "2", "3");
        }
    }

    @Nested
    @DisplayName("替换与分割")
    class ReplaceAndSplit {

        @Test
        void replaceFirstAndAll_stringReplacement() {
            assertThat(Patterns.replaceFirst("a1b2", "\\d", "#")).isEqualTo("a#b2");
            assertThat(Patterns.replaceAll("a1b2", "\\d", "#")).isEqualTo("a#b#");
            assertThat(Patterns.replaceAll("a1", "\\d", (String) null)).isEqualTo("a");
            // 勘误:null 直传在 String/Function 两个 replaceAll 重载间歧义,需显式 cast(执行时发现)
        }

        @Test
        void replaceAll_functionConverter_receivesMatcher() {
            String result = Patterns.replaceAll("a1b2", "\\d", m -> "<" + m.group() + ">");
            assertThat(result).isEqualTo("a<1>b<2>");
        }

        @Test
        void replace_functionResult_dollarSignsTreatedLiterally() {
            assertThat(Patterns.replaceAll("x1", "\\d", m -> "$" + m.group())).isEqualTo("x$1");
        }

        @Test
        void removeAndRemoveFirst_deleteMatches() {
            assertThat(Patterns.remove("a1b2", "\\d")).isEqualTo("ab");
            assertThat(Patterns.removeFirst("a1b2", "\\d")).isEqualTo("ab2");
        }

        @Test
        void split_variants() {
            assertThat(Patterns.split("a,b,,c", ",")).containsExactly("a", "b", "", "c");
            assertThat(Patterns.splitNonEmpty("a,b,,c", ",")).containsExactly("a", "b", "c");
            assertThat(Patterns.splitTrimmed(" a , b ", ",")).containsExactly("a", "b");
        }
    }

    @Nested
    @DisplayName("流式与转义")
    class StreamAndEscape {

        @Test
        void stream_yieldsMatchResults() {
            assertThat(Patterns.stream("a1b2", "\\d").map(java.util.regex.MatchResult::group))
                    .containsExactly("1", "2");
            assertThat(Patterns.stream(null, "\\d")).isEmpty();
        }

        @Test
        void escape_literalizesRegexMetachars() {
            assertThat(Patterns.matches("a.b", Patterns.escape("a.b"))).isTrue();
            assertThat(Patterns.matches("axb", Patterns.escape("a.b"))).isFalse();
            assertThat(Patterns.escape(null)).isNull();
        }

        @Test
        void literals_spotChecks() {
            assertThat(Patterns.matches("user@example.com", Patterns.EMAIL)).isTrue();
            assertThat(Patterns.matches("192.168.1.1", Patterns.IPV4)).isTrue();
            assertThat(Patterns.matches("256.1.1.1", Patterns.IPV4)).isFalse();
            assertThat(Patterns.matches("13812345678", Patterns.MOBILE_CN)).isTrue();
            assertThat(Patterns.matches("2026-07-02 12:30:00", Patterns.DATETIME)).isTrue();
        }
    }
}
```

- [ ] **Step 2: 新写 CommonPatternsTest.java(10 用例)**

```java
package cn.code91.facility.pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommonPatterns - 预制谓词")
class CommonPatternsTest {

    @Test
    void isEmail() {
        assertThat(CommonPatterns.isEmail("a.b+c@d-e.org")).isTrue();
        assertThat(CommonPatterns.isEmail("not-an-email")).isFalse();
        assertThat(CommonPatterns.isEmail(null)).isFalse();
    }

    @Test
    void isMobileCN() {
        assertThat(CommonPatterns.isMobileCN("13912345678")).isTrue();
        assertThat(CommonPatterns.isMobileCN("12912345678")).isFalse();
    }

    @Test
    void isIdCard_accepts18And15() {
        assertThat(CommonPatterns.isIdCard("110101199003077516")).isTrue();
        assertThat(CommonPatterns.isIdCard("110101900307751")).isTrue();
        assertThat(CommonPatterns.isIdCard("12345")).isFalse();
    }

    @Test
    void isUrl() {
        assertThat(CommonPatterns.isUrl("https://example.com/a?b=1")).isTrue();
        assertThat(CommonPatterns.isUrl("ftp://example.com")).isFalse();
    }

    @Test
    void isIpv4() {
        assertThat(CommonPatterns.isIpv4("10.0.0.255")).isTrue();
        assertThat(CommonPatterns.isIpv4("10.0.0.256")).isFalse();
    }

    @Test
    void isInteger() {
        assertThat(CommonPatterns.isInteger("-42")).isTrue();
        assertThat(CommonPatterns.isInteger("4.2")).isFalse();
    }

    @Test
    void isNumber() {
        assertThat(CommonPatterns.isNumber("-4.2")).isTrue();
        assertThat(CommonPatterns.isNumber("4.")).isFalse();
    }

    @Test
    void containsChinese() {
        assertThat(CommonPatterns.containsChinese("hello 世界")).isTrue();
        assertThat(CommonPatterns.containsChinese("hello")).isFalse();
    }

    @Test
    void isUuid() {
        assertThat(CommonPatterns.isUuid("123e4567-e89b-12d3-a456-426614174000")).isTrue();
        assertThat(CommonPatterns.isUuid("123e4567e89b12d3a456426614174000")).isFalse();
    }

    @Test
    void nullInput_alwaysFalse() {
        assertThat(CommonPatterns.isUuid(null)).isFalse();
        assertThat(CommonPatterns.containsChinese(null)).isFalse();
    }
}
```

- [ ] **Step 3: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,`cannot find symbol`(Patterns / CommonPatterns)

- [ ] **Step 4: 标准迁移命令复制 3 个源文件(无内容 rework;package-info 属实,原样迁移)**

1. `$SRC\...\pattern\Patterns.java` → `$DST\src\main\java\cn\code91\facility\pattern\Patterns.java`
2. `$SRC\...\pattern\CommonPatterns.java` → `$DST\src\main\java\cn\code91\facility\pattern\CommonPatterns.java`
3. `$SRC\...\pattern\package-info.java` → `$DST\src\main\java\cn\code91\facility\pattern\package-info.java`

- [ ] **Step 5: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 292`(256 + 36),0 失败

- [ ] **Step 6: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 pattern 簇(Patterns/CommonPatterns),补 36 个行为测试(原零测试盲区)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 7: hash 簇迁移(Hashing)

**Files:**
- Test(新写): `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\hash\HashingTest.java`
- Create(迁移): `Hashing.java` → `$DST\src\main\java\cn\code91\facility\hash\Hashing.java`
- Create(全新内容): `package-info.java`(源文件仅一行,按仓库三段式补齐)

**Interfaces:**
- Consumes: P1 的 `Result`/`WrappedError`/`FacilityErrorType`
- Produces:`Hashing.md5(File)/sha256(File)/hash(File,String)/md5(byte[])/hashBytes(byte[],String)`,全部返回 `Result<String,WrappedError>`(十六进制小写)
- **行为契约(据实固化,非 rework)**:`hashBytes(null/空数组)` → `Err(FILE_READ_ERROR)`(空数组虽可哈希,源实现有意拒绝,保真迁移并以测试钉住)

- [ ] **Step 1: 新写 HashingTest.java(10 用例)**

```java
package cn.code91.facility.hash;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hashing - 哈希计算")
class HashingTest {

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.UTF_8);
    private static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Nested
    @DisplayName("字节数组")
    class Bytes {

        @Test
        void md5_knownVector() {
            assertThat(Hashing.md5(ABC).get()).isEqualTo(ABC_MD5);
        }

        @Test
        void hashBytes_sha256_knownVector() {
            assertThat(Hashing.hashBytes(ABC, "SHA-256").get()).isEqualTo(ABC_SHA256);
        }

        @Test
        void hashBytes_outputIsLowercaseHex() {
            String hex = Hashing.hashBytes(ABC, "SHA-1").get();
            assertThat(hex).matches("[0-9a-f]{40}");
        }

        @Test
        void hashBytes_nullOrEmpty_returnsFileReadError() {
            assertThat(Hashing.hashBytes(null, "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
            assertThat(Hashing.hashBytes(new byte[0], "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
        }

        @Test
        void hashBytes_unknownAlgorithm_returnsHashErrorWithAlgorithmArg() {
            var err = Hashing.hashBytes(ABC, "NOPE-1").getErr();
            assertThat(err.isErrorType(FacilityErrorType.FILE_HASH_ERROR)).isTrue();
            assertThat(err.getArg(0, String.class)).isEqualTo("NOPE-1");
        }
    }

    @Nested
    @DisplayName("文件")
    class Files_ {

        @TempDir
        Path tempDir;

        private File abcFile() throws Exception {
            Path p = tempDir.resolve("abc.txt");
            Files.write(p, ABC);
            return p.toFile();
        }

        @Test
        void md5_file_knownVector() throws Exception {
            assertThat(Hashing.md5(abcFile()).get()).isEqualTo(ABC_MD5);
        }

        @Test
        void sha256_file_knownVector() throws Exception {
            assertThat(Hashing.sha256(abcFile()).get()).isEqualTo(ABC_SHA256);
        }

        @Test
        void hash_nullFile_returnsFileNotFound() {
            assertThat(Hashing.hash(null, "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
        }

        @Test
        void hash_missingFile_returnsFileNotFound() {
            File missing = tempDir.resolve("absent.bin").toFile();
            assertThat(Hashing.hash(missing, "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
        }

        @Test
        void hash_unknownAlgorithm_returnsHashError() throws Exception {
            assertThat(Hashing.hash(abcFile(), "NOPE-1").getErr()
                    .isErrorType(FacilityErrorType.FILE_HASH_ERROR)).isTrue();
        }
    }
}
```

- [ ] **Step 2: 运行验证"红"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD FAILURE`,`cannot find symbol: variable Hashing`

- [ ] **Step 3: 标准迁移命令复制 Hashing.java;写入新 package-info.java**

复制:`$SRC\src\main\java\cn\hbads\beacon\facility\hash\Hashing.java` → `$DST\src\main\java\cn\code91\facility\hash\Hashing.java`

写入 package-info.java(按仓库三段式补齐,替代源文件单行版):

```java
/**
 * <h2>cn.code91.facility.hash</h2>
 *
 * <p><b>Purpose:</b> Hash computation over files and byte arrays via JDK
 * {@code MessageDigest} (any built-in algorithm: MD5, SHA-1, SHA-256, ...);
 * results are lowercase hex strings.</p>
 *
 * <p><b>Entry classes:</b> {@code Hashing}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result<String, WrappedError>}); no third-party hashing library.</p>
 *
 * <p><b>Depended on by:</b> downstream application code (file integrity, dedup keys).</p>
 */
package cn.code91.facility.hash;
```

- [ ] **Step 4: 运行验证"绿"**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 302`(292 + 10),0 失败

- [ ] **Step 5: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 hash 簇(Hashing),补 10 个行为测试(原零测试盲区,含 RFC 已知向量)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 8: ArchUnit 追加规则(log 不依赖 logback)

**Files:**
- Modify: `D:\Yiwer\code\server-facility\src\test\java\cn\code91\facility\architecture\ArchitectureTest.java`

**Interfaces:**
- Consumes: T4 的成果(主源码零 logback)
- Produces: 常驻守护规则,锁定 ADR-0011 的成果

- [ ] **Step 1: 在 ArchitectureTest.java 追加规则(插在类收尾 `}` 之前)**

需要新增 import(加在现有 import 区):

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
```

追加规则(类收尾 `}` 前):

```java

    /**
     * ADR-0011:setLevel 已删除,主源码不得再依赖 logback 实现类
     * (logback-classic 仅存在于 test classpath,供 ListAppender 断言)。
     */
    @ArchTest
    static final ArchRule main_code_does_not_depend_on_logback =
            noClasses().that().resideInAPackage("cn.code91.facility..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("ch.qos.logback..");
```

- [ ] **Step 2: 运行验证(守护型,落地即绿;红则 T4 有 logback 泄漏,STOP 回查)**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 303`(302 + 1),0 失败

- [ ] **Step 3: Commit**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
test: ArchUnit 追加规则——主源码不依赖 logback(锁定 ADR-0011)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 9: 决策沉淀(ADR-0011 / ADR-0012 / INDEX 更新)

**Files:**
- Create: `D:\Yiwer\code\server-facility\docs\adr\0011-logutil-setlevel-removal.md`
- Create: `D:\Yiwer\code\server-facility\docs\adr\0012-logutil-slf4j-messageformatter.md`
- Modify: `D:\Yiwer\code\server-facility\docs\adr\INDEX.md`(表尾追加 2 行)

**Interfaces:**
- Consumes: T4/T5 的事实
- Produces: 可溯源决策记录(spec §12 第 5 条)

- [ ] **Step 1: 写 ADR-0011**

```markdown
# ADR-0011: 删除 LogUtil.setLevel,消除主源码 logback 依赖

- **状态**:Accepted(2026-07-02)
- **源起**:spec §5 第 18 行 rework ②(logback 耦合隔离)+ P2 迁移窗口

## 背景

源项目 `LogUtil.setLevel(Class, Level)` 直接引用 `ch.qos.logback.classic.LoggerContext`,
是主源码唯一的 logback 实现类触点,迫使 logback-classic 以 optional-compile 存在,
并使日志门面与具体实现绑定(换 log4j2 即断)。迁移前全仓扫描证实:
**setLevel 在 beacon 全仓库零调用**(仅定义自身)。

## 决策

1. 迁移时删除 `setLevel` 方法(YAGNI:零调用能力不背;动态改级别属日志实现的运维关注点,
   应用可用 Spring Boot actuator loggers 端点或 logback 自身配置实现);
2. logback-classic 降为 **test** scope(测试经 ListAppender 断言真实日志输出,并充当
   SLF4J provider 保证测试输出纯净);
3. ArchUnit 常驻规则 `main_code_does_not_depend_on_logback` 锁定该成果。

## 后果

- 主 classpath 零日志实现依赖,消费方自由选择 SLF4J provider——spec §6"logback 目标降 test"达成;
- 若未来确需动态改级别能力,以独立组件按 §4.3 范式接入(SPI + optional 依赖),不回填门面。
```

- [ ] **Step 2: 写 ADR-0012**

```markdown
# ADR-0012: LogUtil.formatMessage 委托 SLF4J MessageFormatter(RV2-17 翻案)

- **状态**:Accepted(2026-07-02)
- **源起**:REVIEW-2 RV2-17(wontfix-for-now)+ spec A6 重估授权

## 背景

源项目 `formatMessage` 手写 `{}` 顺序替换:不支持 `\\{}` 转义、数组参数输出 JVM 默认
toString(如 `[I@1a2b3c`)、null 模板遇参数抛 NPE——与"SLF4J 风格"名实不符(RV2-17)。
当年 wontfix 理由是"改日志输出格式,风险>收益"——针对已有消费方的日志观感;
新项目零消费方,理由消失。

## 决策

`formatMessage` 委托 `org.slf4j.helpers.MessageFormatter.arrayFormat(template, args, null).getMessage()`
(slf4j-api 自带,零新依赖)。**三参变体传 null throwable**:禁用双参变体的"尾参 Throwable 自动剥离",
所有参数(含 Throwable,经 toString)按占位符填充,与旧手写语义一致;stack trace 输出走显式重载位(ADR-0005)。
行为差异(均为修正而非破坏):

| 场景 | 旧(手写) | 新(MessageFormatter) |
|---|---|---|
| 基础顺序替换 / null 参数 / 参数多于占位符 / 占位符多于参数 | 一致 | 一致(8 个迁移期用例零改动通过) |
| `\\{}` 转义 | 当普通占位符消耗参数 | 输出字面 `{}` |
| 数组参数 | `[I@hash` | 深度格式化 `[1, 2, 3]` |
| null 模板 + 参数 | NPE | 返回 null,不抛 |

## 后果

- LogUtil 占位符渲染语义与 SLF4J 生态一致,"SLF4J 风格"名实相符;
- 守卫用例 `info_trailingThrowableArg_formattedIntoPlaceholder` 钉住"尾参 Throwable 填入占位符
  而非被静默剥离"的语义,防止未来误改回双参变体。
```

- [ ] **Step 3: INDEX.md 表尾追加 2 行**

old:

```markdown
| [0010](0010-error-message-boundary-localization.md) | Accepted | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
```

new:

```markdown
| [0010](0010-error-message-boundary-localization.md) | Accepted | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
| [0011](0011-logutil-setlevel-removal.md) | Accepted | 删除 LogUtil.setLevel,主源码零 logback 依赖 |
| [0012](0012-logutil-slf4j-messageformatter.md) | Accepted | formatMessage 委托 SLF4J MessageFormatter(RV2-17 翻案) |
```

- [ ] **Step 4: 全量回归 + Commit**

Run: `mvn -f D:\Yiwer\code\server-facility\pom.xml test`
Expected: `BUILD SUCCESS`,`Tests run: 303`,0 失败

```powershell
git -C D:\Yiwer\code\server-facility add docs\adr
git -C D:\Yiwer\code\server-facility commit -m @'
docs: ADR-0011 setLevel 删除 + ADR-0012 MessageFormatter 翻案

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## 验收清单(P2 出口)

- [ ] `mvn test` 全绿,303 个用例(P1 218 + context 14 + log 24 + pattern 36 + hash 10 + arch 1),0 失败 0 跳过
- [ ] `mvn dependency:analyze` 对已迁移簇无 used-undeclared(spring-context/beans/core、slf4j-api 均已显式声明)
- [ ] ArchUnit 四规则绿:包无环、error 纯 JDK、log 无 logback(新)、(纯度规则含 lombok 放行)
- [ ] 主源码 `grep -r "ch.qos.logback" src/main` 零命中
- [ ] `docs/adr/` 新增 0011/0012,INDEX 同步
- [ ] git log:T4 迁移与 T5 rework 为两个独立 commit(终审要求)
- [ ] context/log 两个 package-info 依赖声明与 import 实况一致(P1 教训)
