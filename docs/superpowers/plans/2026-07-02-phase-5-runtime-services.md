# server-facility P5 运行期服务 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 迁移 locale/async 两簇与 Core/Locale/Async 三个装配 + i18n 资源;执行 C1 断环的 locale 侧衔接(`LocaleUtil.localize(ErrorTypeInterface, ...)` 便捷解析入口 + 真实 bundle 集成测试)。

**Architecture:** locale→common/context(+新增 →error,C1 锥形单向 `error ← context ← locale` 成立);async→仅 result(源 package-info 声称 error/log/context 系陈旧);三装配→各自簇。顺序:T1 locale 迁移 → T2 C1 localize rework(独立 commit)→ T3 async 迁移 → T4 三装配迁移。TDD:测试先落(红)→ 源码 → 绿 → 提交。

**Tech Stack:** 同 P4;本阶段零新依赖(spring-context/core、starter-test 均已就位)。

## Global Constraints

- 继承 P1-P4 计划全部约束:`$SRC`=`D:\STELE\beacon\beacon-support\beacon-facility`、`$DST`=`D:\Yiwer\code\server-facility`、包名映射 ordinal、UTF-8 无 BOM、每任务 mvn test 全绿才 commit、Co-Authored-By 尾行、**git 提交一律 PowerShell 工具**、**RED/GREEN 原始 mvn 粘贴且行号可交叉核对**、**迁移源仅限 `$SRC`(引用出处照实写)**、**迁移源测试失败=你的转录/环境错误,改迁移源=任务自动失败,STOP 报 BLOCKED**。
- **标准迁移命令**(同前;i18n properties 文件亦用它——replace 无匹配即纯复制,编码保真)。
- **测试计数纪律**(grep 实数,已核):迁移 AggregatedMessageSourceTest 3 / AsyncTest 50 / AsyncContextTest 18 / AsyncInterceptorTest 14 / AggregateExceptionTest 8 / Core 2 / Locale 2 / Async 装配 3(共 100);新写 LocaleUtilTest 11。链:**T1 后 453 → T2 后 464 → T3 后 554 → T4 后 561**。不符即 STOP。
- **禁止事项**:六个 async 主文件与三个装配类除包名替换外零改动(仅 FacilityCoreAutoConfiguration 有 1 处授权 stele 残留编辑);LocaleUtil 迁移 commit(T1)零改动,localize 新增属 T2 独立 rework commit;autoconfigure/package-info 仍不建(P6 全装配齐后统一写)。

## 复核结论(本计划定案)

1. **async/package-info 声称"Depends on: result, error, log, context"——经 import 全扫描,实况仅 `result`**:三项陈旧声明,按 P1 教训重写。
2. **locale/package-info 声称"Depends on: nothing"——实况 common+context,C1 后新增 error**:重写。
3. **`translateMessage`/`translateMessageWithArgs`(非 fallback 变体)的隐性行为**:holder 有 context 但 code 缺失时,`NoSuchMessageException` 会**穿透抛出**(javadoc"翻译失败返回原始key"仅覆盖无 MessageSource bean 的场景)——本阶段照实迁移不改行为,LocaleUtilTest 用反射清空 holder(IdUtilSpringFallbackTest 先例)测"返回 key"路径;该 javadoc 失准列入 P7 文档篮。
4. **FacilityCoreAutoConfiguration javadoc "stele-facility" 残留**:1 处授权编辑。

---

### Task 1: locale 簇迁移 + i18n 资源

**Files:**
- Create(迁移): `$DST\src\test\java\cn\code91\facility\locale\AggregatedMessageSourceTest.java`
- Create(迁移): `LocaleUtil.java` / `AggregatedMessageSource.java` → `$DST\src\main\java\cn\code91\facility\locale\`(零编辑)
- Create(迁移): 3 个资源 `$SRC\src\main\resources\i18n\facility-messages_{en,zh_CN,zh_TW}.properties` → `$DST\src\main\resources\i18n\`(标准命令,纯复制)
- **package-info 属 T2**(其依赖声明含 C1 的 error 项,随 localize 同 commit 落地,避免文档超前一个 commit)

**Interfaces:**
- Consumes: P1 common(NullSafe)、P2 context(SpringContextHolder)
- Produces:`LocaleUtil.getLocale/translateMessage×2/translateMessageWithArgs×2/translateMessageWithFallback`;`AggregatedMessageSource(List<MessageSource>)`(首个命中,RV2-21 显式排序);i18n bundle `i18n/facility-messages`(键 `facility.*`,与 FacilityErrorType.messageKey 吻合)

- [ ] **Step 1: 迁移 AggregatedMessageSourceTest(标准命令)**

`$SRC\src\test\java\cn\hbads\beacon\facility\locale\AggregatedMessageSourceTest.java` → `$DST\src\test\java\cn\code91\facility\locale\AggregatedMessageSourceTest.java`

- [ ] **Step 2: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol: class AggregatedMessageSource`

