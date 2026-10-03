# 04: 在真实 HTTP 链路统一安全错误与显式兼容协议

**What to build:** API 调用者在 MVC、Filter 和异常派发失败时得到准确状态与安全 ProblemDetail；旧客户端仅通过显式兼容路径获得原 envelope。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Traceability:** FR-03、FR-09；AC-04、AC-05、AC-12

## Acceptance criteria

- [ ] 新默认成功 DTO、失败 ProblemDetail 的 code/detail/字段错误/traceId 契约明确，并可供 Security 复用。
- [ ] 覆盖状态及必要响应头，区分坏请求、multipart 过大、业务拒绝和内部返回值错误。
- [ ] 内部 cause 留在服务端；任意异常消息、输入秘密与调试栈不直接进入生产响应。
- [ ] 明确已提交响应、用户 advice 覆盖及兼容模式的处理，不二次写坏响应。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/协议：400/404/405/406/409/413/415/422/429/500/503 的适用场景与 Allow、Retry-After 等头；无需人为制造不适用状态。
- [ ] 接合：实际 Servlet 请求经过 Filter/MVC/ERROR dispatch、用户 mapper/advice、locale；禁止仅直接调用 handler 作证。
- [ ] 故障：秘密哨兵注入异常、cause、字段输入；响应已 flush、序列化失败、multipart 坏数据分别验证。
- [ ] 兼容：旧 HTTP 200 envelope 金样与新协议金样分开，记录明确切换和 superseding ADR。

## Scope boundary

身份认证实现由票 27 交付；本票提供其错误策略并在接合时复验。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。
