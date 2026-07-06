# 批次 2 fix/web-hardening 实施计划(F7/F17/F19/F16/F9)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** web 簇安全与健壮性五项:F7 TraceIdFilter 入站 X-Trace-Id 白名单校验(防日志伪造/CRLF 注入)、F17 RepeatableRequestFilter catch 收窄(下游同型异常不再被误转 413)、F19 RepeatableRequestWrapper.getInputStream() 补 available()、F16 IdempotencyInterceptor 响应失配 WARN(配置故障信号)、F9 AccessLogInterceptor XFF caveat(纯文档)。

**Architecture:** 四处小而准的外科手术,零公共 API 签名变化、零新依赖:F7 在既有 if 链前加正则白名单闸门(不匹配≡缺失);F17 把 try 范围从「构造+doFilter」收窄到「仅构造」;F19 匿名 ServletInputStream 补一 override;F16 在既有 instanceof 分支加 else WARN(经 LogUtil,依赖批次 1 的调用方解析);F9 对齐既有 5 处 XFF caveat 口径。

**Tech Stack:** Java 21、JUnit 5 + AssertJ、Spring MockHttpServletRequest/MockHttpServletResponse(既有测试习语)、logback ListAppender(WARN 断言)。

**评审依据:** docs/superpowers/2026-07-05-whole-code-review-findings.md F7/F16/F17/F19/F9(本批无【决策】项)。

## Global Constraints

- 一切 Maven 命令带 `-o`;基线 **1152 绿**(master d3218df);JaCoCo gate 0.88/0.75 must met;ArchUnit 5/5。
- doc-truth 红线:javadoc 声称与代码逐条相符;计数用构建实数,禁止手数。
- TDD:RED/GREEN 均为原始 mvn 输出粘贴(拼凑=任务失败);既有测试零改动零回归。
- 只许改各 Task 点名文件;提交一律 `git commit -F <消息文件>`(UTF-8);无新第三方依赖。
- 测试毒化纪律:测试内附加的 appender 必须 finally detach;不操纵全局级别。
- 脱敏注意:F16 WARN 消息措辞已核对不含 MaskUtil SECRET 关键词(password/passwd/pwd/token/secret/api-key/authorization/access-token),不得改写为含这些词的措辞(写前脱敏会改消息导致断言失真)。

---

### Task 1: F7 — TraceIdFilter 入站 X-Trace-Id 白名单校验

**Files:**
- Modify: `src/main/java/cn/code91/facility/web/filter/TraceIdFilter.java`
- Create: `src/test/java/cn/code91/facility/web/filter/TraceIdFilterTest.java`

**Interfaces:**
- Consumes: `FacilityWebTraceProperties`(默认 headerName=X-Trace-Id / mdcKey=traceId / generateIfAbsent=true,测试用默认构造)。
- Produces: 行为变化=不合法入站值按「缺失」处理走重新生成;合法值([0-9A-Za-z_-]{1,64})原样透传。签名零变化。

**背景(F7):** 现实现把入站 X-Trace-Id 未经校验反射进 MDC 与响应头——CRLF、超长、控制字符可注入日志与响应头,与全库对 XFF 的警惕口径反差。

- [ ] **Step 1: 新建失败测试 TraceIdFilterTest**

