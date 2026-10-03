# ADR-0037: ZIP完整发布与严格目录结果

## Status

Accepted，2026-10-04。公开接口为Zipping/PathIo，不引入通用压缩框架。替代旧io包“best effort/跳过仍成功”文档及RV2-09内部visitor测试约定；验证进度独立记录于票14报告，Accepted不代表未执行的环境已通过。

## Context

原zipFiles在重复basename或读取失败时跳过条目并返回Result.ok；目标文件在打包前就被创建/覆盖，未完成归档也可见。zipDirectory允许输出位于输入树。PathIo把visitFileFailed当零值继续，完整与部分结果无法区分。

## Decision

1. 现有Result<Path>表示严格完整成功，不引入没有消费需求的部分结果类型。任一输入缺失、重复归档名、不可读、遍历/关闭失败均Err。目录大小也只返回完整遍历的总值，遇错返回Err。
2. 默认不覆盖已有目标。输出只能位于输入目录树之外；拒绝符号链接、Windows junction及链接祖先，不跟随根外链接/环。输入与输出命名空间须由应用可信拥有；本API不提供抵御恶意并发重命名的文件系统沙箱，也不承诺输入树快照。
3. 在目标同目录写唯一临时文件，ZIP关闭成功后用createLink发布，再移除stage。目标不暴露未完成ZIP；不支持hardlink时Err，不降级为复制或检查exists后ATOMIC_MOVE。必要父目录可以创建，属于显式残留政策，不冒充整次操作事务。
4. 输入条目数、实际读取bytes、输出bytes（含ZIP元数据）、深度均采用有限正数预算；旧便利方法使用公开默认。每个条目名有有限UTF-8长度政策。实际读取使用固定buffer，并在边界检查中断，保留中断标志；阻塞provider是否立即响应中断由provider决定。
5. 每层资源以try-with-resources关闭，保留最初失败并将关闭/清理异常列为suppressed。发布后移除stage失败时回滚自身目标；如果底层文件系统拒绝回滚/清理，返回失败并保留诊断，不能声称残留永远为零。调用方不得把失败时路径存在当作提交成功。
6. deleteDirectory是有界逐项删除，不是原子事务；失败可已有部分删除，Err保存实际原因。目录遍历失败不能继续删除父目录并覆盖原异常。null或确实不存在保持旧幂等成功，但无权限与不存在须区分。
7. ZIP默认10,000 entries、256 MiB实际输入、256 MiB完整输出、64层；entryName最多1,024 UTF-8 bytes，拒绝冒号、反斜线及dot路径片段，ZIP使用正斜线。PathIo默认10,000后代、256 MiB逻辑文件bytes、64层；根不计entry/depth，目录元数据不计bytes，硬链接每个路径分别统计。所有显式预算必须为正，不暗含unlimited；逻辑byte累计先减剩余额度，避免long溢出。
8. PathIo拒绝链接节点及链接祖先，deleteDirectory拒绝文件系统根；保留处理单个普通文件的兼容行为。统计不提供同一时点快照，文件并发变化可能改变结果或返回失败；删除不预扫描承诺原子性。中断在遍历、删除与ZIP发布边界检查，底层provider不响应中断时不能强制结束；已发布后才到达的取消不能撤销成功。

## Primary API evidence

2026-10-04核读[JDK25 Files](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html)：移动的原子性与覆盖行为受选项/provider约束，检查存在后移动不能构成可移植的不覆盖发布；hardlink是可选能力。核读[ZipOutputStream](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/zip/ZipOutputStream.html)：关闭还要写出最终ZIP元数据，条目写成功不是整个归档完成的证据。

## Verification

实施中的RED/GREEN和原始失败记录存于`.verification-results/ticket-14/`；最终报告必须区分真实故障与provider测试夹具缺陷，覆盖原有协议、独立reader、资源预算、Windows和Linux实际文件系统分支。未执行项不得标通过。
