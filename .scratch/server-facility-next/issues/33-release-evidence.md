# 33: 在同一候选产物上闭合组合、极端场景与发布证据

**What to build:** 维护者能用同一提交和候选制品的证据判断首版是否兑现全部承诺，关键接合与持续故障不会被各票独立通过掩盖。

**Blocked by:** 02 隔离应用上下文的注册、刷新与关闭；03 让异步组合遵守执行器、截止时间和清理契约；07 让互斥操作获得真实保护或明确拒绝；08 兑现本地缓存的 TTL、容量和装配承诺；09 让 API 配额抵抗非法成本、伪身份和键洪泛；10 以 UUID 默认值和显式 SnowId 协议生成标识；12 只对已授权目标操作执行有界 HTTP 重放；14 让 ZIP 与目录操作报告真实完整性；16 让 Excel 读取与导出拥有预算和临时资源保证；17 升级加密政策时保持历史密文可读；18 让核心值与错误可脱离 Spring 稳定组合；19 用显式 DTO 映射迁移一个真实消费流程；20 让时间、数值和模式输入遵守显式政策；31 验证模板的版本升级与五类 agent 任务；32 明确 Cookie 与 HTML 清洗的兼容和安全边界

**Status:** closed

**Traceability:** FR-01–FR-10；AC-01–AC-15

## Acceptance criteria

- [x] 所有前置票自己的测试已随实现完成；本票只补组合回归、持续场景和遗漏闭合，不能接收无归属功能。
- [x] 逐项复核契约—场景—测试—结果矩阵、29 包处置、全部依赖/processor/plugin 账本和消费者兼容；FR-11/AC-16 保持条件扩展。
- [x] Windows/Linux、真实 Servlet、数据库双实例、真实依赖缺席组合，以及测试策略中的高风险接合逐项通过。
- [x] 候选产物执行受控长稳/键洪泛/重复失败取消/文件与流式资源/有界 fuzz，事先记录规模、持续时间和预算，不耗尽宿主。
- [x] 归档 commit、环境、制品校验、测试发现/跳过差异、覆盖率、种子、资源指标、已知限制与回退说明；未验证的安全/一致性/资源阻断项不能放行。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 接合：按联合场景清单复跑身份×错误×幂等×限流，流×重放×取消，JSON×HTTP×多 context，文件×解析×清理。
- [x] 极端：高竞争、key churn、时钟冻结/回拨、进程重启、慢/断流、输入预算及临时资源持续回收。
- [x] 平台：Windows 文件占用/链接权限/设备名与 Linux 差异、字符集/时区/Locale；缺环境明确未执行。
- [x] 门禁：原覆盖率下限保留；不以重试到绿、静默 skip、放宽 ignore 或拼接不同版本局部报告宣布通过。

## Scope boundary

本票完成代表可发布证据齐备，不包含实际发布、部署或删除远端资源；P2 扩展不阻塞首版。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；该发布记录属于历史状态；实际领取与最终验收记录如下。

2026-10-04 领取：独立 LF 工作树 `ticket-33`，基线 `568b16753e9c8756f2aeba1a803eaa0bf0faf221`，所有前置票已关闭。沿已批准的公共设施、独立消费者及真实 HTTP/database seams 补 J05 同时 executor 隔离、同候选历史升级与严格四角色身份门；适用 ADR0056。原有历史 benchmark 与失败记录保留，不以它们替代本候选执行证据。

2026-10-04 闭合：全部前置及本票验收完成，01–33共33张正式票均closed。被测实现为 `2d14f6a79a05e4e693a7d0e4abb83c4890653e45`；恢复后的独立fresh all/platform均退出0、RESULT=PASS且sourceClean=true，正向发现1790/15/132/153全部0失败/错误/跳过，当前runtime历史78→80与三次packaged生命周期通过。先前user-aborted部分单独保留，不拼接成完整结果。

同来源[CI25 run37203623059 attempt1](https://github.com/yiwer/server-facility/actions/runs/37203623059)的Windows/Linux all、platform、qualification、归档及四角色JAR身份比较均success。最终Standards0 findings/最高严重级别无，Spec0 findings/最高严重级别无；原S1/S2/S3与F1/F2已在实际源码独立复核闭合。FR01–10、AC01–15、Q01–10、J01–17及29包/全部依赖账本均映射此候选；FR11/AC16维持条件扩展。

证据入口：[发布报告](../../../docs/verification/ticket-33-release-evidence.md#final-repaired-candidate-qualification)、[契约账本](../../../docs/verification/ticket-33-contract-ledger.md)、[安全摘要](../../../docs/verification/ticket33/final-evidence-summary.json)、[分轴终审原文](../../../docs/verification/ticket-33-final-review.md)。原执行树为ticket-33-review-fixes，实际证据已逐文件核验并保留main `.verification-results/history/ticket-33-review-fixes/`（7112文件/246140501字节），qualified-source receipt保持2d14身份。历史候选、原失败、benchmark与2025项中止保留文件均不重标。后续提交仅闭合文档，不能冒称其SHA已重新执行CI；本票不包含实际发布或部署。
