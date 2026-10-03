# 15: 让 CSV 方言、流消费和电子表格导出策略可验证

**What to build:** 消费者明确选择严格机器交换或 legacy 方言，有界处理行数据，并按导出用途处理公式风险。

**Blocked by:** None (can start immediately)

**Status:** in-progress

**Traceability:** FR-06、FR-09；AC-10、AC-12

## Acceptance criteria

- [ ] 成熟解析器负责语法，新依赖版本进入账本；旧宽松样本与新严格规则显式区分。
- [ ] 声明字节/行/列/字段长度/错误累计预算，readAll 便利入口也有上限；提供安全行列定位。
- [ ] 机器数据不被隐式改写，spreadsheet 导出政策单独定义；输入/输出流归属明确。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/独立样本：BOM、CRLF/LF、引号换行、双引号转义、最后空字段、空记录。
- [ ] 边界/性质：未闭合引号、尾随垃圾、各预算 N−1/N/N+1；固定种子生成转义/分隔变体并保存失败输入。
- [ ] 接合：公式前缀及空白/控制字符变体，legacy 与严格消费者的兼容金样。
- [ ] 故障/资源：大行数在受限堆处理；consumer 抛错、坏编码、写失败、取消后停止与清理。

## Scope boundary

不做表头 ORM、导入平台或跨格式统一 DSL；不依赖 Excel。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## Implementation record

2026-10-04 领取；独立工作树 ticket-15，分支 codex/ticket-15，基线集成 `d4922df34a96f26cedea4acd9dd78b124fe7c028`。公共 seam 为 CsvUtil 的有界读取/逐行消费与导出入口、借用流的外部效果；沿用户已批准设施公共边界逐项 RED → GREEN。日志从开始保存在 `.verification-results/ticket-15`。ADR0038 将登记对0021中纯JDK手写CSV、无界读取假设的替代，保留其列表形态、无表头ORM与Excel optional理由。
