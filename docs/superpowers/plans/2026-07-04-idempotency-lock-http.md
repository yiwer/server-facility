# 幂等 + 分布式锁 + HTTP client 实现计划

> **For agentic workers:** 沿用既定纪律——TDD(RED 原始 mvn 粘贴)、行号与提交文件交叉核对、git 提交一律 PowerShell 工具且信息不含 ASCII 双引号、每 task 独立可审、门面私有构造抛 UnsupportedOperationException、新类 line ≥90%(补齐私有构造/降级分支盲区)、Co-Authored-By 用 `Claude Opus 4.8 <noreply@anthropic.com>`。

**Goal:** 为 server-facility 增加分布式锁(SPI+单机+门面)、HTTP client(RestClient 薄封装门面)、幂等(完整幂等:同 key 返首次响应)三组件。

**Architecture:** 三簇独立,共用既定范式:Seam SPI(`@ConditionalOnMissingBean` 默认可替换)、门面委托 `SpringContextHolder.getBean(X).map(...)`+降级、optional 依赖、properties 同包、装配经 imports。幂等吸取限流教训:通用 `idempotency`(零 web)+ web 集成 `web.idempotency` 分离,单向 `web→idempotency` 无环。

**Tech Stack:** Java 21、Spring Boot 3.5.10 BOM、Spring `RestClient`(spring-web,已有 optional)、`ContentCachingResponseWrapper`(spring-web)、JDK `ReentrantLock`。

## Global Constraints
- 包根 `cn.code91.facility.*`;前缀 `facility.lock.*` / `facility.http.*` / `facility.idempotency.*`。
- 基线:master @ 963 绿(含 4 ArchUnit);JaCoCo gate 0.88(line/instr)、0.75(branch);dependency:analyze failOnWarning。
- **门面委托范式**:`SpringContextHolder.getBean(X.class)` 返回 `Result<X, WrappedError>`;门面用 `.map(x -> ...).orElse(降级)` 或 `isErr()` 判空显式降级。
- properties 遵循 ADR-0013:无 `@Validated`。
- 降级哲学:锁无 bean → 直接执行 action + WARN(退化无锁,单实例可接受;多实例须确保 bean 在场);HTTP 无 RestClient bean → 默认 `RestClient.create()`;幂等无 store → 拦截器放行(不幂等)。
- **架构避环**:凡 web 集成依赖通用能力,通用能力零 web 依赖、web 集成入 `web.*` 子包(限流范式)。锁/HTTP 纯通用;幂等分 idempotency/web.idempotency。

---

## 簇 C:分布式锁 lock(包 `cn.code91.facility.lock`,纯通用)

### Task C1:DistributedLock SPI + InMemoryDistributedLock

**Files:**
- Create: `src/main/java/cn/code91/facility/lock/DistributedLock.java`、`LockAcquisitionException.java`、`InMemoryDistributedLock.java`
- Test: `src/test/java/cn/code91/facility/lock/InMemoryDistributedLockTest.java`

**Interfaces produced:**
```java
public interface DistributedLock {
    boolean tryLock(String key, Duration leaseTime);
    void unlock(String key);
    default <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action) {
        if (!tryLock(key, leaseTime)) throw new LockAcquisitionException(key);
        try { return action.get(); } finally { unlock(key); }
    }
    default void executeWithLock(String key, Duration leaseTime, Runnable action) {
        executeWithLock(key, leaseTime, () -> { action.run(); return null; });
    }
}
public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String key) { super("Failed to acquire lock for key: " + key); }
}
```

**InMemoryDistributedLock**:`ConcurrentHashMap<String, ReentrantLock> locks`;构造 `(int maxLocks)`;
- `tryLock(key, leaseTime)`:max 防护(size≥maxLocks 且 key 不在 → `locks.clear()` + `LogUtil.warn("locks exceeded {}, cleared", maxLocks)`);`ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock())`;`try { return lock.tryLock(leaseTime.toMillis(), TimeUnit.MILLISECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }`
- `unlock(key)`:`ReentrantLock lock = locks.get(key); if (lock != null && lock.isHeldByCurrentThread()) lock.unlock();`
- **javadoc 明确**:单机 ReentrantLock 无真租约(leaseTime 仅作 tryLock 等待超时,非持锁后自动过期释放);可重入(同线程);分布式语义(跨进程/故障自动释放)须换 SPI 实现(见 ADR-0016)。

