# CI19 — 事务命令同源双平台闭合

2026-10-04，精确源码 `ae7215eb6fea6c12aea9d4bb450aaf4ad21ab0fa` 的 [run37172307215](https://github.com/yiwer/server-facility/actions/runs/37172307215) 已 completed/success，UTC 更新 `2026-10-04T03:08:36Z`。Windows 与 Ubuntu 的完整 `all --fresh`、独立 `platform --fresh` 和证据归档均 success。

| 环境 | Job | all | platform | 归档 | UTC完成 |
|---|---|---|---|---|---|
| verify (ubuntu-latest) | [111347590758](https://github.com/yiwer/server-facility/actions/runs/37172307215/job/111347590758) | success | success | success | 2026-10-04T03:02:00Z |
| verify (windows-latest) | [111347590655](https://github.com/yiwer/server-facility/actions/runs/37172307215/job/111347590655) | success | success | success | 2026-10-04T03:08:36Z |

## 来源与闭合范围

本候选包含29最终实现 `bc7ce170a6114c3c430080f5458e26bc8932a7d4`、merge `edc4eaac1e8443a21f5da0c56a9736c222963bc7` 及中央状态登记。产品、测试、模板、runner、既有消费者、workflow与Wrapper均等于交接树；状态文档没有修改被测产品。29的业务Module在同一事务提交命令身份、业务效果、成功命令计费和原始回执，拒绝不符合当前授权或字节政策的调用；唯一命令竞争、普通数据库不可用和已完成回执各有明确结果。

[29本地报告](ticket-29-transactional-commands.md)保持三段来源：`f6a3a9b`完整all135命令/库1774项；`4c3ba76`最终产品完整模板106项/打包双模式/三CLI及负控；`34c06cb`最后两文件测试数据库ownership修复的18项、4项回归和预期2项失败的清理负控。此次CI才验证最终组合在两个OS上的完整结果，不把三段本地证据拼成某次未执行的全门，也不把本地测试计数冒作CI内部报告精确值。

原run40的PostgreSQL fast shutdown checkpoint79.458s失败原样保留。修复按实际JUnit方法清理自己生成的数据库，保留同一方法的多应用共享、共享默认数据库、原60s停止预算、fsync和no-FORCE语义。最终runner保留原断言并显式验证借用连接导致清理拒绝、原失败保留以及abort不能掩盖清理失败；本次两端完整门均成功，未删除门或放宽产品预算。

29的Q08/Q10及共同完成项闭合，状态为closed，累计30张票closed。30前置解除并进入实施；它仍负责精确提交点进程丢失、两个独立JVM、断连和物理receipt清理后的恢复。31的实际五任务比较/模板升级与33最终单候选完整接合、制品身份和双轴审查仍未完成。普通重启和本轮CI成功不代替这些后续验收。

## 公开证据与限制

主checkout ignored `.verification-results/ci-19/`保存完整公开 `run.json`、`jobs.json`、`artifacts.json`、两个OS的annotations和初始/阶段快照。只读取公开元数据与注解；没有下载或逐文件检查artifact内容。以下digest标识整个证据归档，不等于内部ordinary library或application jar SHA，也不证明两OS内部jar逐字节一致。精确JDK/依赖/环境和测试报告位于对应归档；33需实际比较最终候选制品。

| Artifact ID | 名称 | 字节数 | API digest |
|---|---|---:|---|
| 11292395625 | java25-ubuntu-latest-ae7215eb6fea6c12aea9d4bb450aaf4ad21ab0fa | 35255618 | sha256:1458ce789aa291d3240ea6498f391c28e1e2e21363c1c5fb865f88c4757ef6cd |
| 11291828400 | java25-windows-latest-ae7215eb6fea6c12aea9d4bb450aaf4ad21ab0fa | 35459154 | sha256:bfdd1c8b84d067b639dbc1f5b8243fa6805c42f98ceb92706f8202fd36cc0baf |

两个成功job仍有旧checkout/upload action目标Node20被runner强制运行于Node24的通知；Ubuntu另有latest镜像后续迁移通知。它们不是测试失败。正式33冻结前仍要按官方稳定动作与immutable提交核对工作流；不因通知重跑本次已成功来源。此后闭合文档提交不是本次被测SHA。