- [ ] **Step 3: 迁移 2 个主文件 + 3 个资源(标准命令,零编辑);写入 package-info**

1. `$SRC\...\locale\LocaleUtil.java` → `$DST\src\main\java\cn\code91\facility\locale\LocaleUtil.java`
2. `$SRC\...\locale\AggregatedMessageSource.java` → 同布局
3. `$SRC\src\main\resources\i18n\facility-messages_en.properties` → `$DST\src\main\resources\i18n\facility-messages_en.properties`
4. 同上 `_zh_CN` / `_zh_TW`

- [ ] **Step 4: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 453`(450 + 3),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 locale 簇(LocaleUtil/AggregatedMessageSource)与 i18n 三语资源

package-info 据实重写(源"nothing"失实,实依赖 common/context);
localize 边界解析入口属下一提交(迁移/rework 分离)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 2: C1 locale 侧衔接(localize rework,独立 commit)

**Files:**
- Test(新写): `$DST\src\test\java\cn\code91\facility\locale\LocaleUtilTest.java`
- Modify: `$DST\src\main\java\cn\code91\facility\locale\LocaleUtil.java`(2 处编辑:import 区 + 追加 2 个方法)
- Create(全新内容): `$DST\src\main\java\cn\code91\facility\locale\package-info.java`(随 error 依赖同 commit 落地)

**Interfaces:**
- Consumes: P1 error(ErrorTypeInterface/FacilityErrorType)、T1 的 LocaleUtil 与 i18n bundle
- Produces(P6 异常处理器接线的入口):`LocaleUtil.localize(ErrorTypeInterface, Object[], Locale)` 与 `localize(ErrorTypeInterface, Object...)`——MessageSource 命中返回本地化文案,未命中回退默认模板渲染(语义 = ADR-0010 决策 2 的等价旧行为)

- [ ] **Step 1: 新写 LocaleUtilTest.java(11 用例;holder 复位用反射清空——IdUtilSpringFallbackTest 先例,LocaleUtilTest 不在 context 包无法调 clear())**

```java
package cn.code91.facility.locale;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticApplicationContext;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocaleUtil - i18n 门面与 C1 边界本地化(ADR-0010)")
class LocaleUtilTest {

    @BeforeEach
    @AfterEach
    void clearHolder() throws Exception {
        Field f = SpringContextHolder.class.getDeclaredField("CONTEXT_REF");
        f.setAccessible(true);
        ((AtomicReference<?>) f.get(null)).set(null);
    }

    private static ErrorTypeInterface customType(String key, String template) {
        return new ErrorTypeInterface() {
            @Override public int getCode() { return 1; }
            @Override public String getMessageKey() { return key; }
            @Override public String getDefaultMessage() { return template; }
        };
    }