**Steps(TDD)**:
- [ ] RED:`InMemoryDistributedLockTest`:①`tryLock_thenUnlock_reacquirable`(tryLock 成功、unlock、再 tryLock 成功)②`tryLock_heldByOther_timesOut`(线程 A 持锁不放,线程 B `tryLock(key, 50ms)` 返 false——用 CountDownLatch:A 获锁后 latch,B tryLock 短租约失败,A 释放)③`executeWithLock_runsAndReleases`(executeWithLock 执行 action 返回值,之后 key 可再 tryLock=已释放)④`executeWithLock_actionThrows_stillReleases`(action 抛异常,executeWithLock 传播异常但 finally unlock,之后 key 可再获取)⑤`concurrent_mutualExclusion`(cap 无关,20 线程 executeWithLock 同 key 各自 counter++,CountDownLatch 同步,断言最终 counter==20 且无交错——用 non-atomic int + 锁保护证明串行)⑥`maxLocks_exceeded_clears`。跑 `mvn -o test -Dtest=InMemoryDistributedLockTest` 原始 RED 粘贴。
- [ ] GREEN:实现 3 主源。全绿。补私有构造?无(DistributedLock 接口/InMemory 公开构造)。
- [ ] Commit:`feat: 分布式锁 DistributedLock SPI + InMemoryDistributedLock 单机实现(簇C)`

### Task C2:LockUtil 门面 + 装配 + properties + ADR-0016

**Files:**
- Create: `src/main/java/cn/code91/facility/lock/LockUtil.java`、`FacilityLockProperties.java`、`src/main/java/cn/code91/facility/autoconfigure/FacilityLockAutoConfiguration.java`、`src/main/java/cn/code91/facility/lock/package-info.java`、`docs/adr/0016-distributed-lock-seam.md`
- Modify: imports 文件
- Test: `src/test/java/cn/code91/facility/lock/LockUtilTest.java`、`src/test/java/cn/code91/facility/autoconfigure/FacilityLockAutoConfigurationTest.java`

**LockUtil**(静态门面,private 构造抛 UnsupportedOperationException):
```java
public static boolean tryLock(String key, Duration leaseTime) {
    return SpringContextHolder.getBean(DistributedLock.class).map(l -> l.tryLock(key, leaseTime)).orElse(true);
}
public static void unlock(String key) {
    SpringContextHolder.getBean(DistributedLock.class).map(l -> { l.unlock(key); return true; });
}
public static <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action) {
    Result<DistributedLock, WrappedError> bean = SpringContextHolder.getBean(DistributedLock.class);
    if (bean.isErr()) { LogUtil.warn("无 DistributedLock bean,退化无锁执行: key={}", key); return action.get(); }
    return bean.get().executeWithLock(key, leaseTime, action);
}
public static void executeWithLock(String key, Duration leaseTime, Runnable action) {
    executeWithLock(key, leaseTime, () -> { action.run(); return null; });
}
```

**FacilityLockProperties**(`facility.lock`):`boolean enabled=true`、`int maxLocks=100_000`、`Duration defaultLease=Duration.ofSeconds(30)`。

**FacilityLockAutoConfiguration**:`@AutoConfiguration` + `@EnableConfigurationProperties` + `@ConditionalOnProperty(facility.lock.enabled, matchIfMissing=true)`;`@Bean @ConditionalOnMissingBean(DistributedLock.class) DistributedLock facilityDistributedLock(props) { return new InMemoryDistributedLock(props.getMaxLocks()); }`(无 web 条件,纯通用)。