```java
package cn.code91.facility.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceIdFilter - 入站 X-Trace-Id 校验(F7:防日志伪造/CRLF 注入)")
class TraceIdFilterTest {

    /** 重新生成的 traceId 形态:UUID 去连字符 = 32 位小写 hex */
    private static final String REGENERATED = "[0-9a-f]{32}";

    private final FacilityWebTraceProperties props = new FacilityWebTraceProperties();
    private final TraceIdFilter filter = new TraceIdFilter(props);

    private String runAndCaptureMdc(MockHttpServletRequest req, MockHttpServletResponse resp) throws Exception {
        AtomicReference<String> mdcSeen = new AtomicReference<>();
        filter.doFilterInternal(req, resp, (rq, rs) -> mdcSeen.set(MDC.get(props.getMdcKey())));
        return mdcSeen.get();
    }

    @Test
    @DisplayName("合法值([0-9A-Za-z_-]):原样透传至 MDC 与响应头(锁定,旧新行为一致)")
    void validInboundTraceId_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(props.getHeaderName(), "abc_DEF-0123456789");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String mdc = runAndCaptureMdc(req, resp);

        assertThat(mdc).isEqualTo("abc_DEF-0123456789");
        assertThat(resp.getHeader(props.getHeaderName())).isEqualTo("abc_DEF-0123456789");
    }

    @Test
    @DisplayName("CRLF 注入串:按缺失处理,MDC 与响应头均为新生成 32 位 hex")
    void crlfInjection_regenerated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(props.getHeaderName(), "abc\r\nX-Evil: 1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String mdc = runAndCaptureMdc(req, resp);

        assertThat(mdc).matches(REGENERATED).isNotEqualTo("abc\r\nX-Evil: 1");
        assertThat(resp.getHeader(props.getHeaderName())).isEqualTo(mdc);
    }

    @Test
    @DisplayName("长度边界:64 位合法透传;65 位重新生成")
    void lengthBoundary_64ok_65regenerated() throws Exception {
        String ok64 = "a".repeat(64);
        MockHttpServletRequest req64 = new MockHttpServletRequest();
        req64.addHeader(props.getHeaderName(), ok64);
        assertThat(runAndCaptureMdc(req64, new MockHttpServletResponse())).isEqualTo(ok64);

        String over65 = "a".repeat(65);
        MockHttpServletRequest req65 = new MockHttpServletRequest();
        req65.addHeader(props.getHeaderName(), over65);
        MockHttpServletResponse resp65 = new MockHttpServletResponse();
        String mdc = runAndCaptureMdc(req65, resp65);
        assertThat(mdc).matches(REGENERATED);
        assertThat(resp65.getHeader(props.getHeaderName())).isEqualTo(mdc);
    }

    @Test
    @DisplayName("非 ASCII 与控制字符:重新生成")
    void nonAsciiOrControl_regenerated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(props.getHeaderName(), "跟踪-id");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String mdc = runAndCaptureMdc(req, resp);

        assertThat(mdc).matches(REGENERATED);
        assertThat(resp.getHeader(props.getHeaderName())).isEqualTo(mdc);
    }
}
```

说明:MDC 在 filter finally 中清理,断言值在 chain lambda 内捕获;`doFilterInternal` 为 protected,同包测试直调(对齐 RepeatableRequestFilter413Test 习语)。

- [ ] **Step 2: 跑测试确认 RED(3 红 1 锁)**

```
mvn -o test -Dtest=TraceIdFilterTest
```

预期:crlf/长度 65 段/非 ASCII 三测失败(旧代码原样透传,mdc==入站值不匹配 hex 形态);validInboundTraceId 与 64 位段绿(锁定)。粘贴原始输出。

- [ ] **Step 3: 实现白名单闸门**

`TraceIdFilter.java`:

**3a. 新增常量与 import**(`java.util.regex.Pattern`):

```java
    /**
     * 入站 trace id 白名单:1-64 位 {@code [0-9A-Za-z_-]}。
     * 不匹配(CRLF/控制字符/超长/非 ASCII/空白)按「缺失」处理走重新生成——
     * 防止日志伪造与响应头注入(F7;与全库 XFF caveat 同一警惕口径)。
     */
    private static final Pattern VALID_INBOUND_TRACE_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");
```

**3b. doFilterInternal 首段改为**:

```java
        String inboundTraceId = request.getHeader(props.getHeaderName());
        String traceId;
        if (inboundTraceId != null && VALID_INBOUND_TRACE_ID.matcher(inboundTraceId).matches()) {
            traceId = inboundTraceId;
        } else if (props.isGenerateIfAbsent()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        } else {
            traceId = null;
        }
```

(原 `!inboundTraceId.isBlank()` 判断被正则吸收:空白必不匹配。)

**3c. 类 javadoc 补安全段**(在日志配置示例之后):

```java
 * <p><b>⚠️ 安全:</b>入站 trace id 仅在匹配 {@code [0-9A-Za-z_-]{1,64}} 时透传;
 * 不匹配(含 CRLF、控制字符、超长、非 ASCII)一律按缺失处理并重新生成,
 * 防止日志伪造与响应头注入。</p>
```

- [ ] **Step 4: GREEN**

```
mvn -o test -Dtest=TraceIdFilterTest
```

预期:5 断言组全绿。

- [ ] **Step 5: 全量测试**

```
mvn -o test
```

预期:BUILD SUCCESS,总数 = 1152 + 4 = 1156(以实数为准)。

- [ ] **Step 6: 提交**

```
git add src/main/java/cn/code91/facility/web/filter/TraceIdFilter.java src/test/java/cn/code91/facility/web/filter/TraceIdFilterTest.java
git commit -F <消息文件>
```

消息:

```
fix: TraceIdFilter 入站 X-Trace-Id 白名单校验(F7)

入站值未经校验反射进 MDC 与响应头,CRLF/超长/控制字符可注入日志与响应头。
加 [0-9A-Za-z_-]{1,64} 白名单闸门,不匹配按缺失处理走重新生成;合法值原样
透传。测试:CRLF/65 位/非 ASCII 重生成 + 合法值与 64 位边界透传锁定。
```

---

### Task 2: F17+F19 — RepeatableRequest catch 收窄 + available() 覆写

**Files:**
- Modify: `src/main/java/cn/code91/facility/web/filter/RepeatableRequestFilter.java`
- Modify: `src/main/java/cn/code91/facility/web/filter/RepeatableRequestWrapper.java`
- Test: `src/test/java/cn/code91/facility/web/filter/RepeatableRequestFilter413Test.java`(追加 1 测)
- Test: `src/test/java/cn/code91/facility/web/filter/RepeatableRequestWrapperLimitTest.java`(追加 1 测)

**Interfaces:**
- Consumes: `PayloadTooLargeException`(RuntimeException,构造 `(long actualBytes, long limitBytes)`)。
- Produces: 413 仅由本 filter 的包装构造超限触发;下游同型异常原样穿透。`getInputStream().available()` 反映剩余缓存字节。签名零变化。

**背景:** F17——catch 把 `filterChain.doFilter` 也网罗,下游任何 PayloadTooLargeException 被误判为本 filter 的 413;F19——匿名 ServletInputStream 未覆写 available()(InputStream 默认返 0),消费方按 available 预分配/探测会误判空体。

- [ ] **Step 1: 两个失败测试**

`RepeatableRequestFilter413Test.java` 追加(需补 import `static org.assertj.core.api.Assertions.assertThatThrownBy;`):

```java
    @Test @DisplayName("下游抛 PayloadTooLargeException:原样穿透,不被误转 413(F17)")
    void downstreamPayloadTooLarge_propagates_notConvertedTo413() throws Exception {
        FacilityWebRepeatableRequestProperties props = new FacilityWebRepeatableRequestProperties();
        props.setMaxBodyBytes(1000);
        RepeatableRequestFilter filter = new RepeatableRequestFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x");
        req.setContentType("application/json");
        req.setContent("{}".getBytes(StandardCharsets.UTF_8)); // 远小于 1000,包装构造不超限
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(req, resp,
                (rq, rs) -> { throw new PayloadTooLargeException(9, 1); }))
                .isInstanceOf(PayloadTooLargeException.class);
        assertThat(resp.getStatus()).isNotEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }
```

`RepeatableRequestWrapperLimitTest.java` 追加(沿用该文件既有 import 习语;如缺 MockHttpServletRequest/StandardCharsets import 则补):

```java
    @Test @DisplayName("getInputStream().available():读前=缓存体长度,读尽=0,新流复位(F19)")
    void available_reflectsRemainingBytes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/x");
        req.setContent("hello".getBytes(StandardCharsets.UTF_8));
        RepeatableRequestWrapper wrapper = new RepeatableRequestWrapper(req, 100);

        var in = wrapper.getInputStream();
        assertThat(in.available()).isEqualTo(5);
        assertThat(in.readAllBytes()).hasSize(5);
        assertThat(in.available()).isZero();
        assertThat(wrapper.getInputStream().available()).isEqualTo(5);
    }
```

- [ ] **Step 2: RED**

```
mvn -o test -Dtest=RepeatableRequestFilter413Test,RepeatableRequestWrapperLimitTest
```

预期:新增 2 测均红——F17 测:旧 catch 吞异常写 413,`assertThatThrownBy` 报「期望异常未抛」;F19 测:默认 available()=0,首断言 0≠5。既有测试绿。粘贴原始输出。

- [ ] **Step 3: 实现**

**3a. RepeatableRequestFilter.doFilterInternal 整方法体替换为**(try 收窄到构造):

```java
        if (!shouldWrap(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        RepeatableRequestWrapper wrappedRequest;
        try {
            wrappedRequest = new RepeatableRequestWrapper(request, props.getMaxBodyBytes());
        } catch (PayloadTooLargeException ex) {
            // 413 仅对应本 filter 的包装构造超限;下游同型异常不在此网罗(F17)
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            byte[] body = ERROR_MAPPER.writeValueAsBytes(
                    java.util.Map.of("code", 413, "message", ex.getMessage()));
            response.getOutputStream().write(body);
            return;
        }
        filterChain.doFilter(wrappedRequest, response);
```

**3b. RepeatableRequestWrapper 匿名 ServletInputStream 补覆写**(加在 `read()` 之后):

