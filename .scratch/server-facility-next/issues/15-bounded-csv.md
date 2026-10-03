# 15: 让 CSV 方言、流消费和电子表格导出策略可验证

**What to build:** 消费者明确选择严格机器交换或 legacy 方言，有界处理行数据，并按导出用途处理公式风险。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-06、FR-09；AC-10、AC-12

## Acceptance criteria

- [x] 成熟解析器负责语法，新依赖版本进入账本；旧宽松样本与新严格规则显式区分。
- [x] 声明字节/行/列/字段长度/错误累计预算，readAll 便利入口也有上限；提供安全行列定位。
- [x] 机器数据不被隐式改写，spreadsheet 导出政策单独定义；输入/输出流归属明确。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/独立样本：BOM、CRLF/LF、引号换行、双引号转义、最后空字段、空记录。
- [x] 边界/性质：未闭合引号、尾随垃圾、各预算 N−1/N/N+1；固定种子生成转义/分隔变体并保存失败输入。
- [x] 接合：公式前缀及空白/控制字符变体，legacy 与严格消费者的兼容金样。
- [x] 故障/资源：大行数在受限堆处理；consumer 抛错、坏编码、写失败、取消后停止与清理。

## Scope boundary

不做表头 ORM、导入平台或跨格式统一 DSL；不依赖 Excel。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## Implementation record

2026-10-04 领取；独立工作树 ticket-15，分支 codex/ticket-15，基线集成 `d4922df34a96f26cedea4acd9dd78b124fe7c028`。公共 seam 为 CsvUtil 的有界读取/逐行消费与导出入口、借用流的外部效果；沿用户已批准设施公共边界逐项 RED → GREEN。日志从开始保存在 `.verification-results/ticket-15`。ADR0038 将登记对0021中纯JDK手写CSV、无界读取假设的替代，保留其列表形态、无表头ORM与Excel optional理由。


## Verification record

Windows精确源码 `e1f078a6507d5a3f2dee00edd7ecfd4d83f45566`（含09中央c32e72e）运行 `java verification/Verify.java all --fresh`，`.verification-results/20261004-030730-029-all/summary.txt RESULT=PASS`：1506/0/0/0、5架构/原覆盖率与依赖门、全部普通jar/平台矩阵/资源周期/3负控。CSV新增28项，旧29项保留；Commons CSV1.14.1与无Tika/POI必需传递图已实际消费。

64MiB进程4m行/76MB流读写、200失败后存活堆约4.88MB，线程7→7；保留初始默认列洪泛OOM的RED及修正后结果。独立CPython3.14金样/seed/SHA、每轮TDD、预算与异常所有权见 [完整报告](../../../docs/verification/ticket-15-bounded-csv.md)（仓库路径：docs/verification/ticket-15-bounded-csv.md）。

未完成：本票新增代码Linux证据待root集成CI，因此Q08/Q10总体勾选仍未闭合。14/15若在此次分支验证后合并，不能将本报告当合并后同源结果。31上传到CSV业务接合、33最终候选组合独立登记，不反向构成本票实现依赖。后续提交仅票/报告/账本，不改变被测源码。


**2026-10-04 平台闭合**：`c2f0f6b`在Windows与Ubuntu实际完成all --fresh、platform --fresh及归档，全部success；见[同源CI证据](../../../docs/verification/ticket-09-14-15-ci.md)。本票closed。历史阶段状态与局部失败保留，不混用各环境数值。
