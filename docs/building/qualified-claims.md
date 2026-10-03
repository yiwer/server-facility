# 独立 claim 迁移（票11，ADR0034）

`IdempotencyStore.claim` 的结果直接表达是否取得执行资格。只有 `Acquired` 进入这个协议的执行业务分支；`Processing` 等待或拒绝，`Replay` 读取回执，`Conflict` 拒绝同键不同内容，`Unavailable` 不执行。旧自定义 Store 没有实现新方法时返回 `UNSUPPORTED`，不会暗中退回 boolean 接口。

```java
try (var store = new InMemoryIdempotencyStore(1024, 64 * 1024, 8L * 1024 * 1024, Clock.systemUTC())) {
    var request = new ClaimRequest(trustedTenantActorOperation, clientKey, canonicalFingerprint,
            Duration.ofSeconds(30));
    switch (store.claim(request)) {
        case ClaimResult.Acquired acquired -> {
            byte[] receipt = executeWithIndependentBusinessProtection();
            ClaimUpdate update = store.complete(acquired.token(), receipt, Duration.ofHours(1));
            // APPLIED 只表示记录更新成功；REJECTED/UNAVAILABLE 必须进入应用的恢复/核对流程。
        }
        case ClaimResult.Replay replay -> useReceiptAfterCheckingCurrentAuthorization(replay.receipt());
        case ClaimResult.Processing processing -> rejectOrRetryLater(processing.retryAfterMillis());
        case ClaimResult.Conflict conflict -> rejectDifferentContent();
        case ClaimResult.Unavailable unavailable -> rejectAndReconcile(unavailable.reason());
    }
}
```

示例中的 store 由应用长期持有，到应用关闭时再 close；不要每请求创建/关闭。scope 必须由应用可信身份与操作构造，不能直接相信自定义请求头。fingerprint 由应用规范化内容计算，库不定义业务 JSON 的规范形式，也不认证调用者。token 是记录执行资格，不能当作认证凭据。

| 输入/资源 | 约定 |
|---|---|
| scope/key/fingerprint | 分别最多512/256/128个UTF-16单元，非blank、无ISO控制字符；原样比较，不隐式trim/Unicode归一化 |
| lease/retention | 可表示为long的正整毫秒；小数毫秒、非正值、转换溢出拒绝；等于截止值即到期 |
| maxEntries | 新旧命名空间共享硬上限；新命令绑定直到close都占槽位；满额明确不可用 |
| maxReceiptBytes/maxStoredReceiptBytes | 单条/合计驻留payload正字节预算；彼此独立，总预算可小于单条预算 |
| 默认构造器 | `maxEntries`由调用者提供；单条1MiB，总量64MiB，系统UTC Clock |
| 字节所有权 | complete复制输入，Replay与旧IdempotencyRecord复制输出；调用者负责分配输入、并发与调用返回前不修改输入 |
| 时间 | 注入Clock；观察到回拨时夹紧至已观察最高毫秒，前跳推进到期；新claim截止溢出/时钟异常返回CLOCK；合格完成/释放取时失败保存UNKNOWN |
| 清理 | 无后台线程。压力下最多扫描maxEntries条目以释放过期payload/旧TTL记录；永不驱逐新协议绑定。close清内存且不可重开 |

新协议的 `DONE` 到期只释放正文，留下 `RESULT_EXPIRED`；`RELEASED` 和 `UNKNOWN` 也保留终态绑定，不会因为等了更久而重新授予业务许可。不同fingerprint始终Conflict。满额时需要应用持久化/分代或核对政策，不能用LRU/clear解决正确性问题。失败返回中不包含调用者key、owner或Clock异常消息；请求与token的默认诊断也脱敏。

**PROCESSING是明确例外**：租约到期允许同内容取得新owner，旧执行者可能仍在执行。当前owner比较只能拒绝旧执行者覆盖记录，不能取消其支付、数据库写入或其他副作用。真正的唯一命令、数据库约束、事务receipt和恢复策略由业务负责；票29的新模板采用同库唯一键与原子receipt，不依赖本地租约窗口。

迁移必须把首次占位、重复决策、完成和释放整条路径切换。旧 `tryBegin/find/complete(String,...)` 保留并弃用，使用独立命名空间；对同一业务混用两套入口没有互斥保证。旧 complete 现在要求活跃旧PROCESSING，不能凭空插入、覆盖旧DONE或突破预算；旧key也限256单元，TTL必须正数，旧record的状态与Content-Type校验收紧。旧TTL仍保持历史 `now > expiresAt` 才过期与可重入政策，不能据此宣称安全业务重试。

票11没有迁移HTTP拦截器，因此不宣布整体HTTP幂等已安全。票12会迁移仓库内旧调用、当前身份/授权、状态与允许头编码、捕获失败/断连/异步时的UNKNOWN及结果重放；票29负责持久业务命令。旧自定义Adapter须提供其真实后端上的原子资格比较、故障保留与容量证据，内存测试不能替代网络分区或跨实例验证。

普通jar验证入口是 `java verification/Verify.java integration` 或 `all`，包含仅JDK的claim消费者：历史三方法SPI二进制兼容、A/B迟到owner屏障、固定seed110034、硬容量churn以及128个保持可达的已关闭store（64MiB堆）。完整状态表见[ADR0034](../adr/0034-qualified-legacy-claims.md)。
