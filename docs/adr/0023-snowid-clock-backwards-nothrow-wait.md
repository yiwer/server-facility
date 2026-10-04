# ADR-0023: SnowIdGenerator 回拨语义——false 无界等待绝不抛

- **状态**:Superseded by [ADR0033](0033-explicit-id-policy.md), 2026-10-04；以下保留历史决策。
- **源起**:全库评审 F2(P0);决策 a(用户确认,2026-07-05)

## 背景

`facility.id.throw-on-clock-backwards-exceed-threshold=false` 承诺"超阈值回拨不抛",
但实现里 false 分支复用 `spinUntil`(硬编码 1s 超时),delta>1s 时仍抛
`ClockBackwardsException`——配置承诺自相矛盾;`false` 分支的大回拨路径无测试。
连带:`spinUntil` 的 1s 硬上限对「阈值配 >1s 且 true」同样违约(阈内回拨本应被吸收)。

## 决策

候选:a) false 时无界等待直到追上;b) 有界等待+特殊失败返回(nextId 返 long,签名不容,
等于删配置);c) 保留 1s 上限、文档收窄"false 仅对 ≤1s 生效"。**取 a(语义最忠实)**:

- true:超阈值立抛;阈值内有界 spin,上限 `max(1s, 阈值)`(阈值>1s 不再违约;
  病理时钟下超限抛出——true 模式允许抛);
- false:`awaitClockCatchUp` 无界等待(1ms `LockSupport.parkNanos` 步进),**绝不抛**。

## 后果(无界等待风险,如实记载)

- false 模式大幅回拨时,`nextId()` 阻塞整个回拨时长且不可被中断打断(中断标志保留);
  等待发生在 synchronized 内,本生成器所有调用方整体停顿——这是"不抛"承诺的代价,
  选 false 前须理解;
- 三测锁定(注入步进时钟,零真实 sleep):false+2s 回拨不抛、false+阈内不抛、
  true+3s 阈值下 2.5s 回拨越过旧 1s 上限;
- `waitForNextMillis`(同毫秒序列耗尽)本就无界等待,新语义与之一致。
