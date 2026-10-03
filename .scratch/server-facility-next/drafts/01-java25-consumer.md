# 01: 用固定 JDK 25 工具链构建并消费普通库产物

**What to build:** 全新环境通过 Wrapper 完成 release 25 构建，并让一个独立消费应用使用发布 jar 中的公共能力；形成可复用的消费者验证入口。

**Blocked by:** None (can start immediately)

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-01、FR-02、FR-10；AC-01、AC-02、AC-13

## Acceptance criteria

- [ ] 固定 Maven Wrapper 及下载校验、唯一 release 25、无 preview；更新字节码相关插件/处理器，产物 class major 为 69。
- [ ] 维护全部直接依赖、BOM、processor 与显式插件账本；Boot 3.5 若作中间基线，明确不代表最终平台。
- [ ] 独立应用从普通 jar 消费，不能借用库源码或测试 classpath；装配 imports 与配置 metadata 随产物生效。
- [ ] 建立快速、集成和资源测试的执行入口与结果归档约定；Windows/Linux 构建记录测试发现、跳过、覆盖率、架构及依赖分析。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：干净本地仓库获取依赖、构建、安装产物、独立 JVM 启动消费者，用户配置实际生效。
- [ ] 边界/失败：错误 JDK、损坏下载校验、缺少声明先决条件给出清楚失败，不静默跳过消费者测试。
- [ ] 回归：解释原 1196 项测试的发现差异，五条架构规则与原覆盖率门保留；不固定未来测试总数。

## Scope boundary

只建立可复現的 Java 25 中间基线与最小消费者，不在本票完成整个 Boot 4 迁移。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。

