# CI17 — 08/10/20 联合候选保留失败

2026-10-04，精确源码 `f081f2db7ecc14783e81f667b18586ba98d3a316` 的 [run37166952423](https://github.com/yiwer/server-facility/actions/runs/37166952423) 已 completed/failure，UTC 更新 2026-10-04T01:18:19Z。两个 OS 的完整 all 均在模板观测测试失败；独立 platform 与 artifact 归档步骤均 success。08/10/20 保持 verification-pending，正式 closed 仍为 26，不把先前本地通过或 platform 成功替代此次完整门。

| 环境 | Job | all | platform | 归档 |
|---|---|---|---|---|
| verify (ubuntu-latest) | [111331738095](https://github.com/yiwer/server-facility/actions/runs/37166952423/job/111331738095) | failure | success | success |
| verify (windows-latest) | [111331738274](https://github.com/yiwer/server-facility/actions/runs/37166952423/job/111331738274) | failure | success | success |

## 公开失败证据与限制

- Ubuntu annotation 指向 `119-template-build.log`，`StandardObservationHttpTest.standardTracerAndObservationRemainActiveAcrossApplicationExecutor(boolean)[1]` 第 66 行，`span-deferred` 期望 HTTP 200、实际 503；模板摘要为 78 tests / 1 failure / 0 errors / 0 skipped。
- Windows annotation 指向同一阶段，`StandardObservationHttpTest.applicationsKeepDifferentSamplingPoliciesAndSurviveAnotherContextClosing` 第 41 行，实际 trace 为 `""`，不满足 32 位十六进制断言；模板摘要同为 78 / 1 / 0 / 0。公开注解还包含 `parent can only be null in a local root!`。
- 上述内容来自实际失败注解，未将尾部正常 JWT 拒绝测试的异常栈误当本次失败。HTTP 503 本身不能区分等待期限到达与 worker 异常后未完成结果；根代理正在独立修复树以标准观测生命周期和真实 HTTP 屏障诊断，不先放宽期限或移除断言。
- CI16 已通过候选的历史结论保留；本次新组合暴露的观测失败需要实际修复与新同源 CI。08/10/20 自身本地证据仍各按原来源记录，不由此推定其产品是此次根因。最终 33 的单一候选完整门和双轴审查仍独立执行。
- CI17 不包含随后 `fb1bdee` 的纯文档/YAML 推荐修正；其先前局部绑定与 context 证据单独见 [推荐示例预检](recommendation-examples-preflight.md)，不得冒作此 run 已验证的新输入。

## 原始元数据

主 checkout ignored `.verification-results/ci-17/` 保存公开 API 原始 `run.json`、`jobs.json`、`artifacts.json`、两个 OS 的 `*-annotations.json`，初始/进行中快照另行保留。本次没有下载或逐文件核对 artifact 内容；下列大小与 digest 仅来自 API 元数据，digest 属于整个归档，不是库 jar。

| Artifact ID | 名称 | 字节数 | API digest |
|---|---|---:|---|
| 11290680264 | java25-ubuntu-latest-f081f2db7ecc14783e81f667b18586ba98d3a316 | 5228152 | sha256:bb2f09c2d539ccc4961309577a74e28c337c4e028e7471988897d71ac43105a3 |
| 11289796484 | java25-windows-latest-f081f2db7ecc14783e81f667b18586ba98d3a316 | 5426151 | sha256:8b7b4b3319bc56063f8275e4efe79eff0f78d6a6a65f8bdcd57edcfba18f8c84 |
