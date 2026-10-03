# 07 — 本地互斥、实际所有权与必需实现

2026-10-04，in-progress。公共契约局部测试已通过；普通jar资源消费者与完整同源门即将执行，尚不关闭票。ADR0030先登记0016部分替代；公开签名保留，装配默认改为准确本地类型。

| 共同标准 | 契约、测试与证据 |
|---|---|
| Q01 | FR02/05/09、AC08/09/12；公共LocalKeyedMutex/旧SPI、实际Spring装配、Async提交是已批准seam。旧无锁放行与历史键永久耗尽均有真实RED。 |
| Q02 | LocalKeyedMutexContractTest：0/1ns/1day、negative/超限/overflow、null/blank/512与513单元、Unicode、重入、nullable返回、必需callback；旧输入语义变化明示。 |
| Q03 | FacilityLockAutoConfigurationTest：用户bean、非法默认预算、禁用后required注入失败、缺跨节点Adapter失败、两应用关闭；旧SPI原历史源码编译/当前jar运行消费者待执行。 |
| Q04 | LocalKeyedMutexConcurrencyTest：原子3-key/16竞争、不同key并行、同key回收竞争、错误owner/重复释放、中断与引用清理；LifecycleTest关闭不释放action且等待者拒绝进入。 |
| Q05 | 严格maxLocks活动key、512 UTF-16/key、最大1day竞争等待；持有无自动过期，业务/线程预算由宿主负责。64MiB/2CPU/45s普通jar消费者已接入入口，尚待执行。 |
| Q06 | verification/lock-consumer/legacy-api逐字取自0ee9d54基线，SHA清单；不删除旧public接口，全部入口迁移见building/local-locking.md。锁无线协议，不自造分布式实现。 |
| Q07 | 固定seed0x5A17+worker、每模式16线程×2000次、8key，计数与active-owner独立oracle；不靠随机sleep取得互斥。 |
| Q08 | 原始RED/GREEN在.verification-results/ticket-07。OracleJDK25.0.4.1/Windows11/amd64/zh_CN/Asia/Shanghai；完整来源/资源观测待下方补齐。诊断不复制任意key。 |
| Q09 | 不调整原88/88/75及5架构/依赖门。旧允许无锁执行/消息含key的断言按显式新契约替换，其他旧测试保留；完整计数与门待执行。 |
| Q10 | 代码、旧入口逐项迁移和ADR齐备；完整门与所需平台证据完成后再关闭。 |

## TDD与接合记录

1. 缺bean execute会执行action的真实RED→在action之前LockAcquisitionException，单项GREEN。
2. 缺bean tryLock错误true的RED→false；两个execute重载零效果，LockUtil 9项GREEN。
3. 容量1第二个新订单order-1失败的RED→活动引用注册表，1000key轮转及旧互斥/错误owner行为21项GREEN。
4. 负等待错误被当作立即成功的RED→有限0..1day与key预算；22项GREEN。
5. 新LocalKeyedMutex同步scope缺接口的编译RED→同步action/reentry/Error/null返回和必需callback验证，23项GREEN。
6. 新close接口缺席的编译RED→停止准入、保留真实owner、排队者拒绝，24项GREEN。
7. 默认仍暴露DistributedLock的真实装配RED→仅本地类型、保留旧公开直接构造方法但不注册bean，28项GREEN。
8. 扩展既有契约验证：两线程模式每种32000次固定seed回收竞争、原子准入、错误owner、中断、多context/关闭等合计38项GREEN；该轮是已实现行为的回归扩展，无伪造RED。
9. J17真实Async接合6项GREEN：platform/virtual × deadline/cancel(true)/cancel(false)，故意不合作的action在observer终止后仍锁住同key，只有屏障释放后才可执行第二项。回归验证，不把observer状态当业务终止。
10. 外部旧Adapter释放失败覆盖业务AssertionError的RED→保留原对象+suppressed；22项相关GREEN。
11. null callback先尝试获取的RED→必需callback前置拒绝；11项相关GREEN。
12. 异常消息复制65536个Unicode单元的RED→固定安全诊断，24项相关GREEN。构造签名保留；错误消息不作为业务协议。

所有异步屏障有限时限并在finally释放，不靠提前解锁让测试结束。新本地线程owner规则不用于跨线程传递lease；未建立Redis/fencing/分布式模型。应用诊断和实际工作期限的适用边界详见[锁迁移](../building/local-locking.md)。
