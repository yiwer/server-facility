# 14: 让 ZIP 与目录操作报告真实完整性

**What to build:** 打包和目录统计能区分完整成功、声明的部分结果与失败；损坏或未完成的产物不会被当作成功文件消费。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-06、FR-09；AC-10、AC-12

## Acceptance criteria

- [x] 明确覆盖、输出位于输入树、重复名称、链接和部分失败政策。
- [x] 条目数、读写、取消与输出资源有预算；清理异常不遮蔽原始失败。
- [x] 成功前的目标可见性及平台保证公开，不用错误忽略把目录不可读记成完整零值。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/互操作：空目录、递归树、Unicode、大文件，独立 ZIP reader 验证条目和内容。
- [x] 边界：输出位于输入树、既有目标、重复 basename、链接环/根外链接、预算 N−1/N/N+1。
- [x] 故障：遍历时消失、权限拒绝、读写失败、取消、文件占用与清理失败；检查完整性结果和残留。

## Scope boundary

不依赖上传、CSV 或 Excel；不添加未需要的压缩格式框架。

## Implementation record

2026-10-04：root从integration `d4922df`建立独立分支/worktree `codex/ticket-14`。公开seam为Zipping与PathIo；先复现完整成功误报、覆盖与目录失败，再实现明确的严格结果和有限预算。采用ADR-0037登记政策；不把旧实现静默跳过的行为继续称为完整成功。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## Windows implementation verification

被测58a1e83319a094224edc3d71fdeb2c34ef36d304已同步09中央c32e72e。2026-10-04完整integration：1507/0/0/0、5架构/原覆盖率/依赖、所有消费者/矩阵/真实3负控PASS；独立64MiB进程读取128MiB归档、seed140037/64个树、200失败资源稳态通过。详见[票14报告](../../../docs/verification/ticket-14-io-integrity.md)、ADR0037。原始证据.verification-results/20261004-030710-501-integration/与ticket-14/。仅Linux链接/权限分支待集成同源码CI；保留Q01–Q10及涉及平台的场景复选框未勾选，不能借历史CI关闭。


**2026-10-04 平台闭合**：`c2f0f6b`在Windows与Ubuntu实际完成all --fresh、platform --fresh及归档，全部success；见[同源CI证据](../../../docs/verification/ticket-09-14-15-ci.md)。本票closed。历史阶段状态与局部失败保留，不混用各环境数值。
