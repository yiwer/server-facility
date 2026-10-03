# 票18：核心值契约验证

日期2026-10-04。状态：本票已关闭。局部 TDD、历史制品兼容及同步票23后的完整目标平台 integration 均已通过；各阶段证据分别记录，不把选定源码编译代替普通新 jar 验证。

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
| Q03 | 旧普通jar与新目标普通jar的独立无框架消费者均已通过；新jar由统一 integration runner 构建、安装并隔离运行 |
| Q04 | 公共回调的IllegalStateException/Error、适配器InterruptedException；没有I/O/时钟/异步资源，因此不构造数据库或调度测试 |
| Q05 | 集合O(n)预算归消费者，溢出在物化前拒绝；无全局缓存/线程/连接/临时资源；消费者64MiB，固定有限512输入 |
| Q06 | 历史jar独立编译/执行同一保留契约样本；公开入口无删除、版本号不变；新规则迁移明确 |
| Q07 | seed180041，512组identity/association/swap性质；失败输出迭代索引，可同命令重放 |
| Q08 | 原始命令、编译/运行日志分轮保存；固定UTF8与seed；没有生产数据或秘密 |
| Q09 | 保留原有215核心JUnit用例，新增4回归，局部219已过；全库1339/0/0/0与原覆盖率/架构/依赖门均通过，未降低或忽略 |
| Q10 | 源码、ADR、迁移、消费者与原始结果齐备；本票完整目标平台门通过，closed；最终候选平台组合仍按24/33责任重验 |

## 完整目标平台结果

已同步正式集成 `7e168199a812fba6396540922241036d85767d8e`，被测源码 `5c29047b4b25cce27e67f752db64b29380cad984`。执行 `java verification/Verify.java integration`，证据目录 `.verification-results/20261004-012930-659-integration`，summary 为 `RESULT=PASS`。Windows11/amd64、Oracle JDK25.0.4.1、Asia/Shanghai/zh_CN、Boot4.1.1/Jackson3.1.5/JUnit6.0.3；本工作树隔离 Maven repository 复用缓存（fresh=false），没有使用其他树的 SNAPSHOT。

完整库 **1339 tests / 0 failures / 0 errors / 0 skipped**，其中原 5 条 ArchUnit；dependency analyze 和原覆盖率门全部通过。相对票23的1335净增4个公共边界回归，没有删测或 skip。

| JaCoCo bundle | Covered / Total | 实测 | 门槛 |
|---|---:|---:|---:|
| INSTRUCTION | 17418 / 18770 | 92.7970% | 88% |
| LINE | 3571 / 3826 | 93.3351% | 88% |
| BRANCH | 1760 / 2047 | 85.9795% | 75% |

普通 jar SHA256 `a15ffb0a91db6ac83f8c96ef7d8796621ac00621e9e9d32cb647eaa5ad604caa`。新的 CoreConsumer 由该隔离仓库 jar 通过 javac 编译，运行 classpath 仅自身 classes + 同一个普通 jar，64MiB/45秒进程完成，`seed=180041 iterations=512 framework=absent`；编译和执行均 exit=0。普通消费者 configured/override/invalid、JSON 真 HTTP 金样和双应用/关闭重建、损坏下载/缺失 JAVA_HOME/真实错误 JDK 三项负控也全部通过。

本票纯核心值没有文件系统/网络/数据库平台政策；Windows完整证据满足本票关闭条件，未声称 Linux 已执行。Windows/Linux 统一平台矩阵与最后候选组合由24/33按实际结果登记，不以这些后续组合反向阻塞本票。
