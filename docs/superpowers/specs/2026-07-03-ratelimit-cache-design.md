# 限流 ratelimit + 缓存 cache 组件设计

> **状态**:待用户 review(用户 brainstorming 时暂离,4 项关键决策按推荐选项取值,用户可调整)
> **来源**:spec 2026-07-02 §10 roadmap(限流=web filter/拦截器 + 存储 SPI + Seam;缓存=薄封装 Spring Cache + 装配范式)
> **日期**:2026-07-03

## 1. 背景与范围

为 server-facility 增加两个 §10 roadmap 功能组件,遵循既定架构范式(deep module 静态门面、
`@ConditionalOnMissingBean` 装配兜底、Seam SPI 默认实现可替换、optional 依赖、ADR 记录、
`facility.*` 前缀、测试全面)。两组件相互独立,共用同一套范式,故合于一个 spec、实现时拆为
独立 task 簇(可独立测试/审查/合并)。

## 2. 关键决策(brainstorming;推荐取值,用户可调)

| # | 决策 | 取值 | 依据 |
|---|---|---|---|
| D1 | 限流算法 | **令牌桶 Token Bucket** | 平滑限流 + 允许突发,最通用;纯 JDK 可实现(惰性 refill),无强依赖 |
| D2 | 分布式支持 | **仅单机默认 + SPI 扩展点** | 遵循 Seam/optional 哲学;不引 Redis 依赖(spring-data-redis 不在 Boot BOM);消费方经 SPI 注入分布式实现 |
| D3 | 限流触发 | **注解 @RateLimit + 拦截器 + 编程 API** | 方法级声明式(Spring 惯用)+ `RateLimiterUtil.tryAcquire(key)` 编程式(非 web 场景可用) |
| D4 | 缓存形态 | **编程门面 CacheUtil + 装配 CacheManager** | 门面 deep module 一致;装配 CacheManager 后 `@Cacheable` 注解自然可用;Caffeine optional 支持 TTL |

## 3. 限流 ratelimit 设计

**包**:`cn.code91.facility.ratelimit`

### 3.1 SPI 与默认实现(Seam)
- **`RateLimiter`**(SPI 接口):
  - `boolean tryAcquire(String key)` —— 取 1 个令牌
  - `boolean tryAcquire(String key, int permits)` —— 取 N 个
  - `RateLimitResult acquire(String key, int permits)` —— 返回带元数据(是否放行/剩余令牌/建议重试毫秒)
- **`RateLimitResult`**(record):`boolean allowed`、`long remaining`、`long retryAfterMillis`
- **`TokenBucketRateLimiter`**(默认实现):
  - `ConcurrentHashMap<String, TokenBucket>` 每 key 一桶;`computeIfAbsent` 惰性建桶
  - **`TokenBucket`**(包内):`capacity`(容量)+ `refillTokensPerSecond`(填充速率);字段 `double tokens` + `long lastRefillNanos`;`synchronized tryConsume(permits)` 惰性 refill(基于 `System.nanoTime()` 时间差补令牌,上限 capacity)后扣减
  - **无界 key 防护**:`max-buckets` 上限(默认 100_000),超限时清理(简单策略:size 超限则 `clear()` 全清 + 日志警示,或拒绝新 key —— 实现时择一,倾向清理最久未访问的近似 LRU);文档说明默认实现适合**有界 key 集**(IP/用户/接口),海量唯一 key 场景应经 SPI 注入 Caffeine/Redis 实现
  - `@ConditionalOnMissingBean(RateLimiter.class)` 装配 —— 消费方提供自己的 `RateLimiter` bean(Redis 等)即替换

### 3.2 门面 + 注解 + 拦截器
- **`RateLimiterUtil`**(静态门面):`tryAcquire(key)` / `tryAcquire(key, permits)` / `acquire(key, permits)` 委托 `SpringContextHolder.getBean(RateLimiter.class)`;无 bean 时优雅降级(返回放行 true,记 warn —— 限流不可用不应阻断业务,参照 LocaleUtil 降级哲学)
- **`@RateLimit`**(方法注解):`key()`(SpEL 或固定串,默认方法签名)、`capacity()`、`permitsPerSecond()`、`permits()` 默认 1
- **`RateLimitInterceptor`**(HandlerInterceptor):`preHandle` 读方法 `@RateLimit`,构造 key(默认 `类#方法` + 可选 SpEL 求值加 IP/用户),调 `RateLimiter.tryAcquire`;超限抛 **`RateLimitExceededException`**(带 retryAfterMillis)
- **`RateLimitExceededException`**:web 异常;由 web 簇 `AbstractGlobalExceptionHandler` 新增 `@ExceptionHandler` 处理 —— 返回 HTTP 429 + `Retry-After` 头 + BaseResponse/ProblemDetail(呼应 ADR-0003 双轨)

### 3.3 装配 + properties
- **`FacilityRateLimitAutoConfiguration`**:`@ConditionalOnMissingBean` 装配 `TokenBucketRateLimiter`、`RateLimitInterceptor`、注册拦截器的 `WebMvcConfigurer`;`@ConditionalOnWebApplication(SERVLET)` 门(拦截器需 web;门面/SPI 本身不需 web,故 SPI+门面装配与拦截器装配可分离,SPI 装配无 web 条件)
- **`FacilityRateLimitProperties`**(前缀 `facility.ratelimit`):`enabled`、`default-capacity`、`default-permits-per-second`、`max-buckets`;归位 ratelimit 包(遵循 C3 properties 同包)
- imports 文件追加装配类

