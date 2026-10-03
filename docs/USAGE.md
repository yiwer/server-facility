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
- [上传、MIME 与摘要](#上传mime-与摘要)
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

Spring 服务代码优先注入应用拥有的 `Jsons`（ADR-0046）；它复用本应用的 Jackson 3 JsonMapper 和 Boot JsonMapperBuilderCustomizer，两个应用的实例各自保有其策略。用户自有 `Jsons` bean 优先，此时与 MVC 策略的一致性由用户负责。

```java
final class OrderExport {
    private final Jsons jsons;
    OrderExport(Jsons jsons) { this.jsons = jsons; }
    Result<String, WrappedError> encode(Order order) { return jsons.serialize(order); }
}
```

非 Spring 代码继续显式构造 `new Jsons(mapper)`。构造 mapper 时可以使用 `JsonConfig.standard().customizeBuilder(builder -> builder.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).build()`；预设/模块/特性先应用，再按顺序执行 builder 回调，随后构建。旧 `customize(mapper -> ...)` 已移除；改用 `customizeBuilder(builder -> ...)`。返回的 JsonMapper 不可变，后续构建不会改写已发布实例。core/databind 类型改为 tools.jackson；annotations 仍在 com.fasterxml.jackson.annotation。

预期解析、约束和 I/O 失败返回 `Result`，错误只携带稳定类型，不附带 payload、原始异常或 cause，也不记录它们。用户 getter/serializer/deserializer 的程序故障和无效 mapper 定义仍抛出，不被伪装成坏输入。必需参数为 null 时立即失败。以下静态示例仅适用于明确独立于 Spring 应用政策的 standalone 调用：

```java
Result<String, WrappedError> json  = JsonUtil.serialize(user);
Result<byte[], WrappedError> bytes = JsonUtil.serializeToBytes(user);
Result<Void, WrappedError>   toOut = JsonUtil.serializeTo(user, outputStream);
String unsafe = JsonUtil.serializeUnsafe(user);    // 失败抛异常,仅用于确信不失败处

// 命名空间:DEFAULT / GENERIC / CANONICAL / PRETTY
Jsons pretty = JsonUtil.use(JsonUtil.PRETTY);
Result<String, WrappedError> pj = pretty.serialize(user);
```

注入的 `JsonsRegistry` 属于本应用，默认入口复用该应用 Jsons。`JsonUtil.registry()` 另有 standalone 静态实例，Spring 启停不会改写它。GENERIC/CANONICAL/PRETTY 是显式独立预设，不继承 HTTP 政策；canonical 仅排序，不是密码签名规范化算法。

InputStream 字段可用 `new SimpleModule().addSerializer(InputStream.class, new InputStreamSerializer(1024)).addDeserializer(InputStream.class, new InputStreamDeserializer(1024))` 注册显式字节预算，再通过 `JsonConfig.Builder.addModule` 或宿主 Jackson builder 装配。正数限制原始/解码字节数；serializer 最多读取上限加一个探测字节，并在成功、超限和 I/O 失败时关闭字段源流。解码后的返回流由调用方关闭。无参注解入口固定为 1 MiB；显式预算 ≤0 立即拒绝。默认不自动注册流字段能力。Base64 解码前闸门把临时数组限制在 N+2，成功结果严格 ≤N。根 JSON 输入/输出流的关闭由 mapper 的 AUTO_CLOSE_SOURCE / AUTO_CLOSE_TARGET 决定，与字段源流分别管理。解析、预算和 I/O 失败通过 `Jsons` 的 Result 错误通道返回；必需依赖/回调为 null 时立即失败。

JSON 文档和字符串预算必须在 parser 构建前设置；字段字节预算不会阻止 parser 先形成大字符串。Boot 4.1 使用标准 factory customizer：

```java
@Bean
JsonFactoryBuilderCustomizer jsonInputBudget() {
    return factory -> factory.streamReadConstraints(StreamReadConstraints.builder()
            .maxDocumentLength(2 * 1024 * 1024).maxStringLength(256 * 1024).build());
}
```

类型分别来自 `org.springframework.boot.jackson.autoconfigure` 和 `tools.jackson.core`。独立使用时将受限 `JsonFactory` 传给 `JsonMapper.builder(factory)`。这两个正数是应用按业务选择的示例；不要调用进程级 `overrideDefaultStreamReadConstraints`。Jackson 按输入块/缓冲增长检查，存在固定探测开销；精确网络请求字节闸门属于 Web 边界。迁移列表、默认变化和旧金样见 [Jackson 3 迁移](building/jackson3-migration.md)。

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
- **过滤链**:`FacilityRequestContextFilter`(来源快照、兼容身份和 trace 作用域，内部调用 `TraceIdFilter`)、显式启用的 `RepeatableRequestFilter`(有界同步重复读，超限 413)。
- **访问日志**:`AccessLogInterceptor`(慢请求阈值告警)。
- **安全上传下载**:`SafeUpload`(路径穿越防御 + 危险扩展名拦截 + 类型/大小校验)、`HttpFileResponses`
  (中文文件名 RFC 5987 编码、Content-Type 推断)。
- **会话**:`SessionUtil` / `SessionUserHolder`(仅兼容 ThreadLocal 数据；请求边界负责 SYNC/ASYNC/ERROR 清理，`SessionUserClearInterceptor` 适配宿主 Principal)。
- **工具**:`RequestUtil`(客户端 IP 等)、`ResponseUtil`(写 JSON / 下载头)、`CookieUtil`、`XssUtil`(jsoup allowlist)。

### 请求来源、身份与观测（ADR-0029）

`RequestUtil.getClientIp(request)` / 无参入口默认只采用 Servlet 提供的数值 `remoteAddr`，未装配 Web 边界时也遵守该安全默认。X-Real-IP、Proxy-Client-IP 等旧厂商头不参与解析。要由 facility 负责代理链，显式配置可信 CIDR：

```yaml
facility:
  web:
    proxy:
      trusted-proxies: ["10.20.0.0/16", "2001:db8:abcd::/48"]
    trace:
      accept-inbound: false # 不接受客户端相关性标识；仍优先使用有效宿主 MDC，否则按 generate-if-absent 生成
```

只有直接 peer 在列表中时才读取单个 X-Forwarded-For。从右向左跨过可信代理，返回首个不可信地址；不把最左项天然视为可信。重复头、空白/非法字面量、控制字符、Unicode、超过2048字符或32跳整条退回 peer；可信 CIDR 最多128项。IPv4只接受完整十进制四段且无前导零，IPv6不接受端口、方括号或zone；IPv4-mapped IPv6规范为IPv4，并使用IPv4 CIDR。`InetAddress.ofLiteral` 不查 DNS；hostname/非法 peer 为 `unknown`。所有请求派发共享一次来源快照。声明自己的 `ClientIpPolicy` bean可替代默认具体类；策略失败时不重试解析ERROR派发，使用unknown来源并交给安全500边界。

代理解析只设一个所有者。若已使用可信配置的 Tomcat RemoteIpValve 或 Spring ForwardedHeaderFilter，让 facility 的可信列表保持空，采用其处理后的 remoteAddr；Tomcat已转发标记也会阻止再次解析剩余XFF。facility不替这些宿主组件建立信任，更不把网络IP用于证明登录。

`FacilityRequestContextFilter` 以最高优先级单次注册，覆盖 REQUEST/ASYNC/ERROR（含嵌套ERROR）；错误策略+1、repeatable+2、捕获+3顺序保持。`TraceIdFilter` bean在此边界内使用，旧单独注册默认禁用，不能再手动重复注册；按类型声明替代 TraceIdFilter 可复用相同生命周期。关闭 `facility.web.trace.enabled` 仍保留来源和兼容身份清理；该开关控制默认trace bean，宿主显式声明的TraceIdFilter仍由请求边界采用，且会禁用其额外的容器自动注册。

兼容 holder 仅取宿主 Servlet Principal（MVC前再次适配宿主认证Filter的Principal）或宿主显式设置的兼容值。`SessionUserHolder.isLoggedIn()` 已弃用，它仅判断有值，不能证明认证/授权。新应用直接注入/读取 Spring Security 原生身份；库不验证JWT、不从 X-User/XFF/trace 建立Principal。使用已认证Principal时，旧自定义用户对象的消费者应迁移到Principal或由自己的MVC适配器显式设置兼容值。

顶层请求进入时丢弃遗留holder，实际执行线程在finally清理；嵌套派发恢复外层作用域。Callable只在Spring MVC管理的实际工作线程安装快照，结束后清理，取消/超时回调不跨线程删除仍在执行的上下文。DeferredResult外部生产者、AsyncContext.start和应用任意executor不隐式传播holder；它们使用宿主Security/观测传播政策，重派发才安装请求快照。上下文快照不深拷贝用户对象，宿主仍负责其不可变性与执行器资源。

trace是correlation而非完整分布式追踪。有效宿主MDC优先，其次请求快照、可选单个入站值、UUID；白名单1–64位ASCII字母数字/下划线/短横线。`accept-inbound`默认true保留相关性兼容，可显式false；无效/歧义值按缺失处理。库只改配置的MDC键并在finally恢复原值，不替换宿主其他观测数据。Callable/DeferredResult交接捕获链路内建立的有效观测；worker已有有效观测优先且归原所有者清理。配置在构造时冻结，header-name与mdc-key名称上限128，非法配置启动即失败。MDC安装部分失败时立即清理身份并回滚已捕获的旧值；业务/安装异常是首因，清理异常作为suppressed保留。若宿主MDC本身拒绝恢复，库不能保证其内部数据已恢复，但仍保证兼容身份清理，不把原异常替换成清理错误。

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

过滤顺序为 FacilityRequestContextFilter（最高优先级，内含 TraceIdFilter）、FacilityHttpErrorFilter（+1）、RepeatableRequestFilter（+2）、IdempotencyFilter（+3）。ERROR dispatch 也走统一边界；Boot 和宿主错误页映射仍可选目的路径，部分状态注册不会撤掉其他错误的兜底。已提交响应保持原样；未提交的响应清除旧正文和实体头，保留安全/CORS/追踪头并设置 `Cache-Control: no-store`。错误 serializer 失败时回退为固定英文安全 500 ProblemDetail。

重复读默认关闭。需要 webhook 验签等同步原始字节重读时，显式配置 `facility.web.repeatable-request.enabled=true`，用 `include-paths` 和媒体类型限定目标，`max-body-bytes` 必须为正数（默认 10 MiB）。0/负数不再表示无上限，启用时会构造失败；无参数 `RepeatableRequestWrapper` 构造也使用 10 MiB。预算按实际字节计算，Content-Length 不用于接受或预分配，chunked 同样受限；本地溢出经公共错误边界返回 413。默认媒体类型含合法 `application/*+json`，不会把 `application/json-unknown` 当作 JSON。

每次 `getInputStream/getReader` 都是独立游标，允许混合或重复读取，字节相同；声明 charset 在包装时冻结，缺省 UTF-8，非法 charset 为 400，畸形字节采用 JDK reader 替换字符。此 wrapper 只支持同步读取，`setReadListener` 每次明确拒绝，不假装完成非阻塞回调。它不关闭容器输入。`HttpFileResponses` 用固定缓冲复制文件，关闭自己打开的文件输入，借用而不关闭 Servlet 输出；写失败或线程中断返回 Err，停止复制，不再追加错误正文。应用自己创建的其他流/生产任务仍由应用管理取消和清理。

旧客户端必须显式配置 `facility.web.exception.use-problem-detail=false`。这保留 `{code,message,data,description,success}` 字段形状、HTTP 200（429 仍为 429）与必要头；消息已安全化，`description` 为空，dev/test/local 也不恢复调试栈。依赖旧异常消息或原始 ErrorResponse body 的客户端应迁移到稳定 code 和 traceId。完整决策与真实 HTTP 证据见 [ADR-0027](adr/0027-safe-http-error-policy.md) 与票 04。

## 上传、MIME 与摘要

上传使用设施自己打开的 `MultipartFile` 流，保存过程关闭该流。声明的长度和 Content-Type 不能替代实际字节预算或内容检测。

```java
var saved = SafeUpload.saveFile(file, Path.of("/srv/app-private/uploads"),
        10L * 1024 * 1024, Set.of("image/png", "image/jpeg"));
if (saved.isErr()) {
    // 依据 getErrorType() 映射业务错误；FILE_SIZE_EXCEEDED 可映射 413。
    // 不把底层异常、路径或原始文件名返回给 HTTP 客户端。
    return;
}
Path stored = saved.get(); // 保存这个返回值；不要按 file.getOriginalFilename() 推导路径。
// 宿主记录展示名、权限和保留政策；不再需要文件时 Files.delete(stored)。
```

`maxSizeBytes` 必须正数，≤0 返回 Err；旧便利保存与 `toTempFile` 默认 10 MiB。空/null allowlist 表示不限制类型，但仍有限制大小和危险后缀校验。空上传拒绝；发现超限最多多读 1 字节，拒绝/取消后不 drain。阻塞读的停止取决于底层流协作中断，宿主仍应设置请求 I/O deadline、并发 admission 和磁盘配额。

原名和 `customFileName` 只参与展示名校验；存储名由服务端生成固定长度 `UUID.upload`。因此旧代码不能再根据自定义名字查找文件。应用必须独占维护真实根目录及祖先，不允许其他主体修改，也不要将根目录挂为静态资源目录。保存先在同卷私有暂存完成，并关闭输入/输出，再通过 `Files.createLink` 发布；需要本地文件系统支持硬链接，目标存在或不支持时明确失败，不覆盖或退化复制。已验证 Windows NTFS，Linux 结果见票 13 验证报告；不保证任意 provider、断电持久性或对同权限恶意替换目录的防护。

失败清理仅删除本次自有路径；删除权限/占用仍可能阻止清理，原始异常保留 suppressed 清理原因，宿主应对私有暂存目录做受控恢复。`toTempFile` 成功后必须显式删除，不再使用 `deleteOnExit`：

```java
var temporary = SafeUpload.toTempFile(file);
if (temporary.isOk()) {
    Path path = temporary.get().toPath();
    try { /* consume path synchronously */ }
    finally { Files.deleteIfExists(path); }
}
```

Tika 4.1.0 为 optional；需要 MIME/type allowlist 的消费方显式添加 tika-core。缺包不把类型政策降级成成功保存。只使用 core detector 和至多 64 KiB 前缀，不解压容器；伪 `.xlsx` 的 ZIP 内容仍可能只是 `application/zip`，业务不得把“可识别”理解成“安全”。`SafeUpload.detectMime` 不证明整个文件的大小合规。上传默认不采用客户端文件名提示；仅低层 `MimeTyping.detect(stream, filename)` 显式接受提示。

低层 `MimeTyping.detect(InputStream)` 借用而不关闭流，要求 mark/reset，成功或读失败后尝试恢复当前位置（替换旧 mark）。不可 mark 的流在读取前返回 Err；需要继续消费时保留同一个包装流：

```java
try (var replayable = new BufferedInputStream(openMyInput())) {
    var mime = MimeTyping.detect(replayable);
    if (mime.isOk()) { /* consume replayable, including the detected prefix */ }
}
```

reset 失败时无法保证位置恢复；原读故障仍为首因，reset 故障为 suppressed。`detect(byte[])` 的 null/空以及空内容继续返回 octet-stream；I/O 故障走 Result Err，旧 String 重载改抛 UncheckedIOException。程序错误/Error 清理后传播。

`Hashing.sha256(File)` / `hash(File, algorithm)` 打开并关闭文件、固定缓冲并协作响应中断；不代替文件大小政策。`hashBytes(byte[], algorithm)` 借用数组。输出小写十六进制；空 File 返回标准空内容摘要，空/null byte[] 保留旧 FILE_READ_ERROR，null/未知算法为 FILE_HASH_ERROR。MD5/SHA-1 只为旧非安全协议兼容保留，不用于密码存储或对抗恶意篡改；完整性安全需业务选择经过认证的机制。

设计和平台边界见 [ADR-0036](adr/0036-upload-integrity.md)。

## 限流:RateLimiterUtil / @RateLimit

默认是进程内令牌桶，重启或显式 `clear()` 会重置；多实例不会共享额度。新服务构造器注入本应用的 `RateLimiter`。返回拒绝意味着额度不足，`RateLimiterUnavailableException` 意味着缺设施、运行故障或无法在槽位预算内接纳新主体。

```java
RateLimitResult decision = limiter.acquire("order:" + verifiedSubject, 1, 100, 10);
// 必需兼容门面：缺Bean/故障抛RateLimiterUnavailableException
boolean allowed = RateLimiterUtil.tryAcquire("order:" + verifiedSubject);
// 仅在业务明确接受入口保护降级时使用；remaining=-1表示未知
RateLimitResult optional = RateLimiterUtil.acquireOptional("preview:" + verifiedSubject, 1, 100, 10);

@RateLimit(scope = RateLimit.Scope.PRINCIPAL, capacity = 20, permitsPerSecond = 5)
@PostMapping("/orders")
public Order create() { /* ... */ }

@RateLimit(scope = RateLimit.Scope.GLOBAL, key = "global-export", capacity = 2, permitsPerSecond = 0.5)
@GetMapping("/export")
public void export() { /* ... */ }
```

- **输入**：key为1..512个UTF-16代码单元、非blank、无控制字符；cost为正整数且不大于正容量；rate为有限正数。非法输入和同驻留key的容量/速率冲突抛IllegalArgumentException，不扣费、不创建桶。remaining为原子扣费结果的整数下取整，retryAfterMillis按实际缺额向上取整，超long范围饱和。
- **身份**：PRINCIPAL只取宿主认证后的Servlet Principal.name，要求非blank、无控制字符、最多128单元；缺失/非法403，不回落IP，也不解析用户头/JWT。宿主需保证主体名跨租户唯一。IP使用06的来源快照：默认peer、显式trusted-proxies才用可信XFF；同NAT共享额度。GLOBAL共享所选操作。DEFAULT保留旧空key=IP、非空key=GLOBAL语义，新代码建议显式scope。
- **操作**：空key使用完整类名、方法名和参数类型，跨包及重载分离；非空key是明确共享的字面别名，不能全blank。最终编码key有scope隔离且仍限512单元，长方法签名用短别名；SPI看到的key不透明，外部存储不可依赖旧拼接格式。
- **资源**：默认max-buckets=100000是严格槽位数上界。满额时每次最多检查16个轮转候选，只回收补满桶，否则拒绝新key；既有主体不恢复额度。没有定时器/线程，惰性补充；`clear()`是显式管理重置，自动准入不会调用。构造器可注入单调纳秒LongSupplier；时间倒退忽略，连续观察间隔需小于2^63纳秒。
- **故障**：默认HTTP拒绝为429 + Retry-After，缺Adapter/运行故障为04安全503。`facility.ratelimit.fail-open=true`只显式放行设施不可用；确定的额度拒绝、非法政策和Error不放行。`enabled=false`仅关默认provider，受保护Servlet注解仍守卫；可用宿主RateLimiter bean覆盖默认provider。04显式旧envelope保留429，503则HTTP200/body.code=503。
- **顺序**：入口限流MVC order为HIGHEST_PRECEDENCE+20，在默认幂等order=0前；每个新请求含重放/处理中/冲突重试都计费，后续业务失败不自动退款。同请求同操作/身份/政策的ASYNC完成重派发只扣一次。该注解保护入口工作量；只针对成功业务效果的配额需在事务执行处设计，12/29继续验证组合。

迁移依据与限制见[ADR-0032](adr/0032-local-rate-limit-contract.md)。Optional静态门面仅保留单context兼容，不能代替应用注入，也不背书共享后端的集群保证。

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

新代码从宿主注入 `RestClient.Builder`，按外部服务克隆后装配类型化 Adapter。完整、可运行的双服务聚合见[partner-aggregation](../examples/partner-aggregation/README.md)：继承宿主JSON/customizer/观测，独立凭据和时限，安全失败分类，副作用默认一次，显式GET重试共用deadline。

```java
this.client = hostBuilder.clone()
    .baseUrl(trustedServiceBaseUrl)
    .requestInterceptor(new ResponseBodyLimit(1024 * 1024))
    .build();
```

`ResponseBodyLimit` 限制消息转换前实际body字节；transport启用解压时按解压后计量。必须在拥有response的exchange作用域内读完并关闭，禁止返回借用流。关闭先中止body，避免transport在关闭时继续drain被拒绝的尾部；时间、连接和并发政策由宿主负责。

`HttpClients` 静态API保留签名但已弃用，仍使用历史SpringContextHolder查找、缺Bean时RestClient.create回退及粗粒度Result映射。它不提供新Adapter的有限响应、隔离、未知结果与重试契约；历史错误可能携带URL/cause，不直接输出到公共响应或日志。迁移应把整个外部服务调用移入类型化Adapter。

默认兼容RestClient bean现在在Boot装配之后克隆宿主builder，保留其factory；用户RestClient bean仍让默认装配退让。`facility.http.connect-timeout/read-timeout`仅作用于**没有宿主builder**的兼容Simple factory，使用前要求1ms..2147483647ms；不能用这两个历史属性覆盖宿主Boot的transport政策。详见ADR0048和示例配置表。

## 独立 claim 与执行资格

`IdempotencyStore` 新增 `claim(ClaimRequest)`、`complete(ClaimToken, byte[], Duration)` 与 `release(ClaimToken)`；五类决定区分取得、处理中、回执、内容冲突与不可用。新结果到期只释放正文，不重新许可执行；释放和无法保存结果也保留终态。默认内存新旧命名空间共享严格条目/字节预算，当前owner资格只能保护记录更新。参见[迁移与边界](building/qualified-claims.md)及ADR0034。旧自定义SPI未实现新协议时默认不可用。

## 幂等:@Idempotent

旧 HTTP 响应重放(`web.idempotency` + `idempotency` 存储)：对已保存 DONE 的同 key 返回状态、Content-Type 和正文。当前旧 key/TTL 协议不等于跨身份隔离、事务 exactly-once 或安全的过期重试；票11已提供独立执行资格入口；HTTP整条路径迁移由票12负责，持久业务命令由票29负责。

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

纯 JDK 静态门面(`crypto` 包)，无 bean、无 properties；算法固定AES-GCM，默认生成256位key，
不暴露mode/padding参数。先落实输入与并发预算，详见[历史读取和消费示例](building/legacy-crypto.md)及ADR-0040。

```java
// 对称加解密
SecretKey key = CryptoUtil.generateAesKey();                          // 或 deriveKey / aesKeyFromBytes
String cipher = CryptoUtil.encrypt("敏感数据", key)
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage())); // Base64(IV‖密文+tag)
String plain = CryptoUtil.decrypt(cipher, key)
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage())); // 不把读取失败默认为空明文

// 口令派生密钥(PBKDF2)
byte[] salt  = CryptoUtil.generateSalt();                             // 16 字节,须与密文一同持久化
SecretKey dk = CryptoUtil.deriveKey("用户口令", salt)
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage()));

// 密钥导出/导入
String exported     = CryptoUtil.exportKey(key);                     // Base64,写入密钥库
SecretKey restored  = CryptoUtil.importKey(exported)
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage()));

// HMAC 消息认证 / 编解码
String mac = CryptoUtil.hmacSha256("body", "secret")
        .orElseThrow(e -> new IllegalStateException(e.getFullMessage())); // hex 小写；示例key非生产凭据
byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);           // 待编码字节(示例)
String b64 = CryptoUtil.base64Encode(bytes);
String hex = CryptoUtil.hexEncode(bytes);
```

- **密钥存储是调用方责任**:密钥/盐**不得硬编码**进源码或配置,应取自密钥管理服务(KMS/Vault)或
  受控环境变量;`CryptoUtil` 只做算法调用,不托管密钥。
- **GCM nonce 由门面生成**:每次 `encrypt` 自动生成随机IV前置拼进密文；随机性不保证无限调用无碰撞，
  消费应用仍负责密钥生命周期与使用量。不要自行改写IV。
- **对称密钥强度取决于传入的 `SecretKey`**:`generateAesKey()` 产出 256-bit;`aesKeyFromBytes` 接受
  16/24/32 字节原始密钥(对应 AES-128/192/256),自行拼装密钥字节时留意长度选择。
- **Result失败不携带秘密诊断**:全部加密/解密/派生/MAC/解码错误只带稳定错误类型，不附provider原始cause、
  输入或日志。解密失败均为相等的`CRYPTO_DECRYPT_ERROR`，但这不是恒定时间保证。业务只记录稳定错误码与
  自有请求关联；程序`Error`继续传播，非Result入口可抛参数/配置异常。旧版本加密等错误保留cause的政策已由ADR-0040替代。
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
`List<List<String>>`(裸行集,无表头/POJO 语义,首行是否为表头由调用方自行处理)。CSV 的格式、预算、编码与 I/O 失败走 `Result`；数据 null 返回 Err，必需的方言、预算及 consumer 为 null 时快速抛出，consumer/程序异常原样传播。设计见 ADR-0021 与 [CSV 替代决定 ADR-0038](adr/0038-bounded-csv-dialects.md)。

```java
// ---- CSV(csv 包,Commons CSV required；不依赖 Excel)----
var okW1 = CsvUtil.write(Path.of("out.csv"), List.of(List.of("h1", "h2"), List.of("v1", "v2")));
var okW2 = CsvUtil.write(outputStream, List.of(List.of("a", "b,c")));   // 含逗号字段自动加引号
Result<List<List<String>>, WrappedError> r1 = CsvUtil.read(Path.of("in.csv"));
Result<List<List<String>>, WrappedError> r2 = CsvUtil.read(inputStream);
List<List<String>> rows = r1.orElse(List.of());

// 大文件采用逐行消费，并给出本次实际预算；不要把默认 read 当作无限制入口。
var limits = new CsvLimits(64L * 1024 * 1024, 1_000_000, 32, 4096);
var consumed = CsvUtil.forEach(inputStream, CsvDialect.STRICT, limits, row -> storeRow(row));
var machine = CsvUtil.writeMachine(outputStream, rowIterable, limits); // 无 BOM，原值
var spreadsheet = CsvUtil.writeSpreadsheet(outputStream, rowIterable, limits); // BOM，公式前缀拒绝

// ---- Excel(excel 包,POI optional)----
var okW3 = ExcelUtil.write(Path.of("out.xlsx"), List.of(List.of("h1", "h2"), List.of("v1", "v2")));
var okW4 = ExcelUtil.write(outputStream, List.of(List.of("a", "b")));
Result<List<List<String>>, WrappedError> r3 = ExcelUtil.read(Path.of("in.xlsx"));  // xls/xlsx 均可
Result<List<List<String>>, WrappedError> r4 = ExcelUtil.read(inputStream);
```

- **CSV 预算**：`CsvLimits(maxBytes, maxRows, maxColumns, maxFieldChars)` 全部为正数，字段长度计 UTF-16 单元，字节含 BOM；错误累计固定为 1，首次失败立即停止。旧 `read`/`write` 与 `CsvLimits.DEFAULT` 为 **1 MiB / 10,000 行 / 128 列 / 1,024 字符**。需要更大规模时显式传预算；`readAll` 仍累积行，优先用 `forEach`。
- **CSV 解析资源**：Commons CSV 负责语法；精确列/字段限制在交付前检查。为阻止单条畸形记录在库内无界分配，另有 `(2 * maxFieldChars + 3) * maxColumns + 3` 源字符上限（默认 262,531），超过整型范围的预算构造失败。冗余语法也消耗该预算。输入最多实际探测到字节预算 N+1；解码器可能预读 8 KiB，借用流不是可恢复的记录游标。
- **CSV 方言**：旧 `read` 采用 LEGACY，`"ab"x,c` 保留为 `abx,c`；STRICT 拒绝该尾随非空白文本。两者剥一个开头 BOM，接受 CR/LF/CRLF、空/ragged 记录、引用内换行和双引号转义，拒绝未闭合引用与坏 UTF-8。STRICT 是本项目机器方言，**并非完整 RFC 验证器**：裸字段内引号仍为字面值，闭合引号后空白会被忽略；LEGACY 保留该空白。
- **CSV 导出**：旧 `write` 为 UTF-8+BOM；`writeMachine` 无 BOM，两者保留机器原值。`writeSpreadsheet` 带 BOM，拒绝前导 Unicode 空白/控制/格式字符之后的 `= + - @` 及全角对应字符，也拒绝前导区域的 Tab/CR/LF；不偷偷加引号前缀或改数据。该保守政策也拒绝负数字符串，不承诺所有电子表格导入方式的通用安全。普通 CSV 引号不是公式防护。
- **CSV 生命周期**：借用输入/输出永不关闭，成功输出会完成 UTF-8 编码并 flush；Path 方法关闭自己打开的流，写文件直接覆盖，不保证原子发布。首次异常后不 drain、不重试坏输出；输出可能已有前缀，已执行 consumer 副作用不会回滚。线程中断在 I/O/记录边界检查，不能取代宿主对不响应中断的 I/O 设置超时或并发准入。
- **CSV 诊断**：`CsvException.reason()/row()/column()` 提供原因与逻辑记录位置，列 0 表示未知；外层消息不包含字段内容，保留的外部 I/O cause 只供受信任诊断，不能直接作为 HTTP 错误。null 单元格写为空串，null 行为 Err。
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
- **CSV BOM**：旧 `write` 保留 BOM 兼容；机器交换用 `writeMachine`，不必自行剥字节。
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
      accept-inbound: true # 仅相关性，不是认证；公网边界可显式false
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
    enabled: true                       # 仅默认provider；false仍保留Servlet注解守卫
    fail-open: false                    # 仅显式允许设施不可用时放行
    default-capacity: 100                # 令牌桶容量(未被 @RateLimit 覆盖时的默认)
    default-permits-per-second: 10       # 每秒填充速率
    max-buckets: 100000                  # 严格槽位上限；只回收补满桶，无法准入则503
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
- **JsonUtil standalone 静态入口**：Spring 启停不再覆盖它；多应用必须注入各自 Jsons 才能复用其 HTTP 政策。应用 registry 和静态 registry 相互独立，GENERIC/CANONICAL/PRETTY 仍是显式独立预设。
- **Context 生命周期与注入**：新代码将 `CacheManager`、`MessageSource`、业务 Module 等必需依赖写在构造器中，由各应用自己的 Spring 容器装配。不要通过静态 holder 再查一次依赖。例如 `OrderQueries(CacheManager cacheManager)` 的实例始终使用本应用传入的缓存管理器；父子容器按 Spring 的常规依赖解析规则工作。
- **SpringContextHolder 兼容入口（已弃用，ADR-0025）**：首个成功发出本容器 `ContextRefreshedEvent` 的 holder 取得唯一进程级注册，刷新中不可查。只有取得注册的实例可以撤销；被拒绝的 B 关闭/启动失败不清理 A，A 关闭后不会自动将曾被拒绝的 B 提升为 owner。新的应用或显式重新成功刷新可以竞争空位。关闭事件先撤销，destroy 兜底且幂等；lookup 与关闭竞争返回既有 Result 错误，已经返回的 bean/正在执行的业务由应用生命周期负责。
- **兼容测试迁移**：用真实 context 注册 holder、refresh、close；不要全局 reset 或用反射清空 holder。`setApplicationContextManually` 只在 `refresh()` 返回后接受活跃且未开始关闭、使用 Spring 标准 singleton registry 的 `AbstractApplicationContext`，不替换已有 owner，并随自己的 context 关闭/原地刷新撤销；原地刷新后须重新手工登记，不支持与 refresh 并发调用。null 保持忽略；未刷新/关闭中/已关闭/不支持该生命周期的对象抛 `IllegalArgumentException`。查询的必需 Class 参数 null 立即报错，null bean 名按缺席返回错误/false。`IdUtil` 和 `LogUtil` 不缓存 Spring bean，因此应用重新创建后使用新服务；`IdUtil.setGenerator` 的显式进程级 override 仍由调用方管理。`LogUtil.clearHandlerCache()` 仅保留为已弃用空操作。
- **两类让位机制(勿混淆)**:
  - ① **`@ConditionalOnMissingBean` 真回退**:`messageSource`、`facilityAsyncExecutor`(按 `Executor`
    类型)、全局异常处理器(按 `AbstractGlobalExceptionHandler` 类型)、三个 `WebMvcConfigurer`(按 bean 名)
    —— 你声明同类/同名 bean 即让位,facility 只填空缺。
  - ② **AccessLogInterceptor 靠开关**：它仍不按类型让位，关闭 `facility.web.access-log.enabled` 后再注册自己的。`TraceIdFilter` 与 `ClientIpPolicy` 已按类型让位，并由唯一请求边界使用。`SessionUserClearInterceptor` 保留MVC Principal适配；声明同名 `facilitySessionWebMvcConfigurer` 可替换MVC接线，但非MVC/异步清理由请求边界负责。
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

## ZIP 与目录操作

`Zipping.zipFiles` / `zipDirectory` 的 `Result.ok(Path)` 只代表完整 ZIP 已关闭并发布。缺失文件、重复 basename、遍历/读写/关闭失败均使整次打包失败，不再跳过条目。目录归档保留空目录，使用相对路径与正斜线；Unicode 名字按 UTF-8 写入。名字最多 1,024 UTF-8 bytes，拒绝冒号、反斜线及 dot 路径片段。此接口只创建 ZIP，不提供解包安全保证。

```java
var zipLimits = new Zipping.Limits(2_000, 64L << 20, 72L << 20, 16);
var archive = Zipping.zipDirectory(inputDirectory, outputArchive, zipLimits);
var directoryLimits = new PathIo.Limits(2_000, 64L << 20, 16);
var size = PathIo.directorySize(inputDirectory, directoryLimits);
```

旧便利 ZIP 方法默认 10,000 条目、256 MiB 实际读取、256 MiB 完整输出（含最终目录记录）、64 层；目录方法默认 10,000 后代、256 MiB 逻辑文件 bytes、64 层。显式预算必须全为正；null Limits 为程序错误。根不计条目和深度，顶层文件/目录深度为 1，目录元数据不计 bytes，硬链接按路径分别统计。大小统计不是分配磁盘空间，也不是并发输入树的快照。`directorySize` 只有完整统计或 Err，不返回部分和；`deleteDirectory` 保留 null/确实不存在的幂等成功，拒绝文件系统根。

输入/输出命名空间必须由应用可信拥有，拒绝符号链接、Windows junction、特殊节点和链接祖先；不提供抵抗恶意并发重命名的沙箱。ZIP 输出不得位于输入目录树，已有目标绝不覆盖。同目录私有 stage 在关闭成功后以 `Files.createLink` 发布，文件系统必须支持此能力；不支持时失败，不复制到可见目标作为回退。输入 provider 还须支持 NOFOLLOW_LINKS 打开；例如 JDK ZipFS 不能作为 ZIP 的直接输入流 provider。新建的父目录可保留；文件系统拒绝清理时 stage 或完整目标也可能残留，失败后不能以 Path 存在代替成功信号。检查 Result 及原始异常的 suppressed 诊断，由拥有该目录的应用按策略处理残留。

所有打开的流和 stage 属于操作；成功返回的归档归调用方管理。中断在读写、遍历、删除及发布边界被观察并保留中断标志，阻塞 provider 是否及时响应取决于 provider；不创建后台任务。递归删除逐项生效，失败或预算耗尽可能已经删除部分条目，不会回滚。错误保留最初原因，关闭/清理异常不覆盖它。调用方应只记录受控诊断，不能把含路径的原始异常直接作为 HTTP detail。
