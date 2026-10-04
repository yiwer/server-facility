# 08: 兑现本地缓存的 TTL、容量和装配承诺

**What to build:** 显式选用缓存的应用获得声明的到期与容量政策；后端或依赖不满足配置时清楚失败，不退化为永久 Map。

**Blocked by:** None (can start immediately)

**Status:** in-progress

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

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


2026-10-04 领取：独立工作树 ticket-08 / codex/ticket-08，从12中央 `1d6377dee64db3e8b072dd590a9f09c78df5b6df` 开始。沿已批准 Spring Cache/公开装配与普通 jar 消费者 seam 逐个RED→GREEN。计划 `docs/superpowers/plans/2026-10-04-ticket-08-cache-guarantees.md`；ADR0031记录对0015默认注册/永久Map回退范围的替代；中央INDEX由merger维护。原始日志 `.verification-results/ticket-08`。
