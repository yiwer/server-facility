# Ticket 03 Windows 验证证据

**2026-10-04 平台闭合**：本票适用待验证项已由`80670fa`的Windows/Ubuntu同源CI完成，状态closed。见[票24 CI证据](ticket-24-ci.md)。下文保留各次执行的来源、数值及当时状态，不以旧数字代替新环境观测。

日期：2026-10-03。实现已可合并；票保持 `verification-pending`，不把 Windows 的当前中间平台结果称作 Linux / Boot 4 / 最终组合验收。

## 固定版本与环境

- 被测源码/测试提交：`75ed8345a1614b67c85affa61c8d41d60a002d95`；实现 `68eb67f`，取消竞态修复 `05cf6bc`。
- 已合入集成线最新基线：`731598b229915e40cc173d0a0deaaea12f24d343`（票 01/02）；合并记录 `1677ac9`、`4a11ad3`。本证据后的票据/文档提交不改变 Java 源码、测试、POM 或产物。
- Microsoft Windows 10.0.26100；Oracle JDK `25.0.4.1+1-LTS-5`；PowerShell 主机 Culture `zh-CN`，时区 `China Standard Time`（Asia/Shanghai）。
- Wrapper Maven 3.10.0；编译/运行 Java 25，无 preview；Boot 3.5.16、Surefire 3.6.0、JaCoCo 0.8.15。此平台是票 01 的中间基线。
- 未使用数据库、HTTP 服务、容器或外部账号；这些不是本票 Async 公共契约的依赖。

## 最终质量门

```powershell
.\mvnw.cmd -B -ntp clean verify
```

2026-10-03 23:12:34 +08:00 完成，耗时 36.812s，`BUILD SUCCESS`：**1251 tests，0 failures，0 errors，0 skipped**。含 5 条 ArchUnit，依赖分析 `No dependency problems found`；没有降低门槛、增加 ignore 或移除旧测试。

| JaCoCo BUNDLE | Covered / Total | 实测 | 门槛 |
|---|---:|---:|---:|
| INSTRUCTION | 16940 / 18052 | 93.8400% | 88% |
| LINE | 3424 / 3653 | 93.7312% | 88% |
| BRANCH | 1647 / 1889 | 87.1890% | 75% |

普通 jar：`target/server-facility-0.1.0-SNAPSHOT.jar`；SHA-256 `e22265cfb7fdc096a62c27ea3c811e47911812356c6844dff8d913234bde41d9`。

完整日志保存于实施 worktree 忽略目录 `E:/GenCode/server-facility-worktrees/ticket-03/target/ticket03/target-ticket03-final-clean-verify.log`；同目录有第 18–20 轮 RED/GREEN 与阶段全门日志。前 17 轮临时日志被后续 `clean` 清理，以下保留当轮失败/通过的观察摘要；不将已经删除的原始日志列为可下载证据。最终源码和命令均可重跑。

## RED → GREEN 摘要

每轮先通过已批准的 Async / Executor / TaskExecutor / interceptor seam 写失败回归，再实现对应行为。前 17 轮使用研究期 Maven 3.9.16 和原 release 21 POM，仅是实施过程证据；最终上述 clean verify 才是 Java 25 平台证据。

| 轮次 | RED 的公开行为反例 | GREEN 回归 |
|---:|---|---|
| 01 | all 子任务忽略外层 executor | allChildrenAndContinuationUseDeclaredSingleThreadExecutor |
| 02 | AssertionError 被 timeout 改成 TimeoutException | timeoutPreservesTheOriginalAssertionError |
| 03 | completed 后 map/recovery 在错误线程运行 | completedPipelineRecoveryAndNestedTaskRunOnDeclaredExecutor |
| 04 | scope/MDC 安装在 caller，worker 读到旧值 | scopedInterceptorAndMdcAreInstalledOnWorkerAndRestored |
| 05 | 链中 timeout 不覆盖后续 nested task | outerDeadlineCoversNestedTaskAndInterruptsIt |
| 06 | Future 取消没有中断实际工作 | cancellingSubmissionInterruptsWorkerAndDoesNotCancelCompletedSubmission |
| 07 | await 超时留下工作运行 | awaitTimeoutCancelsOwnedSubmission |
| 08 | any 成功后 loser 仍占用线程 | firstSuccessCancelsLosingTasks |
| 09 | 普通 Executor 不使 Spring fallback 让位 | userPlainExecutorSuppressesFacilityDefaultAndRunsAsync |
| 10 | 忽略中断任务使容器 close 超出上限 | contextCloseIsBoundedWhenTaskIgnoresInterruption |
| 11 | 静态默认不满足声明的进程平台池 | staticDefaultUsesFacilityOwnedPlatformWorkers |
| 12 | 已知值附带 interceptor 被跳过 | completedValueStillRunsItsInterceptorOnExecutor |
| 13 | 已取消 Future 占住 Spring 队列 | repeatedQueuedCancellationReclaimsSpringExecutorCapacity |
| 14 | 组合 scope/metadata 不进入子任务 | compositionPropagatesScopedInterceptorsAndChildMetadataOverridesParent |
| 15 | cancel(false) 仍中断 nested worker | cancellationWithoutInterruptionAlsoRespectsNestedWorkers |
| 16 | runnable/executor null 未 fail-fast | requiredFunctionsAndExecutorsFailFastButNullValuesRemainValid |
| 17 | around before 失败不运行清理 | aroundCleanupRunsWhenBeforeFailsAndTaskDoesNotRun |
| 18 | 调用者 complete 公开 Future 后再 cancel 仍偷偷中断工作 | externallyCompletedFutureCannotBeCancelledOrInterruptWork |
| 19 | terminal callback 等 worker finally，实际取消却在 callback 之后形成闭环 | terminalObserversCanWaitForWorkerFinallyBecauseCancellationSignalComesFirst |
| 20 | 取消移除发生在 execute 入队前，晚入队项没有回收 | cancellationWhileExecutorIsAcceptingCannotLeaveCancelledQueueEntry |

