# 票15：有界 CSV 的行为、兼容和资源证据

2026-10-04。FR-06/FR-09；AC-10/AC-12；决定 [ADR-0038](../adr/0038-bounded-csv-dialects.md) 部分替代0021的手写解析器/无界读取假设，保留列表形态、无表头模型和 Excel optional 理由。

## 实现与验证范围

- 工作树 `E:/GenCode/server-facility-worktrees/ticket-15`，分支 `codex/ticket-15`；起点 `d4922df34a96f26cedea4acd9dd78b124fe7c028`。首个实现 checkpoint `c4f379f`，合入最新 integration `8cfaa6e933b4a098f5d2d934ac9433b0ec18cbee` 后为 `835e8bc5767d3508b3dd3fba3cd9b6e8b4567a88`。
- Commons CSV 1.14.1 required，UTF-8 编解码 REPORT；没有 Excel/POI 的实现依赖，不新增 bean、properties、全局缓存或方言 DSL。
- STRICT 与 LEGACY 的实际规则、外部 IO cause 的诊断权限、流归属与迁移见 ADR/USAGE。STRICT 不声称完整 RFC 验证：裸字段中的引号允许，闭合引号后空白忽略；LEGACY 保留该空白与尾随文本。
- 当前 Windows 局部 CSV 切片已通过，完整同源门在收尾执行；Linux 新代码尚未执行，不借用票24旧代码的 CI 结果。本票完成后，31负责上传→CSV业务接合，33负责最终候选组合，不反向制造实现依赖。

## 资源登记

| 资源 | 政策及可观察保证 |
|---|---|
| 实际 UTF-8 字节 | 正数 maxBytes，含 BOM；输入最多探测 N+1，输出不写超过 N 的字节，拒绝可能保留已写前缀 |
| 行/列/字段 | 正数 maxRows/maxColumns/maxFieldChars；字段为 UTF-16 单元；第 N+1 行错误而非静默截断；列/字段检查在交付 consumer 前 |
| 默认便利预算 | 1 MiB / 10,000 行 / 128 列 / 1,024 字符，旧 read/write 同样有界；大文件显式给预算并用 forEach |
| 库内解析分配 | Reader 按逻辑记录限制 `(2F+3)*C+3` 源字符，默认262,531；超整型配置拒绝。精确字段/列限制是解析后的语义检查，解析前分配由源记录预算约束；不谎称逐字段预分配检测 |
| 缓冲/位置 | 解码器至多8KiB预读；Commons 的 CR peek 至多保留下一记录1字符，源记录预算留出余量。失败后不 drain，不支持继续恢复原流位置 |
| 错误累计 | 固定1；格式、编码、预算、IO错误进Result，callback RuntimeException/UncheckedIOException/Error 原样传播 |
| 所有权 | 借用流永不关闭；Path流由门面关闭，首因与关闭失败suppressed保留；成功写完成UTF-8编码并flush；Path非原子覆盖，失败可能留前缀 |
| 并发/时间/清理 | 无共享可变状态、后台线程、队列、连接、临时文件；每次调用限额，宿主负责并发准入和不协作IO超时；中断检查不清除标记；已取消Path写在开流前拒绝 |

## Q01–Q10 与场景追踪

| 要求 | 公开行为证据 |
|---|---|
| Q01 契约/研究反例 | CsvDialectContractTest、CsvRowConsumptionTest；新方言、逐行消费、旧宽松样本与N+1错误；默认列洪泛真实OOM反例归并资源测试 |
| Q02 正常/边界 | CsvBudgetContractTest、CsvWriteBoundaryTest、CsvApiBoundaryTest；各预算N−1/N/N+1、0/负/最大配置、null、空、UTF-8/BOM/emoji/单独surrogate与逻辑定位 |
| Q03 真实接合 | Path真实文件；CsvConsumer直接依赖普通库jar并排除Tika/POI，独立字节金样、200,000行输入/输出；本票不需HTTP/数据库 |
| Q04 故障/取消 | CsvFailureContractTest用latch确认真实阻塞读已进入，再interrupt并证明worker实际退出、标记保留、借用流未关闭；源半途失败、callback三类异常、输出写/flush故障，CsvOwnedPathTest注入真实开流边界检查首因+close故障 |
| Q05 资源 | CsvResourceContractTest启动64MiB独立JVM，1m/4m行流读写、200轮畸形输入/consumer失败；默认洪泛提前拒绝，保留堆/线程与临时目录有阈值断言 |
| Q06 独立样本 | Python3.14标准csv.writer生成冻结的164记录金样+独立base64字段预期；旧29项CsvUtil测试保持；Java字面量独立输出断言避免仅同实现round-trip |
| Q07 性质/重放 | 固定seed `0x15C5` 的160生成记录+4固定记录，quotes/逗号/CR/LF/emoji/中/BOM/空值；源、预期、SHA和重建命令均提交；默认洪泛最小重复模式 `x,` 已提升永久回归 |
| Q08 环境/诊断 | 下节执行元数据/版本/规模；安全错误只含原因与记录/列；外部IOcause可能敏感，不作为公共错误。Locale/时区不参与字符串CSV转换；没有日期/数值自动转换 |
| Q09 质量门 | 完整runner保留88/88/75覆盖率、5条架构与dependency failOnWarning；保留全部旧测试，不通过skip或改门获得绿色 |
| Q10 可审阅 | 代码、API/迁移、ADR、生成器、金样、TDD与资源原始日志同票保留；Linux未执行保持verification-pending，执行后才能关闭 |

