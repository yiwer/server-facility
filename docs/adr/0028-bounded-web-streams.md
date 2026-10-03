# ADR-0028: 显式、有界捕获与 Servlet 流所有权

## Status

Accepted

日期：2026-10-03

部分替代 ADR-0017 的全局 `ContentCachingResponseWrapper`、延迟复制响应体与无界响应捕获决定；不替代其旧 claim、授权、保存资格和重放协议，后者由票 11/12 修订。

## Context

Web 研究 §2.2/3.4 的真实探针证明全局响应缓存使下载及 SSE 的 flush 无法到达客户端。重复请求体虽有大小配置，却关闭容器输入流、忽略声明 charset，且 `setReadListener` 静默无操作。Servlet 流的所有者与库创建的资源必须区分。共同测试入口已由 PRD v0.2 批准。

## Decision

- 非目标响应直接向容器输出，显式选定响应才保留有界副本。捕获是旁路副本，不延迟提交；超限或写失败丢弃副本并禁止保存不完整结果，不更换已提交响应、不再次执行操作。
- 旧 `@Idempotent` 路径只在既有 claim 成功后开启捕获。捕获默认预算 1 MiB；此新预算必须为正数。保存政策、scope、授权、异步端点资格、持久化恢复属于票 12，不能由缓存成功推断业务 exactly-once。
- Repeatable request 默认关闭，宿主显式启用时使用 10 MiB 默认预算，可用 include/exclude paths 缩小目标。经资源政策复核明确替代旧 `max-body-bytes <= 0` 的无界语义：启用时配置及 wrapper 构造必须为正预算，默认构造使用 10 MiB；原用 0/-1 的消费者需改为明确正数，禁用只用 enabled=false。
- 限额以实际读到的字节计数，最多读取 N+1 字节来识别超限，不信任 Content-Length。413 交给外层统一错误策略；读故障向上传播。
- 每次 `getReader/getInputStream` 提供独立游标，可以混合或重复读取；字节保持一致，reader 使用请求声明的 charset，缺省 UTF-8。非法 charset 400，畸形字节按 JDK reader 的替换字符规则处理。同步 repeatable stream 明确拒绝非阻塞 listener，永不静默忽略。
- 不关闭容器拥有的输入/输出流。文件下载关闭自己打开的文件输入，遇到输出故障立即结束复制且不写第二份错误响应。应用的其他生产者仍负责观察 IOException/中断并清理自身资源。
- 过滤器次序：trace 为最高优先级、统一错误 +1、repeatable +2、幂等捕获 +3。每个由一项 FilterRegistrationBean 注册；重复请求包装及响应包装已有时不重复。

## Consequences

**Positive**：普通下载、SSE 可及时发送；默认无全局正文缓存；有限捕获与流式输出具有独立契约。

**Negative**：捕获超限后该次旧 claim 保留 PROCESSING 至原 TTL，不能重放；这不是安全重试保证。已提交响应的失败不能改成一个新的完整错误响应。同步 wrapper 不支持 Servlet 非阻塞读取。

**Carry-forward**：票 12 明确业务保存资格、过期重试和异步生命周期；票 24 复验 Boot 4；票 33 汇总同一候选版本跨票接合。它们不反向成为票 05 已实现局部流契约的实现依赖。

## References

1. [Web 研究 §2.2/3.4](../research/2026-10-03-web-and-agent-design.md)
2. [运行时研究 §7](../research/2026-10-03-runtime-contracts.md)
3. [Servlet 6.0 API](https://jakarta.ee/specifications/servlet/6.0/apidocs/)
4. [ADR-0017](0017-idempotency-full-semantics-response-capture.md)

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*

