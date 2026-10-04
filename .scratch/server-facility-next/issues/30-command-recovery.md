# 30: 在断连、进程丢失和结果清理后恢复同一命令

**What to build:** 客户端不知道提交结果、服务进程重启或两个实例同时收到重试时，系统恢复同一业务效果；receipt 清理不会重新授权重复执行。

**Blocked by:** 29 在业务事务中提交命令身份、结果与业务写入

**Status:** in-progress

2026-10-04：29已通过CI19同源Windows/Ubuntu完整门、平台门与归档并closed，前置解除。领取基于已验证集成 `ae7215eb6fea6c12aea9d4bb450aaf4ad21ab0fa` 及后续纯状态文档提交；独立实施与真实双JVM/提交点故障/清理恢复证据由本票继续完成，工作树外预研不计验收。

**Traceability:** FR-04；AC-05、AC-06

## Acceptance criteria

- [x] 补齐持久化恢复与过期清理行为，明确处理中/完成但表示已过期/拒绝/可重试的外部结果。
- [x] 故障点覆盖提交前、提交后响应前、返回中断和清理竞争；不能把超时解释为未执行。
- [x] 双独立应用进程共享同类型数据库，不用两个线程访问内存 Map 代替跨实例证据。
- [x] 业务键保留与 receipt 策略有迁移及操作说明；外部未知副作用的边界公开。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 故障注入：在受控进程与精确提交点终止/重启，重试查询业务记录与 receipt，断言承诺范围内只有一个已提交效果。
- [x] 并发：两个进程同键/异键/异内容、旧请求迟到、数据库短暂不可用与等待竞争。
- [x] 保留边界：到期前/等于/后、清理并发、权限变化；已清理表示不直接变为可重做命令。
- [x] 资源：连续失败/重启后数据库连接、等待任务和记录增长遵守预算，诊断能解释未知结果。

## Scope boundary

不引入全局恢复平台或跨系统 exactly-once；这是新故障路径的实现和验收，不能只补一个测试名。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


## 实施与本地证据（2026-10-04）

实现提交 `a575288`（V4/永久marker/权限/schema/历史升级）、`9b14e45`（独立进程fixture，来自child865074f）、冻结 `cd79a19540d1c4b1ce6ed26af3e14205bf1d79c3`（实际SHOW预算、清理双PID/撤权接合、完整PK保护、Verify归档）。已同步集成main `3f25c38`；ADR0053延续0052，未增加通用恢复框架或跨系统 exactly-once。

同源Windows `Verify all --fresh`：`.verification-results/20261004-115313-477-all/summary.txt`，137命令、RESULT=PASS；普通库1774/0/0/0、模板130/0/0/0，原coverage/architecture、ordinaryjar consumers、真实packaged HTTP及故障负控全部通过。19child全部确认退出+PG会话0、6精确kill、71scope数据库正常清理；未改原预算、未跳过正向测试。完整Q01–Q10/FR04/AC05/06/J10映射、失败账本、三类产物SHA和复现命令见 [ticket30验证报告](../../../docs/verification/ticket-30-command-recovery.md)。

这里的勾选表示已取得的本地契约证据。Q08/Q10的正式Linux与最终双OS CI仍由集成后现有CI完成，当前不closed；此前V3子fixture单独GREEN没有替代最终V4组合。本票的报告/状态尾提交只有文档差异，不改变冻结验证输入。
