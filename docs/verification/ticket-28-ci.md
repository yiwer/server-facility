# 28：PostgreSQL模板跨平台验收记录

2026-10-04，[CI14 run37159629444](https://github.com/yiwer/server-facility/actions/runs/37159629444)在精确候选`f4837ee1311b775b8890878177dade647d05562d`结束，整体failure，最后更新UTC为2026-10-03T22:58:39Z。28保持verification-pending；这次结果不足以释放要求28关闭的29。

| 环境 | Job | PG工具准备 | all | platform | 归档 |
|---|---|---|---|---|---|
| Ubuntu | [111310189975](https://github.com/yiwer/server-facility/actions/runs/37159629444/job/111310189975) | success | success | success | success |
| Windows | [111310190000](https://github.com/yiwer/server-facility/actions/runs/37159629444/job/111310190000) | success | failure | success | success |

Windows公开failure annotation指出`82-template-packaged-http.log`退出码1。公开尾部含`TemplateConsumer.main:87`的`local database cleanup failed`断言和`LocalDatabase.main:38`的`Native database did not become ready`，数据库日志路径位于Windows临时目录的`RUNNER~1`短别名下。公开内容没有包含postgres.log；这里不把短路径或超时猜作已确认原因，也不把cleanup错误替代为已经查明的首因。28实施代理继续诊断，修复后需重新执行相应门并以实际联合CI闭合。

| Artifact ID | 名称 | 字节数 | 整体digest |
|---|---|---:|---|
| 11287266827 | java25-ubuntu-latest-f4837ee1311b775b8890878177dade647d05562d | 34726068 | sha256:8935b563092f6590f3d00804313d5410eb7fa0ba1e6bede0b45eff146d9a9e63 |
| 11287122138 | java25-windows-latest-f4837ee1311b775b8890878177dade647d05562d | 34881800 | sha256:ed2b257c94002a57507a71845e18fa1b80adf35c70f3f06387c717f294b04773 |

原始公开API响应保存于主工作树ignored `.verification-results/ci-28/{run,jobs,annotations,artifacts}.json`，较早in-progress响应另行保留。这里读取的是run/job/步骤/annotation与artifact元数据，没有下载或逐文件检查artifact内容。digest代表整个归档，不是库jar；本地报告的测试数值不充作此次CI精确观测。

本地完整与最后模板子集的不同来源仍见[28实施报告](ticket-28-persistent-business.md)。07在此CI来源之后才合入，亦尚待新的联合CI；本次没有修改产品或放宽质量门。


## CI14修复候选（尚待CI15）

2026-10-04：修复已从干净中央2e79ee7以--no-ff合入，merge `baeed369d4cde9640ff006b139e3413a2c496df7`，交接HEAD `c03f3b138d4632e360e7825235ba82d5f8ebdd03`。pg_ctl拥有原生启动/停止，进程首因与有限日志保留、properties删除失败仍停止数据库均有真实公开进程RED/GREEN。核读两个summary为PASS：06881a9完成库install1667与完整模板76/质量门/真实打包重启；后续baeb8c1仅开发辅助进程清理变更，三项CLI及平台/虚拟实际jar重启单独复验。精确来源、命令、失败与限制见[修复报告](ticket-28-ci14-fix.md)。

合入产品/模板/runner/CI配置与交接分支相同，保留07/12/32消费者和归档。Windows管理员token仅为官方源码支持的待CI对照假说，本机非管理员通过不证明CI14底层原因。CI14原失败及metadata不改写；即将授权push的CI15验证联合源，28仍pending、29仍等待。


## CI15联合候选的新失败

精确源码`e3382ce6556d30b35d8b7e1084a210d8ef938d5e`触发[run37163228381](https://github.com/yiwer/server-facility/actions/runs/37163228381)，最终completed/failure。Ubuntu job111320828270的all、platform与归档全部success；Windows job111320828519的all失败，platform与归档success。platform配置为独立执行，进入或通过该步骤不能推出先前all成功。

Windows公开annotation明确给出新的失败：`90-template-build.log`中模板76项、0断言失败、1 error；`MigrationHttpTest.twoApplicationsReleasedAtTheNativeBeforeMigrateBoundaryInitializeOneSchema:59`抛ExecutionException，cause摘要为`IllegalStateException: ConcurrentModificationException`。它发生于模板测试阶段，尚未执行新增pg_ctl三项CLI或打包消费者，故不能把CI15当作原生启动假说已证实或已排除。已重新交28实施代理诊断；不删并发场景、不以重复运行到绿替代修复依据。

公开API的run/jobs/artifacts与Windows annotations原始JSON保存于主checkout ignored `.verification-results/ci-15/`，earlier in-progress快照单独保留。记录的是artifact元数据，不声称已下载或核读其内部原生日志；公开annotation仅包含cause摘要，完整堆栈仍是诊断限制。07/12/28/32继续verification-pending，29未开始，closed仍21项。下一修复按真实差异选择验证，最终联合候选仍待双OS完整门。

- Artifact `java25-ubuntu-latest-e3382ce6556d30b35d8b7e1084a210d8ef938d5e`：ID `11288950707`，34953940bytes，API digest `sha256:e95e519fe341b73de29631688bbcc99b9ec78c0793dd233f7f8be21421caa597`。

- Artifact `java25-windows-latest-e3382ce6556d30b35d8b7e1084a210d8ef938d5e`：ID `11288551903`，5224110bytes，API digest `sha256:d9a41d51dba8c6cf67c4792c3a604d91533dd16c5eaef716ddfc4f4d1452f200`。
