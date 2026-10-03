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
- [加解密:CryptoUtil](#加解密cryptoutil)
- [日志脱敏:MaskUtil](#日志脱敏maskutil)
- [CSV / Excel:CsvUtil / ExcelUtil](#csv--excelcsvutil--excelutil)
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

- **排障指引**:`getFormattedMessage()` 面向用户,不含参数上下文(防路径泄漏,债 1 决议);排障用
  `getArgs()` 或 `toString()`(含 `args=[...]`)。

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

Spring 服务代码优先注入应用拥有的 `Jsons`（ADR-0044）；它复用本应用的 ObjectMapper 和 Boot Jackson customizer，两个应用的实例各自保有其策略。用户自有 `Jsons` bean 优先，此时与 MVC 策略的一致性由用户负责。

```java
final class OrderExport {
    private final Jsons jsons;
    OrderExport(Jsons jsons) { this.jsons = jsons; }
    Result<String, WrappedError> encode(Order order) { return jsons.serialize(order); }
}
```

非 Spring 代码继续显式构造 `new Jsons(mapper)`。构造 mapper 时可以使用 `JsonConfig.standard().customizeBuilder(builder -> builder.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).build()`；预设/模块/特性先应用，再按顺序执行 builder 回调，随后构建。旧 `customize(mapper -> ...)` 仍在构建后最后执行；新代码优先选构建期入口，发布后不再突变 mapper。

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

InputStream 字段可用 `new SimpleModule().addSerializer(InputStream.class, new InputStreamSerializer(1024)).addDeserializer(InputStream.class, new InputStreamDeserializer(1024))` 注册显式字节预算，再通过 `JsonConfig.Builder.addModule` 或宿主 Jackson builder 装配。正数限制原始/解码字节数；serializer 最多读取上限加一个探测字节，并在成功、超限和 I/O 失败时关闭字段源流。解码后的返回流由调用方关闭。无参及 ≤0 仍无上限；JSON 文本读取预算由宿主单独设置。根 JSON 输入/输出流的关闭由 mapper 的 AUTO_CLOSE_SOURCE / AUTO_CLOSE_TARGET 决定，与字段源流分别管理。解析、预算和 I/O 失败通过 `Jsons` 的 Result 错误通道返回；必需依赖/回调为 null 时立即失败。

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

`Async<T>` 是惰性计算描述，`submit/await` 才触发；不新增 DSL。静态默认使用进程共享、有界平台线程池（4 个工作线程、256 个等待项、daemon、空闲 30 秒回收）。应用应注入 Boot 或自己的 `Executor`，显式传入；静态工厂不查 SpringContext。Async 不关闭用户执行器。虚拟线程通过 Boot 的 `spring.threads.virtual.enabled=true` 或显式虚拟线程 Executor 选择。

```java
// applicationTaskExecutor 是应用注入的 Executor
Result<String, Throwable> out = Async.supply(() -> httpGet(url), applicationTaskExecutor)
        .map(Response::body)
        .timeout(Duration.ofSeconds(2))
        .recover(e -> "fallback")
        .await();

// 子任务未指定 executor 时继承；子任务明确指定的 executor 优先。
Async<List<String>> all = Async.all(taskA, taskB).executor(applicationTaskExecutor);
CompletableFuture<Result<String, Throwable>> future = task.submit();
future.cancel(true); // 请求中断实际工作；get/join 遵循 JDK CancellationException 语义
```

- **预算**：timeout 的位置不改变范围，它从本次 submit 起覆盖整棵任务树；重复设置及子任务只能缩短，不能延长。到达 deadline 即失败；零/负 Duration 立即到期，极大正值饱和处理。父 deadline 到期是终态，不再执行 recover；任务本身的失败、较短子任务的超时可在剩余父预算内恢复。`await(Duration)` 同样取消超时的工作；等待被中断时请求取消并恢复等待线程中断标志。
- **失败与取消**：Result 保留原始异常对象，包括 AssertionError、RejectedExecutionException；只有真实 deadline 到期才产生 TimeoutException。`cancel(true)` 使用实际 FutureTask 中断工作，false 不中断已运行代码。`any` 首成功后取消其他分支，`all` 等待并聚合失败。取消已完成结果返回 false。
- **上下文**：提交时捕获 MDC，每个 supplier/mapper/recovery/effect 在实际工作线程安装并 finally 恢复原值；元数据和拦截器向子任务继承。拦截器现在每个执行段各执行一次，proceed 返回已完成 Future，拦截器不能再返回自行异步派发的未完成 Future。自定义 ThreadLocal 使用下例作用域；已有事务、安全身份不自动跨线程复制。

```java
public <T> CompletableFuture<Result<T, Throwable>> intercept(AsyncContext ctx, AsyncInvocation<T> next) {
    String prior = holder.get();
    holder.set(ctx.<String>attribute("scope").orElse("default"));
    try { return next.proceed(); }
    finally { if (prior == null) holder.remove(); else holder.set(prior); }
}
```

- **生命周期**：Boot/User Executor 保留自身的容量与关闭策略。没有任何 Executor 时，facility 容器回退为 ThreadPoolTaskExecutor（4 线程/256 队列），销毁阶段拒绝新提交、中断运行项、取消排队项，最多等 1000ms。它跳过更早的 SmartLifecycle 排空阶段，因此 context-close 事件到 bean 销毁之间仍可能接受提交。忽略中断任务会使 `getThreadPoolExecutor().isTerminated()` 继续为 false；关闭返回不会伪报任务已结束。
- **队列和副作用**：JDK 线程池及无 TaskDecorator 的 Spring 线程池会移除已取消任务；其他外部 Executor 的包装/队列行为由所有者负责，标准 shutdownNow 返回的未开始 Future 应由所有者取消。任务可能忽略中断并继续产生副作用；锁、连接及自定义线程上下文在工作本身 finally 中释放，不因观察超时提前释放。不要在同一受限线程池的回调中阻塞 await 新提交的工作。详见 [ADR-0026](adr/0026-async-execution-contract.md)。
## Web 簇

需要 servlet 栈 optional 依赖(见矩阵)。整体在 servlet Web 应用下装配,各组件由 `facility.web.*` 开关控制。

- **统一响应**:`BaseResponse<T>` / `PageBaseResponse<T>`;`BaseResponse.fromResult(result)` 把 `Result` 桥到响应体。
- **全局异常**:默认注册 `DefaultGlobalExceptionHandler`，失败使用真实 HTTP 状态与 RFC 9457 ProblemDetail，成功 DTO 不包装（ADR-0027 替代 ADR-0003 的默认协议）。宿主较高优先级 `@RestControllerAdvice` 可以处理自己的异常；`AbstractGlobalExceptionHandler` 子类会使默认 advice 退让。
- **过滤链**:`TraceIdFilter`(MDC traceId)、显式启用的 `RepeatableRequestFilter`(有界同步重复读，超限 413)。
- **访问日志**:`AccessLogInterceptor`(慢请求阈值告警)。
- **安全上传下载**:`SafeUpload`(路径穿越防御 + 危险扩展名拦截 + 类型/大小校验)、`HttpFileResponses`
  (中文文件名 RFC 5987 编码、Content-Type 推断)。
- **会话**:`SessionUtil` / `SessionUserHolder`(ThreadLocal 当前用户,请求结束由 `SessionUserClearInterceptor` 清理)。
- **工具**:`RequestUtil`(客户端 IP 等)、`ResponseUtil`(写 JSON / 下载头)、`CookieUtil`、`XssUtil`(jsoup allowlist)。

### HTTP 错误迁移与扩展

默认错误体的 `code` 为业务短码（FacilityException）或 HTTP 状态；`detail` 使用安全文案，`errors` 为字段错误数组，`traceId` 与追踪响应头一致。实例 URI 使用 `urn:facility:error:<traceId>`，不会反射请求路径、查询或秘密输入。字段错误最多 32 项，包含 `field`、`code=invalid`、安全 `message`；不公开 rejectedValue、校验注解原文或 cause。

| 场景 | 默认 HTTP | 必要头 |
|---|---|---|
| 输入解析/校验、业务拒绝、坏 multipart | 400 | — |
| 无匹配资源/方法/媒体 | 404/405/406/415 | 405 Allow、415 Accept |
| 显式业务状态、上传超限 | 409/413/422 | 标准 ErrorResponse 携带的协议头 |
| 限流 | 429 | Retry-After，毫秒向上取整为秒且至少 1 |
| 内部异常、内部返回值校验、异步超时 | 500/503 | — |
| 认证入口/权限拒绝 adapter | 401/403 | 401 可携带 WWW-Authenticate；身份实现由宿主提供 |

`FacilityHttpErrors` 是可替换 bean，MVC、Filter 和 ERROR dispatch 共享相同策略。程序式 adapter 注入它并使用 Spring 标准异常：

```java
errors.write(request, response, new ErrorResponseException(HttpStatus.FORBIDDEN));
// MVC 或宿主 advice 需要响应对象时：
return errors.response(failure, new ServletWebRequest(request, response));
```

策略使用应用 ObjectMapper 和 MessageSource。宿主可以覆盖 `facility.web.error.system`、`facility.web.error.invalid_value` 等安全文案；不把输入值放入文案模板。新 advice 子类注入 `FacilityHttpErrors` 并 `super(errors)`；旧 `(properties, environment)` 构造器仅作为弃用的源代码兼容入口，无法采用宿主 mapper/MessageSource。

过滤顺序为 TraceIdFilter（最高优先级）、FacilityHttpErrorFilter（+1）、RepeatableRequestFilter（+2）、IdempotencyFilter（+3）。ERROR dispatch 也走统一边界；Boot 和宿主错误页映射仍可选目的路径，部分状态注册不会撤掉其他错误的兜底。已提交响应保持原样；未提交的响应清除旧正文和实体头，保留安全/CORS/追踪头并设置 `Cache-Control: no-store`。错误 serializer 失败时回退为固定英文安全 500 ProblemDetail。

重复读默认关闭。需要 webhook 验签等同步原始字节重读时，显式配置 `facility.web.repeatable-request.enabled=true`，用 `include-paths` 和媒体类型限定目标，`max-body-bytes` 必须为正数（默认 10 MiB）。0/负数不再表示无上限，启用时会构造失败；无参数 `RepeatableRequestWrapper` 构造也使用 10 MiB。预算按实际字节计算，Content-Length 不用于接受或预分配，chunked 同样受限；本地溢出经公共错误边界返回 413。默认媒体类型含合法 `application/*+json`，不会把 `application/json-unknown` 当作 JSON。

每次 `getInputStream/getReader` 都是独立游标，允许混合或重复读取，字节相同；声明 charset 在包装时冻结，缺省 UTF-8，非法 charset 为 400，畸形字节采用 JDK reader 替换字符。此 wrapper 只支持同步读取，`setReadListener` 每次明确拒绝，不假装完成非阻塞回调。它不关闭容器输入。`HttpFileResponses` 用固定缓冲复制文件，关闭自己打开的文件输入，借用而不关闭 Servlet 输出；写失败或线程中断返回 Err，停止复制，不再追加错误正文。应用自己创建的其他流/生产任务仍由应用管理取消和清理。

旧客户端必须显式配置 `facility.web.exception.use-problem-detail=false`。这保留 `{code,message,data,description,success}` 字段形状、HTTP 200（429 仍为 429）与必要头；消息已安全化，`description` 为空，dev/test/local 也不恢复调试栈。依赖旧异常消息或原始 ErrorResponse body 的客户端应迁移到稳定 code 和 traceId。完整决策与真实 HTTP 证据见 [ADR-0027](adr/0027-safe-http-error-policy.md) 与票 04。

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

- **后端**:Caffeine 在 classpath → `CaffeineCacheManager`(`facility.cache.default-ttl` / `maximum-size` 生效);否则 `ConcurrentMapCacheManager`(无 TTL、无界;两项配置被忽略,装配期有 WARN 提示——F15)。
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

旧 HTTP 响应重放(`web.idempotency` + `idempotency` 存储)：对已保存 DONE 的同 key 返回状态、Content-Type 和正文。当前旧 key/TTL 协议不等于跨身份隔离、事务 exactly-once 或安全的过期重试；授权、业务保存资格与持久化恢复由票 11/12 的协议收敛负责。

```java
@Idempotent                                        // header 默认 Idempotency-Key
@PostMapping("/pay")
public BaseResponse<PayResult> pay(@RequestBody PayRequest req) {
    return BaseResponse.ok(paymentService.charge(req));   // 支付侧仍需自身事务/幂等与恢复保证
}
@Idempotent(headerName = "X-Request-Id", ttlSeconds = 600)   // 自定义 header + TTL
@PostMapping("/order")
public BaseResponse<Order> createOrder(...) { ... }
```

- **语义**:客户端每次业务请求带唯一 `Idempotency-Key` 头。首次 → 处理并缓存响应(status+body);重复(同 key,TTL 内)→ 直接返回首次缓存的响应,业务方法**不再执行**;首次仍处理中的并发重复 → **409**;缺 key 头 → **400**。
- **存储**:默认内存 `InMemoryIdempotencyStore`(PROCESSING/DONE 状态机 + TTL);SPI 可替换 Redis(多实例共享)。
- **捕获**：`IdempotencyFilter` 默认直接流出，包括下载与 SSE；旧 claim 成功后才开启选定响应的有界副本，写入同时到达容器，flush 不等待整个响应生成。`facility.idempotency.max-response-bytes` 默认 1 MiB、必须为正数（ADR-0028）。超限时原响应仍完整流出，副本被丢弃；写失败、已被 MVC 解析的异常和异步移交也不保存不完整结果。旧 PROCESSING 仍保留至 TTL，这不是安全重试承诺。手工提供旧 `ContentCachingResponseWrapper` 不再绕过预算写 DONE，须装配有界 filter。
- **非异常的 4xx/5xx 同样固化**:handler 直接 `return ResponseEntity.status(...)`(非异常的 4xx/5xx)
  同样被固化为幂等首响并回放至 TTL——非异常路径视为业务定论;要避免固化请改抛异常(异常路径不缓存)。

## 加解密:CryptoUtil

纯 JDK 静态门面(`crypto` 包),无需任何配置(无 bean、无 properties),恒可用;算法固定
AES-256-GCM,不暴露 mode/padding 参数(ADR-0019)。

```java
// 对称加解密
SecretKey key = CryptoUtil.generateAesKey();                          // 或 deriveKey / aesKeyFromBytes
String cipher = CryptoUtil.encrypt("敏感数据", key).orElse("");         // Base64(IV‖密文+tag)
String plain  = CryptoUtil.decrypt(cipher, key).orElse("");            // 失败(错误密钥/篡改/畸形)→ err

// 口令派生密钥(PBKDF2)
byte[] salt  = CryptoUtil.generateSalt();                             // 16 字节,须与密文一同持久化
SecretKey dk = CryptoUtil.deriveKey("用户口令", salt)
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage()));

// 密钥导出/导入
String exported     = CryptoUtil.exportKey(key);                     // Base64,写入密钥库
SecretKey restored  = CryptoUtil.importKey(exported)
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage()));

// HMAC 消息认证 / 编解码
String mac = CryptoUtil.hmacSha256("body", "secret").orElse("");     // hex 小写
byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);           // 待编码字节(示例)
String b64 = CryptoUtil.base64Encode(bytes);
String hex = CryptoUtil.hexEncode(bytes);
```

- **密钥存储是调用方责任**:密钥/盐**不得硬编码**进源码或配置,应取自密钥管理服务(KMS/Vault)或
  受控环境变量;`CryptoUtil` 只做算法调用,不托管密钥。
- **GCM nonce 由门面管理**:每次 `encrypt` 自动生成随机 IV 前置拼进密文,调用方无需也无从操心 nonce
  ——不要试图复用密文或自行拼 IV。
- **对称密钥强度取决于传入的 `SecretKey`**:`generateAesKey()` 产出 256-bit;`aesKeyFromBytes` 接受
  16/24/32 字节原始密钥(对应 AES-128/192/256),自行拼装密钥字节时留意长度选择。
- **解密失败统一且不含原因**:错误密钥、密文篡改、畸形输入(Base64 畸形/IV 长度不足)全部返回
  `equals` 相等的 `CRYPTO_DECRYPT_ERROR`,且**不附加底层异常**——这正是设计目的所在(细分失败原因
  会给攻击者提供 padding-oracle 类判别信号),`WrappedError.getException()` 对解密失败恒为 `null`,
  无失败模式可供区分;需要排障请用已知明文/密钥在别处**复现**,不要指望检视该异常(ADR-0019)。
  这与其他方法不同——`encrypt` 失败仍保留底层异常(非 oracle 向量,cause 有助于诊断)。
- **`deriveKey` 口令内存卫生**:内部用 PBKDF2WithHmacSHA256(210_000 迭代)拉伸口令,并在 `finally`
  清零 `PBEKeySpec` 内部口令副本;但入参 `String password` 本身**不可清零**(JVM 字符串不可变)——
  调用方应避免长期持有明文口令 `String`(用完即弃引用,不缓存、不打日志)。
- **国密 SM 系列未内置**:如需 SM2/SM3/SM4,须自行引入 BouncyCastle(本组件纯 JDK,不含)。

## 日志脱敏:MaskUtil

纯 JDK 静态门面(`masking` 包),无需任何配置(无 bean、无 properties),恒可用。为
[`LogUtil`](#日志logutil) 写前脱敏提供引擎,也可独立用于任意文本。设计取舍与规则清单见
ADR-0020。

**默认开启,`LogUtil` 自动生效**:引入依赖后,`LogUtil` 的全部日志方法在消息写盘与
`LogPostHandler` 分发之前会自动调用 `MaskUtil.mask` 脱敏,无需任何额外配置。需要关闭时(如
受控环境下排障需要原始值):

```java
LogUtil.setMaskingEnabled(false);   // 关闭写前脱敏(全局开关,默认 true)
boolean on = LogUtil.isMaskingEnabled();
```

`MaskUtil` 本身也可独立调用,直接对任意字符串脱敏:

```java
String masked = MaskUtil.mask("card=4111111111111111");   // → "card=************1111"
```

`mask` 应用全部六类内置规则;若只需其中一类,可用对应的单规则方法:

```java
String secrets = MaskUtil.maskSecrets("token=abc123");           // → "token=******"
String idCard  = MaskUtil.maskIdCard("110101199003070011");      // → "110101********0011"
String card    = MaskUtil.maskBankCard("4111111111111111");      // → "************1111"
String email   = MaskUtil.maskEmail("zhangsan@example.com");     // → "z***@example.com"
String phone   = MaskUtil.maskPhone("13800138000");               // → "138****8000"
```

**六规则一览**(alternation 顺序即遮蔽优先序;完整设计理由见 ADR-0020):

| 规则 | 匹配 | 遮蔽 | 校验 |
|---|---|---|---|
| SECRET(键值秘密) | password/passwd/pwd/token/accessToken/secret/apiKey/authorization 等键名(大小写不敏感),`=` 或 `:` 分隔,值可带引号;`Authorization: Bearer/Basic <token>` 整体识别 | 值 → 固定 `******`(**不保长**) | — |
| JWT(裸 token) | `eyJ` 开头三段 base64url | 整体 → `******` | — |
| IDCARD(身份证 18 位) | 18 位数字(末位可 X) | 前 6 + `********` + 后 4(保长) | GB 11643 mod 11-2 |
| BANKCARD(银行卡) | 15-19 位连续数字 | 仅留后 4(保长) | Luhn |
| EMAIL | `local@domain.tld` | 首字符 + `***` + 完整域名 | — |
| PHONE(大陆手机号) | `1[3-9]` 开头 11 位 | 前 3 + `****` + 后 4 | — |

**局限须知**:

- **`Throwable` 不脱敏**:`LogUtil.warn(msg, t)` / `error(msg, t)` 中,`msg` 部分经过脱敏,但
  `t` 的 message 与 stack trace 原样输出——重写异常对象不可行,若异常消息可能携带敏感数据,
  请在抛出前避免写入。
- **绕过 `LogUtil` 直连 slf4j 不覆盖**:直接调用 `org.slf4j.Logger` 的代码路径(含本工程内部
  少量直连 slf4j 的类)不经过写前脱敏。
- **校验不过的号码不遮**:IDCARD/BANKCARD 依赖 mod11-2/Luhn 校验位抑制对雪花 ID、epoch 毫秒等
  长数字串的误伤;校验不过的伪造/测试假号(本非真实敏感数据)不会被遮蔽,真实证/卡号定义上必过
  校验故不受影响。
- **SECRET 键名为 substring 语义**:不设左词边界,`accessToken`/`clientSecret`/`mypassword` 等
  含关键词的复合键一并命中(覆盖 camelCase 复合键的必要条件);代价是 `mypassword=` 这类前缀词
  也会命中,方向是宁多遮不漏遮,`tokenizer=` 因关键词未紧邻分隔符而不命中(详见 ADR-0020)。
- **带分隔符卡号、`+86` 前缀手机号、15 位老身份证、姓名/地址/IP** 均不识别或不做处理(设计
  取舍见 ADR-0020 诚实局限)。
- **未闭合引号的秘密值不遮蔽**:`token="abc123`(值以引号开头但未闭合,常见于被截断的 JSON
  片段)三种 `SVAL` 分支均不命中,整段原样输出——若值本身不含 JWT/数字等其他规则形态则完全
  裸奔(详见 ADR-0020)。

## CSV / Excel:CsvUtil / ExcelUtil

两个门面同形对称:读 → `Result<List<List<String>>, WrappedError>`,写 ←
`List<List<String>>`(裸行集,无表头/POJO 语义,首行是否为表头由调用方自行处理);所有
可失败方法返回 `Result`,从不抛异常,null 入参 → err。设计取舍见 ADR-0021。

```java
// ---- CSV(csv 包,纯 JDK,零依赖恒可用)----
var okW1 = CsvUtil.write(Path.of("out.csv"), List.of(List.of("h1", "h2"), List.of("v1", "v2")));
var okW2 = CsvUtil.write(outputStream, List.of(List.of("a", "b,c")));   // 含逗号字段自动加引号
Result<List<List<String>>, WrappedError> r1 = CsvUtil.read(Path.of("in.csv"));
Result<List<List<String>>, WrappedError> r2 = CsvUtil.read(inputStream);
List<List<String>> rows = r1.orElse(List.of());

// ---- Excel(excel 包,POI optional)----
var okW3 = ExcelUtil.write(Path.of("out.xlsx"), List.of(List.of("h1", "h2"), List.of("v1", "v2")));
var okW4 = ExcelUtil.write(outputStream, List.of(List.of("a", "b")));
Result<List<List<String>>, WrappedError> r3 = ExcelUtil.read(Path.of("in.xlsx"));  // xls/xlsx 均可
Result<List<List<String>>, WrappedError> r4 = ExcelUtil.read(inputStream);
```

- **CSV 写**:UTF-8 编码,前置 BOM(Excel 双击打开不乱码,ADR-0021 记录取舍);行尾 CRLF;
  最小引号策略(字段含逗号/引号/换行/首尾空格才加引号,内嵌引号翻倍);行内 `null` 单元格
  写为空串;`rows` 含 `null` 行 → `err(CSV_WRITE_ERROR)`。
- **CSV 读**:兼容剥离 UTF-8 BOM;裸 CR/LF/CRLF 三种行分隔均容忍;引号字段内的逗号/换行/
  成对引号(`""`→`"`)按 RFC 4180 解析;引号未闭合到 EOF → `err(CSV_READ_ERROR)`。
- **Excel 写**:`SXSSFWorkbook` 恒定内存,仅产出 xlsx,单 sheet(`Sheet1`),写完 `close()`
  即清理临时文件;行内 `null` 单元格写为空串;`rows` 含 `null` 行 → `err(EXCEL_WRITE_ERROR)`。
- **Excel 读**:`WorkbookFactory` 自动识别 xls/xlsx;仅读**第一个** sheet;单元格经
  `DataFormatter` 全字符串化(公式取计算值);空单元格 → 空串;整行缺失 → 空 `List`;
  畸形文件/IO 失败 → `err(EXCEL_READ_ERROR)`。
- **缺库降级(`EXCEL_LIB_MISSING`)**:`ExcelUtil` 无需任何装配开关——引入 `poi` +
  `poi-ooxml`(成对,见 [optional 依赖矩阵](#optional-依赖矩阵))即自动启用;缺失(或只引
  其中一个,违反成对约定)时,四个 API 全部返回 `err(EXCEL_LIB_MISSING)`,不会抛
  `NoClassDefFoundError`,也不影响 facility 其余能力。`CsvUtil` 无 optional 依赖,恒可用。

**局限须知**:

- **Excel 读为整簿内存模型**:`WorkbookFactory` 整簿载入,行数上限受堆约束(万行级常规堆
  可用;十万行级建议等待 SAX 流式读,ADR-0021 roadmap)。
- **仅第一个 sheet / 单 sheet**:Excel 读只处理第一个 sheet,写只产出一个 sheet
  (`Sheet1`);不支持样式、合并单元格、多 sheet(留 roadmap)。
- **全字符串化语义**:Excel 单元格经 `DataFormatter` 忠实还原 Excel 显示效果——
  `General` 格式的大整数会按 Excel 自身规则显示为科学计数法(如 `1.23457E+15`),这是
  `DataFormatter` 复刻 Excel 桌面版行为而非 bug;需要保留精确大数值,请在源文件把目标
  单元格设为文本格式,不要依赖门面做额外数值探测。
- **CSV 默认带 BOM**:面向业务导出场景(Excel 直接打开)选择前置 BOM;纯 Unix 工具链消费
  场景如需无 BOM,请自行处理(ADR-0021 记录该取舍非普适最优)。
- **CSV 空行写读不对称**:写出一个空 `List`(无字段)产出一行仅 CRLF 的空行;该空行回读
  时按 CSV"行至少一个字段"的表达能力,会解析为**一个空字符串字段**的行(即
  `List.of("")` 而非原始的空 `List`)——这是格式表达能力边界,不是实现缺陷。
- **CSV 分隔符固定逗号**:分号/Tab 等变体分隔符留 roadmap。

## 装配开关全表

> 可直接复制的带详注样例:`src/main/resources/application.example.yaml`(全部键 = 源码默认值)。

```yaml
facility:
  id:
    enabled: true
    worker-id: 0                 # 0..3(2 bit,构造器守卫)
    data-center-id: 0            # 0..3(2 bit,构造器守卫)
    clock-backwards-threshold-millis: 5
    throw-on-clock-backwards-exceed-threshold: true   # false=回拨不抛,无界等待追上(阻塞,ADR-0023)
    start-timestamp: 1735660800000  # 纪元起点(2025-01-01 00:00:00 UTC+8);投产后勿改,否则既有 ID 时间解析/排序错乱
  web:
    trace:
      enabled: true
      header-name: X-Trace-Id     # 入站值须匹配 [0-9A-Za-z_-]{1,64},否则按缺失处理(F7)
      mdc-key: traceId
      generate-if-absent: true
    repeatable-request:
      enabled: false            # 显式选择同步重复读取场景
      max-body-bytes: 10485760   # 正数预算；0/负数拒绝构造
      include-paths: ["/**"]    # 可缩小为 ["/webhooks/**"]；空列表=无目标
      include-content-types: [application/json, "application/*+json", application/xml, "application/*+xml", "text/*"]
      exclude-paths: ["/actuator/**"]
    access-log:
      enabled: true
      slow-threshold-millis: 1000  # 超阈升 WARN 并标记 slow;0=禁用(F14)
    cors:
      enabled: true
      allowed-origins: []        # 默认空 = 不开 CORS;生产须显式列举
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allowed-headers: ["*"]
      allow-credentials: false
      max-age: 3600
    exception:
      use-problem-detail: true        # 默认 RFC 9457；false 显式选择安全的旧 HTTP 200 envelope
      # include-trace-profiles 已弃用；任何 profile 都不自动输出异常原文或调试栈
  ratelimit:
    enabled: true
    default-capacity: 100                # 令牌桶容量(未被 @RateLimit 覆盖时的默认)
    default-permits-per-second: 10       # 每秒填充速率
    max-buckets: 100000                  # 桶上限(防无界 key 增长,超限清空)
  cache:
    enabled: true
    default-ttl: 10m                     # 仅 Caffeine 后端生效(expireAfterWrite);ConcurrentMap 回退时忽略+启动 WARN
    maximum-size: 10000                  # 仅 Caffeine 后端生效;ConcurrentMap 回退时忽略+启动 WARN
  lock:
    enabled: true
    max-locks: 100000                    # 锁上限(超限拒新 key,fail-closed,F8);租约时长由各 executeWithLock/tryLock 调用显式传入
  http:
    enabled: true                        # F22:五簇开关对称;false 整体关闭 http 装配
    connect-timeout: 5s                  # RestClient 连接超时
    read-timeout: 10s                    # RestClient 读超时
  idempotency:
    enabled: true
    default-ttl: 5m                      # 幂等记录保留时长
    max-response-bytes: 1048576          # 选定响应副本的正数预算，超限只放弃保存
    max-entries: 100000                  # 记录上限(超限先清过期再拒新,fail-closed,F8)
```

## 消费方须知

- **i18n 抢注模型(`spring.messages.*` 失效)**:facility 以 `@AutoConfigureBefore(MessageSourceAutoConfiguration)`
  抢注 `@Primary` 的 `messageSource`(`AggregatedMessageSource`)。因此 Spring Boot 的
  `spring.messages.basename` 等配置**不影响** facility 自带文案(facility 的 basename 固定为
  `i18n/facility-messages`)。你自己的 `MessageSource` bean 会被聚合进来一起解析;若要完全接管,
  声明名为 `messageSource` 的 bean 即可(`@ConditionalOnMissingBean(name="messageSource")` 让位)。
- **JsonUtil 兼容单例 × 多上下文**：旧静态 JsonUtil / JsonsRegistry 共享进程级命名空间，后创建应用会覆盖默认 mapper，关闭不恢复；bean 初始化中捕获 DEFAULT 还可能早于 registry 装配。多个应用使用构造器注入的 `Jsons` 保持各自策略；不要依赖静态注册表表达应用归属。GENERIC/CANONICAL/PRETTY 仍是旧独立预设，并不自动继承宿主 customizer。
- **Context 生命周期与注入**：新代码将 `CacheManager`、`MessageSource`、业务 Module 等必需依赖写在构造器中，由各应用自己的 Spring 容器装配。不要通过静态 holder 再查一次依赖。例如 `OrderQueries(CacheManager cacheManager)` 的实例始终使用本应用传入的缓存管理器；父子容器按 Spring 的常规依赖解析规则工作。
- **SpringContextHolder 兼容入口（已弃用，ADR-0025）**：首个成功发出本容器 `ContextRefreshedEvent` 的 holder 取得唯一进程级注册，刷新中不可查。只有取得注册的实例可以撤销；被拒绝的 B 关闭/启动失败不清理 A，A 关闭后不会自动将曾被拒绝的 B 提升为 owner。新的应用或显式重新成功刷新可以竞争空位。关闭事件先撤销，destroy 兜底且幂等；lookup 与关闭竞争返回既有 Result 错误，已经返回的 bean/正在执行的业务由应用生命周期负责。
- **兼容测试迁移**：用真实 context 注册 holder、refresh、close；不要全局 reset 或用反射清空 holder。`setApplicationContextManually` 只在 `refresh()` 返回后接受活跃且未开始关闭、使用 Spring 标准 singleton registry 的 `AbstractApplicationContext`，不替换已有 owner，并随自己的 context 关闭/原地刷新撤销；原地刷新后须重新手工登记，不支持与 refresh 并发调用。null 保持忽略；未刷新/关闭中/已关闭/不支持该生命周期的对象抛 `IllegalArgumentException`。查询的必需 Class 参数 null 立即报错，null bean 名按缺席返回错误/false。`IdUtil` 和 `LogUtil` 不缓存 Spring bean，因此应用重新创建后使用新服务；`IdUtil.setGenerator` 的显式进程级 override 仍由调用方管理。`LogUtil.clearHandlerCache()` 仅保留为已弃用空操作。
- **两类让位机制(勿混淆)**:
  - ① **`@ConditionalOnMissingBean` 真回退**:`messageSource`、`facilityAsyncExecutor`(按 `Executor`
    类型)、全局异常处理器(按 `AbstractGlobalExceptionHandler` 类型)、三个 `WebMvcConfigurer`(按 bean 名)
    —— 你声明同类/同名 bean 即让位,facility 只填空缺。
  - ② **部分 Web 过滤器/拦截器靠开关,不靠竞争 bean**:`TraceIdFilter`、
    `AccessLogInterceptor` **不走** `@ConditionalOnMissingBean`,仅
    `@ConditionalOnProperty(...enabled, matchIfMissing=true)` —— 声明同类型的 filter/interceptor **不会**顶替
    facility 的(两者并存,双重入链),要停用请 `facility.web.{trace|access-log}.enabled=false`,
    再注册自己的。`SessionUserClearInterceptor` 无 `enabled` 开关、恒装,要抑制其入链需声明同名的
    `facilitySessionWebMvcConfigurer` bean(归 ① 的按名回退)。
  - `RepeatableRequestFilter` 与 `IdempotencyFilter` 按类型让位；各自默认注册使用该实例，注册 bean 也按名称让位。不要另给同一 filter 添加第二项容器注册。重复读默认关闭，启用时才注册。
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
| `ExcelUtil`(Excel 读写) | `org.apache.poi:poi` **+** `org.apache.poi:poi-ooxml`(**成对**,版本 5.3.0 自 pin;缺任一(或两者都缺)则运行时探测降级,四个 API 全返 `err(EXCEL_LIB_MISSING)`,ADR-0021) |

未引入对应 optional 依赖时,相关自动装配因 `@ConditionalOnClass` 不生效,不影响其余簇;
`ExcelUtil` 不走自动装配,缺失时走运行时探测降级(同一效果,不同机制,详见 ADR-0021)。
