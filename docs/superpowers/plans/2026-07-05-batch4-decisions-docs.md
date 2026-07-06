# 批次 4 docs/decisions-batch 实施计划(F6/F8/F10/F18/F20-F24/F32/F33/F39/F40/F37)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 决策落地三件(F8 锁/幂等 fail-closed、F10 降级哨兵 -1、F22 http enabled 开关)+ crypto 两补测(F37)+ 十一处文档件(F6/F18/F20/F21/F23/F24/F32/F33/F39/F40/F37 文档项)。七项【决策】已由用户裁定,全取推荐 a(2026-07-05)。

**Architecture:** F8 的不对称原则写进 ADR:锁与幂等是**正确性组件**(超限 fail-closed:锁纯拒绝——ReentrantLock 无法安全逐出;幂等先清过期条目再拒——防过期尸体致永久拒新),限流是**保护组件**(维持 clear-all fail-open)。F10/F22 是一行级行为修;其余全部文档/注释/补测。

**评审依据:** docs/superpowers/2026-07-05-whole-code-review-findings.md;决策:F8=a/F10=a/F18=a/F20=a/F22=a/F32=a/F40=a。

## Global Constraints

- 一切 Maven 命令带 `-o`;基线 **1169 绿**(master 24cf521);gate 0.88/0.75;ArchUnit 5/5。
- doc-truth 红线;计数用构建实数;WARN 文案不触 MaskUtil SECRET 关键词表(password/passwd/pwd/token/secret/api-key/authorization/access-token)。
- TDD(行为件):RED/GREEN 原始输出。**本批授权修改的既有测试仅限**:锁溢出测试(maxLocks_exceeded_clears 类,语义随 F8 变)、幂等溢出测试(如有同型)、RateLimiterUtil 降级哨兵断言(如锁 MAX_VALUE)——每处改动须在报告中引旧断言原文+改动理由;其余既有测试零改动。
- 只许改各 Task 点名文件;提交 `git commit -F`(UTF-8);零新依赖。

---

### Task 1: F8(决策 a)— 锁/幂等 fail-closed + ADR-0016/0017 修订

**Files:**
- Modify: `src/main/java/cn/code91/facility/lock/InMemoryDistributedLock.java`
- Modify: `src/main/java/cn/code91/facility/idempotency/InMemoryIdempotencyStore.java`
- Modify: `docs/adr/0016-distributed-lock-seam.md`(追加 fail-closed 决策段)
- Modify: `docs/adr/0017-idempotency-full-semantics-response-capture.md`(追加一段)
- Test: `src/test/java/cn/code91/facility/lock/InMemoryDistributedLockTest.java`(改写溢出测试+新增)
- Test: `src/test/java/cn/code91/facility/idempotency/InMemoryIdempotencyStoreTest.java`(改写/新增)

**Interfaces:**
- Produces: `tryLock`:超限且新 key → WARN + 返 false(不再 clear);`tryBegin`:超限且新 key → 先 removeIf 过期,复查仍超限 → WARN + 返 false。既有 key 路径零变化。

- [ ] **Step 1: 改写/新增测试(先红)**

`InMemoryDistributedLockTest.java`:找到锁定 clear-all 旧语义的溢出测试(名类 `maxLocks_exceeded_clears`),**改写**为 fail-closed 语义(报告引旧断言原文):

```java
    @Test
    @DisplayName("F8 决策 a:超限 fail-closed——新 key 拒绝(tryLock false),在途持锁互斥不破")
    void maxLocksExceeded_failClosed_rejectsNewKeyAndPreservesHeldLocks() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(2);
        assertThat(lock.tryLock("k1", Duration.ofMillis(10))).isTrue();
        assertThat(lock.tryLock("k2", Duration.ofMillis(10))).isTrue();   // 达上限 2

        // 新 key 拒绝,不清空
        assertThat(lock.tryLock("k3", Duration.ofMillis(10))).isFalse();

        // 在途互斥不破:另一线程抢 k1 必失败(旧 clear-all 下 k1 的锁对象被清,新对象可得——互斥破)
        java.util.concurrent.atomic.AtomicBoolean stolen = new java.util.concurrent.atomic.AtomicBoolean();
        Thread thief = new Thread(() -> stolen.set(lock.tryLock("k1", Duration.ofMillis(50))));
        thief.start();
        thief.join(1_000);
        assertThat(stolen).isFalse();

        // 既有 key 的正常操作不受影响
        lock.unlock("k1");
        lock.unlock("k2");
    }
```