J13本票strict/legacy/编码/流生命周期已自测；J14旧CSV便利语义与导出变化已显式迁移。J12上传清理和CSV副作用的整段业务责任归31；J15最终组合长稳和J16最终候选双平台组合归33，本票先提供资源子进程和真实Path用例。无锁/事务/连接/业务重试，所以相关维度不适用；不把缺失组合记作通过。

## TDD 与局部运行

原始日志均在 `.verification-results/ticket-15`，不会被 Maven clean 删除。每轮先执行红色公共契约，再做最小实现；独立金样/已有绿行为验证另行标记。

| 轮次 | RED及原因 | GREEN |
|---|---|---|
| 01 方言 | red-01：新公开API不存在，编译红 | green-01b：30测试；green-01留下不可达catch的编译失败诊断 |
| 02 行消费 | red-02：新API不存在 | green-02：31测试 |
| 03 预算 | red-03：4行为失败 | green-03：35测试 |
| 04 故障 | red-04：2失败/1错误；初始fixture未把worker错误完成future，修正后用事件判断实际退出 | green-04：39测试 |
| 05 导出政策 | red-05：新API不存在 | green-05：40测试 |
| 06 写/编码 | red-06：1失败/3错误；green-06又发现失败flush后close重复抛同异常造成self-suppression | green-06b：45测试；成功才完成encoder，坏输出不重复flush |
| 07 Path所有权 | red-07：新API不存在；red-07b：2首因被close覆盖的行为失败 | green-07：48测试 |
| 08 独立金样 | 已有行为验证，无产品修改，不虚构RED | golden-08-python-seed：3测试 |
| 09 大规模资源 | 已有流行为验证 | resources-09-low-heap：1测试、heap-09-child原始采样 |
| 10 默认洪泛 | red-10-default-column-flood与red-10-default-child：初始默认10MiB/16384字段在64MiB真实OOM | green-10：6测试；默认收紧为1MiB/1024字段，相同虚拟100MB洪泛提前拒绝 |
| 11 必需参数 | red-11：1失败，Path在必需政策校验前开流 | green-11：3测试 |
| 12 取消开流 | red-12：1失败，已中断写截断原文件 | green-12：7测试，开流前中断检查 |
| 13 切片整合 | Javadoc/Nullable与最新资源复跑，无新增产品行为 | csv-polish-13：56/0/0/0 |
| 14 legacy列表边界 | red-14：大惰性List在预算判断前被完整遍历 | green-14：39/0/0/0；以List.size先拒绝，保持原文件与输出无副作用 |

依赖获取的第一次 PowerShell 未正确引用 Maven `-Dartifact`，`parser-dependency.log` 是命令行错误；有引号的 `parser-dependency-retry.log` 成功，不能把前者当产品RED。

## 独立样本与资源结果

`src/test/resources/csv/generate.py` 使用 CPython3.14 标准库；固定seed `0x15C5`，输出164记录。样本CSV保持二进制换行（局部 `.gitattributes`），否则Git转换会损坏字段内CRLF。

- CSV SHA-256 `1ec58ce2d16219e962a04cb1dd4c7734f8130517c2bad8ab40430bb034d5596e`。
- expected SHA-256 `8656c80014a8a105369e62c36914eaafe14dedf0ed179268cceb2c1eb161d0b8`。
- 重建：`python src/test/resources/csv/generate.py`；执行：`./mvnw.cmd '-Dtest=cn.code91.facility.csv.*Test' test`。

资源子进程预登记 `-Xmx64m -XX:MaxDirectMemorySize=8m -XX:ActiveProcessorCount=2`，90秒进程期限，50,000行预热后1,000,000/4,000,000行，单行19字节，最大76,000,000字节输入/输出，虚拟流不分配输入大小数组/行集。每轮full GC后相对预热存活堆增量≤8MiB，线程≤基线+2，临时目录为空。最近局部结果 baseline5,418,640 bytes；1m4,918,168；4m4,929,128；最终4,932,008；线程7→7；200轮畸形输入/consumer失败通过。它证明登记规模下的上界与回收，不是任意Java堆/任意业务callback的保证；callback自己保存所有行仍由应用承担。

## 最终同源门

待执行并填入精确revision、日志目录、质量计数、普通jar SHA、CSV普通consumer实际依赖图及Windows环境；不会把待执行项写作通过。
