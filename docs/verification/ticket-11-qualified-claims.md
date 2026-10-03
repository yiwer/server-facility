# 票11：独立 claim 执行资格验证

状态：closed。CI12 同源 Windows/Ubuntu 完整门、平台门及归档通过，见[跨平台闭合报告](ticket-11-16-27-ci.md)。Windows最终候选完整all已PASS，精确来源见末节。HTTP迁移12、同库业务命令29、最终组合33分别负责其自身验收，不反向作为11的前置。

## 范围

分支codex/ticket-11，起点09中央c32e72e86de7e4f708e0b423c18f84c955ab683a。ADR0034部分替代0017；新增qualified claim、owner/generation、scope/fingerprint、五类决定和条件完成/释放。结果到期、RELEASED、UNKNOWN不自动重做；PROCESSING租约是明确例外。新旧命名空间隔离、共享严格条目与驻留字节预算；旧SPI保留并弃用，新能力默认不支持。记录CAS不取消旧业务副作用，默认内存不提供持久化、跨进程或事务恢复。

## TDD轨迹

日志位于工作树 `.verification-results/ticket-11`。公共seam为IdempotencyStore、公开值对象、宿主Clock和普通jar消费者，已获任务授权；未读私有状态或mock内部结构。基线反例见协调目录claim-preflight.md/claim-baseline.log（A迟到覆盖B、无claim的complete绕容量、数组别名改正文）。

| 公开行为 | RED→GREEN日志后缀 |
|---|---|
| 首次明确执行资格 | 01-qualified-claim |
| 活跃Processing、lease等于边界换owner | 02-processing-lease |
| 指纹绑定跨lease、scope隔离 | 03-fingerprint-scope |
| A过期/B完成/A迟到屏障拒绝 | 04-late-owner |
| retention从完成开始，到期只失去payload | 05-receipt-retention |
| 满容量和key churn不能删除命令绑定 | 06-key-capacity |
| qualified release终态，重复释放不授权 | 07-release |
| 单条/总字节失败UNKNOWN，只回收过期payload | 08-receipt-budgets |
| 请求长度、null、控制字符、整毫秒与溢出 | 09-input-contract |
| 回拨、等于deadline、deadline溢出 | 10-clock-boundaries |
| Clock故障保持UNKNOWN，旧owner不能伤新owner | 11-clock-failure |
| 永久幂等close，时钟故障也可关闭 | 12-close |
| 旧入口隔离与共享硬预算、禁止凭空complete | 13-legacy-isolation-budget |
| 旧receipt防御性复制与合法状态 | 14-legacy-receipt-ownership |
| 旧key/TTL输入预算 | 15-legacy-inputs |
| token/决定合法性与默认诊断脱敏 | 16-public-values |
| 旧SPI不回落旧方法、默认入口输入契约 | 17-legacy-spi-expansion |

新增API的首步RED包括预期编译失败，其余按实际断言/异常红后实现。green-18-concurrency-ownership增加已有契约的并发/所有权回归；green-19-consumer-package打包普通jar。17个GREEN均真实退出0。补充独立消费者首次启动因PowerShell拆分未引用的`-Dfile.encoding=UTF-8`参数失败，原日志consumer-run-invalid-shell-argument.log保留；修正命令引号后consumer-run.log PASS，未改产品掩盖失败。

## 场景和Q01–Q10

| 要求 | 证据与边界 |
|---|---|
| Q01/FR04/FR09/AC06/AC12 | 正式票、ADR0034状态表、IdempotencyClaimContractTest与CompatibilityContractTest、迁移说明一一对应 |
| Q02 | 第一/重复/异内容/未知和错误owner、scope/key/generation、lease前/等于/后、retention前/等于/后、empty byte[]、null、长度上界、非整/非正/溢出Duration、字节预算上界与超限、Unicode输入、重复close/release/complete |
| Q03 | IdempotencyClaimConcurrencyTest真实线程+公共API；普通jar ClaimConsumer无Spring/SLF4J/Servlet、历史旧SPI二进制实现加载新接口 |
| Q04 | CountDownLatch保证A在B完成后迟到；16线程同key恰一owner、32混合命名空间共享7槽位、32轮complete/release恰一APPLIED；可控Clock注入取时失败、回拨/前跳/overflow。无网络/数据库/文件I/O后端，不声称覆盖其故障 |
| Q05 | 正输入长度、maxEntries、单条/合计payload；256槽位、32768次churn、16线程；64MiB进程中128个已close但仍可达store不保留每个1MiB正文。无自建后台线程/队列/临时文件；调用并发和调用者输入分配由宿主负责 |
| Q06 | verification/claim-consumer/legacy-api/IdempotencyStore.java逐字来自c32e72e；LegacyOnlyStore先对历史接口编译，运行时只复制实现class并加载新jar接口，证明新增default方法二进制迁移与不回落旧执行。HTTPreceipt格式本票不新增 |
| Q07 | ClaimConsumer seed110034，2048轮对32个预先构造的DONE/RELEASED/UNKNOWN终态作迟到complete/release、读副本修改与异内容请求；独立固定正文7/11/23及终态不再Acquired不变量 |
| Q08 | 单测/消费者Windows与JDK25，本票最终runner环境、精确SHA、OS/locale/zone由summary保存；Clock与owner/key诊断无秘密原文；Linux集成CI已由[CI12](ticket-11-16-27-ci.md)补齐 |
| Q09 | 最终clean verify/all保留88%指令/行与75%分支、ArchUnit/依赖门；没有改阈值/排除/删测。旧HTTP一个预置DONE测试先取得旧PROCESSING以符合禁止凭空complete的新约定 |
| Q10 | 代码、迁移、ADR与本票日志同工作树交付；Windows全门和Linux是否齐备见末节。缺证据不closed；12/29/33各自接合另登记 |