`InMemoryIdempotencyStoreTest.java`:改写既有溢出测试(如有)并新增两测:

```java
    @Test
    @DisplayName("F8 决策 a:超限且无过期可清 → tryBegin 拒绝(false),在途记录不被清")
    void maxEntriesExceeded_failClosed_rejectsNewKey_preservesInFlight() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        assertThat(store.tryBegin("a", 60_000)).isTrue();
        assertThat(store.tryBegin("b", 60_000)).isTrue();   // 达上限,均未过期

        assertThat(store.tryBegin("c", 60_000)).isFalse();  // 拒绝新 key
        assertThat(store.find("a")).isPresent();            // 在途 PROCESSING 未被清
        assertThat(store.find("b")).isPresent();
    }

    @Test
    @DisplayName("F8:超限但存在过期条目 → 先清过期再放行新 key(防过期尸体致永久拒新)")
    void maxEntriesExceeded_purgesExpiredThenAdmits() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        assertThat(store.tryBegin("stale1", 1)).isTrue();   // 1ms TTL,立即过期
        assertThat(store.tryBegin("stale2", 1)).isTrue();
        Thread.sleep(10);                                    // 让两条过期(≤10ms,非长 sleep)

        assertThat(store.tryBegin("fresh", 60_000)).isTrue(); // 清过期后放行
        assertThat(store.find("fresh")).isPresent();
    }
```

(若该测试类无既有溢出测试则纯新增;`Duration`/并发 import 按需补。)

- [ ] **Step 2: RED**:`mvn -o test -Dtest=InMemoryDistributedLockTest,InMemoryIdempotencyStoreTest`(新/改测试红:旧代码 clear 后 k3/c 会成功、k1 可被偷;粘贴原始输出)

- [ ] **Step 3: 实现**

**3a. InMemoryDistributedLock.tryLock 溢出分支**:

```java
        if (locks.size() >= maxLocks && !locks.containsKey(key)) {
            // fail-closed(F8 决策 a):拒绝新建而非清空——清空会打破在途持锁互斥。
            // 锁是正确性组件;ReentrantLock 无法安全逐出(判定"未持有"与移除之间存在竞态)。
            // 达上限意味着 key 设计失当或上限过低,持续 WARN 使故障显性(ADR-0016)。
            LogUtil.warn("locks exceeded {}, rejecting new lock key (fail-closed)", maxLocks);
            return false;
        }
```

类 javadoc「无界防护」段替换为:

```java
 * <h3>无界防护(fail-closed,F8):</h3>
 * <p>
 * key 基数不可控时,锁集合可能无界增长。锁数达到 {@code maxLocks} 且待建 key 不在集合中时,
 * <b>拒绝新建</b>({@code tryLock} 返 {@code false})并记 WARN——在途持锁互斥永不因防护被打破。
 * 集合无逐出:达上限后新 key 将持续被拒,须修正 key 设计或调高上限(对照限流 clear-all
 * fail-open 的不对称有理:锁是正确性组件,限流是保护组件——ADR-0016)。
 * </p>
```

**3b. InMemoryIdempotencyStore.tryBegin 溢出分支**:

```java
        long now = System.currentTimeMillis();
        if (store.size() >= maxEntries && !store.containsKey(key)) {
            // fail-closed(F8 决策 a):先清过期条目(防过期尸体致永久拒新),复查仍超限则拒绝——
            // 清空会把在途 PROCESSING 一并抹掉,打开并发重复执行窗口(幂等是正确性组件,ADR-0017)。
            store.entrySet().removeIf(e -> e.getValue().isExpired(now));
            if (store.size() >= maxEntries) {
                LogUtil.warn("idempotency entries exceeded {}, rejecting new key (fail-closed)", maxEntries);
                return false;
            }
        }
```

