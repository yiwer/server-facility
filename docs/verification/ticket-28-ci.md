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
