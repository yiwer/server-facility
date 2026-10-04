# 20: 让时间、数值和模式输入遵守显式政策

**What to build:** 代表性的日期与容量输入流程使用明确 Clock、时区、舍入和解析规则；继续支持的格式/模式工具不持有无界缓存。

**Blocked by:** None (can start immediately)

**Status:** in-progress

**Traceability:** FR-05、FR-08、FR-09；AC-08、AC-11、AC-12

## Acceptance criteria

- [ ] 选择真实业务输入流程迁移到 JDK 或有价值的有限策略，停止浅包装扩张。
- [ ] Clock、ZoneId、Locale、严格/legacy 日期及舍入语义公开；无界格式/正则缓存退出。
- [ ] 预算数值拒绝溢出和非有限值，旧 ≤0 表示无限制的约定如有改变须显式迁移。
- [ ] 不把正则缓存有界等同于任意不可信正则有执行时限；不可信模式应限制或拒绝。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/边界：闰年/月末/无效日期、边界年、UTC/上海/DST 跳变重叠；精确小数、单位及舍入边界。
- [ ] 非法输入：NaN/Infinity、溢出、零/负值、非法模式与组号、形状合法但语义无效日期。
- [ ] 性质/资源：固定时钟和固定种子数值/格式输入；格式与模式键轮转后保留规模有界。
- [ ] 兼容：Locale、旧宽松日期、集合/显示约定与新明确入口分别有金样。

## Scope boundary

范围限定为代表用例及已有工具契约；不创建通用规则引擎或声称任意 regex 可安全超时。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


## Implementation record

2026-10-04，领取独立ticket-20/codex/ticket-20，基于集成1d6377d。19产品修复已完成，等待28修复接合后最终验证；本票在该等待窗口独立实施，不覆盖19的验收责任。按用户已批准的DateUtil/NumberFormat/NumberUnits/Patterns公开入口及独立应用Module做逐项RED→GREEN，公共边界不重复索取批准。代表流程为显式Clock/ZoneId/Locale的定时导出输入；严格固定日期和正数容量是业务政策，旧SMART/默认环境/显示语义单独保留金样。无界动态缓存退出，不将Java正则误称可中断的不可信输入引擎。ADR0043，原始证据从开始存.verification-results/ticket-20。
