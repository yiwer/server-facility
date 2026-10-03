# 11／16／27：claim、有界Excel与受保护模板跨平台闭合

2026-10-04，集成提交 `2b06f527a24602842721c4ed800ad71bca255319` 通过[现有 GitHub Actions CI12 run37156503739](https://github.com/yiwer/server-facility/actions/runs/37156503739)。Windows 与 Ubuntu 均实际执行完整门、独立平台门及归档成功，补齐11、16、27剩余平台证据，三票据此 closed。运行于 `2026-10-03T21:52:25Z` 开始，最后更新时间为 `2026-10-03T22:03:21Z`。

| 环境 | Job | all／platform／归档 |
|---|---|---|
| ubuntu-latest | [111300858200](https://github.com/yiwer/server-facility/actions/runs/37156503739/job/111300858200) | 全部 success |
| windows-latest | [111300858379](https://github.com/yiwer/server-facility/actions/runs/37156503739/job/111300858379) | 全部 success |

API 中核对的三步分别是 `Clean build, independent consumer, resource and prerequisite checks`、`Independent platform toolchain and engine controls`、`Archive evidence even on failure`；run 与两个 job 均为 completed/success。原始 API JSON 保存于 main 的 ignored `.verification-results/ci-11-16-27/{run,jobs,artifacts}.json`。

该候选实际执行11的 owner/lease/永久终态与普通jar有限堆故障消费者、16的四种Excel依赖图/独立格式输入/有限堆大表与恶意XML/平台文件清理场景，以及27的独立模板构建、真实可执行包认证授权、上下文与生命周期、coverage缺失负控。各项公共契约、Windows精确数值及历史RED/GREEN分别保留在[11报告](ticket-11-qualified-claims.md)、[16报告](ticket-16-bounded-excel.md)、[27报告](ticket-27-secured-template.md)。既有消费者、质量门、资源循环和工具链负控同候选执行。

此前 CI8–11 的 Windows 失败记录继续保留。Unicode 原生 agent/进程参数及短路径别名检查分别经过实际复现与修复；CI12 包含官方 offline JaCoCo、ASCII file URI/manifest classpath 和临时根目录 `toRealPath()` 修复，未删除测试或放宽质量门。诊断过程与局部验证详见[25报告](ticket-25-outbound-http.md)。这些此前失败不被回写为成功。

| Artifact ID | 名称 | 字节数 | 整体 SHA-256 | 到期时间 UTC |
|---|---|---:|---|---|
| 11285717645 | java25-ubuntu-latest-2b06f527a24602842721c4ed800ad71bca255319 | 28971627 | 08039c914f310c6dc19ee35c928496c56596eb1d42d5ab6ab416f8e4ced1be58 | 2026-11-02T21:59:32Z |
| 11285448382 | java25-windows-latest-2b06f527a24602842721c4ed800ad71bca255319 | 29130410 | edffcf395aa840d50e20c634301bf2b310ae77cf4192e05037bf4bd8fa2c056c | 2026-11-02T22:03:13Z |

此处只读取公开 API 元数据，没有下载或逐文件审阅归档内部内容。digest 属于整个 artifact，不是普通库 jar；CI 精确 JUnit 数、覆盖率、保留堆和 jar 哈希以各环境原始报告为准，不把本机数值写作 Linux 观测。工作流已归档所需执行报告与消费者输入，未发布制品。

25新增 Inventory `[null]` 的产品修复在 CI12 之后才合入 main `6ff81c371b1fe171dc11a88b4d6d5c331e94e0c2`，其15测试组合仍须 CI13 验证，因此25保持 verification-pending。12、28–31及33继续承担各自业务/事务/最终候选组合验收，不反向改变本次11、16、27的已完成范围。
