# 10: 以 UUID 默认值和显式 SnowId 协议生成标识

**What to build:** 新应用多实例启动不需要隐藏的节点协调；继续支持的 SnowId 只在节点与时钟条件明确时产生结果。

**Blocked by:** None (can start immediately)

**Status:** in-progress

**Traceability:** FR-05、FR-09；AC-08、AC-12

## Acceptance criteria

- [ ] 新推荐路径默认 UUID，用户 generator 可覆盖；无 context 或缺节点配置不静默使用 SnowId 节点 0。
- [ ] SnowId 节点范围、epoch、序列耗尽、回拨、等待预算与中断契约明确。
- [ ] 存量 ID 不改写，公开解析与 JSON 表示提供兼容样本；部署必须明确重复节点的责任。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：多应用默认配置、用户 generator、SnowId 正常序列及旧 ID 金样。
- [ ] 边界：节点范围、epoch 前后、序列边界、冻结时钟、回拨/向前跳变、等待预算到期。
- [ ] 故障/并发：序列竞争、中断、缺配置启动/首次使用失败；用不变量和部署协议论证，不以有限随机样本声称证明无限唯一。

## Scope boundary

不建设分布式节点分配服务；该需求出现时另立后端扩展。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

实现分支codex/ticket-10，基于1d6377d。已批准验收入口：公开IdUtil/SnowIdGenerator、真实自动装配和普通jar应用；默认业务使用JDK UUID及应用自有Supplier，不增加通用ID框架。ADR0033先登记替代ADR0023的无限等待，保留ADR0008的实例epoch解析。
