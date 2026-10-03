# 12: 只对已授权目标操作执行有界 HTTP 重放

**What to build:** 旧 HTTP 幂等消费者迁至安全 claim 入口，以可信身份、操作和内容隔离结果；普通响应保持流式输出。

**Blocked by:** 04 在真实 HTTP 链路统一安全错误与显式兼容协议；05 让普通下载与请求体处理保持流式和有界；11 扩展旧幂等协议以拒绝迟到 owner 覆盖

**Status:** in-progress

**Traceability:** FR-03、FR-04、FR-09；AC-04、AC-05、AC-06、AC-12

## Acceptance criteria

- [ ] scope 包含适用 tenant/actor/operation/key，fingerprint 有规范化规则；每次重放重新检查当前授权。
- [ ] 只捕获明确目标并限制结果大小；定义成功、业务拒绝、advice 处理异常、4xx/5xx 和不可恢复结果的保存政策。
- [ ] 安全允许集控制重放头，201/Location 等必要业务信息一致；过大结果/断连/完成失败不能自动授权重复执行业务。
- [ ] 迁移所有仓库内旧完成调用，废弃无 owner 路径；删除外部 API 仍需盘点。异步目标支持或明确拒绝，不半支持。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/隔离：同请求重试、跨用户/租户/路由/重载操作、相同键不同内容、权限撤回。
- [ ] 边界：空白/超长 key、请求/响应限额、不可重放头、空体、超大结果。
- [ ] 故障/接合：advice 吞异常、断连、store 故障、过期竞争；普通大下载/SSE、多 filter/wrapper 顺序和单次注册。
- [ ] 配额：与票 09 的防滥用/业务配额顺序在双方具备后运行接合场景，不把票 09 设为本票本身的阻塞。

## Scope boundary

不复用本协议作为新模板同库事务模型，不宣称响应重放解决跨系统 exactly-once。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

2026-10-04 领取：从集成 `6a66672` 创建工作树后同步 `b7b7ea4`。票 11 的真实 Linux all 已通过，协调者明确允许本票实施；联合 Windows 的票 25 示例构建问题由 root 单独诊断，不能当本票通过证据。设计/TDD 顺序见 `docs/superpowers/plans/2026-10-04-ticket-12-http-replay.md`，替代决定草案 ADR-0035；原始证据存 `.verification-results/ticket-12`。
