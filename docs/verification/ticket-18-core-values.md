# 票18：核心值契约验证

日期2026-10-04。状态：局部核心契约与旧制品兼容已验证，集成线票23完成前，完整目标平台与普通新 jar 验证待执行。本报告不会把选定源码编译写成主库完整构建通过。

## 环境与入口

- worktree `E:\GenCode\server-facility-worktrees\ticket-18`，分支 `codex/ticket-18`，初始集成提交 `042006daaed1451476d355d1a3199eb249202825`。
- Windows，Oracle JDK25.0.4.1，UTF-8；主构建目标仍为 Boot4.1.1 / Jackson3.1.5，未改变任何原门。
- 局部源码编译只选 `result/error/common/structure`；注解和 Lombok 仅在 javac 编译 classpath / processorpath。消费者子进程只有编译后的核心类与消费者，`-Xmx64m`，主动检查 Spring/SLF4J/Jackson/Lombok/annotation 类型均缺席。
- 日志在 `.verification-results/ticket-18`。临时执行脚本也保存在该目录，最终普通制品入口提交在 `verification/Verify.java` 和 `verification/core-consumer/CoreConsumer.java`。

## TDD 与结果

| 阶段 | RED | GREEN / 结果 |
|---|---|---|
| 01 必需回调 | 空数据与 null mapper 未抛 NPE | 在所有 mapper/extractor 公共入口检查，完整消费者 PASS |
| 02 容量算术 | 1,610,612,735 输入溢出成负值 | N边界与最大int饱和，PASS |
| 03 join 总长度 | 虚拟大 List 长度加和回绕为正1，开始物化输入 | Math.addExact 在分配与读取前拒绝，PASS；虚拟 List 不分配大量内存 |
| 04 typed error argument | null 参数值掩盖必需 Class 为 null | 先验证 Class，仍允许 null 数据，PASS |
| 05/06 保留语义 | 既有行为的兼容测试，本身不虚构 RED | 领域订单流程、短路、惰性、空值、Swap、程序异常/Error/中断、数组别名、Tuple/Triple有名输出及512组性质 PASS |
| 07 历史普通 jar | 使用下述历史jar，只执行保留语义，不把新收紧行为伪称旧兼容 | 只用普通jar编译/运行同一消费者 `legacy` 模式，PASS |
| 08 核心 JUnit6 | 只编译上述四包测试，不涉及全库迁移缺口 | 219发现/219成功/0失败/0错误/0跳过/0中止，44容器全成功 |

初次01执行因 PowerShell 没给 `-Dfile.encoding=UTF-8` 加引号导致 Java 启动失败，保留日志并修正启动器后才取得真实行为 RED。05消费者初稿误写不存在的 WrappedError.getFullCode，编译失败保留，修正为其 errorType.getFullCode 后才取得 PASS。这两项是测试驱动程序问题，不计为产品 RED。

历史样本来自票21普通制品，SHA256 `61a10a50d223dd760f073bf7cd915b1761aeb4766f6e9475846012d2b11f6116`，已存入主仓库 `.verification-results/history/ticket-21/target/server-facility-0.1.0-SNAPSHOT.jar`。消费者兼容模式覆盖 Result/Swap/WrappedError、NullSafe/Collects、Tuple/Triple；四项新校验修复明确不纳入旧行为保持承诺。

## Q01–Q10 对应

| 标准 | 本票证据 / 边界 |
|---|---|
| Q01 | ADR0041、core-value-contracts公共处置台账、CoreConsumer实际领域流程与四项回归 |
| Q02 | null/empty/default/重复/不可变集合、容量边界与int最大值、源数组与可变元素观察 |
| Q03 | 旧普通jar实际无框架消费者已过；新普通jar待目标构建恢复后用统一runner执行 |
| Q04 | 公共回调的IllegalStateException/Error、适配器InterruptedException；没有I/O/时钟/异步资源，因此不构造数据库或调度测试 |
| Q05 | 集合O(n)预算归消费者，溢出在物化前拒绝；无全局缓存/线程/连接/临时资源；消费者64MiB，固定有限512输入 |
| Q06 | 历史jar独立编译/执行同一保留契约样本；公开入口无删除、版本号不变；新规则迁移明确 |
| Q07 | seed180041，512组identity/association/swap性质；失败输出迭代索引，可同命令重放 |
| Q08 | 原始命令、编译/运行日志分轮保存；固定UTF8与seed；没有生产数据或秘密 |
| Q09 | 保留原有215核心JUnit用例，新增4回归，局部219已过；全库覆盖率/架构/依赖门待恢复后执行，未降低或忽略 |
| Q10 | 源码、ADR、迁移、消费者与原始结果齐备；完整目标平台门待验证，票仍in-progress |

## 完整目标平台结果

待票23恢复构建后同步最新集成线，执行 `java verification/Verify.java integration`，记录精确源码提交、普通 jar SHA、全部发现测试、覆盖率和架构/依赖结果，再关闭本票。Windows/Linux统一CI及最后候选组合另按实际运行记录，不能用历史版本局部证据拼接。
