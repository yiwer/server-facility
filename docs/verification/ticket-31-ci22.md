# CI22 — 模板交付最终候选双平台闭合

2026-10-04，精确源码 `4470870b0d8ad50888dba34d85e38d624987dc91` 的 [run37192581803](https://github.com/yiwer/server-facility/actions/runs/37192581803)，attempt 1，于 UTC10:03:51 completed/success。Windows、Ubuntu 的完整 `all --fresh`、独立 `platform --fresh` 和证据归档均逐项 success。本次与已完成的本地20/21共同闭合 ticket31；最终候选组合、四类制品身份与跨OS字节可复现性仍由33承担。

| 环境 | Job | all | platform | 归档 | 整个 job |
|---|---|---:|---:|---:|---:|
| Ubuntu | [111407707501](https://github.com/yiwer/server-facility/actions/runs/37192581803/job/111407707501) | success /947s | success /50s | success /7s |1022s|
| Windows | [111407707703](https://github.com/yiwer/server-facility/actions/runs/37192581803/job/111407707703) | success /1641s | success /83s | success /15s |1774s|

时长来自公开 jobs 元数据。Windows job 于 UTC10:03:50 结束，耗时29分34秒；现有30分钟预算未调整。此测量不是后续新增历史重验步骤的预算保证。

## 来源与本地证据

被测实现冻结为 `b2cbb0f368d7187c87fb90b0bd11741c9d71e0a7`。CI源码与它仅有四个中央注册文档差异；产品、POM、测试、模板、示例、验证runner、workflow和Wrapper保持相同。新增模板连接池下限2的前置拒绝与真实HTTP回归、workflow继承testcase及重复身份校验均在本次来源内。默认4/minIdle0/1000ms不变。

同一实现的本地20 `java verification/Verify.java all` 实际148命令、1988.219秒、`RESULT=PASS`；主库1774、模板132、workflow153项分别为0失败/错误/跳过，原覆盖率、架构、依赖门和真实打包消费者通过。本地21 `java verification/Verify.java platform` 实际11命令、102.207秒、`RESULT=PASS`。这两次使用私有依赖仓库，命令没有 `--fresh`；不能写成空仓库运行。原始summary、正向XML和coverage由[完整报告](ticket-31-template-upgrade.md)、[解析结果](ticket31/final-local-results.json)及[持久保留索引](ticket31/local-evidence-retention.json)定位。

最终clean交接 `45d0809659cb547eadd862fff45638c3f294b8d0` 在已测实现上补充结果与保留报告并合入CI源码，仍只有文档差异。关闭登记不触发同源本地构建，也不把后续文档提交称为CI被测SHA。

原本地18的10秒HTTP超时与19的默认最大池4启动超时仍真实保留，有限原入口/前置顺序诊断没有复现二者，也没有建立其原因。独立最大池1控制揭示的配置契约缺口有单独RED→GREEN证据；它不是18/19的复现或原因。冻结历史升级与五任务测量身份未改，历史结果不冒充33最终候选重验。

## 公开归档元数据

主树 `.verification-results/ci-22` 保存原始run/jobs/artifacts/annotations、阶段快照，以及 `head-checks.json`、`derived-timings.json`、`evidence-summary.json`。run/head/attempt、两系统job与步骤、artifact名称和来源均已校验。

| Artifact ID | 环境 | 字节数 | API digest |
|---|---|---:|---|
|11300400453|Ubuntu|66148460|`sha256:68dc64dbb7f8846332bf9d036f6f27f063b13311172ef352e2857eda9caec2f3`|
|11299179967|Windows|66397788|`sha256:cd34c443caa5ce3f62a7cd65f45fcfaf9afd74c2f4eb069aacc7eb188cb7c61c`|

归档名为 `java25-<os>-4470870b0d8ad50888dba34d85e38d624987dc91`；API报告两项未过期并保留至2026-11-03。没有下载或读取归档内部内容，故不推定CI内部精确测试数、覆盖率或JAR身份。这里的digest标识整个CI证据归档，不是四类交付JAR的SHA，也不证明跨OS字节相等。

公开注解记录checkout/upload-artifact的Node20→24运行时通知，以及Ubuntu未来镜像迁移通知；本次没有失败注解。最终33仍须按其冻结时的实际平台核对。
