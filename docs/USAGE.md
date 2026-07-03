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
- **`@ConditionalOnMissingBean` 全量回退**:6 个自动装配的每个 bean 都可被同名/同类型的消费方 bean
  覆盖 —— 包括 `messageSource`、`facilityAsyncExecutor`、异常处理器、各 Web 过滤器/拦截器。facility
  只填空缺,从不抢占你声明的实现。
- **配置属性无校验 provider 依赖**:properties 类不用 `@Validated`(ADR-0013),即便消费方 classpath
  没有 Bean Validation provider 也能正常启动;取值约束(如 worker-id 范围)在组件构造器兜底。

## optional 依赖矩阵

facility 把重依赖声明为 Maven `optional`,消费方按用到的能力自行引入:

| 能力 | 需要引入 |
|---|---|
| 全部 Web 簇(filter/interceptor/exception/response/session/download/argument/util) | `org.springframework:spring-web`、`spring-webmvc`、`jakarta.servlet:jakarta.servlet-api` |
| `XssUtil`(HTML 清洗) | `org.jsoup:jsoup` |
| `MimeTyping` / `SafeUpload` 的 MIME 魔数探测 | `org.apache.tika:tika-core` |

未引入对应 optional 依赖时,相关自动装配因 `@ConditionalOnClass` 不生效,不影响其余簇。
