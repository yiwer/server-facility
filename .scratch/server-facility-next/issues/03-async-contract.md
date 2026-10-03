# 03: 让异步组合遵守执行器、截止时间和清理契约

**What to build:** 一组异步工作确实在声明的执行器上运行，保留原始失败类别，遵守整体截止时间，并在完成、取消和关闭后释放自身资源。

**Blocked by:** None (can start immediately)

**Status:** in-progress

**Traceability:** FR-05、FR-08；AC-07、AC-08、AC-11

## Acceptance criteria

- [ ] 默认与用户执行器的选择、所有者和关闭责任明确；冻结新 DSL 扩张。
- [ ] 子任务继承剩余预算，不延长 deadline；超时、任务失败、提交拒绝和协作式取消可区分。
- [ ] 上下文在实际执行线程安装并在 finally 恢复；调用者和复用线程不残留前一任务值。
- [ ] 为不响应中断的任务定义有界关闭结果，不承诺强行终止任意代码。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：单任务、组合、恢复、首成功路径使用实际声明 executor；Executor/TaskExecutor 与 Boot 配置接合。
- [ ] 边界：截止时间前/等于/之后、已完成任务再取消、嵌套上下文与原值恢复。
- [ ] 故障/并发：拒绝提交、供应函数抛错、AssertionError、超时与完成竞争、关闭时提交；受控调度和屏障复现。
- [ ] 资源：重复失败/取消后工作线程、任务队列及上下文回到约定范围；目标平台线程/显式虚拟线程配置复验。

## Scope boundary

不引入通用强制取消平台，不让虚拟线程代替预算与生命周期。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 决策登记（2026-10-03）

ADR-0026 将替代 ADR-0002 的 TaskExecutor-only 回退类型及裸虚拟线程兜底部分；保留 Boot 优先和用户 bean 让位原则。静态 Async 不查全局 SpringContext，应用显式传入容器 Executor。拦截器改为实际执行段作用域，deadline/取消使用实际 FutureTask 句柄，关闭沿用标准 Executor/ThreadPoolTaskExecutor 生命周期，不新增 DSL。

## 实施与阶段证据

公开测试 seam：Async 工厂/组合/submit/await、AsyncInterceptor、标准 Executor/TaskExecutor、Spring ApplicationContextRunner（用户已批准）。完成 17 轮逐项 RED→GREEN，失败复现与通过日志位于本 worktree `target/ticket03/target-ticket03-{red,green}-01..17.log`。包含整树 executor、Error 原类、实际线程 scope、父 deadline、协作取消/await、any loser、类型让位、有界关闭、默认平台池、已知值 scope、256 次 Spring 队列取消、子元数据、cancel(false)、必需参数与 before 失败清理。

补充边界 12 tests、Boot 接合 9 tests 已通过：512 次固定 seed=20261003 的受控交错、128 次真实完成/取消竞争、256 次重复失败和 256 次排队取消；队列恢复 0、工作线程池大小 1、复用线程 MDC 空值。Boot platform/virtual 配置走实际 Async；关一个应用不影响另一个；256 队列容量/第257项拒绝，shutdown 取消未开始项并终止协作任务。忽略中断项不虚报结束，容器关闭小于测试上界2s（配置等待1s），任务由测试显式释放。

完整质量门、提交 SHA、目标工具链结果及 Q01–Q10 最终映射待合入当前集成工具链后补齐；本票当前不标 closed。
