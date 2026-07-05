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
