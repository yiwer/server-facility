# 应用拥有消息、日志与观测

库提供安全的边界能力，应用决定公开消息、日志字段、采样和导出后端。可执行消费者是 [secured-api](../../templates/secured-api/README.md)，其中 GreetingController 构造注入 MessageSource，RequestExecutionConfiguration 使用该应用的 ObservationRegistry。领域 Actor/Greetings 仍为纯 Java。

## 消息优先级

```properties
spring.messages.basename=i18n/application,i18n/facility-messages
spring.messages.fallback-to-system-locale=false
```

Boot 按明确的 basename 顺序解析，应用 bundle 在前。应用需提供默认 `i18n/application.properties`；法语可放在 `i18n/application_fr.properties`。如果已有名为 `messageSource` 的 bean，库完全让位。没有宿主来源时，库仅提供自身 bundle 的 UTF-8 fallback，不搜集其他 MessageSource，更不隐式建立委托图。旧 `facilityMessageSource` 仍可按名引用，不抢占默认构造注入。

新代码通过构造器接收 MessageSource，显式传 Locale。缺 key 时采用 MessageSource 的带 defaultMessage 重载；不把任意 key 当作公开文本。格式参数必须经过业务审核；类型不匹配遵守 Spring/MessageFormat 的异常语义，不能把异常和参数串接成面向用户的消息。

`WrappedError`/`ErrorTypeInterface` 仍是纯数据。在默认 HTTP 边界，BusinessException 的 code 被保留，合法、有限的 messageKey 用于查应用 bundle；不格式化其诊断 args，也不返回异常的 defaultMessage/cause。缺 key 回到安全状态文本。5xx 始终使用系统安全文案。应用需要参数化公开错误时，应在自己的 HTTP 策略中选取允许字段并调用 MessageSource。

## 日志与请求关联

```java
private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OrderQueries.class);
// Use reviewed metadata, never a whole request, entity, exception or token.
LOG.info("Order query completed; outcome={}", outcome.name());
```

新路径直接使用 SLF4J。设施访问日志仅记录标准方法、匹配路由模板、状态、耗时；不记录原始 URI/query/IP/header/body。HTTP 5xx 日志仅包含状态、错误类名与 incidentId，没有 Throwable 代理或异常消息。默认诊断的日志后端 RuntimeException 不改变原业务结果；JVM Error 不伪装成可恢复日志故障。下载失败由返回的 Result 交给调用方记录，避免重复输出内部原因。

标准 tracing 可用时，HTTP 错误的历史 `traceId` 字段读取有效的 MDC traceId；不改写 MDC，也不额外生成 X-Trace-Id 头。没有活动 trace 的错误边界生成 UUID incident reference 并同时写入安全日志；它不是一个分布式 span，不应当被当作 W3C traceparent。

通用日志、metrics、traces 不等于审计记录。审计事件的权限、完整性、保留期与持久化由应用独立实现。MaskUtil 只是有限的纯函数；它不能识别任意密码、SQL、token 或上传内容，标准日志不会自动通过它。优先选择允许输出的字段。

## 标准上下文与生命周期

模板选择 Boot Actuator + Brave/Zipkin starter；W3C 传播、MDC 和采样使用框架标准配置。`management.tracing.export.zipkin.enabled=false` 默认不向外部后端发送数据；应用配置后端、导出、采样与字段审查后自行启用。该属性已核对 Boot 4.1.1 的实际配置元数据。X-Trace-Id 兼容协议默认关闭。

应用的 TaskDecorator 使用私有 ContextRegistry 与该应用的 ObservationThreadLocalAccessor，不修改 Micrometer 全局 registry。Callable/DeferredResult 的受管执行器同时传播标准 Observation scope 和 Security context，不能只复制一个 MDC 字符串。嵌套 scope 恢复、平台/虚拟线程、执行拒绝/取消、部分 MDC 安装失败与原异常保留均有回归。两个应用可采用不同采样政策，关闭其中一个后另一个继续完成请求。

W3C traceparent 只用于相关性；它不是身份或授权证据。任意第三方线程应接收不可变 Actor，或由应用选择明确的受管执行路径。导出 tracing 时应检查选用的第三方 instrumentation 是否记录异常/URL 等信息；设施库的字段策略不等于全应用秘密检测。

## 旧入口迁移

| 保留入口 | 替代与兼容边界 |
|---|---|
| LocaleUtil 的 translate/localize/fallback/getLocale | 构造注入 MessageSource，显式 Locale；HTTP 示例见 GreetingController。静态入口已弃用，仍按历史 SpringContextHolder 的单 owner 规则工作；fallback 格式化尊重传入 Locale。 |
| AggregatedMessageSource、FacilityLocaleAutoConfiguration.messageSource(List) | Boot basename 顺序，或显式命名宿主 MessageSource。旧构造/SPI签名保留，显式组合仍按 Ordered；调用方必须保证委托无环，默认路径不会进入该图。 |
| LogUtil 的等级/Throwable/配置/cache 方法 | 类自己的 SLF4J Logger。旧二次 post handler、默认有限 masking 和静态开关仍是兼容行为；Throwable 不自动脱敏，动态 logger 类可能增长旧缓存，调用方负责生命周期。 |
| LogPostHandler、LogPostHandlerComposite、LogContext | 宿主日志后端的标准 appender/filter/encoder；业务审计用独立应用服务。旧 SPI 不删除，新路径不调用第二次分发。 |
| TraceIdFilter、FacilityWebTraceProperties | Boot/Micrometer tracer 和私有受管 context propagation。旧过滤器仍可显式 `facility.web.trace.enabled=true` 或声明 bean 启用；保留其 header/key/生成策略，但不与标准追踪同时使用。 |
| MaskUtil | 继续直接用纯函数处理已知文本；没有全局自动安装或任意秘密发现承诺。 |

先迁移消息调用方与日志调用方，再移除应用旧聚合/后处理配置。升级后默认不再返回 X-Trace-Id，应依赖标准 tracing 或安全错误中的 incident reference。未知外部 API 未删除。

依据：[Boot internationalization](https://docs.spring.io/spring-boot/reference/features/internationalization.html)、[Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)、[Micrometer context propagation](https://docs.micrometer.io/context-propagation/reference/usage.html)。