**ADR-0016**:SPI 设计;默认单机 InMemoryDistributedLock 语义(ReentrantLock,leaseTime=等待超时非持锁过期,可重入,进程内);单机↔分布式差异(跨进程互斥、故障自动释放、真租约);**real seam 升级示范**(消费方引入 Redisson,声明 `RedissonDistributedLock implements DistributedLock` bean,`@ConditionalOnMissingBean` 让位——给骨架);LockUtil 无 bean 降级(直接执行+WARN)的权衡与多实例风险。

**Steps(TDD)**:
- [ ] RED:`LockUtilTest`(GenericApplicationContext 注册 DistributedLock bean + `@AfterEach SpringContextHolderTestSupport.reset()` 毒化清理):①`noBean_executeWithLock_runsAction`(无 context → executeWithLock 直接执行返值)②`withBean_executeWithLock_delegates`(注册 InMemoryDistributedLock bean → executeWithLock 委托,action 执行)③`noBean_tryLock_returnsTrue`(降级)④`privateConstructor_throws`。`FacilityLockAutoConfigurationTest`:①默认注册 DistributedLock ②用户 bean 让位 ③enabled=false 无 bean ④properties 绑定(max-locks)。跑 RED 粘贴。
- [ ] GREEN:实现门面 + properties + 装配 + package-info(纯通用,零 web,可非 web 复用)+ imports 追加 `FacilityLockAutoConfiguration` + ADR-0016。全绿。
- [ ] Commit:`feat: LockUtil 门面 + 装配 FacilityLockAutoConfiguration + ADR-0016 real seam 示范(簇C)`

---

## 簇 D:HTTP client(包 `cn.code91.facility.http`,纯通用)

### Task D1:FacilityErrorType.HTTP_STATUS_ERROR + HttpClients 门面

**Files:**
- Modify: `src/main/java/cn/code91/facility/error/FacilityErrorType.java`(加 `HTTP_STATUS_ERROR`)、4 个 i18n bundle(加 `facility.http.status_error`)
- Create: `src/main/java/cn/code91/facility/http/HttpClients.java`、`src/main/java/cn/code91/facility/http/package-info.java`
- Test: `src/test/java/cn/code91/facility/http/HttpClientsTest.java`

**FacilityErrorType 加**(HTTP 段 500300-500399,现有到 500303):
```java
HTTP_STATUS_ERROR(500304, "facility.http.status_error", "HTTP 请求返回错误状态"),
```
4 bundle 加 `facility.http.status_error`(base/en=`HTTP request returned error status`、zh_CN=`HTTP 请求返回错误状态`、zh_TW=`HTTP 請求返回錯誤狀態`)。

**HttpClients**(静态门面,private 构造抛):
```java
private static RestClient restClient() {
    return SpringContextHolder.getBean(RestClient.class).orElseGet(RestClient::create);
}
public static <T> Result<T, WrappedError> get(String url, Class<T> type) {
    try {
        return Result.ok(restClient().get().uri(url).retrieve().body(type));
    } catch (RestClientResponseException e) {          // 4xx/5xx
        return Result.err(WrappedError.of(FacilityErrorType.HTTP_STATUS_ERROR, e, e.getStatusCode().value()));
    } catch (Exception e) {                             // 网络/超时/其他
        return Result.err(WrappedError.of(FacilityErrorType.HTTP_SEND_AND_PARSE_ERROR, e, url));
    }
}
// post(url, body, type): restClient().post().uri(url).body(body).retrieve().body(type)
// put(url, body, type): 类似
// delete(url): restClient().delete().uri(url).retrieve().toBodilessEntity(); return Result.ok(null) → Result<Void,...>
// get 带 headers 重载: .headers(h -> headers.forEach(h::add))
```
(import `org.springframework.web.client.RestClient`、`org.springframework.web.client.RestClientResponseException`。)