    private static void installBundleContext() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/facility-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        StaticApplicationContext ctx = new StaticApplicationContext();
        // refresh 前以内置名 "messageSource" 注册,顶替容器默认——避免与内置 bean 撞类型
        ctx.getBeanFactory().registerSingleton("messageSource", ms);
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);
    }

    @Nested
    @DisplayName("无 Spring(holder 空)——回退语义")
    class NoSpring {

        @Test
        void localize_withArgs_rendersDefaultTemplate() {
            assertThat(LocaleUtil.localize(customType("t.k", "用户 {0} 不存在"), "张三"))
                    .isEqualTo("用户 张三 不存在");
        }

        @Test
        void localize_noArgs_returnsTemplateVerbatim() {
            // 无参不经 MessageFormat:单引号与花括号原样保留(镜像 renderFallback 语义)
            assertThat(LocaleUtil.localize(customType("t.k", "it's {raw}")))
                    .isEqualTo("it's {raw}");
        }

        @Test
        void localize_nullTemplate_returnsKey() {
            assertThat(LocaleUtil.localize(customType("t.only.key", null))).isEqualTo("t.only.key");
        }

        @Test
        void localize_facilityErrorType_fallsBackToDefaultMessage() {
            assertThat(LocaleUtil.localize(FacilityErrorType.JSON_SERIALIZE_ERROR))
                    .isEqualTo("对象序列化异常");
        }

        @Test
        void translateMessage_returnsKeyItself() {
            assertThat(LocaleUtil.translateMessage("absent.key", Locale.ENGLISH)).isEqualTo("absent.key");
        }

        @Test
        void translateMessageWithArgs_returnsKeyItself() {
            assertThat(LocaleUtil.translateMessageWithArgs("absent.key", new Object[]{"x"}))
                    .isEqualTo("absent.key");
        }

        @Test
        void translateMessageWithFallback_rendersFallbackPattern() {
            assertThat(LocaleUtil.translateMessageWithFallback("absent.key", new Object[]{"7"}, "共 {0} 条", Locale.ENGLISH))
                    .isEqualTo("共 7 条");
        }
    }

    @Nested
    @DisplayName("有 Spring + facility bundle——命中与未命中")
    class WithBundle {

        @Test
        void localize_englishBundleHit() {
            installBundleContext();
            assertThat(LocaleUtil.localize(FacilityErrorType.JSON_SERIALIZE_ERROR, new Object[]{}, Locale.ENGLISH))
                    .isEqualTo("Failed to serialize object to JSON");
        }

        @Test
        void localize_simplifiedChineseBundleHit() {
            installBundleContext();
            // zh_CN bundle 值恰与默认模板同文——本用例锚定的是"命中路径可用",与英文用例互证
            assertThat(LocaleUtil.localize(FacilityErrorType.JSON_SERIALIZE_ERROR, new Object[]{}, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("对象序列化异常");
        }

        @Test
        void localize_bundleMiss_fallsBackToTemplate() {
            installBundleContext();
            assertThat(LocaleUtil.localize(customType("facility.test.absent", "兜底 {0}"), new Object[]{"X"}, Locale.ENGLISH))
                    .isEqualTo("兜底 X");
        }
    }

    @Test
    @DisplayName("getLocale 取 LocaleContextHolder 当前值")
    void getLocale_returnsLocaleContextHolderValue() {
        Locale original = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(Locale.FRANCE);
            assertThat(LocaleUtil.getLocale()).isEqualTo(Locale.FRANCE);
        } finally {
            LocaleContextHolder.setLocale(original);
        }
    }
}
```

- [ ] **Step 2: 验证"红"** Run mvn test → `BUILD FAILURE`(编译错误:`cannot find symbol: method localize`——其余既有方法用例可编译,但整类因 localize 缺失编译失败)

- [ ] **Step 3: LocaleUtil.java 两处编辑**

编辑 1(import 区)—— old:

```java
import cn.code91.facility.common.NullSafe;
import cn.code91.facility.context.SpringContextHolder;
```

new:

```java
import cn.code91.facility.common.NullSafe;
import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.ErrorTypeInterface;
```

编辑 2(在 translateMessageWithFallback 方法体结束后追加;锚点为其收尾)—— old:

```java
        return fallbackPattern == null ? messageKey : renderFallback(fallbackPattern, args);
    }
```

new:

```java
        return fallbackPattern == null ? messageKey : renderFallback(fallbackPattern, args);
    }

    // ==================== ErrorTypeInterface 边界本地化(C1,ADR-0010) ====================

    /**
     * <b>错误类型的边界本地化解析</b>
     * <p>C1 断环(ADR-0010)后 error 包不做 i18n;需要本地化消息的边界(如 P6 的全局异常处理器)
     * 经此入口解析:MessageSource 命中返回本地化文案,未命中回退 {@code errorType.getDefaultMessage()}
     * 模板渲染——语义即 {@link #translateMessageWithFallback} 的等价旧行为。</p>
     *
     * @param errorType 错误类型(不能为 null)
     * @param args      消息参数
     * @param locale    目标语言环境
     * @return 本地化消息,或默认模板渲染结果
     */
    public static String localize(ErrorTypeInterface errorType, Object[] args, Locale locale) {
        java.util.Objects.requireNonNull(errorType, "errorType cannot be null");
        return translateMessageWithFallback(errorType.getMessageKey(), args, errorType.getDefaultMessage(), locale);
    }

    /**
     * <b>错误类型的边界本地化解析(当前线程 Locale)</b>
     *
     * @param errorType 错误类型(不能为 null)
     * @param args      消息参数
     * @return 本地化消息,或默认模板渲染结果
     */
    public static String localize(ErrorTypeInterface errorType, Object... args) {
        return localize(errorType, args, getLocale());
    }
