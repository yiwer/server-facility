# ADR-0027: 真实 Servlet 边界的安全 HTTP 错误策略

## Status

Accepted

日期：2026-10-03

Supersedes ADR-0003 的默认关闭、异常原文 detail、请求路径 instance、multipart 统一 413 与每个 handler 各自转换的决定。保留 ProblemDetail、标准 Spring 类型和显式 BaseResponse 迁移入口的原始理由。旧 ADR 的历史正文不改写。

## Context

票 04（FR-03/09，AC-04/05/12）要求真实 Filter、MVC 与 ERROR dispatch 使用一致的 HTTP 协议。旧默认 HTTP 200 会掩盖失败；直接复制异常消息、校验消息或 ErrorResponse body 会把输入、cause 或实现信息交给客户端。仅调用 advice 不能证明容器派发、用户 mapper、multipart 和已提交响应的实际行为。

Spring 的 ErrorResponse / ResponseEntityExceptionHandler 提供状态及必要头；RFC 9457 的 detail 面向客户端，不是内部诊断。宿主可能只注册部分错误页，因此不能仅因存在任意 ErrorPageRegistrar 就撤掉兜底。

## Decision

- `facility.web.exception.use-problem-detail` 默认 `true`。成功 DTO 不包装；错误使用真实状态、`application/problem+json`，标准字段之外包含整数 `code`、`traceId` 和 `errors` 数组。FacilityException 的 code 保留业务短码，其他默认等于 HTTP 状态。
- `FacilityHttpErrors` 是可替换的应用 bean，仅提供 `response(Exception, WebRequest)` 和 `write(HttpServletRequest, HttpServletResponse, Exception)` 两个操作。MVC advice、Filter 和后续 Security adapter 共用它。调用方使用 Spring `ErrorResponseException`/`ResponseStatusException` 表达 401/403/409/413/422 等状态，不建立第二套错误 DSL。
- 标准 Spring 异常的状态/必要头由 ResponseEntityExceptionHandler 解析。普通业务拒绝/输入校验/坏 multipart 是 400，真实上传超限 413，返回值校验和内部转换错误 500，异步超时 503。429 的 Retry-After 为毫秒向上取整的秒数，最少 1，避免 long 加法溢出。
- 默认 detail 仅来自安全固定文案/宿主 MessageSource；不使用任意 exception message、reason、ErrorResponse body、cause、校验默认消息或 rejectedValue。字段条目仅含规范化字段名、固定 `invalid` code 与本地化文案；输出最多 32 个，路径最多 120 字符，集合索引/Map key 替换为 `[]`。内部异常留在服务端日志。
- trace 使用已验证响应追踪头（遵循配置 headerName），缺席生成 UUID；请求属性使重复派发使用同一值。instance 为 `urn:facility:error:<traceId>`，不反射可能带秘密的 URI。locale 使用 DispatcherServlet 的 LocaleResolver；在其外使用 Servlet 请求 locale。mapper 使用宿主 ObjectMapper。
- `FacilityHttpErrorFilter` 位于 `HIGHEST_PRECEDENCE + 1`，注册 REQUEST/ASYNC/ERROR；TraceIdFilter 位于最高优先级。票 05 的 RepeatableRequestFilter 位于 `+2`，其 413/400 可抛标准 ErrorResponseException，让外层策略统一处理。票 06/12/27 必须复用此边界，不新增独立 JSON 错误格式。
- 兜底 ErrorPageRegistrar 先注册 `server.error.path`（默认 `/error`），Boot 与宿主 registrar 随后仍可选择映射路径；ERROR dispatch 在公共 filter 边界安全输出。宿主仅注册 404 不得导致其他状态暴露容器 HTML。
- 默认 advice 优先级为 LOWEST_PRECEDENCE，宿主较高优先级 advice 可处理自己的异常；AbstractGlobalExceptionHandler 子类仍使默认 advice 退让。宿主自定义响应内容由宿主负责。
- 响应已提交时不覆写、不追加。未提交时使用 Servlet reset 释放 Writer/OutputStream 选择并移除旧实体元数据（Content-Type/Length/Encoding/Disposition、ETag、Last-Modified、Content-Range、Accept-Ranges、缓存实体设置），保留 CORS/安全/追踪等其他头，错误默认 `Cache-Control: no-store`。
- 错误体先序列化再写出。宿主错误 serializer 失败时只进行一次固定、安全、英文 500 ProblemDetail 回退，不再次调用失败的 mapper；此故障回退优先于 legacy 200。写入中的 IOException 传播给容器，已提交后不尝试再次写 JSON。
- 显式 `use-problem-detail=false` 选择旧 BaseResponse 字段形状和 HTTP 200（历史 429 仍为 429 且保留 Retry-After）。此入口只兼容 envelope/状态；不会恢复任意异常原文或调试栈。`include-trace-profiles` 已弃用，无论 dev/test/prod 均不自动公开栈。迁移调用方应按真实 HTTP status 与 code 分支。

## Consequences

**Positive**：
- 错误策略集中、可注入且对 Security 可复用，真实 HTTP 测试覆盖容器边界。
- 默认协议可观测，校验信息可用于字段定位，秘密输入和诊断不会被直接反射。
- 保留宿主 mapper、locale、advice 与错误页映射的扩展点。

**Negative**：
- 默认状态/体发生破坏性变更；旧客户端必须显式切换并迁移。依赖异常原文或 dev 栈的客户端需要改用安全业务消息与 traceId。
- 极端 serializer 故障回退固定为英文 500；宿主定制错误 serializer 和 advice 可以改变其自己负责的协议。

**Carry-forward**：
- 票 27 的真实认证 entry point/access denied 接合；本票验证标准 401/403 错误策略可用，不实现身份系统。
- 票 05/06/12 的流、请求生命周期与重放接合，及票 24/33 的目标平台组合/Linux 最终发布门。

## References

1. RFC 9457, Problem Details for HTTP APIs. https://www.rfc-editor.org/rfc/rfc9457.html
2. Spring Framework Error Responses. https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
3. Spring 6.2 ResponseEntityExceptionHandler. https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html
4. 本库 `docs/research/2026-10-03-runtime-contracts.md`、`docs/research/2026-10-03-web-and-agent-design.md`。

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
