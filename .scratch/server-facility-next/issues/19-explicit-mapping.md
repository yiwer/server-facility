# 19: 用显式 DTO 映射迁移一个真实消费流程

**What to build:** 一个真实 DTO 转换流程通过具名值与显式映射工作，字段、别名和容器语义清楚；旧反射复制入口有有限的支持范围与替代。

**Blocked by:** None (can start immediately)

**Status:** verification-pending

**Traceability:** FR-08、FR-09；AC-11、AC-12

## Acceptance criteria

- [x] 选定真实调用流程完成替换并提供示例，不只增加另一套无人使用的接口。
- [x] 公开旧复制能力的支持/拒绝范围：具体容器、比较器、共享别名、循环与不可变值。
- [x] 不反射修改不可变字段；DTO 漏字段可被业务契约或明确选用的编译检查发现。
- [x] 复杂对象图复制若不适合有限接口则弃用并迁移，不建设新通用复制引擎。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常：源到目标的完整字段结果、集合顺序、重复键、目标预存值。
- [x] 边界/兼容：源 null、null 默认值、空容器、不可变集合、比较器与具体容器类型。
- [x] 故障/图结构：循环、共享别名、不支持类型、字段新增与变更；明确拒绝或结果，不无界递归。

## Scope boundary

MapStruct 仅在有具体价值时采用；不依赖模板完整交付，不以弃用名义立即删除外部 API。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


## Implementation record

2026-10-04领取，独立ticket-19 / codex/ticket-19，初始integration418e26f，开始实施前同步2b06f52。ADR0042；按已批准CopyUtil公共入口与独立应用Module逐项RED→GREEN。根库目前没有生产autoCopy调用，故用独立订单转换消费流程展示可执行迁移，而不是新建无人消费的映射接口。尊重普通容器/嵌套容器的历史浅引用政策；收窄反射支持范围、禁止final写入并明确复杂对象图拒绝。日志迁移只走标准SLF4J和固定有限metadata，backend RuntimeException不改变复制结果。原始证据从开始保存`.verification-results/ticket-19`。


## Verification handoff

2026-10-04：实现与Windows验收完成。冻结`4bcad8726e1b4379a0a56fe0804d1164086ae7e8`的all --fresh在`.verification-results/20261004-075535-739-all/summary.txt`为PASS，110命令；库1712/0/0/0、模板76/0/0/0、partner15，原覆盖率/5架构/依赖及所有消费者/资源/负控通过。普通jar SHA256 `5853fd0737501be5a63f0d0efbbb59823cf820c7dcd219d76c7e72d97100cef2`。固定字段演进、旧普通jar编译binary和64MiB资源证据及22轮红绿见docs/verification/ticket-19-explicit-mapping.md；root短审发现的Map key→value中断缺口已真正RED→GREEN并进入此次全门。

待验仅本票最终源Linux/联合CI；Q01–Q10 Windows维度已按报告完成，不把中央CI15 Windows迁移CME或未来33组合重验冒称已过。本票不声称已经迁移根库不存在的生产autoCopy调用，交付的是实际编译运行的订单→发运消费流程。33的最终双审/组合责任独立，不反向制造实现阻塞边；未获缺失证据前不closed。
