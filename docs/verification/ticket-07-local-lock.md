# 07 — 本地互斥、实际所有权与必需实现

2026-10-04，verification-pending。冻结 `df7f7889d558d37af7a2daec0a7432862de670d1` 的 Windows 完整门已通过；合入后真实 Linux CI 尚待完成。ADR0030部分替代0016；公开签名保留，装配默认改为准确本地类型。

| 共同标准 | 契约、测试与证据 |
|---|---|
| Q01 | FR02/05/09、AC08/09/12；公共LocalKeyedMutex/旧SPI、实际Spring装配、Async提交是已批准seam。旧无锁放行与历史键永久耗尽均有真实RED。 |
| Q02 | LocalKeyedMutexContractTest：0/1ns/1day、negative/超限/overflow、null/blank/512与513单元、Unicode、重入、nullable返回、必需callback；旧输入语义变化明示。 |
| Q03 | FacilityLockAutoConfigurationTest：用户bean、非法默认预算、禁用后required注入失败、缺跨节点Adapter失败、两应用关闭；旧SPI原历史源码编译/当前jar运行消费者通过。 |
| Q04 | LocalKeyedMutexConcurrencyTest：原子3-key/16竞争、不同key并行、同key回收竞争、错误owner/重复释放、中断与引用清理；LifecycleTest关闭不释放action且等待者拒绝进入。 |
| Q05 | 严格maxLocks活动key、512 UTF-16/key、最大1day竞争等待；持有无自动过期，业务/线程预算由宿主负责。64MiB/2CPU/45s普通jar消费者已实际通过，见下方数据。 |
| Q06 | verification/lock-consumer/legacy-api逐字取自0ee9d54基线，SHA清单；不删除旧public接口，全部入口迁移见building/local-locking.md。锁无线协议，不自造分布式实现。 |
| Q07 | 固定seed0x5A17+worker、每模式16线程×2000次、8key，计数与active-owner独立oracle；不靠随机sleep取得互斥。 |
| Q08 | 原始RED/GREEN在.verification-results/ticket-07。OracleJDK25.0.4.1/Windows11/amd64/zh_CN/Asia/Shanghai；冻结来源与资源观测见下方。诊断不复制任意key；Linux待CI。 |
| Q09 | 不调整原88/88/75及5架构/依赖门。旧允许无锁执行/消息含key的断言按显式新契约替换，其他旧测试保留；1637项及完整门通过，见下方。 |
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

## 冻结候选的 Windows 完整执行

命令：设置 JAVA_HOME 为 Oracle JDK25.0.4.1，VERIFY_WRONG_JAVA_HOME 为真实 JDK21 后执行 `java verification/Verify.java all --fresh`。原始证据 `.verification-results/20261004-064526-873-all`；01-revision=df7f788，02-working-tree为空，97个命令完成且 `RESULT=PASS`。库1637/0/0/0（tests/failures/errors/skipped），5条架构门；指令24727/26639、行4854/5149、分支2599/3066，原88/88/75门未变。

普通库jar SHA256 `161b43268da1ccb41f0c998ee9d36b12c05744d77c4bce3fd34fda5f7d575c0e`。原SPI编译/当前jar运行消费者PASS；原始两文件与基线0ee9d54的Git blobs逐字一致，历史classes不在运行classpath。新消费者5周期共250000新key、10000非法输入、16worker、platform/virtual均通过，64MiB堆/2CPU/45秒进程预算下耗时453ms；预热后GC观察基线1893568字节，随后最大1622952字节。它是这次有限进程观测，不承诺宿主任意GC或业务等待内存。

完整门同时执行其他普通jar消费者、真实Spring装配图、仓库外Unicode路径的聚合应用与安全模板、打包HTTP、缺coverage/错误JDK/校验和负控和5次应用生命周期。此冻结来源尚不包含28 PostgreSQL模块；之后合入f4837ee以消除分支差异，新增组合必须以实际CI结果为准，不把上述旧来源数据拼接为新来源通过。

impl_01的独立短审未发现锁边界问题，记录位于工作树外coordination/ticket-07-premerge-review.md；它不替代票33后的最终Standards/Spec双审。