```java
                @Override
                public int available() {
                    return bais.available();
                }
```

- [ ] **Step 4: GREEN**

```
mvn -o test -Dtest=RepeatableRequestFilter413Test,RepeatableRequestWrapperLimitTest
```

- [ ] **Step 5: 全量测试**

```
mvn -o test
```

预期:BUILD SUCCESS,总数 = Task 1 后基线 + 2(以实数为准)。

- [ ] **Step 6: 提交**

```
git add src/main/java/cn/code91/facility/web/filter/RepeatableRequestFilter.java src/main/java/cn/code91/facility/web/filter/RepeatableRequestWrapper.java src/test/java/cn/code91/facility/web/filter/RepeatableRequestFilter413Test.java src/test/java/cn/code91/facility/web/filter/RepeatableRequestWrapperLimitTest.java
git commit -F <消息文件>
```

消息:

```
fix: RepeatableRequest catch 收窄至包装构造 + available() 覆写(F17/F19)

F17:catch 原网罗 filterChain.doFilter,下游同型 PayloadTooLargeException
被误判为本 filter 的 413;try 收窄到 wrapper 构造,下游异常原样穿透。
F19:匿名 ServletInputStream 未覆写 available()(默认恒 0),补返剩余
缓存字节;读前=体长/读尽=0/新流复位三向锁定。
```

---

### Task 3: F16+F9 — Idempotency 失配 WARN + AccessLog XFF caveat

**Files:**
- Modify: `src/main/java/cn/code91/facility/web/idempotency/IdempotencyInterceptor.java`
- Modify: `src/main/java/cn/code91/facility/web/interceptor/AccessLogInterceptor.java`(仅 javadoc)
- Test: `src/test/java/cn/code91/facility/web/idempotency/IdempotencyInterceptorTest.java`(追加 1 测)

**Interfaces:**
- Consumes: `LogUtil.warn(String, Object...)`(批次 1 后调用方解析=本类,事件 logger 名=IdempotencyInterceptor FQCN);`InMemoryIdempotencyStore(int)`、`IdempotencyRecord.State.PROCESSING`、测试夹具 `handlerMethodFor("annotated")` + 头名 `Idempotency-Key`(均既有)。
- Produces: 失配路径行为=WARN + 不写记录不崩溃(占位 PROCESSING 存续至 TTL);正常路径零变化。

**背景:** F16——afterCompletion 在响应非 ContentCachingResponseWrapper 时静默 no-op(IdempotencyFilter 未装配/顺序错的配置故障被吞,占位卡 PROCESSING 到 TTL);F9——访问日志记录的 IP 来自信任 XFF 的 RequestUtil.getClientIp,javadoc 无警示,与全库 5 处 XFF caveat 口径反差。

- [ ] **Step 1: 失败测试(F16)**

`IdempotencyInterceptorTest.java` 追加(需补 import:`ch.qos.logback.classic.spi.ILoggingEvent`、`ch.qos.logback.core.read.ListAppender`、`org.slf4j.LoggerFactory`):

```java
    @Test
    @DisplayName("失配:响应非 ContentCachingResponseWrapper(Filter 未装配/顺序错)→ WARN + 不崩,记录保持 PROCESSING(F16)")
    void afterCompletion_withoutWrapper_warnsAndLeavesProcessing() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "k-mismatch");
        MockHttpServletResponse response = new MockHttpServletResponse(); // 刻意不包 ContentCachingResponseWrapper

        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            assertThat(interceptor.preHandle(request, response, hm)).isTrue();
            assertThatCode(() -> interceptor.afterCompletion(request, response, hm, null))
                    .doesNotThrowAnyException();

            assertThat(appender.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(e.getLoggerName()).isEqualTo(IdempotencyInterceptor.class.getName());
                assertThat(e.getFormattedMessage()).contains("ContentCachingResponseWrapper").contains("k-mismatch");
            });
            assertThat(store.find("k-mismatch")).isPresent();
            assertThat(store.find("k-mismatch").get().state()).isEqualTo(IdempotencyRecord.State.PROCESSING);
        } finally {
            root.detachAppender(appender);
        }
    }
```

- [ ] **Step 2: RED**

```
mvn -o test -Dtest=IdempotencyInterceptorTest
```

预期:新测失败于 `anySatisfy`(旧代码静默 no-op,无 WARN 事件);既有测试绿。粘贴原始输出。

- [ ] **Step 3: 实现**

**3a. IdempotencyInterceptor**:import `cn.code91.facility.log.LogUtil;`;afterCompletion 的 instanceof 块补 else:

```java
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            byte[] body = wrapper.getContentAsByteArray();
            long ttl = (long) request.getAttribute(ATTR_TTL);   // 与 preHandle 占位同一 ttl,尊重 @Idempotent.ttlSeconds
            store.complete(key, IdempotencyRecord.done(wrapper.getStatus(), wrapper.getContentType(), body,
                    System.currentTimeMillis() + ttl));
        } else {
            // 配置故障信号:IdempotencyFilter 未装配或顺序错乱,响应未被包装——无法捕获响应体,
            // 本次结果不落 DONE 记录;占位 PROCESSING 存续至 TTL 到期(期间同 key 一律 409)。
            LogUtil.warn("[Idempotency] response is not ContentCachingResponseWrapper "
                    + "(IdempotencyFilter missing or misordered); key={} left PROCESSING until TTL expiry, "
                    + "response not cached", key);
        }
```

类 javadoc 的 afterCompletion 段落(「仅在方法体正常完成…」那段)句末补一句:

```
 * 响应未被包装(Filter 未装配/顺序错)时记 WARN 并跳过缓存——该场景是配置故障信号。
```

**3b. AccessLogInterceptor 类 javadoc**(日志输出示例 pre 块之后)补:

```java
 * <p><b>⚠️ 安全:</b>日志中的客户端 IP 来自 {@link RequestUtil#getClientIp},该方法无条件信任
 * 可被客户端伪造的 {@code X-Forwarded-For} / {@code X-Real-IP} 代理头——公网直连(前面没有
 * 覆写 XFF 的受信反代)部署下,访问日志中的 IP 不可作为审计/取证依据。</p>
```

- [ ] **Step 4: GREEN**

```
mvn -o test -Dtest=IdempotencyInterceptorTest
```

- [ ] **Step 5: 全量测试**

```
mvn -o test
```

预期:BUILD SUCCESS,总数 = Task 2 后基线 + 1(以实数为准)。

- [ ] **Step 6: 提交**

```
git add src/main/java/cn/code91/facility/web/idempotency/IdempotencyInterceptor.java src/main/java/cn/code91/facility/web/interceptor/AccessLogInterceptor.java src/test/java/cn/code91/facility/web/idempotency/IdempotencyInterceptorTest.java
git commit -F <消息文件>
```

消息:

```
fix: IdempotencyInterceptor 响应失配 WARN(F16)+ AccessLog XFF caveat(F9)

F16:afterCompletion 在响应非 ContentCachingResponseWrapper 时静默 no-op,
Filter 未装配/顺序错的配置故障被吞(占位卡 PROCESSING 到 TTL)。补 WARN
(经 LogUtil,消息措辞已核不触 SECRET 脱敏),失配测试锁定 WARN+不崩+
记录保持 PROCESSING。F9:访问日志 IP 来自信任 XFF 的 getClientIp,javadoc
补与全库口径一致的伪造警示(纯文档)。
```

---

### Task 4: 收口 — 全量 verify + 计数同步(控制器亲自)

**Files:**
- Modify: `README.md`(测试计数)
- Modify: `docs/DESIGN.md`(测试计数)
- Modify: `docs/USAGE.md`(装配开关表 trace 段补白名单注释;测试计数如有)

**Steps:**
- [ ] `mvn -o clean verify` → BUILD SUCCESS、gate met、analyze 零 warning、ArchUnit 5/5;取实测总数(预期 1152+7=1159,以实数为准)
- [ ] `grep -n "1152" README.md docs/DESIGN.md docs/USAGE.md` → 全部替换为实数
- [ ] USAGE 装配开关表 `header-name: X-Trace-Id` 行尾补注释 `# 入站值须匹配 [0-9A-Za-z_-]{1,64},否则按缺失重新生成(F7)`
- [ ] 提交(git commit -F):`docs: 批次 2 收口——计数同步 + USAGE trace 白名单注释`

---

## 验收(整分支)

1. `mvn -o clean verify` 全绿(≈1159)、gate 0.88/0.75 met、analyze 零 warning、ArchUnit 5/5;
2. 行为验收:CRLF/超长/非 ASCII 入站 trace id 全部重生成、合法透传;下游 PayloadTooLargeException 穿透;available() 三态;幂等失配 WARN+PROCESSING 存续;
3. doc-truth:TraceIdFilter/IdempotencyInterceptor/AccessLogInterceptor javadoc 与实况相符;
4. 整分支 opus 终审 → 修复(如有)→ merge --no-ff master → 复验 → 删分支 → 台账/记忆。
