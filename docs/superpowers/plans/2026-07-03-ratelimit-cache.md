# 限流 ratelimit + 缓存 cache 实现计划

> **For agentic workers:** 沿用既定纪律——TDD(RED 原始 mvn 粘贴)、行号与提交文件交叉核对、git 提交一律 PowerShell 工具且信息不含 ASCII 双引号、每 task 独立可审。纯新增(无迁移),但补测/装配任务遵循同等严谨。

**Goal:** 为 server-facility 增加限流(令牌桶 + SPI + @RateLimit + 拦截器 + 429)与缓存(CacheUtil 门面 + CacheManager 装配)两个 §10 组件。

**Architecture:** 两簇独立,共用既定范式:Seam SPI(`@ConditionalOnMissingBean` 默认实现可替换)、静态门面(委托 `SpringContextHolder.getBean` + 优雅降级)、optional 依赖(Caffeine)、properties 同包(ADR-0013 无 @Validated)、装配经 imports 文件。

**Tech Stack:** Java 21、Spring Boot 3.5.10 BOM、Caffeine(optional,BOM 管版本)、Spring Cache 抽象(spring-context 已有)、spring-webmvc(optional 已有)。

## Global Constraints
- 包根 `cn.code91.facility.*`;前缀 `facility.ratelimit.*` / `facility.cache.*`;类前缀无强制(既有 web 组件不带 Facility 前缀,SPI/门面/工具同)。
- 基线:master @ 920 绿(含 4 ArchUnit);JaCoCo gate 0.88(line/instr)、0.75(branch);dependency:analyze failOnWarning。
- **门面委托范式**:`SpringContextHolder.getBean(X.class)` 返回 `Result<X, WrappedError>`,门面用 `.map(x -> ...).orElse(降级值)`(无 bean 时 err 分支 → orElse 降级)。
- properties 遵循 ADR-0013:无 `@Validated`;`@Min` 等仅作文档;范围守卫在构造器(若需要)。
- 降级哲学:限流/缓存不可用**不阻断业务**(限流无 bean→放行;缓存无 CacheManager→get 空/getOrCompute 直调 loader)。

---

## 簇 A:限流 ratelimit(包 `cn.code91.facility.ratelimit`)

### Task A1:RateLimiter SPI + TokenBucket 算法 + TokenBucketRateLimiter 默认实现

**Files:**
- Create: `src/main/java/cn/code91/facility/ratelimit/RateLimiter.java`、`RateLimitResult.java`、`TokenBucket.java`(包内)、`TokenBucketRateLimiter.java`
- Test: `src/test/java/cn/code91/facility/ratelimit/TokenBucketRateLimiterTest.java`

**Interfaces produced:**
```java
public interface RateLimiter {
    default boolean tryAcquire(String key) { return tryAcquire(key, 1); }
    boolean tryAcquire(String key, int permits);
    RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond);
}
public record RateLimitResult(boolean allowed, long remaining, long retryAfterMillis) {}
```

**TokenBucket(包内 final class,核心算法)**:
```java
final class TokenBucket {
    private final long capacity;
    private final double refillPerNano;   // permitsPerSecond / 1e9
    private double tokens;
    private long lastRefillNanos;
    TokenBucket(long capacity, double permitsPerSecond) {
        this.capacity = capacity;
        this.refillPerNano = permitsPerSecond / 1_000_000_000.0;
        this.tokens = capacity;                 // 初始满桶
        this.lastRefillNanos = System.nanoTime();
    }
    synchronized boolean tryConsume(int permits) {
        refill();
        if (tokens >= permits) { tokens -= permits; return true; }
        return false;
    }
    synchronized long remaining() { refill(); return (long) tokens; }
    private void refill() {
        long now = System.nanoTime();
        double add = (now - lastRefillNanos) * refillPerNano;
        if (add > 0) { tokens = Math.min(capacity, tokens + add); lastRefillNanos = now; }
    }
}
```