**Steps(TDD)**:
- [ ] RED:`HttpClientsTest` 用 `MockRestServiceServer`(spring-test)绑定到一个 RestClient——**技法**:`RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build(); RestClient client = builder.build();` 然后经 SpringContextHolder 注册该 client bean(GenericApplicationContext + @AfterEach reset),或 HttpClients 支持传入 client 测试。**倾向**:注册 RestClient bean 到 context(HttpClients.restClient() 取容器 bean)。测试:①`get_2xx_returnsOk`(server expect GET 返 200 + JSON body → Result.ok(反序列化对象))②`get_4xx_returnsErrWithStatus`(server 返 404 → Result.err,WrappedError.getArgs 含 404)③`get_5xx_returnsErr`(500)④`post_2xx_serializesBodyAndReturnsOk`⑤`networkError_returnsErr`(server expect 不满足或 client 指向无效——用 RestClientResponseException 外的异常;或简化:MockRestServiceServer 不桩 → 断言 err)⑥`privateConstructor_throws`。跑 RED 粘贴(HttpClients 不存在编译失败)。
- [ ] GREEN:加错误码 + i18n + HttpClients + package-info(依赖 spring-web RestClient + result/error;可非 web 复用即通用 HTTP 调用)。全绿。
- [ ] Commit:`feat: HttpClients 门面(RestClient 委托,4xx/5xx/网络异常→Result.err)+ HTTP_STATUS_ERROR(簇D)`

### Task D2:FacilityHttpProperties + 装配 RestClient bean + ADR-0018

**Files:**
- Create: `src/main/java/cn/code91/facility/http/FacilityHttpProperties.java`、`src/main/java/cn/code91/facility/autoconfigure/FacilityHttpAutoConfiguration.java`、`docs/adr/0018-http-client-restclient-result.md`
- Modify: imports 文件
- Test: `src/test/java/cn/code91/facility/autoconfigure/FacilityHttpAutoConfigurationTest.java`

**FacilityHttpProperties**(`facility.http`):`Duration connectTimeout=Duration.ofSeconds(5)`、`Duration readTimeout=Duration.ofSeconds(10)`。

**FacilityHttpAutoConfiguration**:`@AutoConfiguration` + `@EnableConfigurationProperties` + `@ConditionalOnClass(name="org.springframework.web.client.RestClient")`;`@Bean @ConditionalOnMissingBean(RestClient.class) RestClient facilityRestClient(FacilityHttpProperties p)`:用 `SimpleClientHttpRequestFactory`(setConnectTimeout/setReadTimeout,Duration)或 `ClientHttpRequestFactorySettings` + `ClientHttpRequestFactories`(Boot 3.5)构建 `RestClient.builder().requestFactory(factory).build()`。**探针实证 Boot 3.5.10 的 factory 构建 API 后固化**(SimpleClientHttpRequestFactory 最稳:`f.setConnectTimeout((int)p.getConnectTimeout().toMillis()); f.setReadTimeout(...)`)。

**Steps(TDD)**:
- [ ] RED:`FacilityHttpAutoConfigurationTest`(ApplicationContextRunner):①`registersRestClientByDefault`(hasBean RestClient)②`userRestClient_backsOff`(withBean RestClient → 用户 bean)③properties 绑定(facility.http.read-timeout=3s 生效——可断言不抛;超时值难直接断言,至少验证 bean 装配 + 无异常)。跑 RED 粘贴。
- [ ] GREEN:properties + 装配 + ADR-0018(RestClient 非 RestTemplate/WebClient 理由、门面返 Result、超时 properties、RestClient bean Seam 消费方可换 Apache/OkHttp factory)+ imports 追加。全绿。
- [ ] Commit:`feat: HTTP 装配 FacilityHttpAutoConfiguration(RestClient bean 超时)+ ADR-0018(簇D)`

---

## 簇 E:幂等 idempotency(通用 `idempotency` + web 集成 `web.idempotency`)

### Task E1:通用 IdempotencyStore SPI + InMemoryIdempotencyStore

**Files:**
- Create: `src/main/java/cn/code91/facility/idempotency/IdempotencyStore.java`、`IdempotencyRecord.java`、`InMemoryIdempotencyStore.java`、`package-info.java`
- Test: `src/test/java/cn/code91/facility/idempotency/InMemoryIdempotencyStoreTest.java`

