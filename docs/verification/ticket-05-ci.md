# 票 05：流式 I/O 的跨平台证据

2026-10-04，集成提交 `2304a57103b8c6f6a0791a783b80440dd652c1b0` 通过 [GitHub Actions 37137011984](https://github.com/yiwer/server-facility/actions/runs/37137011984)。本轮包含票05新增的真实下载/SSE、请求体边界、取消/断连和独立受限堆进程。

| 环境 | Job | 完整验证、前置检查与归档 |
|---|---|---|
| Windows | [111243486082](https://github.com/yiwer/server-facility/actions/runs/37137011984/job/111243486082) | 全部 success |
| Ubuntu | [111243485942](https://github.com/yiwer/server-facility/actions/runs/37137011984/job/111243485942) | 全部 success |

两端执行 `java verification/Verify.java all --fresh`，包括完整库质量门、普通 jar 消费者、constructed/injected JSON HTTP、多应用生命周期与真实工具链负向检查。root 已通过 GitHub API 核对确切 SHA、每个步骤 success 及归档；没有下载归档内部日志，各环境精确测试计数、覆盖率、堆观测与 jar 校验以对应原始报告为准。本地1323测试和17.3MB保留堆不代替Linux精确观测值。

| Artifact ID | 环境 | 字节数 | SHA-256 |
|---|---|---:|---|
| 11278869495 | Windows | 2530165 | 0154005e82afa69dffe2516b370cca8e7ed60d08b874aca6d84f71d046d75fc9 |
| 11279280059 | Ubuntu | 2441989 | 229f510734b97808c7970ecd397afa5164d81a142e323b086649b4c09d149ac0 |

归档名称为 `java25-<os>-2304a57103b8c6f6a0791a783b80440dd652c1b0`，保存至2026-11-02。校验对应整个artifact，不等同内部jar。统一runner、Surefire/JaCoCo、有效POM、依赖树、产物及消费者记录可用于复核。

票05新增Linux场景已经取得真实证据。仍保留 `verification-pending`，只待票24完成Boot4/Servlet6.1重载覆盖及完整复验；已知新增sendRedirect与Charset重载不能用Servlet6.0结果代替。票12的授权、claim和结果保存政策，以及票33的最终候选组合仍按各票责任处理，不新增05对33的前置依赖。
