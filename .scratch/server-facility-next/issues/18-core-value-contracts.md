# 18: 让核心值与错误可脱离 Spring 稳定组合

**What to build:** 独立纯 Java 消费者能表达成功、无值成功、查询缺席、预期失败和程序错误；核心错误不会强制依赖框架或全局枚举。

**Blocked by:** None (can start immediately)

**Status:** in-progress

**Traceability:** FR-08、FR-09；AC-11、AC-12

## Acceptance criteria

- [ ] 保留 Result/error 有价值语义与领域自主错误，精简推荐入口且记录公共 API 处置。
- [ ] ok(null)、empty、swap、null/default 和必需回调规则不暗改为 Unit/Optional 新模型。
- [ ] error 核心无 Spring，本地化留边界；防御性复制与可变值范围说明准确。
- [ ] 结构类型公开协议优先有名值；common/structure 替代需证明真实消费者语义，不因内部无引用删除公共入口。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/性质：map/flatMap、短路、惰性 fallback、错误映射与消费者业务分支。
- [ ] 边界：null/empty、空集合、重复值、不可变输入、变更源对象后的观察。
- [ ] 故障：回调为空、回调程序错误、Error 与中断的传播符合公开契约，不吞成普通业务失败。
- [ ] 兼容：现有 Result/Swap/WrappedError/集合与 tuple 样本有保留或显式替代记录，不增加 record getter 镜像测试。

## Scope boundary

不强制所有函数 Result 化，不推动未批准的 non-null/Unit 改造。

## Implementation record

- 2026-10-04：从集成线 `042006d` 创建 `codex/ticket-18`。测试 seam 沿用已确认的设施公共接口与独立业务消费者；不测试私有实现或 record getter 镜像。
- 先登记 ADR-0041：保留 ADR-0007/0010；补充浅不可变、必需回调和集合算术边界。集成线处于票22已批准的迁移红态，先对选定核心源码运行无框架消费者逐条 RED/GREEN；普通完整 jar、覆盖率与架构门须在合并票23后另行执行，不能用局部编译替代。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。
