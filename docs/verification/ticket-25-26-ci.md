# 25／26：外部HTTP与应用观测跨平台闭合

2026-10-04，集成提交 `90098104ec8bb0edcc3eb93db848d4f2f0f51207` 通过[现有 GitHub Actions CI13](https://github.com/yiwer/server-facility/actions/runs/37157891623)。Windows、Ubuntu实际执行完整 `all --fresh`、独立平台控制与归档，均为completed/success；25、26据此闭合Q08/Q10并关闭。运行从2026-10-03T22:16:52Z至22:28:15Z。

| 环境 | Job | all／platform／归档 |
|---|---|---|
| verify (windows-latest) | [111305028022](https://github.com/yiwer/server-facility/actions/runs/37157891623/job/111305028022) | 全部success |
| verify (ubuntu-latest) | [111305028159](https://github.com/yiwer/server-facility/actions/runs/37157891623/job/111305028159) | 全部success |

该同源候选包含25的双上游配置、凭据、JSON、观测、响应预算和显式重试政策，以及库存数组中null元素的协议拒绝修复；26的宿主消息源、安全SLF4J事件、标准W3C/Brave SERVER span与真实异步scope、多应用不同采样及关闭隔离一并运行。库、独立聚合应用与仓库外生成模板保持原88/88/75覆盖率门、架构和依赖门；两应用缺覆盖率数据的负控也由完整入口执行。先前Windows Unicode原生agent、Java参数和短目录别名失败及其修复证据继续保留，不回写为历史成功。

局部TDD和本机精确数量见[25报告](ticket-25-outbound-http.md)、[26报告](ticket-26-host-observability.md)。25原完整来源a9c6400、26原完整来源72a37b6的数字分别保留；当前CI是合入后的独立执行，不能把这些本地计数写成CI报告中的精确观测。

| Artifact ID | 名称 | 字节数 | 整体SHA256 | 到期UTC |
|---|---|---:|---|---|
| 11287305000 | java25-windows-latest-90098104ec8bb0edcc3eb93db848d4f2f0f51207 | 31571569 | 84fe3329532ab5e76c1482f64c5d8e4446f2d10b0f7a5e25284e5b3bff173410 | 2026-11-02T22:28:06Z |
| 11287300014 | java25-ubuntu-latest-90098104ec8bb0edcc3eb93db848d4f2f0f51207 | 31405736 | d8720fbb6daa8ae5149fcbd763a70a8e854a81f7bfa5ac91a5a17cfe9adad80d | 2026-11-02T22:24:53Z |

原始公开API元数据保存在主工作树 ignored `.verification-results/ci-25-26/{run,jobs,artifacts}.json`。这里只核读run/job/步骤与artifact元数据，没有下载或逐文件检查归档；digest指整个artifact，不能替代库jar哈希。准确CI测试计数、覆盖率和资源数据以各环境归档内报告为准。

本轮不含尚在独立分支实施的07/08/10/12/19/20/28–32，也不替代33最终候选的29模块与全部接合验收。没有发布制品或部署应用。
