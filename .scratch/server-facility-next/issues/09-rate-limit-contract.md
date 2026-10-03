# 09: 让 API 配额抵抗非法成本、伪身份和键洪泛

**What to build:** 一个受保护 API 的本地限流能按可信主体和合法成本计费，拒绝结果可解释，新增键不能恢复其他主体额度。

**Blocked by:** 04 在真实 HTTP 链路统一安全错误与显式兼容协议；06 固定请求身份、代理信任与上下文清理

**Status:** verification-pending

**Traceability:** FR-03、FR-05；AC-04、AC-08、AC-09

## Acceptance criteria

- [x] 容量、速率、cost、身份、操作范围及故障策略固定且可配置验证；本地额度不宣称集群额度。
- [x] 拒绝非正/非法成本与冲突政策；状态回收不能清空全部桶恢复额度。
- [x] 429/设施不可用采用票 04 的外部协议；IP 来源采用票 06 的信任政策。
- [x] 区分入口防滥用与业务配额；与幂等重试的先后顺序登记为接合契约。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/边界：消耗、拒绝、补充、Retry-After 向上取整；cost 为零/负/超容量、非法 rate、时钟边界。
- [x] 并发/资源：同主体竞争不超发，键上限和 churn 不影响已有桶，重复失败后状态受预算约束。
- [x] 接合：伪造 XFF 不换桶，跨包同名/重载操作不冲突，缺 Adapter 与运行故障采用明确政策。
- [ ] 后续组合：票 12/29 接入时验证同一键重试是否计费，所有顺序由发布候选复跑。

## Scope boundary

不引入分布式限流平台；不把可选降级复制为所有业务的默认策略。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 领取记录（2026-10-04）

独立工作树 `E:/GenCode/server-facility-worktrees/ticket-09`、分支 `codex/ticket-09`，起点为06中央集成 `5faff896d04a1b15ed10310be81bed91a14121b7`。已批准公共seam为RateLimiter操作及真实Servlet HTTP；可控单调时间作为外部边界注入。ADR0032预留，中央INDEX由merger维护。逐步证据放 `.verification-results/ticket-09`。


## 实现与验证交付（2026-10-04）

本票实现在codex/ticket-09，已合integration8cfaa6e。候选50d492d9160476052560910db5d1c1664e92d4ad包含精确正成本令牌桶、strict槽位/full-only回收、required/Optional设施政策、完整操作+IP/Principal/Global范围、MVC入口先于幂等以及ASYNC单次扣费。ADR0032替代0014适用部分，USAGE/CHANGELOG记破坏性迁移，中央索引留merger。

Windows主库 **1478/0/0/0**；指令92.995%、行93.579%、分支85.618%，原门/5架构/依赖均过；真实HTTP 13场景、ordinaryjar 1024槽/32768churn/16线程/64MiB/45秒及继承的全部消费者、平台缺类矩阵、5次生命周期均通过。首轮all最后缺VERIFY_WRONG_JAVA_HOME因此原summary仍FAIL；同源码设置真实JDK21后独立prerequisites三负控 **PASS**，诚实使用组合证据而不改写原结果。

- [x] Q01–Q07、Q09适用场景和反例均在[完整报告](../../../docs/verification/ticket-09-rate-limit-contract.md)逐项追踪。
- [ ] Q08/Q10：本票Linux CI待root集成后补齐；保留verification-pending，不借用历史平台CI。
- [ ] J07后续复跑由12/29/33负责：处理中、异内容冲突、事务receipt的入口/业务配额分工。本票已真实验证旧重放计费，此后续项不反向变成本票前置。

日志：`.verification-results/ticket-09`（逐步RED/GREEN）；`.verification-results/20261004-025443-462-all`（产品/消费者/资源PASS，最后环境缺项FAIL保留）；`.verification-results/20261004-030212-643-prerequisites`（补齐三负控PASS）。未删测/skip/降门；旧clear-all与缺Bean隐式放行断言随批准契约迁移。