类 javadoc「无界防护」段替换为:

```java
 * <h3>无界防护(fail-closed,F8):</h3>
 * <p>
 * 记录数达到 {@code maxEntries} 且待建 key 不在集合中时,先清除已过期条目;若仍达上限则
 * <b>拒绝占位</b>({@code tryBegin} 返 {@code false},web 侧表现为 409)并记 WARN——在途
 * PROCESSING/未过期 DONE 永不因防护被清(清空会打开并发重复执行窗口)。对照限流 clear-all
 * fail-open 的不对称有理:幂等是正确性组件(ADR-0016/0017)。
 * </p>
```

**3c. ADR-0016 追加段**(文末,标题 `## 修订(2026-07-05,F8 决策 a):溢出防护 fail-closed`):写明——clear-all 打破在途互斥的风险、fail-closed 语义、无逐出的后果(新 key 持续拒绝+WARN)、与限流 fail-open 的不对称原则(正确性组件 vs 保护组件)、幂等同批同向(先清过期再拒)。

**3d. ADR-0017 追加段**(文末,标题 `## 修订(2026-07-05,F8/F39)`):①溢出防护改 fail-closed(引 ADR-0016 原则,tryBegin 拒绝→web 409);②**F39 顺带**:handler 未抛异常但返回 4xx/5xx(如 `ResponseEntity.status(500)`)的响应同样被固化为幂等首响并回放至 TTL——行为可辩护(非异常路径视为业务定论),此处如实记载。

- [ ] **Step 4: GREEN** → **Step 5: 全量 `mvn -o test`**(预期 1169+3 左右,以实数为准;若改写抵消 ±1 以实数为准) → **Step 6: 提交**(消息:)

```
fix: 锁/幂等溢出防护改 fail-closed(F8 决策 a)+ ADR-0016/0017 修订

clear-all 会打破在途持锁互斥/清掉在途 PROCESSING(并发重复执行窗口)。
锁:超限拒新建(无逐出,持续 WARN 显性化);幂等:先清过期条目复查仍超限
才拒(防过期尸体永久拒新)。不对称原则入 ADR:锁/幂等=正确性组件
fail-closed,限流=保护组件维持 clear-all fail-open。ADR-0017 顺带记
F39(非异常 4xx/5xx 固化为幂等首响)。
```

---

### Task 2: F10(决策 a)哨兵 -1 + F22(决策 a)http enabled 开关

**Files:**
- Modify: `src/main/java/cn/code91/facility/ratelimit/RateLimiterUtil.java`
- Modify: `src/main/java/cn/code91/facility/autoconfigure/FacilityHttpAutoConfiguration.java`
- Modify: `src/main/java/cn/code91/facility/http/FacilityHttpProperties.java`
- Test: `src/test/java/cn/code91/facility/ratelimit/RateLimiterUtilTest.java`(降级断言随语义更新,授权)
- Test: `src/test/java/cn/code91/facility/autoconfigure/FacilityHttpAutoConfigurationTest.java`(追加 enabled=false 测)

**Interfaces:**
- Produces: 降级放行结果 `remaining=-1`(未知/降级哨兵,轻 breaking 0.1.0 窗口);`facility.http.enabled=false` 可整体关闭 http 装配(缺省 true 零破坏)。

- [ ] **Step 1: 测试(先红)**

`RateLimiterUtilTest.java`:找到锁定降级 `remaining==Long.MAX_VALUE` 的断言,**更新**为 `-1`(报告引旧断言原文);若无此断言则新增:

```java
    @Test
    @DisplayName("F10 决策 a:无 RateLimiter bean 降级放行,remaining=-1 表「未知/降级」(勿透传为真实配额)")
    void degradedAcquire_returnsMinusOneSentinel() {
        // <沿用该文件既有"无 bean 降级"用例的上下文清理习语(SpringContextHolderTestSupport.reset() 等)>
        RateLimitResult result = RateLimiterUtil.acquire("k", 1, 10, 5.0);
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(-1L);
        assertThat(result.retryAfterMillis()).isZero();
    }
```