**Interfaces produced:**
```java
public interface IdempotencyStore {
    boolean tryBegin(String key, long ttlMillis);        // 原子占位:成功 true / 已有未过期记录 false
    Optional<IdempotencyRecord> find(String key);
    void complete(String key, IdempotencyRecord done);
}
public record IdempotencyRecord(State state, int statusCode, String contentType, byte[] body, long expiresAtMillis) {
    public enum State { PROCESSING, DONE }
    public static IdempotencyRecord processing(long expiresAtMillis) {
        return new IdempotencyRecord(State.PROCESSING, 0, null, null, expiresAtMillis);
    }
    public static IdempotencyRecord done(int statusCode, String contentType, byte[] body, long expiresAtMillis) {
        return new IdempotencyRecord(State.DONE, statusCode, contentType, body, expiresAtMillis);
    }
    public boolean isExpired(long nowMillis) { return nowMillis > expiresAtMillis; }
}
```

**InMemoryIdempotencyStore**:`ConcurrentHashMap<String, IdempotencyRecord> store`;构造 `(int maxEntries)`;
- **原子 tryBegin**(引用相等技法):
```java
public boolean tryBegin(String key, long ttlMillis) {
    long now = System.currentTimeMillis();
    if (store.size() >= maxEntries && !store.containsKey(key)) { store.clear(); LogUtil.warn("idempotency entries exceeded {}, cleared", maxEntries); }
    IdempotencyRecord proc = IdempotencyRecord.processing(now + ttlMillis);
    IdempotencyRecord result = store.compute(key, (k, existing) ->
        (existing != null && !existing.isExpired(now)) ? existing : proc);
    return result == proc;   // 引用相等:proc 被写入 = begin 成功;compute 对 key 原子
}
public Optional<IdempotencyRecord> find(String key) {
    IdempotencyRecord r = store.get(key);
    return (r == null || r.isExpired(System.currentTimeMillis())) ? Optional.empty() : Optional.of(r);
}
public void complete(String key, IdempotencyRecord done) { store.put(key, done); }
```

**Steps(TDD)**:
- [ ] RED:`InMemoryIdempotencyStoreTest`:①`tryBegin_new_returnsTrue_findProcessing`②`tryBegin_existing_returnsFalse`(再 begin 同 key 返 false)③`complete_thenFind_returnsDone`(complete 后 find 得 DONE 记录 statusCode/body)④`expired_tryBeginAgain_returnsTrue`(ttl 极短如 1ms,sleep 后 tryBegin 同 key 返 true=过期可重入;find 过期返 empty)⑤`concurrent_tryBegin_onlyOneSucceeds`(50 线程同 key tryBegin,AtomicInteger 计成功数==1)⑥`maxEntries_exceeded_clears`。跑 RED 粘贴。
- [ ] GREEN:实现 3 主源 + package-info(通用幂等存储,零 web 依赖,Record 纯数据可 Redis 序列化)。全绿。
- [ ] Commit:`feat: 幂等 IdempotencyStore SPI + InMemoryIdempotencyStore(原子占位+TTL,簇E)`

### Task E2:web 集成 @Idempotent + 拦截器 + Filter

**Files:**
- Create: `src/main/java/cn/code91/facility/web/idempotency/Idempotent.java`、`IdempotencyInterceptor.java`、`IdempotencyFilter.java`、`package-info.java`
- Test: `src/test/java/cn/code91/facility/web/idempotency/IdempotencyInterceptorTest.java`

**@Idempotent**:`@Target(METHOD) @Retention(RUNTIME)`;`String headerName() default "Idempotency-Key"`、`long ttlSeconds() default 0`(0=用 props 默认)。

