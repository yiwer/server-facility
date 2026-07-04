# server-facility 使用指南

## 目录
- [核心值类型:Result](#核心值类型result)
- [ID 生成:IdUtil](#id-生成idutil)
- [JSON:JsonUtil](#jsonjsonutil)
- [日志:LogUtil](#日志logutil)
- [日期:DateUtil](#日期dateutil)
- [i18n:LocaleUtil](#i18nlocaleutil)
- [异步:Async](#异步async)
- [Web 簇](#web-簇)
- [限流:RateLimiterUtil / @RateLimit](#限流ratelimiterutil--ratelimit)
- [缓存:CacheUtil / @Cacheable](#缓存cacheutil--cacheable)
- [分布式锁:LockUtil](#分布式锁lockutil)
- [HTTP client:HttpClients](#http-clienthttpclients)
- [幂等:@Idempotent](#幂等idempotent)
- [装配开关全表](#装配开关全表)
- [消费方须知](#消费方须知)
- [optional 依赖矩阵](#optional-依赖矩阵)

## 核心值类型:Result

`Result<T,E>` 是 sealed 类型,把"成功值 T"与"失败原因 E"编码在类型里,替代 `null` 与异常穿透。

```java
Result<User, WrappedError> r = Result.ok(user);
Result<User, WrappedError> e = Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
Result<Void, WrappedError> v = Result.ok();          // 成功但无值
Result<String, E> empty = Result.empty();            // 显式"空成功"(ADR-0007)

// 查询与取值
if (r.isOk()) { User u = r.get(); }
User u = r.orElse(defaultUser);

// 变换(错误短路透传)
Result<String, WrappedError> name = r.map(User::getName);
Result<Order, WrappedError> order = r.flatMap(u -> orderService.latest(u));

// 与 Optional / nullable 桥接
Result<User, MyErr> fromOpt = Result.fromOptional(opt, () -> new MyErr());
Result<User, MyErr> fromNul = Result.fromNullable(maybeNull, () -> new MyErr());
```

## ID 生成:IdUtil

```java
Long id            = IdUtil.snowId();              // 雪花 ID(long)
UUID u             = IdUtil.uuid();
String s1          = IdUtil.uuidStr();             // 带连字符
String s2          = IdUtil.uuidSimpleStr();       // 无连字符
String s3          = IdUtil.uuidStrUpperCase();

// 从雪花 ID 反解(实例方法风格,ADR-0008)
long ts            = IdUtil.parseTimestamp(id);
long worker        = IdUtil.parseWorkerId(id);
String info        = IdUtil.parseInfo(id);         // 可读摘要
```

worker/dataCenter 经 `facility.id.*` 配置(见开关全表)。范围校验在 `SnowIdGenerator` 构造器
兜底(ADR-0013),越界配置在启动时失败而非绑定时。

## JSON:JsonUtil

序列化/反序列化返回 `Result`,不抛异常。支持多命名空间(不同 ObjectMapper 策略)。

```java
Result<String, WrappedError> json  = JsonUtil.serialize(user);
Result<byte[], WrappedError> bytes = JsonUtil.serializeToBytes(user);
Result<Void, WrappedError>   toOut = JsonUtil.serializeTo(user, outputStream);
String unsafe = JsonUtil.serializeUnsafe(user);    // 失败抛异常,仅用于确信不失败处

// 命名空间:DEFAULT / GENERIC / CANONICAL / PRETTY
Jsons pretty = JsonUtil.use(JsonUtil.PRETTY);
Result<String, WrappedError> pj = pretty.serialize(user);
```

`JsonsRegistry`(经 `JsonUtil.registry()`)是进程级单例 —— 见[消费方须知](#消费方须知)。

## 日志:LogUtil

SLF4J 风格静态门面。占位符 `{}`;**Throwable 显式置于参数区**,避免被当作占位符实参吞掉(ADR-0005)。

```java
LogUtil.info("user {} logged in from {}", userId, ip);
LogUtil.warn("retry {} of {}", attempt, max);

// 带异常:Throwable 在 msgPattern 之后、可变参数之前
LogUtil.error("load resource {} failed", ex, resourceId);
LogUtil.warn("degraded", ex);
```

主源码零 logback 依赖(ADR-0011);运行时由消费方的 SLF4J 绑定决定实际输出。

## 日期:DateUtil

```java
Result<String, WrappedError> s = DateUtil.format(LocalDate.of(2025,1,2), "yyyy-MM-dd");  // Result
Date today      = DateUtil.nowDay();
Date y          = DateUtil.yesterday(someDate);
boolean same    = DateUtil.isSameDay(d1, d2);
LocalDateTime t = DateUtil.longToLocalDateTime(epochMillis);
```

解析支持多种常见格式(`SUPPORT_DATE_FORMAT`);非法输入返回 `Result.err(...)` 而非抛异常。

## i18n:LocaleUtil

```java
String msg = LocaleUtil.translateMessage("facility.json.serialize_error");           // 当前线程 Locale
String en  = LocaleUtil.translateMessage("facility.json.serialize_error", Locale.ENGLISH);
String p   = LocaleUtil.translateMessageWithArgs("facility.web.error.missing_parameter",
                                                 new Object[]{"userId"});
// 缺键兜底:MessageSource 缺该键时用 fallbackPattern 渲染,而非抛 NoSuchMessage
String safe = LocaleUtil.translateMessageWithFallback(key, args, "default {0}", Locale.ENGLISH);
```

> **注意**:`translateMessage` / `translateMessageWithArgs` 仅在 **无 MessageSource bean** 时返回原始
> key;MessageSource 在场但 key 缺失会抛 `NoSuchMessageException` 穿透。需要缺键兜底请用
> `translateMessageWithFallback`。

## 异步:Async

`Async<T>` 是惰性异步计算描述,`await()`/`submit()` 才触发,结果落到 `Result`。默认虚拟线程执行器。

```java
Result<String, Throwable> out = Async.supply(() -> httpGet(url))   // 可抛受检异常的 supplier
        .map(Response::body)
        .recover(e -> "fallback")
        .await();                                     // 阻塞取 Result

String v = Async.supply(() -> compute())
        .executor(myPool)                             // 覆盖默认执行器(submit 时生效)
        .timeout(Duration.ofSeconds(2))
        .awaitValue();                                // 失败抛出

// 组合
Async<List<String>> all = Async.all(taskA, taskB);    // 全部成功才成功
CompletableFuture<Result<String, Throwable>> f = task.submit();   // 非阻塞
```

## Web 簇

需要 servlet 栈 optional 依赖(见矩阵)。整体在 servlet Web 应用下装配,各组件由 `facility.web.*` 开关控制。

- **统一响应**:`BaseResponse<T>` / `PageBaseResponse<T>`;`BaseResponse.fromResult(result)` 把 `Result` 桥到响应体。
- **全局异常**:默认注册 `DefaultGlobalExceptionHandler`;继承 `AbstractGlobalExceptionHandler`
  并声明 `@RestControllerAdvice` 即可覆盖(`@ConditionalOnMissingBean` 让位)。`use-problem-detail=true`
  切 RFC 7807(ADR-0003)。
- **过滤链**:`TraceIdFilter`(MDC traceId)、`RepeatableRequestFilter`(可重复读请求体,超限 413)。
- **访问日志**:`AccessLogInterceptor`(慢请求阈值告警)。
- **安全上传下载**:`SafeUpload`(路径穿越防御 + 危险扩展名拦截 + 类型/大小校验)、`HttpFileResponses`
  (中文文件名 RFC 5987 编码、Content-Type 推断)。
- **会话**:`SessionUtil` / `SessionUserHolder`(ThreadLocal 当前用户,请求结束由 `SessionUserClearInterceptor` 清理)。
- **工具**:`RequestUtil`(客户端 IP 等)、`ResponseUtil`(写 JSON / 下载头)、`CookieUtil`、`XssUtil`(jsoup allowlist)。

## 限流:RateLimiterUtil / @RateLimit

通用限流(`ratelimit` 包,令牌桶,零 web 依赖)+ web 集成(`web.ratelimit` 包,注解 + 拦截器)。

```java
// 编程式(任意场景,含非 web):无 RateLimiter bean 时降级放行 true
if (RateLimiterUtil.tryAcquire("order:" + userId)) {
    // 放行
} else {
    // 超限
}
RateLimitResult r = RateLimiterUtil.acquire("k", 1, 100, 10);  // 显式 cap=100/rate=10/s

// 声明式(Spring MVC controller 方法):超限自动 429 + Retry-After
@RateLimit(capacity = 20, permitsPerSecond = 5)   // key 空 = 类#方法#clientIp(按 IP 限流)
@GetMapping("/api/report")
public BaseResponse<Report> report() { ... }

@RateLimit(key = "global-export", capacity = 2, permitsPerSecond = 0.5)  // 固定 key = 全局限流
@GetMapping("/api/export")
public BaseResponse<Void> export() { ... }
```

- **算法**:令牌桶(容量 + 每秒填充速率,允许突发);默认单机 `ConcurrentHashMap` 桶存储,`max-buckets` 防无界。
- **SPI 替换**:声明自己的 `RateLimiter` bean(如 Redis 实现)即整体替换(`@ConditionalOnMissingBean`)。默认实现适合有界 key 集(IP/用户/接口);海量唯一 key 应经 SPI 注入 Caffeine/Redis 实现。
- **降级**:无 `RateLimiter` bean 时 `RateLimiterUtil` 放行(限流不可用不阻断业务)。
- **⚠ 安全(默认 IP 维度)**:空 `key()` 时按 `clientIp` 限流,IP 取自 `X-Forwarded-For` 头,**该头可被客户端伪造**。若服务可被公网直连(前面无覆写 XFF 的受信反代),攻击者可轮换伪造 IP **绕过**按 IP 限流,或伪造海量唯一 IP 顶到 `max-buckets` 触发桶集合清空、**抹掉合法用户限流状态**(放大攻击)。**公网直连服务请设显式 `key()`(如已认证用户 ID),或仅在前置受信反代覆写 XFF 的部署下依赖默认 IP 维度。**

## 缓存:CacheUtil / @Cacheable

```java
// 编程门面(委托 Spring CacheManager;无 CacheManager 时优雅降级)
Optional<User> u = CacheUtil.get("users", id, User.class);
CacheUtil.put("users", id, user);
CacheUtil.evict("users", id);
User loaded = CacheUtil.getOrCompute("users", id, User.class, () -> userRepo.findById(id));  // 穿透便捷

// Spring 注解(装配 CacheManager 后 + 消费方 @EnableCaching 即可用)
@Cacheable("users")
public User findById(Long id) { ... }
```

- **后端**:Caffeine 在 classpath → `CaffeineCacheManager`(`facility.cache.default-ttl` / `maximum-size` 生效);否则 `ConcurrentMapCacheManager`(无 TTL、无界)。
- **TTL**:Spring 原生 `@Cacheable` 无 per-cache TTL;经 `CaffeineCacheManager` 全局 `expireAfterWrite` 实现。
- **SPI 替换**:`CacheManager` 是 Spring 标准 SPI,声明 Redis `CacheManager` 即替换。
- **降级**:无 `CacheManager` 时 `get` 返空、`getOrCompute` 直调 loader(缓存不可用不阻断业务)。

## 分布式锁:LockUtil

通用分布式锁(`lock` 包,零 web 依赖,批处理/定时任务也可用)。默认单机 `InMemoryDistributedLock`(ReentrantLock),SPI 可替换 Redisson 等分布式实现。

```java
// 高阶(推荐):try-finally 自动获取释放,防忘记 unlock 死锁
String result = LockUtil.executeWithLock("order:" + orderId, Duration.ofSeconds(10), () -> {
    // 临界区:同 key 串行执行
    return processOrder(orderId);
});
LockUtil.executeWithLock("job:daily", Duration.ofSeconds(30), () -> runDailyJob());  // Runnable 重载

// 命令式:灵活但须自己 try-finally
if (LockUtil.tryLock("resource", Duration.ofSeconds(5))) {
    try { /* 临界区 */ } finally { LockUtil.unlock("resource"); }
}
```

- **默认单机语义**:`InMemoryDistributedLock` 用 JDK `ReentrantLock`;`leaseTime` 是 `tryLock` **等待超时**,非持锁后自动过期释放(单机无真租约);可重入(同线程);仅进程内互斥。
- **升级分布式(real seam,ADR-0016)**:多实例部署须声明自己的 `DistributedLock` bean(如基于 Redisson),`@ConditionalOnMissingBean` 自动让位。
- **⚠ 降级**:无 `DistributedLock` bean 时 `executeWithLock` 退化为**直接执行 + WARN**(单实例可接受,但**多实例部署必须确保 bean 在场**,否则退化无锁破坏跨实例互斥)。

## HTTP client:HttpClients

通用 HTTP 调用门面(`http` 包),委托 Spring `RestClient`,返回 `Result`(不抛异常)。

```java
Result<User, WrappedError> r = HttpClients.get("https://api.example.com/users/1", User.class);
Result<Order, WrappedError> o = HttpClients.post("https://api.example.com/orders", newOrder, Order.class);
Result<Void, WrappedError> d = HttpClients.delete("https://api.example.com/users/1");
Result<User, WrappedError> h = HttpClients.get(url, Map.of("Authorization", "Bearer " + token), User.class);
```

- **错误映射**:4xx/5xx 响应 → `Result.err`(`FacilityErrorType.HTTP_STATUS_ERROR`,args[0]=HTTP 状态码);网络/超时异常 → `err`(`HTTP_SEND_AND_PARSE_ERROR`,args[0]=url)。
- **超时**:经 `facility.http.connect-timeout` / `read-timeout` 配置(装配的 `RestClient` bean);消费方可声明自己的 `RestClient` bean 替换(换 Apache HttpComponents/OkHttp requestFactory)。

## 幂等:@Idempotent

完整幂等(`web.idempotency` 集成 + `idempotency` 通用存储):同幂等 key 的重复请求返回**首次的响应**,业务方法只执行一次。

```java
@Idempotent                                        // header 默认 Idempotency-Key
@PostMapping("/pay")
public BaseResponse<PayResult> pay(@RequestBody PayRequest req) {
    return BaseResponse.ok(paymentService.charge(req));   // 同 key 重复请求不会再次扣款
}
@Idempotent(headerName = "X-Request-Id", ttlSeconds = 600)   // 自定义 header + TTL
@PostMapping("/order")
public BaseResponse<Order> createOrder(...) { ... }
```

- **语义**:客户端每次业务请求带唯一 `Idempotency-Key` 头。首次 → 处理并缓存响应(status+body);重复(同 key,TTL 内)→ 直接返回首次缓存的响应,业务方法**不再执行**;首次仍处理中的并发重复 → **409**;缺 key 头 → **400**。
- **存储**:默认内存 `InMemoryIdempotencyStore`(PROCESSING/DONE 状态机 + TTL);SPI 可替换 Redis(多实例共享)。
- **机制**:`IdempotencyFilter` 包装响应捕获 body,`IdempotencyInterceptor` 读 `@Idempotent` 执行状态机(ADR-0017)。

## 装配开关全表

```yaml
facility:
  id:
    enabled: true
    worker-id: 0                 # 0..31
    data-center-id: 0            # 0..31
    clock-backwards-threshold-millis: 5
    throw-on-clock-backwards-exceed-threshold: true
  web:
    trace:
      enabled: true
      header-name: X-Trace-Id
      mdc-key: traceId
      generate-if-absent: true
    repeatable-request:
      enabled: true
      max-body-bytes: 10485760   # 10MB;≤0 = 不限制
      include-content-types: [application/json, application/xml, "text/"]
      exclude-paths: ["/actuator/**"]
    access-log:
      enabled: true
      log-headers: false
      slow-threshold-millis: 1000
    cors:
      enabled: true
      allowed-origins: []        # 默认空 = 不开 CORS;生产须显式列举
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allow-credentials: false
      max-age: 3600
    exception:
      include-trace-profiles: [dev, test, local]   # 仅这些 profile 暴露堆栈摘要
      use-problem-detail: false                     # true = RFC 7807
  ratelimit:
    enabled: true
    default-capacity: 100                # 令牌桶容量(未被 @RateLimit 覆盖时的默认)
    default-permits-per-second: 10       # 每秒填充速率
    max-buckets: 100000                  # 桶上限(防无界 key 增长,超限清空)
  cache:
    enabled: true
    default-ttl: 10m                     # 仅 Caffeine 后端生效(expireAfterWrite)
    maximum-size: 10000                  # 仅 Caffeine 后端生效
  lock:
    enabled: true
    max-locks: 100000                    # 锁上限(防无界 key 增长);租约时长由各 executeWithLock/tryLock 调用显式传入
  http:
    connect-timeout: 5s                  # RestClient 连接超时
    read-timeout: 10s                    # RestClient 读超时
  idempotency:
    enabled: true
    default-ttl: 5m                      # 幂等记录保留时长
    max-entries: 100000                  # 记录上限(防无界 key 增长)
```

## 消费方须知

- **i18n 抢注模型(`spring.messages.*` 失效)**:facility 以 `@AutoConfigureBefore(MessageSourceAutoConfiguration)`
  抢注 `@Primary` 的 `messageSource`(`AggregatedMessageSource`)。因此 Spring Boot 的
  `spring.messages.basename` 等配置**不影响** facility 自带文案(facility 的 basename 固定为
  `i18n/facility-messages`)。你自己的 `MessageSource` bean 会被聚合进来一起解析;若要完全接管,
  声明名为 `messageSource` 的 bean 即可(`@ConditionalOnMissingBean(name="messageSource")` 让位)。
- **JsonUtil 单例 × 多上下文**:`JsonsRegistry` 是进程级(静态)单例,不随 Spring 上下文创建。
  同一 JVM 内多个 `ApplicationContext`(如测试并行、多模块)共享同一套 ObjectMapper 命名空间 ——
  这是刻意设计(门面无状态、零上下文耦合),但若你在不同上下文注册了不同的 Jackson 定制,注意它们
  作用于同一注册表。
- **两类让位机制(勿混淆)**:
  - ① **`@ConditionalOnMissingBean` 真回退**:`messageSource`、`facilityAsyncExecutor`(按 `TaskExecutor`
    类型)、全局异常处理器(按 `AbstractGlobalExceptionHandler` 类型)、三个 `WebMvcConfigurer`(按 bean 名)
    —— 你声明同类/同名 bean 即让位,facility 只填空缺。
  - ② **Web 过滤器/拦截器靠开关,不靠竞争 bean**:`TraceIdFilter`、`RepeatableRequestFilter`、
    `AccessLogInterceptor` **不走** `@ConditionalOnMissingBean`,仅
    `@ConditionalOnProperty(...enabled, matchIfMissing=true)` —— 声明同类型的 filter/interceptor **不会**顶替
    facility 的(两者并存,双重入链),要停用请 `facility.web.{trace|repeatable-request|access-log}.enabled=false`,
    再注册自己的。`SessionUserClearInterceptor` 无 `enabled` 开关、恒装,要抑制其入链需声明同名的
    `facilitySessionWebMvcConfigurer` bean(归 ① 的按名回退)。
- **配置属性无校验 provider 依赖**:properties 类不用 `@Validated`(ADR-0013),即便消费方 classpath
  没有 Bean Validation provider 也能正常启动;取值约束(如 worker-id 范围)在组件构造器兜底。

## optional 依赖矩阵

facility 把重依赖声明为 Maven `optional`,消费方按用到的能力自行引入:

| 能力 | 需要引入 |
|---|---|
| 全部 Web 簇(filter/interceptor/exception/response/session/download/argument/util) | `org.springframework:spring-web`、`spring-webmvc`、`jakarta.servlet:jakarta.servlet-api` |
| `XssUtil`(HTML 清洗) | `org.jsoup:jsoup` |
| `MimeTyping` / `SafeUpload` 的 MIME 魔数探测 | `org.apache.tika:tika-core` |
| 缓存 TTL/maxSize(`CaffeineCacheManager`) | `com.github.ben-manes.caffeine:caffeine` **+** `org.springframework:spring-context-support`(**成对**——`CaffeineCacheManager` 在 context-support 而非 spring-context;缺任一则回退 `ConcurrentMapCacheManager`) |

未引入对应 optional 依赖时,相关自动装配因 `@ConditionalOnClass` 不生效,不影响其余簇。