**TokenBucketRateLimiter**:`ConcurrentHashMap<String, TokenBucket> buckets`;构造 `(long defaultCapacity, double defaultPermitsPerSecond, int maxBuckets)`;`tryAcquire(key, permits)` 委托 `acquire(key, permits, defaultCapacity, defaultPermitsPerSecond).allowed()`;`acquire(key, permits, capacity, rate)`:
- 无界防护:`if (buckets.size() >= maxBuckets && !buckets.containsKey(key)) { buckets.clear(); LogUtil.warn("RateLimiter buckets exceeded {}, cleared for memory safety", maxBuckets); }`
- `TokenBucket b = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, rate));`(桶按 key 首次的 cap/rate 创建)
- `boolean ok = b.tryConsume(permits); long rem = b.remaining();`
- `long retryAfter = ok ? 0 : (long) Math.ceil((permits - rem) / rate * 1000.0);`
- `return new RateLimitResult(ok, rem, retryAfter);`
- 加 `clear()`(清空所有桶)供门面/测试。

**Steps(TDD)**:
- [ ] RED:`TokenBucketRateLimiterTest` 写:①`capacity3_fourthCallRejected`(cap=3,rate=1;连取 3 个 true,第 4 个 false);②`refill_afterWait_recovers`(cap=1,rate 高如 1000/s;取 1 true,取 1 false,`Thread.sleep(5)` 后取 1 true——refill 恢复);③`multiKey_isolated`(key A 耗尽不影响 key B);④`concurrent_consistency`(cap=100,50 线程各取 1,CountDownLatch 同步,断言成功数 ≤100 且桶不超发——用 AtomicInteger 计成功数,`assertThat(success).isLessThanOrEqualTo(100)`);⑤`maxBuckets_exceeded_clears`(maxBuckets=2,放 3 个不同 key,断言 size ≤ maxBuckets);⑥`acquire_returnsRetryAfter`(耗尽后 acquire 的 retryAfterMillis > 0)。跑 `mvn -o test -Dtest=TokenBucketRateLimiterTest` 原始 RED 粘贴。
- [ ] GREEN:实现上述四类。跑全绿。
- [ ] Commit:`feat: 限流令牌桶 RateLimiter SPI + TokenBucketRateLimiter 默认实现(簇A)`

### Task A2:RateLimiterUtil 门面 + @RateLimit 注解

**Files:**
- Create: `src/main/java/cn/code91/facility/ratelimit/RateLimiterUtil.java`、`RateLimit.java`(注解)
- Test: `src/test/java/cn/code91/facility/ratelimit/RateLimiterUtilTest.java`

**RateLimiterUtil**(静态门面):
```java
public final class RateLimiterUtil {
    private RateLimiterUtil() { throw new UnsupportedOperationException(); }
    public static boolean tryAcquire(String key) { return tryAcquire(key, 1); }
    public static boolean tryAcquire(String key, int permits) {
        return SpringContextHolder.getBean(RateLimiter.class)
                .map(rl -> rl.tryAcquire(key, permits))
                .orElse(true);   // 无 RateLimiter bean → 放行(降级不阻断)
    }
    public static RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond) {
        return SpringContextHolder.getBean(RateLimiter.class)
                .map(rl -> rl.acquire(key, permits, capacity, permitsPerSecond))
                .orElse(new RateLimitResult(true, Long.MAX_VALUE, 0));
    }
}
```

**@RateLimit**:
```java
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface RateLimit {
    String key() default "";              // 空=按 类#方法#clientIp(拦截器构造);非空=固定全局 key
    long capacity() default 0;            // 0=用 properties 默认
    double permitsPerSecond() default 0;  // 0=用 properties 默认
    int permits() default 1;
}
```

**Steps(TDD)**:
- [ ] RED:`RateLimiterUtilTest`(用 `SpringContextHolderTestSupport` 卫生,@AfterEach reset):①`noBean_degradesToAllow`(无 context → `tryAcquire("k")` 返 true);②`withRateLimiterBean_delegates`(注册一个 cap=1 的 TokenBucketRateLimiter bean via GenericApplicationContext + SpringContextHolder,`tryAcquire("k")` 首次 true 次 false)。**毒化陷阱**:注册 context 后 @AfterEach 经 `SpringContextHolderTestSupport.reset()` 清理。跑 RED 粘贴。
- [ ] GREEN:实现门面 + 注解。全绿。
- [ ] Commit:`feat: RateLimiterUtil 门面(委托+降级) + @RateLimit 注解(簇A)`

