# ADR-0034: 为独立 claim 增加执行资格与不可重做的终态

## Status

Accepted

日期：2026-10-04

部分替代 ADR-0017 的无owner完成、PROCESSING/DONE不足以表达失败、结果到期等同允许重做及advisory容量；保留旧消费者签名和Web分包理由。票11只扩展独立存储协议，HTTP迁移由12完成，业务同库事务/永久唯一键属于29。

## Context

正式票11追踪FR-04/FR-09、AC-06/AC-12。独立旧jar探针复现A租约过期、B完成后A覆盖B，未取得资格的complete突破maxEntries，以及byte[]别名改写receipt。原boolean/void无法区分取得、处理中、重放、内容冲突与设施不可用。

PRD故事16明确结果保留期结束不能自动许可重新执行业务。只加owner比较而保留“删过期结果就当新命令”的隐式政策，仍不足以作为12迁移入口。因此新协议保留命令键/指纹绑定，payload与执行许可分别管理。

## Decision

### 小型扩展接口

IdempotencyStore保留旧tryBegin/find/无owner complete，新增claim(ClaimRequest)、complete(ClaimToken, byte[], Duration retention)、release(ClaimToken)。旧自定义SPI默认新能力返回Unavailable，绝不调用旧boolean协议伪造取得。

ClaimRequest包含可信scope、client key、规范化fingerprint和正lease。scope由应用内部构造，包含适用tenant/actor/operation；库不解析用户头、不认证、不定义业务指纹规范。ClaimResult区分Acquired(token)、Processing、Replay、Conflict、Unavailable。ClaimToken含精确scope/key、随机owner和generation；只有当前有效资格可以条件完成或释放。ClaimUpdate区分APPLIED/REJECTED/UNAVAILABLE。APPLIED仅说明本地记录条件更新成功。

receipt是有界不透明byte[]，写入及读取均由防御性复制划分所有权；HTTP状态与允许重放头由12的Adapter编码，业务Receipt由29负责，不为独立claim引入Servlet依赖或公共错误DSL。

### 状态与许可表

| 当前记录与时点 | 同scope/key、同fingerprint | 不同fingerprint | 完成/释放与回收 |
|---|---|---|---|
| 不存在且预算允许 | Acquired，新owner/generation | 首次绑定该fingerprint | 只有取得者可更新 |
| PROCESSING且now < lease截止 | Processing | Conflict | 当前owner可完成/释放 |
| PROCESSING且now >= lease截止 | 同内容可Acquired新owner/generation | Conflict，绑定不消失 | 旧owner拒绝；容量不得删除该绑定 |
| DONE且now < receipt截止 | Replay防御性副本 | Conflict | 重复complete/release拒绝，原receipt不变 |
| DONE且now >= receipt截止 | Unavailable(RESULT_EXPIRED)，释放payload | Conflict | key/fingerprint墓碑保留，不重新授权 |
| RELEASED | Unavailable(RELEASED) | Conflict | 无期限拒绝重新执行；重复释放不删除、不延长期限 |
| UNKNOWN（取得后无法保存结果） | Unavailable(UNKNOWN) | Conflict | 不报告成功、不删除资格记录后放行，不自动重做 |
| store已关闭 | Unavailable(CLOSED) | Unavailable(CLOSED) | 完成/释放Unavailable，释放内部内存 |

**PROCESSING例外不是业务唯一性保证**：租约到期允许同内容新owner时，旧业务可能仍然在运行。CAS拒绝旧owner写记录，不能停止其外部支付、数据库写入或网络副作用。应用必须另有条件写/外部幂等键/恢复策略；新模板优先29的同库唯一命令键与原子业务receipt。新协议的安全性仅限此记录资格与不隐式重授权，不宣称exactly-once。

### 有界状态与时间

maxEntries是所有新旧命名空间共享的严格条目上界；新协议命令键绑定在store生命周期内保留，即使PROCESSING lease过期也只允许同键同指纹换owner。终态payload可按独立retention惰性清理，命令绑定不随payload删除；满额返回Unavailable，不LRU驱逐、不清空、不为可用性授予新业务许可。

单receipt字节与全部驻留receipt字节分别预算；写入超预算时不得返回APPLIED或恢复可执行状态。没有后台定时器/执行器，宿主控制调用并发。安全key数到顶需要宿主采用持久业务键/显式分代部署政策，不能靠自动清理伪造无限容量；进程重启仍丢失本地绑定。

采用可注入JDK Clock、毫秒粒度和明确排他截止边界（now等于deadline即到期）；回拨按已观察最高时间夹紧，正向跳变会推进到期。Duration必须为可表示的正整毫秒，deadline计算不得溢出。Clock来自宿主，异常按不可用处理，不启动查询线程。时间策略与真正的业务唯一性分开。

### 兼容与12/29边界

新旧记录命名空间隔离，旧无owner complete不能覆盖或查询新记录。旧签名保留供迁移；这不等于旧协议获得执行资格保证，旧消费者仍必须整条路径迁到新claim，不能对同一业务并行混用新旧协议。

11不改写HTTP认证/捕获/重放流程，不宣布整体HTTP幂等已安全。12负责全部仓库内无owner完成调用迁移、每次重放当前授权、201/Location允许头、有界响应、advice/断连/无法保存结果。29的同库命令模型不依赖本协议、不复制租约窗口；33按最终候选复验。

## Consequences

**Positive**：状态原子转换、所有者归属、指纹冲突、结果所有权和失败后的重新执行许可集中在存储接口后；旧自定义Adapter不会因为新增方法而暗中获得安全能力。

**Negative**：终态键占据槽位直到store关闭，内存默认实现可能明确停止接纳新命令；缺持久存储时无法跨重启恢复。扩展接口有破坏性语义迁移成本，但保持旧签名以支持逐条迁移，不能承诺新旧路径互斥保护。

**Carry-forward**：12迁HTTP；29持久业务唯一键/事务receipt；外部Adapter必须用真实后端故障/并发测试证明原子条件更新。本地测试不作为跨进程、网络分区或持久化证据。

## References

1. [Java 25 Clock](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Clock.html)：标准可注入时钟，允许宿主Clock抛出取时异常。
2. [Java 25 Duration](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html)：精度与毫秒转换边界。
3. [Java 25 UUID](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/UUID.html#randomUUID())：随机owner来源；generation与当前记录比较共同确定资格。
4. [运行契约研究](../research/2026-10-03-runtime-contracts.md) §7、[原ADR0017](0017-idempotency-full-semantics-response-capture.md)、[正式票11](../../.scratch/server-facility-next/issues/11-legacy-claim-owner.md)。

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
