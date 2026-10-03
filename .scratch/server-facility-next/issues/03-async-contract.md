# 03: 让异步组合遵守执行器、截止时间和清理契约

**What to build:** 一组异步工作确实在声明的执行器上运行，保留原始失败类别，遵守整体截止时间，并在完成、取消和关闭后释放自身资源。

**Blocked by:** None (can start immediately)

**Status:** verification-pending

**Traceability:** FR-05、FR-08；AC-07、AC-08、AC-11

## Acceptance criteria

- [x] 默认与用户执行器的选择、所有者和关闭责任明确；冻结新 DSL 扩张。
- [x] 子任务继承剩余预算，不延长 deadline；超时、任务失败、提交拒绝和协作式取消可区分。
- [x] 上下文在实际执行线程安装并在 finally 恢复；调用者和复用线程不残留前一任务值。
- [x] 为不响应中断的任务定义有界关闭结果，不承诺强行终止任意代码。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：单任务、组合、恢复、首成功路径使用实际声明 executor；Executor/TaskExecutor 与 Boot 配置接合。
- [x] 边界：截止时间前/等于/之后、已完成任务再取消、嵌套上下文与原值恢复。
- [x] 故障/并发：拒绝提交、供应函数抛错、AssertionError、超时与完成竞争、关闭时提交；受控调度和屏障复现。
- [x] 资源：重复失败/取消后工作线程、任务队列及上下文回到约定范围；目标平台线程/显式虚拟线程配置复验。

## Scope boundary

不引入通用强制取消平台，不让虚拟线程代替预算与生命周期。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 决策登记（2026-10-03）

ADR-0026 将替代 ADR-0002 的 TaskExecutor-only 回退类型及裸虚拟线程兜底部分；保留 Boot 优先和用户 bean 让位原则。静态 Async 不查全局 SpringContext，应用显式传入容器 Executor。拦截器改为实际执行段作用域，deadline/取消使用实际 FutureTask 句柄，关闭沿用标准 Executor/ThreadPoolTaskExecutor 生命周期，不新增 DSL。

## 实施与阶段证据

公开测试 seam：Async 工厂/组合/submit/await、AsyncInterceptor、标准 Executor/TaskExecutor、Spring ApplicationContextRunner（用户已批准）。完成 20 轮逐项 RED→GREEN；前17轮临时日志已被clean清理，观察摘要和最终完整证据见 `docs/verification/ticket-03-windows.md`，第18–20轮日志位于本 worktree `target/ticket03/`。包含整树 executor、Error 原类、实际线程 scope、父 deadline、协作取消/await、any loser、类型让位、有界关闭、默认平台池、已知值 scope、256 次 Spring 队列取消、子元数据、cancel(false)、必需参数与 before 失败清理。

最终 AsyncBoundaryTest 16 tests、AsyncContractTest 15 tests、Boot 接合 9 tests 已通过：512 次固定 seed=20261003 的受控交错、128 次真实完成/取消竞争、256 次重复失败和 256 次排队取消；队列恢复 0、工作线程池大小 1、复用线程 MDC 空值。Boot platform/virtual 配置走实际 Async；关一个应用不影响另一个；256 队列容量/第257项拒绝，shutdown 取消未开始项并终止协作任务。忽略中断项不虚报结束，容器关闭小于测试上界2s（配置等待1s），任务由测试显式释放。

完整证据见 [ticket-03-windows](../../../docs/verification/ticket-03-windows.md)：已测代码提交75ed834，已合入集成最新731598b；Wrapper `clean verify` 1251 tests、0失败/错误/跳过；instruction93.8400%、line93.7312%、branch87.1890%，架构和依赖检查通过。环境为Windows10.0.26100 / Oracle JDK25.0.4.1 / Boot3.5.16；不代表Linux/Boot4证据。

Q01–Q10 逐项证据与不适用理由记录在验证文档；公共契约已实现，Linux同提交CI、最终Boot4及J04/J17组合仍待root/24/33和相应能力票闭合，所以共同完成标准复选框保留未勾选，状态verification-pending。任务本身无数据库/线格式协议，相关金样与持久化维度不适用；多小时heap/连接长稳未执行，不把固定循环称为长稳通过。
