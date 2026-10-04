# CI21 — 工作流双平台通过与本地观测超时的独立记录

2026-10-04，精确源码 `eebd736a231ee8aaa9bd80b350b8dcccc8c44b07` 的 [run37185527651](https://github.com/yiwer/server-facility/actions/runs/37185527651) 于 UTC07:45:13 completed/success。Windows、Ubuntu 的完整 `all --fresh`、独立 `platform --fresh` 和证据归档均逐项 success。

**本次 CI 成功不关闭 ticket31。** 同产品源码的本地完整运行18仍有一项尚待解释的并发观测超时，状态保持 verification-pending。后续诊断、修复及其来源必须另行记录，不能用 CI 绿色删除本地失败。

| 环境 | Job | all | platform | 归档 | 整个 job |
|---|---|---:|---:|---:|---:|
| Ubuntu | [111386517144](https://github.com/yiwer/server-facility/actions/runs/37185527651/job/111386517144) | success /948s | success /48s | success /7s |1029s|
| Windows | [111386517228](https://github.com/yiwer/server-facility/actions/runs/37185527651/job/111386517228) | success /1325s | success /69s | success /11s |1431s|

时长由公开 jobs 元数据的开始、结束时标计算。当前30分钟 job 预算未修改；新增最终候选历史重验时，应根据实际增量选择有限预算，不能把这里的时长当成未来必然上限。

## 来源与验证边界

最终实现来源为 `d4ac86bf162d35b4ea1e7d9abdc5ac99a30fb5db`。CI 源码与其仅有五个中央 Markdown 登记文件差异，产品、POM、测试、模板、消费者、验证 runner、workflow 和 Wrapper 均相同。新入口包含20文件工作流 overlay、模板来源检查、合作式上传中断 fixture，以及独立工作流构建和实际可执行 JAR 消费。

先前本地16在 `57dc707` 的模板来源检查失败：Java 源码启动器的原始 Unicode 路径传参被替换，触发 InvalidPathException。`1dc2912` 改为 ASCII file URI，并在原失败目录及新工作流目录完成四条实际检查。该失败源码从未推送；CI21只运行修复后的来源。参见[完整实施报告](ticket-31-template-upgrade.md)。

本地18从干净的 `d4ac86b` 执行，先完成主库1774项、原模板130项、数据库生命周期、打包 HTTP、清理及覆盖率负控，随后在 `workflow-build` 失败。该工作流发现151项，0 failures、1 error、0 skipped；错误为 `StandardObservationHttpTest.concurrentDeferredRequestsKeepTheirIncomingTrace(false)` 经 `RunningApp.get` 收到10秒 HttpTimeoutException。此参数实例耗时13.78秒，同类其余六个实例通过。总运行1341.516秒，未完成后续工作流打包、资源与先决条件入口，不能称为本地完整 PASS。

本地原始报告保留在 ticket31 工作树 `.verification-results/20261004-152145-168-all`；错误 XML/txt 位于 `workflow/positive-surefire-reports`，完整命令日志为 `137-workflow-build.log`。初次针对原入口的诊断循环保持8×200、10秒请求及90秒整轮预算，两种线程模式共3200次 HTTP 后2项通过；这是独立诊断观察，不是修复或对原失败的撤销。诊断记录位于工作树外 `coordination/ticket31-observation-diagnosis`。

## 公开归档元数据

主工作树 `.verification-results/ci-21` 保存最终 run/jobs/artifacts/annotations、阶段快照和 `derived-timings.json`。本次读取的是公开 API 元数据及注解，没有下载或逐文件检查 CI 归档内部内容。因此不从本地计数推定 CI 内部的精确测试数、覆盖率、工具链镜像或 JAR 字节身份。

| Artifact ID | 环境 | 字节数 | API digest |
|---|---|---:|---|
|11296884093|Ubuntu|66144932|`sha256:e45256af66f9b6ac0f49ae556afdd12a37f6f7ec1a2403b17358a2a138e1d1b2`|
|11297099105|Windows|66395295|`sha256:b1057ad02eae7c88ab6e82d68520e218a4ba38136d27cb4f8230b829b4ecf2a9`|

归档名为 `java25-<os>-eebd736a231ee8aaa9bd80b350b8dcccc8c44b07`，API报告保留至2026-11-03。这些 digest 标识整个传输归档，不是内部普通库或应用 JAR 的 SHA-256，也不证明跨 OS 制品相同。最终33仍需严格核对四类当前制品身份及同一候选的历史升级重验；本记录不宣称它们已经完成。
