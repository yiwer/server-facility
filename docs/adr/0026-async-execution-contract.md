# ADR-0026: Async 执行器、截止时间与同步上下文作用域

## Status

Accepted

日期：2026-10-03。部分替代 [ADR-0002](0002-rp-04-async-bean-type-matching.md)：保留 Boot 优先及按类型让位，替代 TaskExecutor-only 条件、裸虚拟线程兜底与其未落实的运行路径。对应本地 ticket 03，FR-05/FR-08、AC-07/AC-08/AC-11。

## Context

研究基线中组合丢失外层 executor，每次默认提交新建无人关闭的虚拟线程执行器。拦截器在调用线程设置 ThreadLocal 后从异步线程清除；timeout 只改变观察状态，并把 AssertionError 误写成 TimeoutException。完整反例见 [运行时研究](../research/2026-10-03-runtime-contracts.md)。

JDK CompletableFuture 的取消状态不保证实际工作中断，Executor 的所有者也不能由一个静态工厂隐式决定。Boot 已管理应用线程池及显式虚拟线程选择。本次修复保留现有惰性 Async 接口，不扩张组合 DSL，不引入预览 Structured Concurrency 或另一个公开运行时包装类。

## Decision

1. 静态 `Async.supply/run` 使用进程共享平台线程池：4 个 daemon 工作线程、最多 256 个等待项、AbortPolicy、空闲 30 秒回收。共享 deadline 调度器最多 1 个 daemon 线程，完成/取消会移除定时项，空闲 30 秒回收。应用需要独立生命周期、容量或虚拟线程时显式注入 `Executor`，传给工厂或 `.executor(executor)`；Async 不查全局 SpringContext，不关闭用户执行器。
2. `FacilityAsyncAutoConfiguration` 继续排在 Boot TaskExecution 之后，改为 `@ConditionalOnMissingBean(Executor.class)`；普通 Executor、TaskExecutor、Boot applicationTaskExecutor 均使它让位。独立回退是容器拥有的 ThreadPoolTaskExecutor：4 个平台线程、队列 256、daemon、空闲 30 秒、有界等待关闭 1000ms。通过 `acceptTasksAfterContextClose=true` 跳过更早的 SmartLifecycle 排空等待，销毁阶段立即 shutdownNow 并取消等待项；从 shutdown 开始拒绝提交。此设置不把 context-close 事件到 bean 销毁之间的短窗口宣称为已经拒绝。Boot 或用户执行器的容量、关闭策略由各自所有者决定。
3. 所有 supplier、mapper、recovery、effect 在本任务声明的 executor 执行。子任务未声明 executor 时继承父选择，显式子 executor 优先；父后续回调回到父 executor。组合使用非阻塞依赖连接，单线程池不会因内部等待子任务而死锁。直接 Executor（如 Runnable::run）按其标准语义可能同步执行；不要在同一个受限池的用户回调中再阻塞 await 自己提交的新工作。
4. timeout 从一次 submit 起计算整个任务树的单调 deadline，与链中位置无关。到达或超过 deadline 均为超时；零/负 Duration 是立即到期（持续保持旧 Duration 语义，不是配置项的“无限制”哨兵）。溢出纳秒时饱和到 Long.MAX_VALUE；重复 timeout 只缩短；子任务取父剩余预算和自身预算中的较小值。父 deadline 到期是终态，不再执行恢复或后续业务。较短子 deadline 失败可在父剩余预算内恢复。
5. 每个实际用户执行段在工作线程安装提交时捕获的 MDC；finally 恢复该线程原 MDC。上下文元数据与拦截器向子任务继承，子属性覆盖父属性。拦截器按 order 同步进入/退出每个执行段，proceed 和 interceptor 均必须返回已完成 Future；返回未完成 Future 得到 IllegalStateException。这样 try/finally 能可靠恢复自定义 ThreadLocal。around 的 after 在 before 失败时也执行。此契约不传播 JDBC 事务、任意 ThreadLocal 或安全身份，也不承诺整个多线程 pipeline 位于一个事务内。
6. 原始任务 Throwable（包括 AssertionError）及提交拒绝通过 Result.err 原对象返回，不改类为超时；只剥离框架的 CompletionException/ExecutionException 包装。`submit().cancel(true)` 取消观察状态并通过实际 FutureTask 请求中断，false 只取消状态/未开始项，不中断运行中的工作。返回 Future 的 get/join 遵循 JDK CancellationException；await 等待被中断会取消自己的提交并恢复中断标志。await(Duration) 使用同一整体预算。any 首成功后取消未完成分支；all 保留等待其余分支并聚合失败的语义。
7. 取消会清除 JDK ThreadPoolExecutor 与未装 TaskDecorator 的 Spring ThreadPoolTaskExecutor 中本次提交的队列项。任意外部 Executor 或 TaskDecorator 可以包裹/丢弃工作；它们的队列回收仍由其所有者负责。所有者调用标准 shutdownNow 时，应对返回的未开始 Future 项调用 cancel；Spring 回退会执行该步骤。Async 不擅自关闭、清空或 purge 其他消费者工作。
8. 观察结果终止不等于任意业务已停止。忽略中断的代码可能继续产生副作用；容器回退关闭最多等待 1000ms 后返回并记录未终止警告，实际状态由 `getThreadPoolExecutor().isTerminated()` 检查。锁/连接必须在工作本身 finally 中释放；不得因观察方超时提前释放工作仍使用的资源。

## Consequences

**Positive**：执行器选择、资源所有权、实际线程上下文和预算成为可验证契约；取消/超时/拒绝/任务异常可区分；两个应用关闭互不影响。

**Negative**：默认从每任务虚拟线程变成有界平台线程，容量不足会拒绝；原来把一次拦截等同整个 pipeline 的消费者需迁移到执行段语义；依赖超时后继续执行业务或首成功后继续其他分支的代码会观察到新的取消请求。消费者仍需设计外部副作用的幂等/条件写与协作停止。

**Carry-forward**：票 07/33 复验“持锁任务超时不提前解锁”；票 06/26/27 负责 HTTP 身份传播政策，本票只处理 MDC/显式元数据；票 24 负责最终 Boot 4 接合；候选发布在同一产物复跑长稳与支持平台，不将当前 Windows、Boot 3.5 证据称作 Linux 或 Boot 4 证据。

## References

1. [JDK 25 FutureTask](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/FutureTask.html)：实际任务取消与中断。
2. [Spring ExecutorConfigurationSupport](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html)：销毁、等待上限、取消未开始任务及生命周期阶段。
3. [Boot Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)：容器执行器和显式虚拟线程配置。
4. [测试策略 Q01–Q10](../superpowers/plans/2026-10-03-server-facility-next-test-strategy.md)、[消费者迁移说明](../USAGE.md#异步async)。

---

本 ADR 遵循 [仓库模板](0000-adr-template.md)。
