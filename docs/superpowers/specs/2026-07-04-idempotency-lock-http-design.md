# 幂等 idempotency + 分布式锁 lock + HTTP client 组件设计

> **状态**:待用户 review
> **来源**:spec 2026-07-02 §10 roadmap(幂等=注解+拦截器+存储 SPI;分布式锁=SPI+默认单机,Seam 升 real seam 示范;HTTP client=RestClient 薄封装)
> **日期**:2026-07-04
> **用户已定 3 决策(brainstorming)**:D1 完整幂等(同 key 返回首次缓存响应);D2 锁门面高阶 executeWithLock + 命令式 tryLock/unlock 都提供;D3 HTTP 底层 RestClient 薄封装。

## 1. 背景与范围

为 server-facility 增加三个 §10 roadmap 组件,遵循既定范式(deep module 门面、Seam SPI `@ConditionalOnMissingBean` 兜底、通用能力/web 集成分离避环、optional 依赖、ADR、`facility.*` 前缀、测试全面 + gate 0.88)。三组件相互独立,合于一个 spec、实现拆独立 task 簇。

**关键架构教训(限流)**:ArchUnit slice 按顶层子包 `facility.(*)..` 聚合,`web.*` 全归 `web` slice。凡"web 集成依赖通用能力 + 通用能力的异常/类型被 web 处理"的组件,通用能力须零 web 依赖、web 集成移 `web.*` 子包,保持单向 `web→通用` 无环。幂等适用此范式;锁/HTTP 纯通用不涉及。

## 2. 幂等 idempotency

### 2.1 通用 `cn.code91.facility.idempotency`(零 web 依赖)
- **`IdempotencyStore`**(SPI):
  - `boolean tryBegin(String key, long ttlMillis)` —— 原子占位:key 不存在则写入 PROCESSING 记录返 true;已存在返 false
  - `Optional<IdempotencyRecord> find(String key)`
  - `void complete(String key, IdempotencyRecord done)` —— 写入 DONE 记录(含缓存响应)
- **`IdempotencyRecord`**(record):`State state`(枚举 PROCESSING/DONE)、`int statusCode`、`String contentType`、`byte[] body`、`long expiresAtMillis`
- **`InMemoryIdempotencyStore`**(默认实现):`ConcurrentHashMap<String, IdempotencyRecord>`;`tryBegin` 用 `putIfAbsent` 原子占位(过期记录视为不存在,惰性清理);`max-entries` 上限防无界(超限清空 + WARN,同限流范式);`@ConditionalOnMissingBean(IdempotencyStore.class)` 可替换 Redis。

### 2.2 web 集成 `cn.code91.facility.web.idempotency`(依赖 idempotency + servlet)
- **`@Idempotent`**(方法注解):`headerName()` 默认 `"Idempotency-Key"`、`ttlSeconds()` 默认 0(0=用 properties 默认)
- **`IdempotencyInterceptor`**(HandlerInterceptor,构造注入 `IdempotencyStore` + 默认 ttl):
  - `preHandle`:读方法 `@Idempotent`;无注解放行。读 header key;key 空 → 400(幂等请求须带 key)或放行(择一,倾向 400 明确)。`store.find(key)`:
    - DONE → 把缓存的 statusCode/contentType/body 写入 response,返回 false(阻断 handler,直接返回首次响应)
    - PROCESSING → 写 409 Conflict("请求处理中,请勿重复提交"),返回 false
    - 不存在 → `store.tryBegin(key, ttl)`;成功放行(request attribute 记 key 供 afterCompletion)、失败(并发 begin 竞态)→ 409
  - `afterCompletion`:若本请求 begin 了 key 且无异常,从 `ContentCachingResponseWrapper` 读 status+body+contentType,`store.complete(key, DONE 记录)`;有异常则不缓存(可选:清除占位让重试)
- **`IdempotencyFilter`**(OncePerRequestFilter,`@Order` HIGHEST):把 response 包装为 `ContentCachingResponseWrapper` 使拦截器 afterCompletion 能读 body,chain 后 `copyBodyToResponse()`。仅包装(基础设施),幂等逻辑在拦截器。
- **`IdempotencyConflictException`**?—— 不需要;拦截器直接写 response(preHandle 返 false),不抛异常穿过 handler(避免又依赖 web.exception)。

