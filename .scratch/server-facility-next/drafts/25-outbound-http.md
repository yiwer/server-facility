# 25: 隔离两个外部服务的配置、凭据和失败

**What to build:** 类型化 HTTP Adapter 继承宿主 Boot builder、JSON 和观测配置，同时为不同第三方保持独立凭据、时限和错误语义。

**Blocked by:** 24 把目标平台集成为可发布的真实消费者组合

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-08；AC-11、AC-15

## Acceptance criteria

- [ ] 注入 Boot 管理的 RestClient.Builder 或标准 HTTP service 配置，不直接建绕过宿主的私有 builder。
- [ ] 必要状态、头和失败信息保留；连接/读取超时、4xx、5xx、坏响应和未知结果可区分。
- [ ] 有副作用请求默认只发送一次；显式重试需要幂等依据、次数和整体预算，不延长 deadline。
- [ ] 迁移一个实际外部聚合消费者，明确返回流/大响应的限额和关闭责任。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：两个受控服务返回 DTO、泛型列表、204；实际请求证明 customizer、JSON、trace 生效。
- [ ] 隔离/并发：两服务并发调用时认证头、base URL、超时、customizer 不污染。
- [ ] 故障：连接失败、慢响应、截断/坏 JSON、4xx/5xx、响应超限与取消，连接最终释放。
- [ ] 重试：实际请求计数验证默认一次；显式重试在整体预算内，提交后断连不盲目重做。

## Scope boundary

不建设通用远程任务 DSL；第三方协议特有的恢复留在类型化 Adapter。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。

