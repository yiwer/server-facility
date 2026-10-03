# 票 04：安全 HTTP 错误的跨平台证据

2026-10-04，集成提交 `66bf4d04be3b40a5d81a80fa418cbe213c2307f4` 通过 [GitHub Actions 37136353128](https://github.com/yiwer/server-facility/actions/runs/37136353128)。此轮新增票04的真实 Servlet HTTP 错误场景，保留普通 jar 与 JSON 消费者验证。

| 环境 | Job | 完整验证、前置检查与归档 |
|---|---|---|
| Windows | [111241569440](https://github.com/yiwer/server-facility/actions/runs/37136353128/job/111241569440) | 全部 success |
| Ubuntu | [111241569601](https://github.com/yiwer/server-facility/actions/runs/37136353128/job/111241569601) | 全部 success |

两端执行同一 `java verification/Verify.java all --fresh`。root 已实际通过 GitHub API 核对确切 SHA、全部步骤 success 及归档；本次没有下载归档内部日志。各环境精确测试计数、覆盖率和 jar 校验以各自原始报告为准，本机 Windows 的1276项不代替 Linux 的精确数。

| Artifact ID | 环境 | 字节数 | SHA-256 |
|---|---|---:|---|
| 11278068516 | Windows | 2460469 | 55ef2f8288aeed64207ad06b579e66c37852aa834c2f9df07fadb112ce579128 |
| 11278778338 | Ubuntu | 2379161 | eed5087bc154fd5ae790714d63b1711bb739412cfb997ae56daa18df2437ad61 |

归档名称为 `java25-<os>-66bf4d04be3b40a5d81a80fa418cbe213c2307f4`，保存至2026-11-02。校验标识整个 artifact，不等同于内部 jar 校验。归档包含统一 runner 日志、Surefire/JaCoCo、有效POM、依赖树、产物和独立消费者证据。

本轮闭合票04的Q08/Q10，票04关闭。票05新增的流/资源测试尚不在这个提交中，需要自己的集成CI；Boot4/Jackson3目标平台由22–24复验。