`FacilityHttpAutoConfigurationTest.java` 追加(照该文件既有 ContextRunner 习语):

```java
    @Test
    @DisplayName("F22 决策 a:facility.http.enabled=false 整体关闭 http 装配(缺省 true 保持现状)")
    void enabledFalse_disablesHttpAutoConfiguration() {
        <既有 runner>.withPropertyValues("facility.http.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(org.springframework.web.client.RestClient.class));
    }
```

- [ ] **Step 2: RED**:`mvn -o test -Dtest=RateLimiterUtilTest,FacilityHttpAutoConfigurationTest`(F10 测红:实返 MAX_VALUE;F22 测红:无开关时 bean 仍在;粘贴输出)

- [ ] **Step 3: 实现**

**3a. RateLimiterUtil**:L66 `orElse(new RateLimitResult(true, Long.MAX_VALUE, 0))` → `orElse(new RateLimitResult(true, -1, 0))`;javadoc L60-61 的 `{@code remaining = Long.MAX_VALUE}` → `{@code remaining = -1}(未知/降级哨兵——负值不可当真实配额透出,如需响应头请先判负)`。`grep -n "MAX_VALUE" src/main/java/cn/code91/facility/ratelimit/ src/main/java/cn/code91/facility/web/ratelimit/ -r` 复查其余透传点(如拦截器写 X-RateLimit-Remaining 头)——若拦截器直写 remaining 头,在其 javadoc 补一句「降级时为 -1」;报告列出命中与处置。

**3b. FacilityHttpAutoConfiguration**:类上加:

```java
@ConditionalOnProperty(prefix = "facility.http", name = "enabled", havingValue = "true", matchIfMissing = true)
```

(import `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`;类 javadoc 补一句「`facility.http.enabled=false` 可整体关闭(F22,与其余四簇开关对称;缺省 true)」。)

**3c. FacilityHttpProperties**:补字段(对齐其他簇风格):

```java
    /** 是否启用 HTTP client 自动装配。默认 {@code true}。 */
    private boolean enabled = true;
```

- [ ] **Step 4: GREEN** → **Step 5: 全量 `mvn -o test`**(预期 +1~2,以实数为准) → **Step 6: 提交**(消息:)

```
fix: 限流降级哨兵改 -1(F10 决策 a)+ http 簇补 enabled 开关(F22 决策 a)

remaining=Long.MAX_VALUE 透传到响应头会被误读为真实配额,改 -1 表
「未知/降级」(轻 breaking,0.1.0 窗口,javadoc 警示先判负);http 装配补
facility.http.enabled(matchIfMissing=true 零破坏,五簇开关对称)+
enabled=false 装配测试。
```

---

### Task 3: F37 — crypto 两补测(主源零改动)

**Files:**
- Test: `src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`(追加 2 测)

**Interfaces:** Consumes 既有 CryptoUtilTest 习语(Result 断言/错误码常量)。主源零改动——若测试暴露行为与 javadoc 不符,STOP 报 BLOCKED(不许改主源迁就)。

- [ ] **Step 1: 追加两测**(照该文件既有断言习语;错误码/访问器以文件实际为准):

```java
    @Test
    @DisplayName("F37:HMAC 空密钥(byte[0])→ CRYPTO_MAC_ERROR(javadoc 既载,补测锁定)")
    void hmacSha256_emptyKey_returnsMacError() {
        var result = CryptoUtil.hmacSha256("data".getBytes(java.nio.charset.StandardCharsets.UTF_8), new byte[0]);
        assertThat(result.isErr()).isTrue();
        // 错误类型断言照既有 CRYPTO_MAC_ERROR 用例习语
    }

    @Test
    @DisplayName("F37:null 覆盖跨重载对称——byte[] 变体 key null + String 变体 data null(补既有不对称缺口)")
    void hmacSha256_nullCoverage_symmetricAcrossOverloads() {
        assertThat(CryptoUtil.hmacSha256("d".getBytes(java.nio.charset.StandardCharsets.UTF_8), (byte[]) null).isErr()).isTrue();
        assertThat(CryptoUtil.hmacSha256((String) null, "k").isErr()).isTrue();
    }
```

