# 进程内互斥与锁迁移

`LocalKeyedMutex` 只保护共享同一个实例的同步代码。默认应用注入该类型；数据库业务的跨进程正确性由数据库事务、唯一约束或条件写负责，需要其他跨节点能力时由应用选择成熟方案并审核其持有、续租和资源端校验政策。设施库没有分布式锁默认实现。

```java
final class RefreshService {
    private final LocalKeyedMutex mutex;
    RefreshService(LocalKeyedMutex mutex) { this.mutex = mutex; }

    String refresh(String accountId) {
        return mutex.executeWithLock("refresh:" + accountId, Duration.ofMillis(350),
                () -> recomputeSynchronously(accountId));
    }
}
```

无Spring的批处理可在 `try (var mutex = new LocalKeyedMutex(128))` 内使用同一接口。应用必须共享该实例；每次调用创建一个新对象不能互斥。该类型为纯JDK，不需要Spring或日志后端。

## 输入、等待与实际持有

- `maxLocks > 0`，默认100,000，严格限制同时被持有或被等待的不同key；不会按历史key永久累积。满时新key立即拒绝，已有key仍使用原锁，不清空、不LRU逐出活动锁。
- key必须非null、非blank且最多512个UTF-16单元。Unicode保留原样，设施不做大小写或业务身份归一化；业务必须提供稳定且有边界的key。
- `waitTimeout`非null，范围为0至1天。0为不等待，纳秒精度传给JDK；调度和操作系统实际唤醒精度不能保证纳秒延迟。预算只用于竞争该key的JDK锁，不能抢占任意业务CPU/I/O，也不承诺硬实时方法返回时限。
- 同一执行线程可重入，每次成功获取必须由该线程释放一次。`executeWithLock`自动在同步action实际返回或抛错时释放；不要在该action内手工释放自动管理的持有次数。新`unlock`对错误owner、未知key、重复释放抛`IllegalMonitorStateException`。
- 等待期限不是持有期限，没有自动到期解锁。`tryLock`在容量满、等待超时、中断或实例关闭时返回false；中断标志恢复。`executeWithLock`遇到上述拒绝抛`LockAcquisitionException`，action不执行。
- action返回Future只保护Future的构建。用Async时，应在真正工作的线程里调用 `mutex.executeWithLock`；观察Future超时/取消只改变观察状态和可能的中断请求，不代表后台工作结束。忽略中断的action继续持锁，直到实际结束。调用方负责执行器的并发/排队和业务终止预算；锁的key预算不等于线程预算。

## 生命周期

Spring拥有默认bean的关闭。`close`幂等、立即停止新准入，不强行释放已经准入的owner，也不等待不合作action。已排队线程在自身等待到期或owner释放时退出并拒绝执行业务；关闭不是立即唤醒所有等待者的API。已成功准入且尚未运行到action的调用可能继续完成，它已在关闭前取得所有权。关闭后允许owner完成解锁及回收。

两个应用各自的默认bean互不共享状态，关闭A不影响B；父子或其他容器需要共享互斥时必须明确共享同一个bean。手工`tryLock`成功后遗忘释放或线程异常终止而未执行finally不会得到自动修复，这是没有租约的线程owner锁契约。

## 旧入口逐项迁移

| 继续支持入口 | 当前行为 | 新消费者迁移 |
|---|---|---|
| `DistributedLock.tryLock/unlock` | 保留SPI签名且弃用；Duration解释为等待。类型名字本身不能证明任何用户Adapter的跨节点保证 | 本地场景注入`LocalKeyedMutex`；跨节点场景明确审核并配置外部Adapter的独立持有/续租政策 |
| `DistributedLock.executeWithLock` Supplier/Runnable | 保留自动获取/释放；业务失败优先，释放失败附加suppressed；必需callback先判空 | 新本地代码用同名`LocalKeyedMutex`方法；保留外部SPI时在应用测试真实失败与恢复 |
| `InMemoryDistributedLock(int)` 与tryLock/unlock | 弃用的本地命名兼容层，委托有界回收核心；旧错误owner/未知key unlock仍no-op | 更换类型为`LocalKeyedMutex`并处理严格owner错误。直接构造旧类不变成分布式能力 |
| `LockUtil.tryLock` | 缺少或无法唯一解析必需bean时返回false，不再报成功 | 构造注入所需类型；避免跨context动态定位 |
| `LockUtil.executeWithLock`两重载 | 缺bean抛`LockAcquisitionException`且action零执行，不再无锁放行 | 由注入在启动时发现缺配置 |
| `LockUtil.unlock` | 保留历史缺beanno-op，因而不能作为跨context/关闭之后的所有权转移工具 | 使用原注入对象且在真实工作线程内释放 |
| `FacilityLockAutoConfiguration.facilityDistributedLock(props)` | 保留可直接调用的旧公开构造入口且弃用，不再注册默认bean | 自动注入`facilityLocalKeyedMutex`，需要分布式SPI则显式提供实现 |
| `FacilityLockProperties.enabled/maxLocks` | 属性名保留；开关控制默认本地装配，容量必须正数 | `facility.lock.enabled`、`facility.lock.max-locks`不变；用户同类型bean优先并拥有自身预算 |
| `LockAcquisitionException(String)` | 保留异常类型和构造签名；表示未获得所需保护 | 上层按异常类型处理，诊断文本不作为业务协议 |

默认不再提供 `DistributedLock` bean，因此旧必需注入会在启动时失败；这是对误用本地实现充当跨节点能力的显式迁移，不能用临时无锁分支绕开。先盘点调用者所需范围，再换成本地类型或实际外部Adapter。旧0016中把同一Duration同时传给Redisson等待与租约的示例已被0030替代。

设计取舍、引用回收与有限资源验收见[ADR0030](../adr/0030-local-keyed-mutex.md)和票07验证报告。JDK线程owner与中断语义依据[JDK25 ReentrantLock](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)。