### Task A3:RateLimitExceededException + RateLimitInterceptor

**Files:**
- Create: `src/main/java/cn/code91/facility/ratelimit/RateLimitExceededException.java`、`RateLimitInterceptor.java`
- Test: `src/test/java/cn/code91/facility/ratelimit/RateLimitInterceptorTest.java`

**RateLimitExceededException**(RuntimeException,带 retryAfterMillis):
```java
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterMillis;
    public RateLimitExceededException(String key, long retryAfterMillis) {
        super("Rate limit exceeded for key: " + key);
        this.retryAfterMillis = retryAfterMillis;
    }
    public long getRetryAfterMillis() { return retryAfterMillis; }
}
```

**RateLimitInterceptor**(HandlerInterceptor;构造注入 `RateLimiter` + `FacilityRateLimitProperties`):
```java
public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
    if (!(handler instanceof HandlerMethod hm)) return true;
    RateLimit ann = hm.getMethodAnnotation(RateLimit.class);
    if (ann == null) return true;
    String key = ann.key().isEmpty()
        ? hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName() + "#" + RequestUtil.getClientIp(req)
        : ann.key();
    long cap = ann.capacity() > 0 ? ann.capacity() : props.getDefaultCapacity();
    double rate = ann.permitsPerSecond() > 0 ? ann.permitsPerSecond() : props.getDefaultPermitsPerSecond();
    RateLimitResult r = rateLimiter.acquire(key, ann.permits(), cap, rate);
    if (!r.allowed()) throw new RateLimitExceededException(key, r.retryAfterMillis());
    return true;
}
```
(`RequestUtil.getClientIp(HttpServletRequest)` 已存在于 web.util——先 grep 确认确切方法名/签名,若不同则适配。)

**Steps(TDD)**:
- [ ] RED:`RateLimitInterceptorTest`:构造带 cap=1 TokenBucketRateLimiter + props 的拦截器;用反射/测试控制器造一个带 `@RateLimit` 的 `HandlerMethod`(`new HandlerMethod(new Object(){ @RateLimit public void m(){} }, "m")` 需公开方法——用测试内静态类 + `getMethod`);`MockHttpServletRequest`;①`annotatedMethod_firstAllows_secondThrows`(第一次 preHandle true,第二次抛 RateLimitExceededException);②`noAnnotation_passes`(无 @RateLimit 方法 preHandle true);③`nonHandlerMethod_passes`(handler 非 HandlerMethod 返 true)。跑 RED 粘贴。
- [ ] GREEN:实现异常 + 拦截器。全绿。
- [ ] Commit:`feat: RateLimitInterceptor + RateLimitExceededException(@RateLimit 超限抛 429 前身,簇A)`

### Task A4:web 簇 handler 新增 429 处理

**Files:**
- Modify: `src/main/java/cn/code91/facility/web/exception/AbstractGlobalExceptionHandler.java`(新增 `@ExceptionHandler(RateLimitExceededException.class)`)
- Test: `src/test/java/cn/code91/facility/web/exception/GlobalExceptionHandlerTest.java`(追加)

新增 handler 方法(放兜底 `handleException` 前):
```java
@ExceptionHandler(RateLimitExceededException.class)
public Object handleRateLimitExceeded(RateLimitExceededException e, WebRequest request) {
    LogUtil.warn("限流触发: {}, retryAfter={}ms, path={}", e.getMessage(), e.getRetryAfterMillis(), getRequestURI(request));
    long retryAfterSeconds = Math.max(1, e.getRetryAfterMillis() / 1000);
    if (props.isUseProblemDetail()) {
        ResponseEntity<ProblemDetail> pd = buildProblemDetail(e, HttpStatus.TOO_MANY_REQUESTS, request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(pd.getBody());
    }
    BaseResponse<Void> body = buildResponse(429, LocaleUtil.translateMessage("facility.web.error.rate_limited"), e);
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(retryAfterSeconds))
            .body(body);
}
```
(import `cn.code91.facility.ratelimit.RateLimitExceededException` —— 注意 web.exception 将依赖 ratelimit 包;ratelimit 拦截器抛的异常由 web handler 处理,方向 web.exception→ratelimit,ratelimit 不反向依赖 web.exception,无环。ArchUnit 复核。)
新增 i18n 键 `facility.web.error.rate_limited`(4 bundle:base/en/zh_CN/zh_TW,如 en "Too many requests")。