### 2.3 装配 + properties
- **`FacilityIdempotencyAutoConfiguration`**:`IdempotencyStore` bean(`@ConditionalOnMissingBean`,无 web 条件)+ `IdempotencyInterceptor` + `IdempotencyFilter` + 注册 configurer(`@ConditionalOnWebApplication(SERVLET)`)。
- **`FacilityIdempotencyProperties`**(`facility.idempotency`):`enabled`、`default-ttl`(Duration)、`max-entries`。

## 3. 分布式锁 lock(纯通用 `cn.code91.facility.lock`)

- **`DistributedLock`**(SPI):
  - `boolean tryLock(String key, Duration leaseTime)` —— 尝试获取(带租约,到期自动释放,对分布式实现有意义)
  - `void unlock(String key)`
  - `default <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action)` —— 高阶:`tryLock` 成功则 try-finally 执行 action + `unlock`;失败抛 `LockAcquisitionException`。default 方法基于前两者,所有实现复用。
  - `default void executeWithLock(String key, Duration leaseTime, Runnable action)` —— Runnable 重载
- **`LockAcquisitionException`**(RuntimeException):获取失败(超时/被占)
- **`InMemoryDistributedLock`**(默认单机实现):`ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>`;`tryLock(key, leaseTime)` 用 `ReentrantLock.tryLock(leaseTime, TimeUnit)`;`unlock` 释放当前线程持有锁;单机无真租约(ReentrantLock 无自动过期),leaseTime 作为 tryLock 等待超时——**ADR 明确单机语义与分布式的差异**;`max-locks` 上限防无界。
- **`LockUtil`**(静态门面):`tryLock`/`unlock`/`executeWithLock` 委托 `SpringContextHolder.getBean(DistributedLock.class)`;**无 bean 时的降级**:锁不可用不能静默放行(会破坏互斥语义)——降级策略:无 bean 时 `executeWithLock` 直接执行 action(退化为无锁,单机单实例场景可接受)+ WARN,或抛异常。**倾向:无 bean → 直接执行 + WARN**(与限流/缓存降级哲学一致:基础设施缺失不阻断业务,但日志告警;ADR 记录此权衡与风险)。
- **装配 `FacilityLockAutoConfiguration`**:`DistributedLock` bean(`@ConditionalOnMissingBean`,无 web)+ properties `facility.lock`(`enabled`、`max-locks`、`default-lease`)。
- **ADR-0016 real seam 示范**:默认单机 `InMemoryDistributedLock` 是 hypothetical seam(单机够用);升级 real seam(多实例分布式)= 消费方引入 Redisson + 声明 `RedissonDistributedLock implements DistributedLock` bean,`@ConditionalOnMissingBean` 自动让位。ADR 给出示范代码骨架 + 单机↔分布式语义差异(租约、可重入、故障释放)。

## 4. HTTP client 门面(纯通用 `cn.code91.facility.http`)

- **`HttpClients`**(静态门面,委托 Spring `RestClient`):
  - `<T> Result<T, WrappedError> get(String url, Class<T> responseType)`
  - `<T> Result<T, WrappedError> get(String url, Map<String,String> headers, Class<T> responseType)`
  - `<T> Result<T, WrappedError> post(String url, Object body, Class<T> responseType)`(body 经 RestClient/Jackson 序列化)
  - `<T> Result<T, WrappedError> put(...)` / `Result<Void, WrappedError> delete(String url)`
  - 内部持有 `RestClient`(从 `SpringContextHolder.getBean(RestClient.class)` 取消费方配置的,或默认 `RestClient.create()` 兜底);超时/错误 → `WrappedError`(新增 `FacilityErrorType.HTTP_*` 或复用既有 http 错误码)。4xx/5xx 状态 → err(带状态码);网络异常/超时 → err。