(注:既有覆盖是 byte[] 测 data==null、String 测 key==null——本测补齐镜像两角;若签名歧义需显式 cast 如上。)

- [ ] **Step 2: 运行确认新测绿**(非 TDD 红绿——这是"javadoc 有测无"的补测,行为已存在;`mvn -o test -Dtest=CryptoUtilTest` 全绿即验收,粘贴输出) → **Step 3: 全量 `mvn -o test`**(+2) → **Step 4: 提交**(消息:`test: crypto HMAC 空密钥与 null 跨重载对称补测(F37 前两项,主源零改动)`)

---

### Task 4: 文档件大扫除(11 处,纯 javadoc/注释/USAGE)

**Files:**
- Modify: `src/main/java/cn/code91/facility/async/DefaultAsync.java`(F6 timeout javadoc)
- Modify: `src/main/java/cn/code91/facility/web/filter/RepeatableRequestFilter.java`(F18 注释)
- Modify: `src/main/java/cn/code91/facility/id/IdUtil.java`(F20 javadoc)
- Modify: `src/main/java/cn/code91/facility/context/SpringContextHolder.java`(F21 javadoc)
- Modify: `src/main/java/cn/code91/facility/autoconfigure/FacilityHttpAutoConfiguration.java`(F23 注释)
- Modify: `src/main/java/cn/code91/facility/autoconfigure/FacilityCacheAutoConfiguration.java`(F23 注释)
- Modify: `src/main/java/cn/code91/facility/ratelimit/TokenBucketRateLimiter.java`(F24 javadoc 一句)
- Modify: `src/main/java/cn/code91/facility/lock/InMemoryDistributedLock.java`(F24 一句)
- Modify: `src/main/java/cn/code91/facility/idempotency/InMemoryIdempotencyStore.java`(F24 一句)
- Modify: `src/main/java/cn/code91/facility/json/JsonConfig.java`(F33)
- Modify: `src/main/java/cn/code91/facility/pattern/Patterns.java`(F40 两方法 javadoc)
- Modify: `src/main/java/cn/code91/facility/crypto/CryptoUtil.java`(F37:encrypt/decrypt javadoc「AES-256」如实化)
- Modify: `docs/USAGE.md`(F6/F32/F39/F37 bytes 占位四处)

逐项精确指令(定位以符号为准;每项一句,不得顺带改写周边):

