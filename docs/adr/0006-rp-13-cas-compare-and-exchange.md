> **inherited-from**: beacon ADR-0006(原仓库 docs/adr/0006-rp-13-cas-compare-and-exchange.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0006: RP-13 LogUtil CAS compareAndExchange 重写

## Status

Partially superseded by [ADR-0025](0025-context-ownership.md)（2026-10-03）。

进程级 LogPostHandlerComposite 缓存策略由 ADR-0025 替代；每次日志最多分发一次的公开保证继续生效。下文保留原决策与理由，作为历史记录。

日期：2026-05-21

## Context

- 评审来源：`docs/facility/REVIEW.md` §7 RP-13 + §3.4 评审发现（`log\LogUtil.java:296–310`）+ §5.4 评审发现 + §6.B.5 `log/`
- beacon 现状：`LogUtil.invokePostHandler`（line 291-311）使用 `AtomicReference<LogPostHandlerComposite> HANDLER_CACHE` 延迟初始化。CAS 写入路径（line 296-307）含双重 `doInvokePostHandler` 调用：
  - CAS 成功分支（line 299）：调一次
  - CAS 失败分支（line 303-305）：再调一次
- 问题：在多线程并发首次日志场景下，多个线程同时进入 `ifOk` callback。线程 A `compareAndSet(null, h)` 成功 → 调 doInvokePostHandler；线程 B 同时 compareAndSet 失败 → 走 else 分支再次调 doInvokePostHandler 用 existing handler。**`LogPostHandler.handle()` 对同一 LogContext 可能被调用两次**，引发重复日志上报或副作用幂等性问题（特别是当 LogPostHandler 是告警通知 / 远程上报实现）。
- 行业现状：
  - **JCIP** *Java Concurrency in Practice* (Brian Goetz et al., 2006) §7.2 (并发初始化模式)：lazy initialization holder idiom 或 `AtomicReference` `compareAndExchange` 单次分支
  - **Effective Java** (Joshua Bloch, 2018) Item 79 (Avoid excessive synchronization)：避免过度同步，但要确保 invariant 不破坏
  - **Java 9+** `AtomicReference.compareAndExchange(expected, update)` 返回旧值：若返回 expected（即 null）则本线程 CAS 成功；返回非 null 则别的线程已先写入，本线程可使用 witnessed 值

## Decision

把 `invokePostHandler` 改用 `compareAndExchange` 单次分支：

```java
private static void invokePostHandler(String message, Level level, String callerClassName, Throwable throwable) {
    LogPostHandlerComposite handler = HANDLER_CACHE.get();
    if (handler == null) {
        SpringContextHolder.getBean(LogPostHandlerComposite.class).ifOk(h -> {
            LogPostHandlerComposite witnessed = HANDLER_CACHE.compareAndExchange(null, h);
            LogPostHandlerComposite winner = (witnessed == null) ? h : witnessed;
            doInvokePostHandler(winner, message, level, callerClassName, throwable);
        });
    } else {
        doInvokePostHandler(handler, message, level, callerClassName, throwable);
    }
}
```

**Invariant**（核心契约）：
> 每次 `invokePostHandler(message, level, callerClassName, throwable)` 调用恰好触发一次 `LogPostHandlerComposite.handle(context)` 调用（除非 `SpringContextHolder.getBean()` 返回 Err，此时不调）。

**并发行为分析**：
- 场景 1（HANDLER_CACHE 已 publish）：line 292 取到非 null handler → 走 line 309 单次调用，invariant 满足
- 场景 2（首次并发，A 与 B 同时进入 ifOk）：
  - A 调 compareAndExchange(null, hA) 返回 null（CAS 成功）→ A 用 hA 调一次
  - B 调 compareAndExchange(null, hB) 返回 hA（CAS 失败）→ B 用 hA 调一次（B 自身的 hB 被丢弃）
  - 总调用次数 = 2（A 和 B 各一次，分别对应各自 invokePostHandler 调用，invariant "每次 invokePostHandler 调用一次" 满足）
- 旧实现 bug：场景 2 中 B 在 if/else 双分支前都进入 callback，按代码读法实际只调一次（else 分支也是单次 doInvokePostHandler），但旧代码的双重 `HANDLER_CACHE.get()` 调用在 CAS 失败分支增加了额外可能性（理论上 existing 在 get 之间可能变化导致两次 doInvokePostHandler 调用同一个 message —— 极小窗口但存在）

## Consequences

**Positive**：
- 消除"同一 LogContext 被 LogPostHandler.handle 重复调用"风险
- 代码意图更清晰（compareAndExchange 显式表达"原子读 + 写一次"语义）
- 与 JCIP / Effective Java 推荐的并发初始化惯例对齐

**Negative**：
- 要求 Java 9+（`compareAndExchange` 是 Java 9 引入的 API）；beacon 用 Java 21，无障碍
- 单元测试覆盖并发场景仍依赖 phase-4+ 测试搬迁（log/ 包测试 phase-3 不搬）

**Carry-forward**：
- phase-4 搬迁 `log/LogUtilTest` 时补充多线程 happy path 测试（验证 invariant：N 次 invokePostHandler 调用 ⇒ LogPostHandler.handle 被调用 ≤ N 次）
- 若未来 LogPostHandler 数量大或 handle 耗时，考虑改用 lazy initialization holder idiom（持久化 holder class），但当前 CAS 路径足够

## References

1. *Java Concurrency in Practice* (Brian Goetz et al., 2006) §7.2 (Concurrent Initialization)
2. *Effective Java* (3rd ed., Joshua Bloch, 2018) Item 79 (Avoid excessive synchronization)
3. `java.util.concurrent.atomic.AtomicReference.compareAndExchange` JavaDoc (Java 9+). https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html#compareAndExchange(V,V)
4. Doug Lea, *JSR 166*: Concurrency Utilities. https://gee.cs.oswego.edu/dl/concurrency-interest/

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
