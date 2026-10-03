# 本地任务约定

本批采用用户已确认的本地文件 tracker。实施 server-facility 下一代改造时，从 [任务清单](../superpowers/plans/2026-10-03-server-facility-next-tickets.md) 进入对应任务；正式票位于 .scratch/server-facility-next/issues，drafts 仅为历史评审快照。

任务图以每票 Blocked by 为准。ready-for-agent 表示已批准的待办；领取后为 in-progress；实现已合入但仍缺环境或场景证据时为 verification-pending；全部验收及所需证据完成后为 closed。按已验证结果勾选复选框，在票末记录提交和执行证据；测试未执行时写明原因，不能勾选为完成。

本地测试日志优先保存在忽略的 .verification-results 下，避免 Maven clean 删除原始证据；历史 target 日志在清理前先保存。摘要与可复现命令写入票或验证报告；原始日志由 CI artifact 或本地证据目录保存。提交代码时核对变更清单，避免把工具下载、数据库数据目录与临时测试日志提交到源码仓库。

所有实现合入 codex/server-facility-next。代码审查固定基线为 0ee9d547022371ad31f885605e999de17ec22777，规格为本地 PRD v0.2 与 33 张已批准 tickets。票 22/23 的中间迁移只进入该集成线，票 24 是完整平台验证门。

领取任务先读取该票、相关研究和 [测试策略](../superpowers/plans/2026-10-03-server-facility-next-test-strategy.md)。用户已确认 HTTP、业务 Module 与设施公共接口的分层测试入口；TDD 在这些入口验证，不重复索取测试边界批准。涉及 ADR 变化时，在对应票记录明确替代关系。

此 tracker 不要求远端 issue 或 PR；关闭工作以本地票状态 closed 及可核查证据为准。实现任务不包含生产部署或向远端发布制品。
