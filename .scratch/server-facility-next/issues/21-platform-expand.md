# 21: 为平台替换预先隔离配置入口与消费者金样

**What to build:** 在旧平台仍能验证的状态下，扩展应用拥有的配置入口并保存消费者协议样本，使 Boot/Jackson 替换有明确接缝和回退依据。

**Blocked by:** 01 用固定 JDK 25 工具链构建并消费普通库产物

**Status:** verification-pending

**Traceability:** FR-01、FR-08、FR-09；AC-02、AC-11、AC-12、AC-13

## Acceptance criteria

- [x] 盘点 Boot/Jackson 公共类型、配置/mapper 注册点、装配和测试引擎依赖，将后续改动按真实影响面登记。
- [x] 在旧行为旁先增加可注入/构建期配置路径，迁移一个真实消费者；不先删除旧公共入口。
- [x] 冻结 JSON/HTTP/配置金样和实际依赖清单，复核目标版本；原环境全 verify 保持可运行。
- [x] 为票 22–24 登记专用集成线和合入规则，明确中间状态不发布、不宣称双主版本同 jar 兼容。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：扩展前后同一独立消费者结果一致，默认与用户 customizer 生效。
- [x] 金样：record、泛型、时间/时区、枚举、long ID、BigDecimal、null/Optional、未知字段、非法与尾随数据。
- [x] 边界：两应用不同 mapper 政策、关闭重建、InputStream 字段的上限/所有权和错误通道。

## Scope boundary

这是 expand/prefactor；不得在该票提前切换整套平台或重新设计全部公开 API。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 实施与证据（2026-10-03）

01 的跨平台基线已关闭后正式领取。实现提交 `57df02a`，已同步最新 integration `9fc7005`；被测干净 HEAD `a18b45f233741f9e88ec005bfcdff1fdc71afb47`。ADR-0044 定义应用 Jsons、构建期回调、预算兼容边界及22–24非发布规则；中央INDEX由merger登记。Boot/Jackson主版本未切换，根POM未改，旧公共入口未删除。

Windows 原生 `java verification/Verify.java all` 完整通过，报告 `.verification-results/20261003-233628-520-all`：1265 tests、0失败/错误/跳过、5架构规则，instruction93.8787%、line93.7636%、branch87.2832%，依赖门通过。普通jar消费者、真实JSON/HTTP constructed/injected、两应用关闭重建、5次独立JVM生命周期和三项工具链拒绝均通过。最终 jar SHA-256 `61a10a50d223dd760f073bf7cd915b1761aeb4766f6e9475846012d2b11f6116` 与消费者实载SHA一致。

扩展前 `731598b` 的完整 install 和同一消费者构造路径已通过，固定文本金样来源明确；先观察注入缺bean/构建回调缺方法/预算缺构造器的红灯，再各自转绿。详见 `docs/verification/ticket-21-json-expand.md` 的TDD记录、Q01–Q10/J映射，以及 `docs/building/platform-migration-inventory.md` 的实际影响/依赖/目标可获取性登记。Base64临时解码数组在分配前限定≤N+2固定舍入开销，返回严格≤N；23必须收缩推荐默认无界入口并修复payload/catch边界。

- [x] Windows 原环境完整质量门及新增消费者。
- [ ] 合入后 Linux CI 的新增场景证据；Q08/Q10待闭合，不能把workflow配置或此前01/03的Linux绿色记成本票通过。