- **`FacilityHttpProperties`**(`facility.http`):`connect-timeout`、`read-timeout`(Duration)。
- **装配 `FacilityHttpAutoConfiguration`**:`@ConditionalOnMissingBean(RestClient.class)` 提供一个按 properties 配置超时的 `RestClient` bean(`@ConditionalOnClass(RestClient.class)`);消费方可提供自己的 RestClient 替换。门面优先用容器内 RestClient bean,无则默认 create。
- 依赖:spring-web(optional,已有——RestClient 在 spring-web 6.1+)。复用 error/result/json。

## 5. 依赖
| 依赖 | scope | 用途 |
|---|---|---|
| spring-web(RestClient) | 已有 optional | HTTP client 门面 + 幂等 ContentCachingResponseWrapper |
| spring-webmvc | 已有 optional | 幂等拦截器/Filter |
| jakarta.servlet-api | 已有 optional | 幂等 servlet 包装 |
不引 Redis/Redisson(分布式锁/幂等/Redis 存储经 SPI,消费方自备)。

## 6. ADR 计划
- **ADR-0016 分布式锁 SPI + real seam 示范**:SPI 设计(tryLock/unlock/executeWithLock 高阶)、默认单机 InMemoryDistributedLock 语义(ReentrantLock,无真租约)、单机↔分布式差异、real seam 升级路径(Redisson 骨架)、LockUtil 无 bean 降级权衡(直接执行 + WARN 的风险)。
- **ADR-0017 幂等完整语义 + 响应捕获**:完整幂等(同 key 返首次响应)vs 防重复提交(409)的选择、Filter 包装 + 拦截器逻辑分工、PROCESSING/DONE 状态机、响应体 ContentCachingResponseWrapper 捕获、通用/web 分离避环。
- **ADR-0018 HTTP client Result 化 + RestClient 委托**:RestClient(非 RestTemplate/WebClient)、门面返 Result(4xx/5xx/网络异常→WrappedError)、超时 properties、RestClient bean Seam。

## 7. 测试计划
- **幂等**:InMemoryIdempotencyStore(tryBegin 原子占位、find、complete、TTL 过期、max-entries 防护、并发 tryBegin 只一个成功)、拦截器(无注解放行、DONE 写缓存响应、PROCESSING 409、新占位放行、afterCompletion 捕获缓存)、Filter(包装+copyBody)、装配、端到端(MockMvc:同 key 二次请求返首次响应体)。
- **锁**:InMemoryDistributedLock(tryLock 成功/超时失败、unlock、executeWithLock 自动释放、并发互斥用 CountDownLatch 验证临界区串行、租约超时、max-locks)、LockUtil 门面(有 bean 委托、无 bean 降级直接执行 + 私有构造)、executeWithLock 异常时仍 unlock(finally)、装配。
- **HTTP**:HttpClients(get/post/put/delete 用 MockRestServiceServer 或 MockWebServer 桩,2xx→ok、4xx/5xx→err 带状态码、网络异常→err、header/body 序列化)、超时 properties、装配 RestClient bean。
- 覆盖率维持 gate 0.88;新包 line ≥90%。

## 8. 分阶段(实现拆分,3 独立簇)
- **簇 C 分布式锁**(最简,纯通用):lock 包 SPI/InMemory/门面/异常 + 装配 + properties + ADR-0016。
- **簇 D HTTP client**(纯通用):http 包 HttpClients 门面 + properties + 装配 RestClient bean + ADR-0018 + 错误码。
- **簇 E 幂等**(最复杂,web 集成):idempotency 通用(SPI/record/InMemory)+ web.idempotency(注解/拦截器/Filter)+ 装配 + properties + ADR-0017。
- 顺序建议 C→D→E(由简到繁);各簇独立可任意序。

## 9. 验收
- `mvn clean verify` 全绿 + gate 0.88 + dependency:analyze 零 warning + ArchUnit 四规则(新包纳入无环,尤其 web.idempotency→idempotency 单向)。
- 锁:LockUtil.executeWithLock 互斥 + 自动释放 + SPI 可替换;HTTP:HttpClients Result 化 + RestClient 委托;幂等:@Idempotent 同 key 返首次响应 + PROCESSING 409。
- imports 8→11 装配(+Lock/Http/Idempotency);ADR-0016/0017/0018;README/DESIGN/USAGE 更新。