```

- [ ] **Step 3b: 写入 locale/package-info.java(全新——源"Depends on: nothing"失实)**

```java
/**
 * <h2>cn.code91.facility.locale</h2>
 *
 * <p><b>Purpose:</b> i18n facade over Spring {@code MessageSource}
 * ({@code LocaleUtil}: translate / translate-with-fallback / boundary
 * localization of {@code ErrorTypeInterface} per ADR-0010), plus
 * {@code AggregatedMessageSource} — first-hit composition of module message
 * sources with explicit {@code @Order} sorting (RV2-21), exposed as the
 * primary {@code messageSource} by the Locale autoconfiguration.</p>
 *
 * <p><b>Entry classes:</b> {@code LocaleUtil}, {@code AggregatedMessageSource}.</p>
 *
 * <p><b>Depends on:</b> {@code common} ({@code NullSafe}), {@code context}
 * ({@code SpringContextHolder} bean lookup), {@code error}
 * ({@code localize(ErrorTypeInterface, ...)} — the C1 boundary-localization
 * entry, keeping the dependency cone {@code error ← context ← locale}).</p>
 *
 * <p><b>Depended on by:</b> {@code web} (exception handler localization,
 * arriving in P6), {@code autoconfigure}, downstream application code.</p>
 */
package cn.code91.facility.locale;
```

- [ ] **Step 4: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 464`(453 + 11),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: C1 locale 侧衔接——LocaleUtil.localize(ErrorTypeInterface) 边界解析入口(ADR-0010)

11 个用例:无 Spring 回退语义镜像 + 真实 facility bundle 英/中命中与未命中集成;
锥形依赖 error ← context ← locale 成立(ArchUnit error 纯度规则不受影响)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 3: async 簇迁移

**Files:**
- Create(迁移): 4 个测试 → `$DST\src\test\java\cn\code91\facility\async\{AsyncTest,AsyncContextTest,AsyncInterceptorTest,AggregateExceptionTest}.java`
- Create(迁移): 6 个主文件 → `Async/AsyncContext/AsyncInterceptor/AsyncInvocation/DefaultAsync/AggregateException`(零编辑)
- Create(全新内容): `async\package-info.java`

**Interfaces:**
- Consumes: P1 result(唯一跨包依赖——源 package-info 声称的 error/log/context 均系陈旧)
- Produces:`Async`(编排门面)/`DefaultAsync`(虚拟线程 executor + 拦截器责任链)/`AsyncContext`(手动透传钩子,默认不自动捕获 MDC——RV2-D1)/`AsyncInterceptor` SPI/`AsyncInvocation`/`AggregateException`(any 聚合全部错因)

- [ ] **Step 1: 迁移 4 个测试(标准命令)**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\async\AsyncTest.java` → `$DST\src\test\java\cn\code91\facility\async\AsyncTest.java`
2. `$SRC\...\async\AsyncContextTest.java` → 同布局
3. `$SRC\...\async\AsyncInterceptorTest.java` → 同布局
4. `$SRC\...\async\AggregateExceptionTest.java` → 同布局

- [ ] **Step 2: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol`(Async/AsyncContext/...)

- [ ] **Step 3: 迁移 6 个主文件(标准命令,零编辑);写入 package-info**

1. `$SRC\...\async\Async.java` → `$DST\src\main\java\cn\code91\facility\async\Async.java`
2. `$SRC\...\async\AsyncContext.java` → 同布局
3. `$SRC\...\async\AsyncInterceptor.java` → 同布局
4. `$SRC\...\async\AsyncInvocation.java` → 同布局
5. `$SRC\...\async\DefaultAsync.java` → 同布局
6. `$SRC\...\async\AggregateException.java` → 同布局

async/package-info.java(全新——源声称依赖 error/log/context,实况仅 result):

```java
/**
 * <h2>cn.code91.facility.async</h2>
 *
 * <p><b>Purpose:</b> Async task orchestration — {@code Async} facade over
 * {@code DefaultAsync} (virtual-thread executor + interceptor chain),
 * {@code AsyncContext} as an explicit cross-thread propagation hook
 * (MDC / traceId is NOT captured automatically — callers wire it via
 * interceptors, RV2-D1), and {@code AggregateException} collecting all
 * failure causes from {@code any(...)} composition.</p>
 *
 * <p><b>Entry classes:</b> {@code Async}, {@code AsyncContext},
 * {@code AsyncInterceptor} (SPI), {@code AggregateException}.</p>
 *
 * <p><b>Depends on:</b> {@code result} only ({@code Result} as the outcome
 * channel). The source project's package-info claimed error/log/context —
 * import-scan showed those stale; corrected here.</p>
 *
 * <p><b>Depended on by:</b> downstream application code;
 * {@code autoconfigure} supplies the default virtual-thread executor.</p>
 */
package cn.code91.facility.async;
```

