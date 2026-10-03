# Ticket 01：仓库 CI 跨平台证据

2026-10-03，按用户授权使用本仓库 GitHub Actions。被验证提交：`e86e1e982286b8c87c2c617bce30b9a69b139168`。本记录只关闭 Java 25 中间基线的跨平台证据，不代表后续平台迁移或其他能力票已经验证。

[工作流运行 37131502759](https://github.com/yiwer/server-facility/actions/runs/37131502759) 的最终状态为 `completed/success`。GitHub API 核验了两个 job 的步骤状态以及 artifact 归档元数据：

| 环境 | 验证与归档步骤 | Job |
|---|---|---|
| Ubuntu | 全部成功，无跳过步骤 | [111227368193](https://github.com/yiwer/server-facility/actions/runs/37131502759/job/111227368193) |
| Windows | 全部成功，无跳过步骤 | [111227368383](https://github.com/yiwer/server-facility/actions/runs/37131502759/job/111227368383) |

两端均按已提交工作流安装固定 Temurin JDK 25 及负向 JDK 21，运行 `java verification/Verify.java all --fresh`。该入口执行干净依赖仓库构建、原覆盖率和依赖检查，检查实际测试发现、零失败/错误/跳过及五条架构规则，然后验证独立普通 jar 消费者、用户覆盖、非法配置、五次有界 JVM 启动/关闭、错误 JDK、缺失 JAVA_HOME 和损坏下载校验。任何检查不满足均使进程和 job 失败。

CI 的原始日志、测试发现数量、覆盖率、依赖树、有效 POM、普通 jar 及其摘要由下列 artifact 保存；本记录没有把本机的精确测试计数和覆盖率冒充 Linux 报告内容。

| Artifact | ID | 字节数 | SHA-256 |
|---|---|---:|---|
| java25-ubuntu-latest-e86e1e982286b8c87c2c617bce30b9a69b139168 | 11277366163 | 2237675 | 15cd9148e4ff6b72d64646e3a2ab9d0b896531055548188ab60f422b7945ab2b |
| java25-windows-latest-e86e1e982286b8c87c2c617bce30b9a69b139168 | 11277341014 | 2315613 | 868a56d0b87ec919ca4bc0c6c520106172b82dc64abbd8484280ef57a2195121 |

归档策略为 30 天，当前到期日为 2026-11-02；同一命令和固定工具版本保留在源码，可重放。最终票 33 仍须在最终候选提交重新闭合全部能力的组合证据。

结合 [本机 Windows 明细及 Q01–Q10](ticket-01-windows.md)，票 01 的待验 Q08/Q10 与跨平台项已完成，状态更新为 `closed`。
