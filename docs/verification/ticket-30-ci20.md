# CI20 — 命令恢复与回执清理同源双平台闭合

2026-10-04，精确源码 `61094b53b23b5d00759df3e6837999bc63af0f44` 的 [run37176585182](https://github.com/yiwer/server-facility/actions/runs/37176585182) 已 completed/success，UTC 更新 `2026-10-04T04:37:55Z`。Windows 与 Ubuntu 的完整 `all --fresh`、独立 `platform --fresh` 和证据归档均 success。

| 环境 | Job | all | platform | 归档 | UTC完成 |
|---|---|---|---|---|---|
| verify (ubuntu-latest) | [111360340295](https://github.com/yiwer/server-facility/actions/runs/37176585182/job/111360340295) | success | success | success | 2026-10-04T04:30:50Z |
| verify (windows-latest) | [111360340365](https://github.com/yiwer/server-facility/actions/runs/37176585182/job/111360340365) | success | success | success | 2026-10-04T04:37:55Z |

## 来源与闭合范围

本候选包含30最终交接 `16214eb8ef09888f65c2a2ff4dd7fcb5546757f8`、merge `57c5028fc757baca0ce60d2770c92a710fec7909` 和中央文档登记。交接相对本地冻结 `cd79a19540d1c4b1ce6ed26af3e14205bf1d79c3` 只有本票和报告两个Markdown文件差异；合入后的产品、测试、模板、runner、消费者、workflow与Wrapper保持交接内容。中央补充CHANGELOG/ADR索引，并删除未观测CI内部计数的比较措辞，未修改被测行为。

[30本地报告](ticket-30-command-recovery.md)记录同源Windows完整137命令、库1774/模板130项零失败/错误/跳过，19个实际child确认退出/数据库会话归零、6次精确kill、71个自有数据库正常DROP与三类产物SHA。这些精确数量和资源观测只属于其标明的本地冻结来源，本报告不将其推定为未下载的CI内部计数。

本次同源双平台结果补齐Q08/Q10：进程丢失、提交后未交付、部分HTTP正文、双独立JVM竞争、清理交错/撤权和永久失效标记均随最终Verify入口参与完整验证。故障提交点由测试classpath的真实JDBC连接代理控制；实际Boot可执行jar消费者另行保留，二者没有混称同一种运行产物。V4只清除回执表示，保留完整命令身份、指纹、deadline和终身计费；权限、schema绑定、有限批次和操作员调用预算见ADR0053与模板COMMANDS。

历史编译/契约/fixture失败、原始RED和修复后的来源继续保留，不用CI成功覆盖。质量门、强制测试发现、无测试fixture泄漏的打包检查、已有消费者及负控均保留；未降低产品时间预算、关闭fsync或使用FORCE清理共享资源。

30状态为closed，共同完成项及Q08/Q10闭合，正式累计31张票closed。31的全部前置已经完成，可正式领取模板升级与五类任务验证；工作树外工具自测仍是预研，不能当成31完成。33仍负责最终同一候选的全部接合、制品身份/可复现性、升级后组合与标准/规格双轴审查。这里的同库事务保证不扩展为外部副作用的跨系统exactly-once。

## 原始公开证据与限制

主checkout ignored `.verification-results/ci-20/`保存最终 `run.json`、`jobs.json`、`artifacts.json`、两个OS的annotations，以及初始和阶段快照。这里只读取公开API元数据与注解，没有下载或逐文件核查归档内容。精确测试内部结果、运行时工具链/runner镜像及内部jar字节身份不能仅由job名称或本地数据推断。

| Artifact ID | 名称 | 字节数 | API digest |
|---|---|---:|---|
| 11293138218 | java25-ubuntu-latest-61094b53b23b5d00759df3e6837999bc63af0f44 | 35454283 | sha256:9a981fa9c8d968eaa691dc950f420ddbea28161dce9aec555265d5e2b05bd4b1 |
| 11293268368 | java25-windows-latest-61094b53b23b5d00759df3e6837999bc63af0f44 | 35659656 | sha256:8c8b2e06a2c3e8abf732a6a2aac0af3cf4b719b907cdb2ec4e60d0cf8b4a63af |

这些digest标识整个证据归档，不是普通库或应用jar的SHA，也不证明两OS内部jar逐字节相同。两个job仍有旧checkout/upload action目标Node20被强制运行于Node24的通知，Ubuntu另有latest镜像后续迁移通知；没有测试失败注解。正式33冻结时继续核对官方稳定动作及immutable提交，不因通知重跑本次已成功来源。后续闭合文档提交不冒充本次被测SHA。
