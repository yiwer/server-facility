# 24: 把目标平台集成为可发布的真实消费者组合

**What to build:** Boot 4/Jackson 3 普通库产物在独立应用中完成启动和 HTTP 往返，各种启用/缺席/覆盖组合有确定行为，迁移线恢复完整绿色。

**Blocked by:** 23 迁移 Jackson 3 并让 HTTP 与应用 mapper 政策一致

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-01、FR-02、FR-09、FR-10；AC-01、AC-02、AC-09、AC-12、AC-13

## Acceptance criteria

- [ ] 对票 22/23 做 integrate-and-verify，只在全量质量门与目标消费者验证后合入主线；临时兼容入口收缩依据消费者盘点。
- [ ] 真实依赖图验证平台的非 Web、默认 Web、用户合法覆盖、能力关闭、类加载/注册及歧义处理。尚待能力票改变的失败语义列为待接合，不标记已通过。
- [ ] 保留快速装配测试，并用独立 JVM 和真实依赖图复核已知危险缺类组合；测试依赖不能掩盖生产缺包。
- [ ] 平台级版本账本完整，非 BOM 的业务行为验收由所属票负责并由票 33 汇总，不冒称本票已验证全部文件能力。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常：Windows/Linux 目标字节码构建、普通 jar 消费、真实 MVC/JSON 往返、配置 metadata、全部引擎发现。
- [ ] 负向：无 Servlet、缺 Jackson 技术模块、无 validation provider、无 Tika/POI、仅 Caffeine/仅 context-support 等适用组合。
- [ ] 接合：验证用户 bean/MessageSource/mapper 覆盖、Filter 单次注册与顺序；必需锁、TTL 等新的业务失败契约由负责票实现并在票 33 合验，不强行成为本平台票的前置。
- [ ] 门禁：无未解释 skip、宽泛 ignore 或残留预期编译失败；旧/新发布线和升级差异可核查。

## Scope boundary

这是票 22/23 唯一主线合入门；不要求其他独立旧库修复先全部完成，最终组合由票 33 验证。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。