**Steps(TDD)**:
- [ ] RED:GlobalExceptionHandlerTest 追加 `handleRateLimitExceeded_returns429WithRetryAfter`(default 路径:构造 handler + `new RateLimitExceededException("k", 3000)`,断言返回 ResponseEntity status 429 + Retry-After 头 == "3");`_problemDetail`(props.useProblemDetail=true,断言 ProblemDetail status 429)。新建 `WebErrorMessagesIntegrationTest` 或既有里加 `rate_limited` 键解析断言。跑 RED 粘贴。
- [ ] GREEN:加 handler + 4 bundle 键。全绿。
- [ ] Commit:`feat: 全局异常处理器新增 429 限流处理(Retry-After 头 + rate_limited i18n 键,簇A)`

### Task A5:装配 + properties + ADR + imports

**Files:**
- Create: `src/main/java/cn/code91/facility/ratelimit/FacilityRateLimitProperties.java`、`src/main/java/cn/code91/facility/autoconfigure/FacilityRateLimitAutoConfiguration.java`、`docs/adr/0014-ratelimit-token-bucket-seam.md`
- Modify: imports 文件(追加装配类)、`src/main/java/cn/code91/facility/ratelimit/package-info.java`(新建)
- Test: `src/test/java/cn/code91/facility/autoconfigure/FacilityRateLimitAutoConfigurationTest.java`

**FacilityRateLimitProperties**(前缀 `facility.ratelimit`,ADR-0013 无 @Validated):`boolean enabled=true`、`long defaultCapacity=100`、`double defaultPermitsPerSecond=10`、`int maxBuckets=100_000`。

**FacilityRateLimitAutoConfiguration**:
```java
@AutoConfiguration
@EnableConfigurationProperties(FacilityRateLimitProperties.class)
@ConditionalOnProperty(prefix = "facility.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityRateLimitAutoConfiguration {
    @Bean @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter facilityRateLimiter(FacilityRateLimitProperties p) {
        return new TokenBucketRateLimiter(p.getDefaultCapacity(), p.getDefaultPermitsPerSecond(), p.getMaxBuckets());
    }
    @Bean @ConditionalOnMissingBean @ConditionalOnWebApplication(type = Type.SERVLET)
    public RateLimitInterceptor rateLimitInterceptor(RateLimiter rl, FacilityRateLimitProperties p) {
        return new RateLimitInterceptor(rl, p);
    }
    @Bean("facilityRateLimitWebMvcConfigurer")
    @ConditionalOnMissingBean(name = "facilityRateLimitWebMvcConfigurer")
    @ConditionalOnBean(RateLimitInterceptor.class)
    public WebMvcConfigurer facilityRateLimitWebMvcConfigurer(RateLimitInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor); }
        };
    }
}
```
(SPI bean `facilityRateLimiter` 无 web 条件——非 web 场景 RateLimiterUtil 编程式也可用;拦截器/configurer 有 `@ConditionalOnWebApplication`。)

**Steps(TDD)**:
- [ ] RED:`FacilityRateLimitAutoConfigurationTest`(ApplicationContextRunner):①`registersRateLimiterByDefault`(hasBean RateLimiter);②`webRunner_registersInterceptor`(WebApplicationContextRunner hasSingleBean RateLimitInterceptor);③`userRateLimiterBean_backsOff`(withBean RateLimiter → facility 不注册,doesNotHaveBean TokenBucketRateLimiter 或断言是用户 bean);④`enabledFalse_noBeans`(enabled=false → doesNotHaveBean RateLimiter);⑤properties 绑定(facility.ratelimit.default-capacity=5 生效)。跑 RED 粘贴。
- [ ] GREEN:实现 properties + 装配 + package-info + imports 追加 + ADR-0014(算法选择/Seam/降级/无界防护)。全绿。
- [ ] Commit:`feat: 限流装配 FacilityRateLimitAutoConfiguration + properties + ADR-0014(簇A)`

---

## 簇 B:缓存 cache(包 `cn.code91.facility.cache`)

