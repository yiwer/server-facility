# 01: 用固定 JDK 25 工具链构建并消费普通库产物

**What to build:** 全新环境通过 Wrapper 完成 release 25 构建，并让一个独立消费应用使用发布 jar 中的公共能力；形成可复用的消费者验证入口。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-01、FR-02、FR-10；AC-01、AC-02、AC-13

## Acceptance criteria

- [x] 固定 Maven Wrapper 及下载校验、唯一 release 25、无 preview；更新字节码相关插件/处理器，产物 class major 为 69。
- [x] 维护全部直接依赖、BOM、processor 与显式插件账本；Boot 3.5 若作中间基线，明确不代表最终平台。
- [x] 独立应用从普通 jar 消费，不能借用库源码或测试 classpath；装配 imports 与配置 metadata 随产物生效。
- [x] 建立快速、集成和资源测试的执行入口与结果归档约定；Windows/Linux 构建记录测试发现、跳过、覆盖率、架构及依赖分析。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：干净本地仓库获取依赖、构建、安装产物、独立 JVM 启动消费者，用户配置实际生效。
- [x] 边界/失败：错误 JDK、损坏下载校验、缺少声明先决条件给出清楚失败，不静默跳过消费者测试。
- [x] 回归：解释原 1196 项测试的发现差异，五条架构规则与原覆盖率门保留；不固定未来测试总数。

## Scope boundary

只建立可复現的 Java 25 中间基线与最小消费者，不在本票完成整个 Boot 4 迁移。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 实施与验证记录（2026-10-03）

- 实现提交 `82d8c6b`；已把当时最新集成线 `bf64995` 合入本票分支，验证 HEAD 为 `7d68039dc0b2b4ebadf5ca82ddd9fafe562789c4`。
- 设计：[ADR-0024](../../../docs/adr/0024-java25-reproducible-consumer-baseline.md) 替代 README Java 21+ 平台说明；ADR-0014/0017 业务契约保留，修复其非 Web 装配提前链接 Servlet 的真实消费者缺陷。
- [版本账本与执行入口](../../../docs/building/java25-baseline.md)；[Windows 执行记录及 Q01–Q10 矩阵](../../../docs/verification/ticket-01-windows.md)。
- Windows 独立消费者验证 configured/override/invalid，199 个 class 均为 69/minor 0；无源码/测试 classpath 帮助；五次有界独立 JVM 启动/关闭通过。
- 原 1196 测试仍全部发现，失败/错误/跳过均 0，架构 5 条按名称保留；指令 93.62%、行 93.34%、分支 86.95%；原覆盖率门与依赖分析均保留，未扩大 ignore。
- 真实 JDK 21、缺 JAVA_HOME、损坏 Wrapper SHA-256 均按预期拒绝，入口未静默跳过；成功验证记录见报告。
- 已提交且启动时干净的 `7d68039` 上执行 `java verification/Verify.java all --fresh`，父进程退出 0、`RESULT=PASS`；最终完整证据目录 `.verification-results/20261003-224849-577-all/`。报告提交只改变文档，不改变已测试实现。
- **跨平台证据已闭合**：集成提交 `e86e1e982286b8c87c2c617bce30b9a69b139168` 的 [GitHub Actions 37131502759](https://github.com/yiwer/server-facility/actions/runs/37131502759) 在 Ubuntu 和 Windows 均成功执行 `all --fresh`，两个报告 artifact 已归档。详见 [CI 记录](../../../docs/verification/ticket-01-ci.md)。Q08/Q10 已闭合；Boot 4/Jackson 3 不属于本票已完成范围。