第 20 轮使用真正的 ThreadPoolExecutor 队列与可控接收屏障；最终回归还固定在取消移除尝试完成后才允许入队，断言最终队列为空。第 19 轮同时验证 cancel 与 timeout，用户 whenComplete 能看到工作 finally 已执行。两个测试都只观察公开 executor/future 行为。

## Q01–Q10 追踪

| 维度 | 本票结果与测试 |
|---|---|
| Q01 契约 | FR-05/FR-08，AC-07/08/11 对应 AsyncContractTest、AsyncBoundaryTest、FacilityAsyncAutoConfigurationTest；原研究的 executor、AssertionError、上下文反例均回归。ADR-0026 登记默认、所有者、预算与旧 ADR-0002 替代范围。 |
| Q02 正常/边界 | 原 AsyncTest/AsyncContextTest/AsyncInterceptorTest 保留；单任务、all、any、恢复、空集合、null 值/函数、子 executor、已完成任务取消、零/负/极大正预算、重复 timeout、较短子 deadline 可恢复与晚到完成不可覆盖均验证。 |
| Q03 接合 | 真正 Boot `applicationTaskExecutor` 的默认 platform/显式 virtual 配置；普通 Executor/TaskExecutor 类型让位；两个应用 executor 关闭隔离；fallback 饱和与销毁。HTTP 身份、锁模块接合由后续对应票负责。 |
| Q04 故障/并发 | supplier IOException/AssertionError、map/recovery Error、提交拒绝、等待线程中断、before 失败 finally、实际 task 中断、取消完成竞争、回调与清理闭环、取消入队竞争、关闭时提交均有可观察断言。新测试无随机 sleep。 |
| Q05 有界资源 | 静态默认 4 个实际在途 worker + 256 排队，第 257 项拒绝；取消后容量恢复，线程数 ≤4、timer ≤1。Spring 256 队列饱和/取消/关闭后空队列及 terminated；单 worker 256 次排队取消、256 次失败，队列恢复 0，MDC 恢复空值。忽略中断项在配置等待 1s 后容器返回（测试上界2s），仍显示未结束，测试随后显式释放。没有声称多小时 heap soak 已执行。 |
| Q06 兼容样本 | 保留全部旧 Async 行为测试；无独立文件/线协议格式，外部格式金样不适用。行为迁移（平台默认、deadline、取消、逐段 interceptor）写入 CHANGELOG/USAGE/ADR，无公开 DSL 删除或新增。 |
| Q07 可重放 | seed=20261003，512 次受控完成/取消顺序；128 次 CyclicBarrier 真实竞争。可单跑 `-Dtest=AsyncBoundaryTest`；资源循环均固定256，无新增测试框架。 |
| Q08 环境/诊断 | 上文固定 commit/JDK/OS/Boot/命令/产物；Surefire 显示回归名称、循环编号、队列结果；Linux 尚未执行本票版本，不能将票01的旧 CI 当作本票证据。 |
| Q09 门槛 | clean verify 1251 全绿，覆盖率/架构/依赖门全通过；旧基线工具的 JaCoCo major69 instrumentation 警告不作为最终证据，最终 0.8.15 无该警告。新增36项测试（相对合入的票01/02基线1215）；无删测/跳过/门槛修改。 |
| Q10 审阅 | 代码、设计/迁移、测试和本证据同票交付；本票尚缺Linux和Boot4目标平台复验，所以未closed。下游HTTP/锁接合与票33候选复验是独立责任，不构成本票对33的前置依赖。 |

## 本票尚需闭合

- 已补齐：包含本票的 `0e8d586` 通过仓库 Windows/Ubuntu CI，详见 [跨平台记录](ticket-02-03-ci.md)。本机 WSL 限制未影响仓库 CI 执行。
- 票24在Boot4目标平台复验platform/virtual和装配后提供本票资源场景的勾选依据；这里是Boot3.5.16中间基线。

## 下游独立接合与候选复验

以下不是本票新增前置条件，不建立03依赖33的循环；各主责票在自身验收/候选发布时执行。
- J04 的 HTTP 身份/安全上下文策略由票 06/26/27 接合；J17 的持锁任务超时不提前解锁由票 07/33 接合。本票只承诺 MDC/显式元数据与协作取消，不传播事务/安全身份、不强行终止任意代码。
- 票 33 扩大长稳规模和跨平台候选回归。上述固定256循环/线程队列观测不能替代多小时 heap/外部连接长稳。
