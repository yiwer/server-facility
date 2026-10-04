# 28: 用一个受保护业务 Module 完成持久化 CRUD 与分页

**What to build:** 调用者通过认证 API 创建并读取业务数据，数据跨重启保留；分页、授权、事务和数据库约束在同一垂直用例中可观察。

**Blocked by:** 27 交付能独立启动且默认受保护的 API 模板

**Status:** verification-pending

2026-10-04 CI15 repair: the original concurrent migration test reproduced a shared Logback reconfiguration race in the same-JVM RunningApp fixture. Only standard logging listener events are now coordinated; both applications must still reach the real BEFORE_MIGRATE barrier. The diagnostic 12 repetitions changed from 3 errors to zero, and the mandatory contract retains three complete lifecycles. Precise original/secondary causes and current-candidate subset evidence: [CI15 repair report](../../../docs/verification/ticket-28-ci15-fix.md). Replacement joint CI remains required; ticket29 has not started.

2026-10-04 CI14 repair: native development startup now uses pg_ctl; primary startup failures and native logs survive cleanup, including independent database stop when readiness-file deletion fails. Frozen06881a9 passed root1667 and template76 complete quality gates; final baeb8c1 passed all three CLI failure/lifecycle regressions and real packaged HTTP in both thread modes. Exact distinct sources and logs: [CI14 repair report](../../../docs/verification/ticket-28-ci14-fix.md). Replacement joint CI remains required before closure.

2026-10-04 frontier: root authorized implementation from b7b7ea46778972822c7a757cf593d441bc1cde3c. Ticket27 has its own same-source Windows all evidence and CI37152209100 Ubuntu all success; the combined Windows run failed in the separate ticket25 partner-build before template execution. Ticket27 remains verification-pending; this authorization does not close its joint CI or the final ticket33 candidate gate. Approved test seams are actual signed HTTP, the public business Module, and observable PostgreSQL effects. ADR0051 records this Module's ownership and migration path.

**Traceability:** FR-07、FR-08；AC-03、AC-04、AC-11

## Acceptance criteria

- [x] 采用单一推荐 PostgreSQL/Flyway/JdbcClient 路径并登记决策及依赖，Schema 只由一种迁移机制管理。
- [x] 业务 Module 拥有授权、不变量与事务，不引入 BaseService/万能 Repository；新增业务不修改通用核心。
- [x] 分页大小/偏移有预算，排序允许集映射到持久化字段；非法输入 400，列表和空结果结构稳定。
- [x] 说明开发数据库、迁移、启动、验证与打包，测试使用同类型数据库，不以 H2 替代证明。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/端到端：建库迁移、认证创建/查询/更新或删除、列表、重启保留；业务约束和当前权限生效。
- [x] 边界：非法页码、超限大小/偏移、未知排序字段、SQL 片段、空页、重复业务键。
- [x] 故障/接合：事务中失败原子回滚、数据库不可用/约束竞争/连接预算、错误协议及日志关联。
- [x] 迁移：空库和已知上一 schema 样本升级；启动竞争和迁移失败不会静默运行错误 schema。

## Scope boundary

不增加 ORM 选项，业务为演示但必须是一条完整可维护路径。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 2026-10-04 implementation handoff

以上行为复选框对应Windows真实执行，不代替待补Linux证据。ADR0051与[完整报告](../../../docs/verification/ticket-28-persistent-business.md)记录单一数据库路径、资源/配置预算、字面金样、Q01–Q10/J03/J09/J10边界、全部RED/GREEN及已发生的harness失败。

- 冻结source `4a5ad5d2513ccf7d87f2feeb95056551b4b0ef15`：`.verification-results/20261004-061826-432-all/`，92步骤all--fresh **PASS**；库1614/0/0/0、partner15/0/0/0、模板74/0/0/0，原覆盖率/架构/依赖/普通jar/资源及负控完整通过。
- 自审发现Boot把亚秒JDBC Duration截为整秒；100ms/1500ms实际两反例RED→GREEN后限制为1–3整秒。最终被测 `e0fd5b38844122e1a97b8bc816a93c079ccb8d9b` 已同步main `ca6816c`；相对冻结all只改一行配置守卫和两个参数化回归，其他产品/POM/验证入口不变。
- `.verification-results/ticket-28/final-template/` 最终完整独立模板子集 **PASS**：76/0/0/0，instruction98.052%、line97.112%、branch84.651%；仓库外Unicode目录、实际可执行jar平台/虚拟线程CRUD与持久化重启、普通jar一致性和无覆盖率负控均通过。遵循root授权保留两套来源，不把早期all拼成最终source全门。
- Prepared native PostgreSQL18.6 Windows下载SHA512/解压/版本检查与开发数据库两次进程生命周期均真实通过；CI已加入Windows/Ubuntu相同固定工具入口。等待root对集成最终候选跑双OS全门，再闭合Q08/Q10与本票；目前不标closed。
-29/30的原子命令receipt、保留清理、提交点进程丢失及双JVM恢复仍由后续独立票实现。28没有用重启读成功冒充这些承诺，也未发布制品。
