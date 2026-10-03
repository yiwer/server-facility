# 25: 隔离两个外部服务的配置、凭据和失败

**What to build:** 类型化 HTTP Adapter 继承宿主 Boot builder、JSON 和观测配置，同时为不同第三方保持独立凭据、时限和错误语义。

**Blocked by:** 24 把目标平台集成为可发布的真实消费者组合

**Status:** closed

**Traceability:** FR-08；AC-11、AC-15

## Acceptance criteria

- [x] 注入 Boot 管理的 RestClient.Builder 或标准 HTTP service 配置，不直接建绕过宿主的私有 builder。
- [x] 必要状态、头和失败信息保留；连接/读取超时、4xx、5xx、坏响应和未知结果可区分。
- [x] 有副作用请求默认只发送一次；显式重试需要幂等依据、次数和整体预算，不延长 deadline。
- [x] 迁移一个实际外部聚合消费者，明确返回流/大响应的限额和关闭责任。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：两个受控服务返回 DTO、泛型列表、204；实际请求证明 customizer、JSON、trace 生效。
- [x] 隔离/并发：两服务并发调用时认证头、base URL、超时、customizer 不污染。
- [x] 故障：连接失败、慢响应、截断/坏 JSON、4xx/5xx、响应超限与取消，连接最终释放。
- [x] 重试：实际请求计数验证默认一次；显式重试在整体预算内，提交后断连不盲目重做。

## Scope boundary

不建设通用远程任务 DSL；第三方协议特有的恢复留在类型化 Adapter。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## Implementation record

2026-10-04：root从integration c32e72e建立codex/ticket-25独立worktree。24已由真实Windows/Linux CI闭合。公开seam为宿主管理的RestClient.Builder及两个类型化聚合Adapter，沿实际HTTP请求验证；标准RestClient承担传输/转换/观测，只为重复的响应预算添加小型设施能力。先复现宿主builder配置被默认装配绕过，再逐项TDD。替代ADR0018的私有builder默认推荐与静态默认路径，兼容API先弃用并提供真实消费替代。

2026-10-04：Windows完整 all --fresh 已通过；详见 [ticket-25验证报告](../../../..//docs/verification/ticket-25-outbound-http.md)。与27合并后的同源Windows/Linux CI尚待执行，Q10未关闭。

2026-10-04：集成90098104ec8bb0edcc3eb93db848d4f2f0f51207已通过CI13 run37157891623的Windows/Ubuntu完整门、独立平台门与归档，闭合Q08/Q10，票closed。精确OS/job/artifact与证据边界见 `docs/verification/ticket-25-26-ci.md`。此前pending文字为实施历史，不代表当前状态。