普通jar命令在Verify.integration/all执行，45秒上限、`-Xmx64m -XX:ActiveProcessorCount=2`。手动消费者PASS输出：`CLAIM_CONSUMER_PASS seed=110034 rounds=2048 slots=256 churn=32768 workers=16 close-rounds=128 legacy-binary=true framework=absent`。

## 审阅与最终候选

最终候选和审阅发现的修复证据见下两节；首轮all与修复后all分别保留，不能互相替代。

## 审阅修复的独立小堆证据

首轮精确c9343f601cd8051c3fb9fb49c86a51c22d32010e的all在`.verification-results/20261004-040450-202-all/summary.txt`为PASS，1555/0/0/0；它只代表修复前候选，不替代以下修复后的最终全门。原首轮输出完整保留。

root审阅指出receipt.clone抛Error时PROCESSING仍可到期重授；impl03审阅指出HashMap.clear保留扩容table，原payload压力不足以证明table释放。两项均在原c9343f6上以公开API独立JVM复现，随后各自最小修改转绿：

- red-21-clone-allocation.log：32MiB堆、20MiB输入的第二份clone必然OOM，推进Clock后原实现重新Acquired。green-21-clone-allocation.log修复为原Error传播、保留UNKNOWN。
- red-22-close-tables.log：每轮填充2048 qualified + 2048 legacy小条目，close后保持store可达，原实现OOM。green-22-close-tables.log断开Map引用后2048轮完整PASS（32MiB）。
- 同类Clock Error边界red-23-clock-error.log也复现重授；green-23-clock-error.log覆盖complete和release，原Error对象传播且UNKNOWN保留。已知now等于/超过lease仍REJECTED并保持原PROCESSING租约例外，由既有边界测试防止误改。

这三步GREEN用javac直接编译变更核心类并调用公开探针，命令/输出在对应green-21/22/23-compile.log及故障日志；最终仍由clean Maven和普通安装jar复验。ClaimFailureProbe已收入verification/claim-consumer并随Verify.integration/all执行，每个新JVM限制32MiB/2 processors/45秒，OOM只在独立消费者中触发，主测试JVM不制造OOM。

## 最终Windows全门与来源

被测源码：`956081d303243e81557de61e54615f9f5608c374`（修复06db48b + 27产品14dbec37dee57e20e7a854bc708e32bd409a2670）。开始时git工作树干净。命令为：

```powershell
$env:VERIFY_WRONG_JAVA_HOME='C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1'
java verification/Verify.java all
```

完整结果：`.verification-results/20261004-042235-271-all/summary.txt` **RESULT=PASS**，退出0。环境Oracle JDK25.0.4.1+1-LTS-5、Maven Wrapper3.10.0、Windows11 amd64、Asia/Shanghai、zh_CN；本票隔离repository，fresh=false。

- 根库1555测试，0失败、0错误、0跳过；其中新增20个JUnit公开行为测试，另有独立消费者性质/资源/故障场景。架构5项与依赖门通过，无阈值/排除/删测变化。
- 覆盖率：指令22009/23552=93.449%，行4378/4651=94.130%，分支2329/2708=86.004%。原88%/88%/75%门保留。
- 普通jar及全部既有核心/JSON/IO/CSV/限流消费者、真实HTTP/双应用/关闭重建、缺类矩阵、三工具链负控均PASS。
- 新claim普通jar/历史SPI二进制/owner屏障/固定seed/容量与payload释放PASS；三个独立32MiB probe分别输出clone、close-tables、clock-error PASS。它们使用本轮安装jar，不是手工target/classes。
- 同候选27模板47项独立测试0失败/错误/跳过，真实打包HTTP的platform/virtual两模式、拒覆盖与缺coverage负控PASS；保留双方runner调用。
- 已归档库jar SHA256：`0fe1fa4fb2c0058f09a4ee022ff6ea54e51dd6cbe08869e74606e56e10edc6a2`。模板jar SHA256：`5709692aff81c1598f6d0be38604438aae7093f5b5a01479aeda1206c6f18212`。

随后合入中央文档5674f6d4888e5ed2e5d83461be39cc7cf31d7e41，得到550b510facb0c813e93e44939943b499861e8a09。与被测956081d之间仅README/DESIGN/ADR索引及27中央文档，src/pom/verification/templates/Wrapper完全相同；最终交付只再补本票报告与状态，不为同源文档重复全门。

审阅：root的记录一致性审阅发现clone Error窗口；impl03的资源/兼容审阅发现close保留Map表。两项都有实际RED和修复后的安装jar GREEN；同类Clock Error也补齐。其余owner、指纹、租约、永久终态及SPI兼容未报告阻断发现。同源CI12已补齐Windows/Ubuntu完整门与归档，本票closed；[CI报告](ticket-11-16-27-ci.md)单独记录精确来源与证据范围。
