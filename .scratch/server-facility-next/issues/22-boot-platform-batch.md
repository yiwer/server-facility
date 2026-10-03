# 22: 迁移 Boot 4、Spring 技术模块与测试工具链

**What to build:** 目标平台依赖、Boot 技术模块、自动装配归属和 JUnit/ArchUnit 工具链在同一迁移线上切换，形成 Jackson 后续批次可消费的明确基线。

**Blocked by:** 21 为平台替换预先隔离配置入口与消费者金样

**Status:** verification-pending

**Traceability:** FR-01、FR-02、FR-10；AC-01、AC-02、AC-09、AC-13

**Integration rule:** 宽迁移批次；仅进入票 21 约定的集成线，票 24 才可合入主线。

## Acceptance criteria

- [x] 复核并锁定 Boot BOM 与工具链；处理技术拆包与 processor，不盲目全局替换包名。
- [x] 迁移 JUnit/ArchUnit 与插件 classfile 支持，用独立工具/引擎探针验证目标工具链；主工程完整测试发现、普通 jar 与配置 metadata 的实际验证归票 24。
- [x] 把尚待票 23 的 Jackson 公共类型编译问题列成精确清单，禁止新增无关错误或以跳测试掩盖。
- [x] 本票成果由目标依赖解析、可独立子集测试和影响清单验证；完整运行保证仅在票 24 给出。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 接合：实际目标依赖图和技术模块归属、test provider/engine 发现探针。
- [x] 负向：不需要能力的缺类组合不提前承诺通过，受影响组合逐项交给票 24 闭合。
- [x] 迁移记录：与旧基线比较测试发现/编译错误，说明每个预期暂时失败的拥有者与关闭条件。

## Scope boundary

这是无法独立全绿时的明确宽迁移例外；不得直接合入主线或作为可发布版本。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 实施与证据（2026-10-04）

- 已满足21 closed前置，从5428982正式开始。实现56cff5e；04模块接合/探针0f15f13；05接合7e4215b。最新integration2304a57已通过fac1a33合入本树，src/POM/verification相对被测7e4215b不变。
- Boot4.1.1 / Framework7.0.9 / Jackson3.1.5 / annotations2.21 / JUnit6.0.3 / ArchUnitJUnit6 1.5.1 实际解析成功；processor同步，Servlet/Tomcat/MVC技术类型逐项归位，保留05显式junit-jupiter-params和测试容器边界。ADR0045部分替代0024；INDEX由集成者维护。
- `0f15f13` 上 `java verification/Verify.java platform --fresh` 成功：5项正向、两个引擎各自1项故意失败且exit1；14种实际模块类型、69.0 classfile、Lombok/config processor、JaCoCo和dependency analyzer通过。报告 `.verification-results/20261004-002422-214-platform/`，包含独立输入、SHA与完整原始结果。
- `7e4215b` 上真实根 `clean verify` 失败于68条Jackson主编译错误，逐条清单 `docs/verification/ticket-22-jackson-diagnostics.md`；全部归23，无跳测试、无Jackson2兼容fallback、无质量门下调。根effective POM/tree/resolve成功；日志 `.verification-results/ticket-22/final-7e4215b/`。05旧平台1323项与目标尚未执行的主库测试明确区分。
- Q01–Q10、TDD红绿、环境和完整交接见 `docs/verification/ticket-22-platform.md`；技术账本和24缺类/Servlet6.1新重载责任见 `docs/building/boot4-platform.md` 与 `platform-migration-inventory.md`。
- `verification-pending` 仅待本票非发布集成复核。本票未声称Linux/主库全门/消费者/metadata已通过；23清空类型缺口，24恢复同产物完整平台保证。不得直接进入master或发布。