### Task B1:pom Caffeine optional + CacheUtil 门面

**Files:**
- Modify: `pom.xml`(Caffeine optional + dependency:analyze ignore 若需)
- Create: `src/main/java/cn/code91/facility/cache/CacheUtil.java`
- Test: `src/test/java/cn/code91/facility/cache/CacheUtilTest.java`

pom 加(P6 段后或新 P8 段):
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <optional>true</optional>
</dependency>
```

**CacheUtil**(委托 CacheManager + 降级):
```java
public final class CacheUtil {
    private CacheUtil() { throw new UnsupportedOperationException(); }
    public static <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        return SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName);
            if (c == null) return Optional.<T>empty();
            Cache.ValueWrapper vw = c.get(key);
            return vw == null ? Optional.<T>empty() : Optional.ofNullable(type.cast(vw.get()));
        }).orElse(Optional.empty());
    }
    public static void put(String cacheName, Object key, Object value) {
        SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName); if (c != null) c.put(key, value); return true;
        });   // 无 CacheManager → no-op
    }
    public static void evict(String cacheName, Object key) {
        SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName); if (c != null) c.evict(key); return true; });
    }
    public static void clear(String cacheName) {
        SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName); if (c != null) c.clear(); return true; });
    }
    public static <T> T getOrCompute(String cacheName, Object key, Class<T> type, Supplier<T> loader) {
        Optional<T> hit = get(cacheName, key, type);
        if (hit.isPresent()) return hit.get();
        T v = loader.get(); put(cacheName, key, v); return v;   // 无 CacheManager → 直调 loader(get 空 + put no-op)
    }
}
```

**Steps(TDD)**:
- [ ] RED:`CacheUtilTest`(用 GenericApplicationContext 注册 ConcurrentMapCacheManager bean via SpringContextHolder + @AfterEach reset):①`putThenGet_hit`;②`get_missing_empty`;③`evict_removes`;④`clear_removesAll`;⑤`getOrCompute_missThenHit`(首次调 loader + 缓存,次命中不调 loader——用 AtomicInteger 计 loader 调用次数==1);⑥`noCacheManager_getEmpty_getOrComputeCallsLoader`(无 context → get 空、getOrCompute 直调 loader 返值)。毒化清理遵守。跑 RED 粘贴。
- [ ] GREEN:pom + CacheUtil。全绿。
- [ ] Commit:`feat: 缓存 CacheUtil 门面(委托 CacheManager+降级) + Caffeine optional(簇B)`

### Task B2:装配 + properties + ADR + imports

**Files:**
- Create: `src/main/java/cn/code91/facility/cache/FacilityCacheProperties.java`、`src/main/java/cn/code91/facility/autoconfigure/FacilityCacheAutoConfiguration.java`、`src/main/java/cn/code91/facility/cache/package-info.java`、`docs/adr/0015-cache-facade-cachemanager.md`
- Modify: imports 文件
- Test: `src/test/java/cn/code91/facility/autoconfigure/FacilityCacheAutoConfigurationTest.java`

**FacilityCacheProperties**(前缀 `facility.cache`):`boolean enabled=true`、`Duration defaultTtl=Duration.ofMinutes(10)`、`long maximumSize=10_000`。

**FacilityCacheAutoConfiguration**:
```java
@AutoConfiguration
@EnableConfigurationProperties(FacilityCacheProperties.class)
@ConditionalOnProperty(prefix = "facility.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityCacheAutoConfiguration {
    @Bean @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnClass(com.github.benmanes.caffeine.cache.Caffeine.class)
    public CacheManager caffeineCacheManager(FacilityCacheProperties p) {
        CaffeineCacheManager cm = new CaffeineCacheManager();
        cm.setCaffeine(com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(p.getDefaultTtl()).maximumSize(p.getMaximumSize()));
        return cm;
    }
    @Bean @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Caffeine")
    public CacheManager concurrentMapCacheManager() { return new ConcurrentMapCacheManager(); }
}
```
(两 @Bean 互斥:Caffeine 在 classpath 走前者、不在走后者;都 @ConditionalOnMissingBean 让消费方覆盖。测试 classpath 有 Caffeine(optional 对测试可见)→ 走 Caffeine 分支;ConcurrentMap 分支用 FilteredClassLoader 隐藏 Caffeine 测试。)

**Steps(TDD)**:
- [ ] RED:`FacilityCacheAutoConfigurationTest`:①`registersCaffeineCacheManagerByDefault`(hasBean CacheManager,且 instanceof CaffeineCacheManager——Caffeine 测试可见);②`filteredCaffeine_fallsBackToConcurrentMap`(`.withClassLoader(new FilteredClassLoader(Caffeine.class))` → CacheManager instanceof ConcurrentMapCacheManager);③`userCacheManager_backsOff`;④`enabledFalse_noBean`;⑤properties TTL/size 绑定(facility.cache.maximum-size=5)。跑 RED 粘贴。
- [ ] GREEN:properties + 装配 + package-info + imports + ADR-0015。全绿。
- [ ] Commit:`feat: 缓存装配 FacilityCacheAutoConfiguration(Caffeine/ConcurrentMap 分支) + properties + ADR-0015(簇B)`

### Task B3:@Cacheable 集成 + Caffeine TTL 行为验证

**Files:**
- Test: `src/test/java/cn/code91/facility/cache/CacheableIntegrationTest.java`、`CaffeineTtlTest.java`

**Steps(补测,非红绿)**:
- [ ] `CacheableIntegrationTest`:ApplicationContextRunner + FacilityCacheAutoConfiguration + `@EnableCaching` + 一个带 `@Cacheable("x")` 方法的测试 bean,断言第二次调用命中缓存(方法体只执行一次,AtomicInteger 计数)。
- [ ] `CaffeineTtlTest`:直接构造 CaffeineCacheManager(expireAfterWrite=50ms),put→get 命中,`Thread.sleep(80)`→get 空(TTL 过期);maximumSize 行为(超 size 驱逐)。
- [ ] `mvn -o clean verify` 全绿。Commit:`test: @Cacheable 集成 + Caffeine TTL/maxSize 行为验证(簇B)`

---

## 收尾(控制器)
- ArchUnit 复核:新包 ratelimit/cache 纳入无环(web.exception→ratelimit 单向);跑 ArchitectureTest 绿。
- 文档:README 特性矩阵 + 装配开关表(facility.ratelimit.* / facility.cache.*)、USAGE(RateLimiterUtil/@RateLimit/CacheUtil/@Cacheable 用法 + optional 依赖矩阵加 Caffeine)、DESIGN ADR 索引 + imports 8 装配。
- opus 终审(算法正确性、降级路径、装配互斥、i18n 键闭环、ArchUnit、文档真实性)。
- ledger + memory;finishing-a-development-branch(本地合并回 master)。

## 验收总标准
- `mvn clean verify` 全绿 + JaCoCo gate 0.88 + dependency:analyze 零 warning + ArchUnit 四规则。
- 限流:@RateLimit 声明式 → 超限 429 + Retry-After;RateLimiterUtil 编程式;SPI 可替换;无 bean 降级放行。
- 缓存:CacheUtil get/put/evict/getOrCompute + @Cacheable 可用;Caffeine TTL 生效;无 CacheManager 降级。
- imports 8 装配(6 既有 + RateLimit + Cache);ADR-0014/0015;文档三件套更新;i18n `rate_limited` 键 4 bundle。
- `@Test` 较基线 920 显著增量(grep 实数);两新包 line ≥90%。

## 自审(计划 vs spec)
- spec §3 限流:SPI/TokenBucket(A1)、门面/注解(A2)、拦截器/异常(A3)、429 handler(A4)、装配/properties/ADR(A5)✓
- spec §4 缓存:CacheUtil 门面(B1)、装配/properties/ADR(B2)、@Cacheable/TTL(B3)✓
- spec §5 依赖:Caffeine optional(B1)✓;§6 ADR-0014/0015(A5/B2)✓;§7 测试计划全覆盖 ✓
- 类型一致:RateLimiter.acquire(key,permits,capacity,rate) 签名在 A1 定义、A2 门面/A3 拦截器/A5 装配一致引用 ✓;RateLimitResult(allowed,remaining,retryAfterMillis)一致 ✓
- 无占位符;路径/签名精确。
