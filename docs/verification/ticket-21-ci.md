# 票 21：JSON 扩展的跨平台证据

2026-10-03，集成提交 `ee2e9cc27fd53b3c5d0044258e64577075a75a0c` 通过 [GitHub Actions 37134465187](https://github.com/yiwer/server-facility/actions/runs/37134465187)。该提交包含 01/02/03 与 21；本轮增加的真实 JSON/HTTP 消费者已进入统一验证入口。

| 环境 | Job | 完整验证、前置检查与归档 |
|---|---|---|
| Windows | [111236050258](https://github.com/yiwer/server-facility/actions/runs/37134465187/job/111236050258) | 全部 success |
| Ubuntu | [111236050353](https://github.com/yiwer/server-facility/actions/runs/37134465187/job/111236050353) | 全部 success |

两端执行同一 `java verification/Verify.java all --fresh`。入口包含库的完整质量门、最小普通 jar 消费者、JSON constructed/injected 两种真实 HTTP 消费模式、多应用关闭重建、进程生命周期和真实工具链负向检查；任意步骤失败即返回非零。GitHub API 已核对确切提交、所有步骤 success 及 artifact 存在。各环境精确测试计数、覆盖率和 jar 校验留在各自原始报告中；本机 Windows 数字不代替 Linux 数字。

| Artifact ID | 环境 | 字节数 | SHA-256 |
|---|---|---:|---|
| 11278191803 | Windows | 2407520 | c936cf83b3bf374dd958d948d97c88e0cfb3597e1549718928bedd260a592e86 |
| 11278880148 | Ubuntu | 2323893 | 9f729f6aa5d8a076082701ca50c6aefddb1d5b3b10f85790657bef1a7a758680 |

归档名称为 `java25-<os>-ee2e9cc27fd53b3c5d0044258e64577075a75a0c`，保存至 2026-11-02。内容包括 runner 日志、Surefire/JaCoCo、有效 POM、依赖树、jar、独立 JSON 消费者源码/POM、字面金样及校验。归档校验标识整个 artifact，不等同于内部 jar 的校验。

本轮闭合票 21 的 Q08/Q10 与新增 Linux 场景，票 21 关闭，解除 22 的前置阻塞。Boot 3.5.16/Jackson 2 仍是迁移前基线，22/23 的中间批次只能进入非发布集成线，24 负责恢复目标平台的完整绿色。