## 4. 缓存 cache 设计

**包**:`cn.code91.facility.cache`

### 4.1 门面 + 装配
- **`CacheUtil`**(静态门面,委托 Spring `CacheManager`):
  - `Optional<T> get(String cacheName, Object key, Class<T> type)`
  - `void put(String cacheName, Object key, Object value)`
  - `void evict(String cacheName, Object key)` / `void clear(String cacheName)`
  - `T getOrCompute(String cacheName, Object key, Class<T> type, Supplier<T> loader)` —— 缓存穿透便捷(命中返回,未命中 loader 求值 + put)
  - 委托 `SpringContextHolder.getBean(CacheManager.class)`;无 CacheManager 时优雅降级(get 返回 empty、put/evict no-op、getOrCompute 直接调 loader —— 缓存不可用不应阻断业务)
- **装配**(`FacilityCacheAutoConfiguration`,`@ConditionalOnMissingBean(CacheManager.class)`):
  - Caffeine 在 classpath → `CaffeineCacheManager`(设 `expireAfterWrite=default-ttl`、`maximumSize`)
  - 否则 → `ConcurrentMapCacheManager`(无 TTL、无界;文档警示)
  - 装配 CacheManager 后消费方加 `@EnableCaching` 即可用 `@Cacheable`/`@CacheEvict` 注解
- **Seam**:`CacheManager` 是 Spring 标准 SPI —— 消费方提供 Redis CacheManager 即替换

### 4.2 TTL 与 properties
- Spring 原生 `@Cacheable` 不支持 per-cache TTL;经 `CaffeineCacheManager` 全局 `expireAfterWrite` 实现(D4 取值)
- **`FacilityCacheProperties`**(前缀 `facility.cache`):`enabled`、`default-ttl`(Duration,默认 10m)、`maximum-size`(long,默认 10_000)—— 仅 Caffeine 后端生效(ConcurrentMap 忽略,文档说明)
- imports 文件追加装配类

## 5. 依赖

| 依赖 | scope | 用途 |
|---|---|---|
| `com.github.ben-manes.caffeine:caffeine` | **optional**(BOM 管版本 `${caffeine.version}`) | 缓存 TTL/maxSize(CaffeineCacheManager);限流可选桶后端 |
| spring-context(org.springframework.cache) | 已有(compile) | CacheManager/ConcurrentMapCacheManager 抽象在 spring-context |
| spring-webmvc | 已有(optional) | RateLimitInterceptor / WebMvcConfigurer |

不引 spring-data-redis、bucket4j(D2:分布式经 SPI,消费方自备)。

## 6. ADR 计划
- **ADR-0014 限流令牌桶 + Seam SPI**:算法选择(令牌桶 vs 滑窗/固定窗)、纯 JDK 默认实现 + `RateLimiter` SPI 可替换、限流降级不阻断业务(门面无 bean 放行)、无界 key 防护(max-buckets + 文档边界)
- **ADR-0015 缓存门面 + CacheManager 装配**:CacheUtil 委托 Spring CacheManager(复用标准 SPI 而非自建)、TTL 经 CaffeineCacheManager、缓存降级不阻断(无 CacheManager 时 getOrCompute 直调 loader)

## 7. 测试计划(全面)
- **限流**:TokenBucket 算法(容量耗尽拒绝、refill 后恢复、突发、并发多线程扣减一致性用 CountDownLatch)、TokenBucketRateLimiter(多 key 隔离、max-buckets 防护)、RateLimiterUtil 门面(有 bean/无 bean 降级)、RateLimitInterceptor(@RateLimit preHandle 放行/超限抛异常)、RateLimitExceededException handler(429 + Retry-After,default + ProblemDetail 双路)、装配(@ConditionalOnMissingBean 兜底、@ConditionalOnWebApplication、enabled=false 开关、properties 绑定)
- **缓存**:CacheUtil(get/put/evict/clear/getOrCompute 命中与穿透、无 CacheManager 降级)、装配(Caffeine 在/不在 classpath 分别装 CaffeineCacheManager/ConcurrentMapCacheManager、@ConditionalOnMissingBean 兜底、properties TTL/maxSize 绑定、Caffeine TTL 过期行为)、@Cacheable 集成(@EnableCaching + CacheManager 联合 runner)
- 覆盖率维持 gate 0.88;新包目标 ≥90% line

## 8. 分阶段(实现拆分)
- **簇 A 限流**:ratelimit 包(SPI/TokenBucket/门面/注解/拦截器/异常/装配/properties)+ web 簇 handler 新增 429 处理 + ADR-0014 + imports
- **簇 B 缓存**:cache 包(CacheUtil/装配/properties)+ ADR-0015 + imports + pom Caffeine optional
- 两簇独立,可任意顺序;每簇内先 SPI/门面后装配,TDD,迁移无关(纯新增)

## 9. 验收
- `mvn clean verify` 全绿 + JaCoCo gate 0.88 + dependency:analyze 零 warning + ArchUnit 四规则(新包纳入无环)
- 两组件门面/注解/装配可用;@RateLimit 声明式限流生效返 429;CacheUtil + @Cacheable 可用
- ADR-0014/0015 落档;README 特性矩阵 + USAGE + 装配开关表更新;imports 8 装配