- [ ] **Step 4: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 554`(464 + 90),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 async 簇(虚拟线程编排 + 拦截器链 + AsyncContext 钩子),90 个迁移测试

package-info 据实重写(源声称 error/log/context 依赖,实况仅 result)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 4: Core/Locale/Async 三装配迁移

**Files:**
- Create(迁移): 3 个测试 → `$DST\src\test\java\cn\code91\facility\autoconfigure\{FacilityCoreAutoConfigurationTest,FacilityLocaleAutoConfigurationTest,FacilityAsyncAutoConfigurationTest}.java`
- Create(迁移+1 处编辑): `FacilityCoreAutoConfiguration.java`(stele 残留);(迁移零编辑): `FacilityLocaleAutoConfiguration.java` / `FacilityAsyncAutoConfiguration.java`
- Modify: imports 文件(追加 3 行 → 共 5 行)

**Interfaces:**
- Consumes: P2 context/log、T1 locale、T3 async(装配测试引用 DefaultAsync)
- Produces: classpath 即得 —— `SpringContextHolder` bean(ApplicationContextAware 注入)、`LogPostHandlerComposite`(聚合全部 LogPostHandler)、`facilityMessageSource`(i18n/facility-messages bundle)、primary `messageSource`(AggregatedMessageSource,@AutoConfigureBefore Boot MessageSourceAutoConfiguration)、`facilityAsyncExecutor`(虚拟线程,@ConditionalOnMissingBean(TaskExecutor) 类型匹配,ADR-0002)

- [ ] **Step 1: 迁移 3 个测试(标准命令)**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\autoconfigure\FacilityCoreAutoConfigurationTest.java` → `$DST\src\test\java\cn\code91\facility\autoconfigure\FacilityCoreAutoConfigurationTest.java`
2. `$SRC\...\autoconfigure\FacilityLocaleAutoConfigurationTest.java` → 同布局
3. `$SRC\...\autoconfigure\FacilityAsyncAutoConfigurationTest.java` → 同布局

- [ ] **Step 2: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol`(FacilityCoreAutoConfiguration/...)

- [ ] **Step 3: 迁移 3 个装配类;Core 执行 1 处编辑;imports 追加**

1. `$SRC\...\autoconfigure\FacilityCoreAutoConfiguration.java` → `$DST\src\main\java\cn\code91\facility\autoconfigure\FacilityCoreAutoConfiguration.java`,然后编辑(stele 残留)—— old:

```java
 * No conditions — these are always activated when stele-facility is on the classpath.
```

new:

```java
 * No conditions — these are always activated when server-facility is on the classpath.
```

2. `$SRC\...\autoconfigure\FacilityLocaleAutoConfiguration.java` → 同布局(零编辑)
3. `$SRC\...\autoconfigure\FacilityAsyncAutoConfiguration.java` → 同布局(零编辑)

imports 文件编辑 —— old:

```
cn.code91.facility.autoconfigure.FacilityIdAutoConfiguration
cn.code91.facility.autoconfigure.FacilityJsonAutoConfiguration
```

new:

```
cn.code91.facility.autoconfigure.FacilityIdAutoConfiguration
cn.code91.facility.autoconfigure.FacilityJsonAutoConfiguration
cn.code91.facility.autoconfigure.FacilityCoreAutoConfiguration
cn.code91.facility.autoconfigure.FacilityLocaleAutoConfiguration
cn.code91.facility.autoconfigure.FacilityAsyncAutoConfiguration
```

- [ ] **Step 4: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 561`(554 + 7),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 Core/Locale/Async 三装配 + imports 注册(共 5 项)

SpringContextHolder/LogPostHandlerComposite/facilityMessageSource/
primary messageSource(Aggregated)/虚拟线程 executor 齐装;
Core javadoc 修正 stele 残留(授权编辑)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## 验收清单(P5 出口)

- [ ] `mvn test` 全绿,561 个用例(P4 450 + locale 14 + async 90 + 装配 7),0 失败 0 跳过
- [ ] `LocaleUtil.localize(FacilityErrorType.X, args, ENGLISH)` 经真实 bundle 命中英文文案(集成用例在绿名单)——C1 决策 2 落地
- [ ] imports 文件恰好 5 行;非 web 装配全部就位;ArchUnit 四规则绿(error 纯度不受 locale→error 影响——方向正确)
- [ ] locale/async 两个 package-info 依赖声明与 import 实况一致(async 陈旧三项已纠)
- [ ] T1 迁移与 T2 rework 独立 commit;提交信息无 @ 包裹;报告转录可交叉核对