**IdempotencyInterceptor**(HandlerInterceptor,构造 `IdempotencyStore` + `long defaultTtlMillis`):
```java
private static final String ATTR_KEY = "facility.idempotency.key";
public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws IOException {
    if (!(handler instanceof HandlerMethod hm)) return true;
    Idempotent ann = hm.getMethodAnnotation(Idempotent.class);
    if (ann == null) return true;
    String key = req.getHeader(ann.headerName());
    if (key == null || key.isBlank()) { resp.sendError(400, "Missing idempotency key header: " + ann.headerName()); return false; }
    Optional<IdempotencyRecord> found = store.find(key);
    if (found.isPresent()) {
        IdempotencyRecord r = found.get();
        if (r.state() == State.DONE) { writeCached(resp, r); return false; }     // 返回首次响应
        resp.sendError(409, "Duplicate request in progress");                     // PROCESSING
        return false;
    }
    long ttl = ann.ttlSeconds() > 0 ? ann.ttlSeconds() * 1000 : defaultTtlMillis;
    if (!store.tryBegin(key, ttl)) { resp.sendError(409, "Duplicate request in progress"); return false; }  // 竞态
    req.setAttribute(ATTR_KEY, key);
    return true;
}
public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) throws IOException {
    String key = (String) req.getAttribute(ATTR_KEY);
    if (key == null) return;
    if (ex != null) { return; }   // 失败不缓存(占位到期后可重试)——ADR 记录
    if (resp instanceof ContentCachingResponseWrapper w) {
        byte[] body = w.getContentAsByteArray();
        long ttl = defaultTtlMillis;  // 与 begin 同 ttl 近似
        store.complete(key, IdempotencyRecord.done(w.getStatus(), w.getContentType(), body, System.currentTimeMillis() + ttl));
    }
}
// writeCached: resp.setStatus(r.statusCode()); if(r.contentType()!=null) resp.setContentType(r.contentType()); resp.getOutputStream().write(r.body()); resp.flushBuffer();
```

**IdempotencyFilter**(extends `OncePerRequestFilter`):`doFilterInternal` 把 response 包 `ContentCachingResponseWrapper`,`filterChain.doFilter(req, wrapper)`,finally `wrapper.copyBodyToResponse()`。仅基础设施(让拦截器 afterCompletion 读 body)。

**Steps(TDD)**:
- [ ] RED:`IdempotencyInterceptorTest`(MockHttpServletRequest/Response + HandlerMethod 造 @Idempotent 方法 + InMemoryIdempotencyStore):①`noAnnotation_passes`②`missingKey_returns400`③`newKey_beginsAndPasses`(preHandle true + store 有 PROCESSING)④`processingKey_returns409`(手动 store.tryBegin 占位后,另请求 preHandle → 409)⑤`doneKey_writesCachedResponse`(store.complete 一个 DONE 记录后,preHandle → false + response 写入缓存 status/body)⑥`afterCompletion_cachesResponse`(用 ContentCachingResponseWrapper 包装的 resp,写入 body,afterCompletion 后 store.find 得 DONE)。跑 RED 粘贴。
- [ ] GREEN:实现注解 + 拦截器 + Filter + package-info(**web 集成,依赖 idempotency + web.util? 不需 web.util;依赖 servlet + spring-web ContentCachingResponseWrapper**;强调通用/web 分离:web.idempotency→idempotency 单向)。全绿。
- [ ] Commit:`feat: web 幂等 @Idempotent + 拦截器(DONE 返缓存/PROCESSING 409/新占位)+ Filter 捕获响应(簇E)`

### Task E3:装配 + properties + 端到端 + ADR-0017

**Files:**
- Create: `src/main/java/cn/code91/facility/idempotency/FacilityIdempotencyProperties.java`、`src/main/java/cn/code91/facility/autoconfigure/FacilityIdempotencyAutoConfiguration.java`、`docs/adr/0017-idempotency-full-semantics-response-capture.md`
- Modify: imports 文件
- Test: `src/test/java/cn/code91/facility/autoconfigure/FacilityIdempotencyAutoConfigurationTest.java`、`src/test/java/cn/code91/facility/web/idempotency/IdempotencyEndToEndTest.java`(MockMvc)

**FacilityIdempotencyProperties**(`facility.idempotency`):`boolean enabled=true`、`Duration defaultTtl=Duration.ofMinutes(5)`、`int maxEntries=100_000`。

