# 票 02/03：Java 25 集成版本的跨平台证据

2026-10-03，包含工具链、上下文生命周期和异步契约的集成提交 `0e8d5863ec51dd2ea691332b3cbbc1512de17c55` 通过 [GitHub Actions 37132912477](https://github.com/yiwer/server-facility/actions/runs/37132912477)。

| 环境 | Job | 完整验证、前置检查与归档 |
|---|---|---|
| Windows | [111231433655](https://github.com/yiwer/server-facility/actions/runs/37132912477/job/111231433655) | 全部 success |
| Ubuntu | [111231433767](https://github.com/yiwer/server-facility/actions/runs/37132912477/job/111231433767) | 全部 success |

两端执行同一 `java verification/Verify.java all --fresh`，固定 Temurin JDK 25、真实 JDK 21 负向检查和 Maven Wrapper。源码入口会在测试失败、错误、跳过、缺少原架构规则、质量门不满足或消费者/资源/先决条件检查失败时返回非零。GitHub API 已确认两个 job 的所有步骤成功，以及报告 artifact 存在；精确测试计数和覆盖率留在各环境原始报告中，不以本机数字代替。

| Artifact ID | 环境 | 字节数 | SHA-256 |
|---|---|---:|---|
| 11276804396 | Windows | 2357622 | 47fe319e227c50210bf711a1c4557012c686acd6d9d06b98784c5cd13488fb00 |
| 11276729617 | Ubuntu | 2276870 | b7921c9db85823abf77cf970a732c1e15c03029ded130214b0649cecc8469663 |

归档名称为 `java25-<os>-0e8d5863ec51dd2ea691332b3cbbc1512de17c55`，保存至 2026-11-02，内容含完整日志、发现/覆盖率报告、依赖清单和 jar。

票 03 的本次 Linux 缺口已闭合。Boot 3.5.16 仍是中间平台，票 24 须在 Boot 4 复跑相关线程及装配场景；因此票 03 仍保持 `verification-pending`。票 07 的持锁超时、票 06/26/27 的 HTTP 身份策略，以及票 33 的最终候选组合/扩大长稳，各自按正式任务承担，不形成票 03 对票 33 的反向依赖。