1. **F6** `DefaultAsync` 的 `timeout` 方法 javadoc 补:「**超时仅影响观察侧**:返回的 future 按时超时,但底层计算不被中断,会继续跑完(虚拟线程静默占用)——资源密集/长任务慎用;真取消需可取消句柄,记 roadmap。」USAGE 的 Async 章节(grep `timeout` 定位)补同口径一句。
2. **F18** `RepeatableRequestFilter.ERROR_MAPPER` 字段注释:「有意隔离的私有 mapper(F18 决策 a):413 错误体是固定形状 {code,message},不随宿主 Jackson 定制漂移——确定性优先;勿改经 JsonUtil/上下文 ObjectMapper。」
3. **F20** `IdUtil.getIdGenerator` javadoc 补:「性能特征(F20 决策 a):Spring 未就绪期间每次调用都经全局锁+bean 查找(非闩锁回退)——换取容器迟到也能接上 Spring 配置;仅静态回退场景有此税,有实测热点再优化。」
4. **F21** `SpringContextHolder` 类 javadoc 补:「多 context 语义:先到先得——第二个 context 注入被忽略(仅 WARN)。测试中需要重置时用 test 桥 `SpringContextHolderTestSupport.reset()`(置 null,勿注入活空上下文——refresh 过的上下文自带空 messageSource,会毒化 LocaleUtil)。」USAGE 如有 SpringContextHolder 章节补一句(grep 定位;无则仅 javadoc)。
5. **F23** `FacilityHttpAutoConfiguration` 与 `FacilityCacheAutoConfiguration` 类 javadoc 各补:「无跨簇装配顺序依赖,故不声明 @AutoConfigureAfter(对比 Json/Async/Locale 的显式声明系它们确有依赖)。」
6. **F24** 三处溢出防护 javadoc 各补一句:「上限为 advisory bound:size 检查非原子,并发突发下可瞬时小幅越界(随后回到防护语义)。」(措辞与各自 fail-closed/clear-all 新语义相容。)
7. **F32** USAGE 错误处理章节(grep `getFormattedMessage` 或 `WrappedError` 定位)补:「排障指引:`getFormattedMessage()` 面向用户,不含参数上下文(防路径泄漏,债 1 决议);排障用 `getArgs()` 或 `toString()`(含 args=[...])。」
8. **F33** `JsonConfig` 四个预设工厂方法 javadoc 各补(或类 javadoc 统一一段):「有意不含:`ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`(空串→null 的宽松反序列化)默认关闭——空串是合法值,静默转 null 会掩盖脏数据。」(实施前 grep 确认该特性确实未启用;若有其他"有意不含"项按实况列。)
9. **F39** USAGE 幂等章节补:「非异常的 4xx/5xx(handler 直接 `return ResponseEntity.status(...)`)同样被固化为幂等首响并回放至 TTL——非异常路径视为业务定论;要避免固化请改抛异常(异常路径不缓存)。」(ADR-0017 段已在 Task 1 落。)
10. **F40** `Patterns.findFirstAsMap`/`findAllAsMap` javadoc 各补:「⚠️ `groupNames` 仅对显式命名组 `(?<name>...)` 生效;对位置捕获组会静默返回全 null 值的 map(异常被吸收)——按位置回填/抛错的增强记 roadmap。」
11. **F37 文档两项** `CryptoUtil.encrypt`/`decrypt`(含 decryptToBytes)javadoc 的「AES-256」措辞如实化:「AES-GCM(密钥强度随 SecretKey:16/24/32 字节即 AES-128/192/256;`generateAesKey()` 产 256 位)」;USAGE 编解码示例中未定义的 `bytes` 占位补定义行(grep "bytes" 于 USAGE crypto 章节定位,给出 `byte[] bytes = ...` 示例行或改用已定义变量)。

- [ ] **Step 1:** 逐项落实(每项完成即在报告勾选,附 file:line)
- [ ] **Step 2:** `mvn -o test`(全量绿;纯文档不改计数,以实数为准)
- [ ] **Step 3:** 提交(消息:)

```
docs: 批次 4 文档件十一连(F6/F18/F20/F21/F23/F24/F32/F33/F39/F40/F37)

Async.timeout 观察侧诚实化;ERROR_MAPPER 有意隔离注释;IdUtil 回退性能
特征;SpringContextHolder 多 context 先到先得;http/cache 无顺序依赖注释;
三处 advisory bound;USAGE 排障指引(债1)/幂等非异常固化(F39)/crypto
bytes 占位;JsonConfig 有意不含清单;Patterns 位置组陷阱警示;crypto
javadoc AES 强度如实化。全部决策取 a(用户裁定 2026-07-05)。
```

---

### Task 5: 收口 — verify + 计数同步(控制器亲自)

- [ ] `mvn -o clean verify` → 全门达标,取实数(预期 ≈1169+6)
- [ ] `grep -n "1169" README.md docs/DESIGN.md docs/USAGE.md` → 实数回填
- [ ] README 特性表如涉及锁/幂等溢出行为措辞(grep `clear`/`清空`)→ 对齐 fail-closed 口径;限流行保持 fail-open 描述
- [ ] USAGE `max-buckets` 行注释「超限清空」保持(限流不变);如有锁/幂等对应行注释 → fail-closed 口径
- [ ] 提交:`docs: 批次 4 收口——计数同步 + 溢出防护口径对齐(fail-closed/fail-open 不对称)`

---

## 验收(整分支)

1. verify 全绿、gate met、ArchUnit 5/5;
2. 行为:锁超限拒新且在途互斥不破;幂等先清过期再拒;降级 remaining=-1;http enabled=false 关装配;
3. doc-truth:ADR-0016/0017 修订段与实现一致;11 处文档件逐条与代码相符;fail-closed/fail-open 口径全库一致;
4. opus 终审 → merge --no-ff → 复验 → 删分支 → 台账/记忆。
