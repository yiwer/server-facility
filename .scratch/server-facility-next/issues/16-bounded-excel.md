# 16: 让 Excel 读取与导出拥有预算和临时资源保证

**What to build:** 选用 Excel 能力的独立消费者获得完整依赖、稳定格式政策和受控资源使用，稀疏或畸形输入不会引发无界分配。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-01、FR-02、FR-06、FR-09；AC-02、AC-10、AC-12

## Acceptance criteria

- [x] POI 升级、必要格式引擎依赖与消费者回归共同交付，版本纳入账本。
- [x] 声明字节、行、列、单元格、错误和临时空间预算；支持的大小路径清楚，不默认全量 List。
- [x] Locale、日期/数值展示、公式读取/计算政策与文本单元格区分；异常及关闭责任明确。
- [x] 继续支持的 XLS 读取不得在迁移到 XSSF/SAX 时消失；可以为 legacy XLS 声明小文件上限，不要求新增大 XLS 流式引擎。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。Windows完整门与映射及CI12两平台证据已完成。

## Required scenarios

- [x] 正常/独立样本：XLS/HSSF 历史文件与 XLSX 普通表，Path/借用流入口、空 sheet、稀疏极末行、长字符串、日期、不同数值格式。
- [x] 边界：每项预算前/等于/后、ZIP 高膨胀率/畸形容器、缺公式缓存/未知函数；=1+2 按文本导出。
- [x] 接合：真实依赖图具备/缺少 XLS 与 XLSX 格式引擎、用户 Locale、独立工具读取导出结果。
- [x] 故障/资源：逐行大输入/输出、读写失败、consumer 抛错、取消、文件占用与关闭失败；验证实际 POI 版本的临时文件清理。Windows本地及CI12两平台完整门均通过，含平台文件清理分支。

## Scope boundary

不建设报表平台或反射 POJO 映射；不依赖 CSV/上传票。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


## Implementation record

2026-10-04 领取；工作树ticket-16、分支codex/ticket-16，基线integration `c2f0f6b4118a3a059993ef53f2d62f547151c560`（含14/15，批次CI由root负责）。ADR0039登记0021中Excel无界usermodel/公式计算/临时清理/双类探针假设的适用替代，保留裸列表、无表头ORM、POI按需消费理由。用户已批准设施公共API和全面测试，在ExcelUtil/借用流/真实Path/普通jar图逐项RED→GREEN，不重复索取边界许可；原始日志从开始保存 `.verification-results/ticket-16`。


## Verification handoff

Windows完整 `all --fresh` 在源码 `99ae71adabb6ada6c3a346ea142c7bf666b7a25d` 通过，1600/0/0/0、5架构/原覆盖率/依赖门、92命令、全部普通jar/平台/模板/负控全绿。原始目录 `.verification-results/20261004-053114-083-all`，报告 [ticket16](../../../docs/verification/ticket-16-bounded-excel.md) 记录逐轮RED/GREEN、独立样本SHA、4种真实格式依赖图、64 MiB400,000行/200失败与真实XML OOM修复。openpyxl3.1.5对最终普通jar导出独立读取通过。

ADR0039部分替代0021；POI5.5.1/Compress1.28.0和实际消费者传递版本已登记。最终同步integration49b3148仅新增失败诊断输出长度和25报告，未变更被测产品。随后CI12对含本票的同源候选实际执行Windows/Ubuntu完整门、平台门及归档并全部成功，本票closed，见 [CI验收报告](../../../docs/verification/ticket-11-16-27-ci.md)。31/33专属最终业务/发布重验独立承担，不反向创造本票前置或循环。
