> **inherited-from**: beacon ADR-0003(原仓库 docs/adr/0003-rp-06-rfc-7807-problem-details.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0003: RP-06 RFC 7807 Problem Details + 可配开关

## Status

Accepted; partially superseded by [ADR-0027](0027-safe-http-error-policy.md) (2026-10-03).

0027 替代默认关闭、任意异常原文 detail、请求路径 instance、multipart 统一 413 与分散转换政策；保留采用标准 ProblemDetail、Spring 类型和显式 BaseResponse 迁移入口的历史理由。下文为原决策记录。

日期：2026-05-21

## Context

- 评审来源：`docs/facility/REVIEW.md` §7 RP-06 + §3.3 评审发现 + §4.2 评审发现 + §6.D.4 `web/exception/`
- beacon 现状：`AbstractGlobalExceptionHandler` 的 12 个 `@ExceptionHandler` 全部返回 `BaseResponse<Void>` + HTTP 200。即使业务异常 / 系统异常 / 参数校验失败 / 媒体类型不支持，HTTP 状态码都是 200，仅靠 body 中的 `code` 字段区分错误。这与 REST 惯例及 RFC 7807 不符。
- 业界现状：
  - **RFC 7807** (Problem Details for HTTP APIs, IETF, March 2016) 定义了 `application/problem+json` 标准响应格式，含 `type` (URI) / `title` / `status` / `detail` / `instance` 五个标准字段
  - **Spring Framework 6** 内置 `org.springframework.http.ProblemDetail` 类实现 RFC 7807
  - **Spring Boot 3.x** `ResponseEntityExceptionHandler` 自动 RFC 7807 处理（Spring Boot 3.0+ 默认对 `ErrorResponseException` 系列返回 ProblemDetail）
  - **Richardson Maturity Model** Level 2（正确使用 HTTP verb + status code）—— REST API 的成熟度衡量
- 问题：consumer 期望按 HTTP status 判断错误类型（400 / 500 / 405 等是 REST 行业共识），现有 HTTP 200 + body code 模式违反此期望，给 client 端错误处理带来复杂性。

## Decision

引入可配开关 `beacon.facility.web.exception.useProblemDetail`（boolean），**默认 false 保留现有行为**（HTTP 200 + BaseResponse），**显式 opt-in 时切到 RFC 7807 ProblemDetail**：

**新增配置**：
```yaml
beacon:
  facility:
    web:
      exception:
        use-problem-detail: true  # opt-in RFC 7807 ProblemDetail
```

**handler 双轨实现**：每个 `@ExceptionHandler` 方法返回类型为 `Object`：
- useProblemDetail=false：返回 `BaseResponse<Void>`（现有行为，向后兼容）
- useProblemDetail=true：返回 `ResponseEntity<ProblemDetail>`，含异常类型映射的 HTTP status

**异常 → HTTP status 映射表**：

| 异常 | HttpStatus |
|---|---|
| BusinessException | 400 Bad Request |
| SystemException | 500 Internal Server Error |
| MethodArgumentNotValidException / BindException / HandlerMethodValidationException / ConstraintViolationException / HttpMessageNotReadableException / MissingServletRequestParameter(Part)Exception | 400 Bad Request |
| HttpRequestMethodNotSupportedException | 405 Method Not Allowed |
| HttpMediaTypeNotSupportedException | 415 Unsupported Media Type |
| MultipartException | 413 Payload Too Large |
| Exception (generic) | 500 Internal Server Error |

**Content-Type 自动设置**：返回 `ResponseEntity<ProblemDetail>` 时 Spring Web 内置 `MappingJackson2HttpMessageConverter` 自动设 `application/problem+json`（RFC 7807 要求）。

**辅助方法**：在 `AbstractGlobalExceptionHandler` 新增 protected `buildProblemDetail(Throwable ex, HttpStatus status, WebRequest request)`：
```java
protected ResponseEntity<ProblemDetail> buildProblemDetail(Throwable ex, HttpStatus status, WebRequest request) {
    String detail = ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(getRequestURI(request)));
    return ResponseEntity.status(status).body(problem);
}
```

## Consequences

**Positive**：
- 对齐 RFC 7807 行业标准 + Spring 6 内置实现
- consumer 可 opt-in 标准化错误响应；不破坏现有调用方期望
- Richardson Maturity Model Level 2 对齐（正确使用 HTTP status code）
- `application/problem+json` Content-Type 是 Web API 客户端（如 OpenAPI / Swagger） 错误处理的事实标准

**Negative**：
- `AbstractGlobalExceptionHandler` 12 handler 双轨实现增加分支复杂度（每 handler 多一个 `if (props.isUseProblemDetail())`）
- handler 返回类型从 `BaseResponse<Void>` 改为 `Object`（API-break：自定义 GlobalExceptionHandler 子类的 `@Override` 签名需更新）；本 phase 无 consumer，影响为 0
- 未来 phase 若决定切默认值为 true，是 API-break，需 deprecation 窗口

**Carry-forward**：
- **MCP error envelope 是否对齐 RFC 7807**：`docs/mcp/CONTRACT.md` §5 当前是自定义 envelope 格式。MCP server skeleton phase 启动时可考虑切到 RFC 7807（自定义字段 `code` / `i18n_key` 与 RFC 7807 标准字段共存或合并），由 T-mcp-contract 决定。本 phase 不动 CONTRACT.md。
- **默认值切换 false → true**：候选 phase-N（建议 beacon 有真实 consumer 之后），需 1 phase deprecation 窗口

## References

1. *RFC 7807: Problem Details for HTTP APIs*. IETF, March 2016. https://datatracker.ietf.org/doc/html/rfc7807
2. Spring Framework 6 Reference §1.4 (ProblemDetail). https://docs.spring.io/spring-framework/docs/6.x/reference/htmlsingle/#mvc-ann-rest-exceptions
3. `org.springframework.http.ProblemDetail` JavaDoc. https://docs.spring.io/spring-framework/docs/6.x/javadoc-api/org/springframework/http/ProblemDetail.html
4. *Richardson Maturity Model*. Martin Fowler, 2010. https://martinfowler.com/articles/richardsonMaturityModel.html
5. Spring Boot 3.x Reference Documentation §3.1.3.1.6 (Error Handling). https://docs.spring.io/spring-boot/docs/3.5.x/reference/htmlsingle/#web.servlet.spring-mvc.error-handling

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
