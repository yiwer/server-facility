# ADR0030: 明确进程内互斥与必需实现

- 状态：Accepted（2026-10-04；票07）。
- 部分替代0016：不再默认把本地锁注册为 DistributedLock；缺实现不执行业务；严格容量与安全键回收取代历史永久累积/advisory bound；等待参数不作为持有租约。

新增纯JDK LocalKeyedMutex，使用公开的 tryLock/unlock/executeWithLock 同步契约，范围仅本对象实例；多个JVM或同JVM多个对象不共享互斥。owner是实际执行线程，可重入，每次成功获取需同线程释放一次。等待预算为0至1天，使用纳秒不截断亚毫秒值；无持有期限或定时自动释放，observer取消/超时不释放实际action。异步操作须在真正执行工作线程内获取并在实际工作结束释放；返回Future的Supplier只保护Future的构建。

有界注册表的引用同时覆盖持有者和等待者，原子准入使活动键不超过maxLocks；仅最后引用退出才回收，不能按isLocked快照驱逐。key长度上限512 UTF-16单元且非blank，避免一个无限大输入击穿条目预算。close停止新准入，保持已准入工作的锁至真实释放；排队者在自身等待到期或锁可用时拒绝进入，close不等待或强停不合作action。

自动装配仅提供 LocalKeyedMutex，用户同类型bean优先。DistributedLock/InMemoryDistributedLock/LockUtil保留已弃用签名；旧本地实现委托同一新核心，旧unlock错误owner仍no-op以兼容，新入口明确抛IllegalMonitorStateException。需要跨节点保证的调用者必须明确提供已审核的外部实现/数据库协议；设施不提供Redis、租约续期或fencing。旧SPI Duration只表示等待，持有政策由Adapter独立配置，不沿用0016把同一Duration同时作为租约的示例。

核对来源：JDK25 [ReentrantLock](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)、[Duration](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html)；研究的Striped固定条带会让不同key串行，因此本票保留逐key互斥并在注册表层计数。验收与有限资源消费者由本票报告登记。
