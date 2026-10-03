# 08: 兑现本地缓存的 TTL、容量和装配承诺

**What to build:** 显式选用缓存的应用获得声明的到期与容量政策；后端或依赖不满足配置时清楚失败，不退化为永久 Map。

**Blocked by:** None (can start immediately)

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-02、FR-05、FR-08；AC-02、AC-08、AC-09

## Acceptance criteria

- [ ] 采用注入的 Spring CacheManager 与明确后端，选中能力带齐依赖，未启用不强行注册。
- [ ] null、loader 失败、同键加载、失效与容量政策按选定实现明确，不增设隐藏全局缓存。
- [ ] 用公共行为证明 TTL/容量；两个应用的管理器与数据独立。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常：命中/未命中、失效、重新加载、用户管理器覆盖。
- [ ] 边界/并发：TTL 前/等于/后、容量边界、同键竞争与不同键并行、key churn 后回收。
- [ ] 装配/故障：Caffeine 与 context-support 四种组合、能力禁用、后端异常、loader 失败不污染缓存、上下文关闭。

## Scope boundary

不默认提供远程缓存；不把缓存内容的业务失效责任转给通用设施。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。