**FacilityIdempotencyAutoConfiguration**:`@AutoConfiguration` + `@EnableConfigurationProperties` + `@ConditionalOnProperty(facility.idempotency.enabled, matchIfMissing=true)`;`IdempotencyStore` bean(`@ConditionalOnMissingBean`,无 web)+ `IdempotencyInterceptor`(web)+ `IdempotencyFilter` FilterRegistrationBean(HIGHEST_PRECEDENCE,`@ConditionalOnWebApplication(SERVLET)`)+ configurer 注册拦截器。

**Steps(TDD)**:
- [ ] RED:`FacilityIdempotencyAutoConfigurationTest`:①store 默认注册 ②web runner 注册拦截器/Filter ③用户 IdempotencyStore 让位 ④enabled=false ⑤properties(max-entries)。`IdempotencyEndToEndTest`(`@WebMvcTest` 或 standalone MockMvc + 一个 `@Idempotent @PostMapping` controller + Filter + 拦截器 + InMemoryStore):同 `Idempotency-Key` 头连发两次 POST → 第一次 200 处理(controller 计数++),第二次返回**相同响应体**且 controller **不再执行**(计数不变);缺 key → 400;不同 key → 各执行。跑 RED 粘贴。
- [ ] GREEN:properties + 装配 + imports 追加 + ADR-0017(完整幂等 vs 防重 409 选择、Filter 包装+拦截器逻辑分工、PROCESSING/DONE 状态机、响应捕获、afterCompletion 异常不缓存的重试语义、通用/web 分离)。全绿。
- [ ] Commit:`feat: 幂等装配 FacilityIdempotencyAutoConfiguration + 端到端 MockMvc + ADR-0017(簇E)`

---

## 收尾(控制器)
- ArchUnit 复核:新包 lock/http/idempotency/web.idempotency 无环(web.idempotency→idempotency 单向);ArchTest 4/4。
- 文档:README 特性矩阵(lock/http/idempotency/web.idempotency)+ 装配开关表(facility.lock/http/idempotency);USAGE(LockUtil/executeWithLock、HttpClients、@Idempotent 用法 + optional 矩阵);DESIGN ADR 索引 0016-0018;autoconfigure/package-info 8→11 装配;ADR INDEX 加 3 行。
- opus 终审(锁互斥+降级、HTTP Result 化+状态码、幂等状态机+响应捕获+端到端、ArchUnit、文档真实性、覆盖率)。
- ledger + memory;finishing-a-development-branch(本地合并回 master)。

## 验收总标准
- `mvn clean verify` 全绿 + gate 0.88 + dependency:analyze 零 warning + ArchUnit 四规则。
- 锁:executeWithLock 互斥+自动释放+异常仍释放+SPI 可替换;HTTP:get/post/put/delete Result 化+4xx/5xx 带状态码;幂等:同 key 返首次响应+PROCESSING 409+缺 key 400。
- imports 11 装配(8+Lock/Http/Idempotency);ADR-0016/0017/0018;文档三件套更新;i18n status_error 4 bundle。
- `@Test` 较基线 959(grep)显著增量;新类 line ≥90%。

## 自审(计划 vs spec)
- spec §2 幂等:通用 Store/Record/InMemory(E1)、web 注解/拦截器/Filter(E2)、装配/端到端/ADR(E3)✓
- spec §3 锁:SPI/InMemory(C1)、门面/装配/ADR-0016 real seam(C2)✓
- spec §4 HTTP:门面/错误码(D1)、properties/装配/ADR-0018(D2)✓
- spec §5 依赖:spring-web(已有,HTTP+幂等 wrapper)✓;§6 ADR-0016/17/18(C2/E3/D2)✓;§7 测试全覆盖 ✓
- 类型一致:DistributedLock.executeWithLock(key,Duration,Supplier)在 C1 定义、C2 门面一致;IdempotencyStore.tryBegin/find/complete 在 E1 定义、E2 拦截器一致;IdempotencyRecord.State/done/processing 一致 ✓
- 无占位符;路径/签名精确。
