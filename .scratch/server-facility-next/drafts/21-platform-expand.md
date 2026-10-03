# 21: 为平台替换预先隔离配置入口与消费者金样

**What to build:** 在旧平台仍能验证的状态下，扩展应用拥有的配置入口并保存消费者协议样本，使 Boot/Jackson 替换有明确接缝和回退依据。

**Blocked by:** 01 用固定 JDK 25 工具链构建并消费普通库产物

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-01、FR-08、FR-09；AC-02、AC-11、AC-12、AC-13

## Acceptance criteria

- [ ] 盘点 Boot/Jackson 公共类型、配置/mapper 注册点、装配和测试引擎依赖，将后续改动按真实影响面登记。
- [ ] 在旧行为旁先增加可注入/构建期配置路径，迁移一个真实消费者；不先删除旧公共入口。
- [ ] 冻结 JSON/HTTP/配置金样和实际依赖清单，复核目标版本；原环境全 verify 保持可运行。
- [ ] 为票 22–24 登记专用集成线和合入规则，明确中间状态不发布、不宣称双主版本同 jar 兼容。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：扩展前后同一独立消费者结果一致，默认与用户 customizer 生效。
- [ ] 金样：record、泛型、时间/时区、枚举、long ID、BigDecimal、null/Optional、未知字段、非法与尾随数据。
- [ ] 边界：两应用不同 mapper 政策、关闭重建、InputStream 字段的上限/所有权和错误通道。

## Scope boundary

这是 expand/prefactor；不得在该票提前切换整套平台或重新设计全部公开 API。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。

