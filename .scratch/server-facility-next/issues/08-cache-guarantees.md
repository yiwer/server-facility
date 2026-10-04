# 08: 兑现本地缓存的 TTL、容量和装配承诺

**What to build:** 显式选用缓存的应用获得声明的到期与容量政策；后端或依赖不满足配置时清楚失败，不退化为永久 Map。

**Blocked by:** None (can start immediately)

**Status:** verification-pending

**Traceability:** FR-02、FR-05、FR-08；AC-02、AC-08、AC-09

## Acceptance criteria

- [x] 采用注入的 Spring CacheManager 与明确后端，选中能力带齐依赖，未启用不强行注册。
- [x] null、loader 失败、同键加载、失效与容量政策按选定实现明确，不增设隐藏全局缓存。
- [x] 用公共行为证明 TTL/容量；两个应用的管理器与数据独立。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常：命中/未命中、失效、重新加载、用户管理器覆盖。
- [x] 边界/并发：TTL 前/等于/后、容量边界、同键竞争与不同键并行、key churn 后回收。
- [x] 装配/故障：Caffeine 与 context-support 四种组合、能力禁用、后端异常、loader 失败不污染缓存、上下文关闭。

## Scope boundary

不默认提供远程缓存；不把缓存内容的业务失效责任转给通用设施。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


2026-10-04 领取：独立工作树 ticket-08 / codex/ticket-08，从12中央 `1d6377dee64db3e8b072dd590a9f09c78df5b6df` 开始。沿已批准 Spring Cache/公开装配与普通 jar 消费者 seam 逐个RED→GREEN。计划 `docs/superpowers/plans/2026-10-04-ticket-08-cache-guarantees.md`；ADR0031记录对0015默认注册/永久Map回退范围的替代；中央INDEX由merger维护。原始日志 `.verification-results/ticket-08`。


2026-10-04 实施交付：显式本地缓存/固定名字/正 TTL及条目政策、成对缺依赖拒绝、host覆盖、标准注入与旧入口迁移、应用关闭归属完成。ADR0031、`docs/building/local-cache.md`、`docs/verification/ticket-08-cache-guarantees.md` 同票交付。

- [x] Q01–Q07：公开契约、输入/边界、实际依赖图/双应用、故障/并发、64MiB资源、兼容边界与seed80031证据完整，详见报告矩阵。
- [x] Q08/Q09 Windows：原冻结6881088库1729/0/0/0、5架构与原88/88/75/依赖门通过。普通jar SHA256 `ce9edfb7198d82eee5ab40007718881552dc72370d19ed033567dfefb9f6a608`。
- [x] 完整Windows组合证据：`.verification-results/20261004-082150-220-all`保留FAIL（扩展no-Jackson override后旧fixture错误期望无mapper），82049ea修断言；同一库源码和SHA的`.verification-results/20261004-083656-716-cache-tail`61步全PASS（尾门范围），5图24场景、partner、模板78、实际PG/packaged两模式/重启、5生命周期和全部先决负控均完成。没有重标原all或借用他票测试数。
- [ ] Q08/Q10当前Linux CI闭合；因此保持verification-pending。旧24默认回退的CI不能代替本票新政策。最终33候选同源复验独立登记。

最新中央207c0cc已合入为ac716815；相对尾门来源d0afe97仅中央文档，无产品/测试/runner差异。限定关闭语义：宿主先quiesce loader/借用Cache；容量为maintenance后条目政策，不是瞬时字节上界；后端清理失败仅保证detach和逐cache尝试。impl03有限两轴短审无阻断，记录coordination/ticket-08-premerge-review-impl03.md，不替代33。
