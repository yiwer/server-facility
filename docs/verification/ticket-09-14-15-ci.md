# 09／14／15：限流、文件完整性与CSV的跨平台闭合

2026-10-04，集成提交`c2f0f6b4118a3a059993ef53f2d62f547151c560`通过[仓库现有GitHub Actions 37147633803](https://github.com/yiwer/server-facility/actions/runs/37147633803)。Windows和Ubuntu均实际执行`all --fresh`、`platform --fresh`及归档成功，关闭09、14、15的剩余平台验证项。最终全部33票同候选验收仍由33负责。

| 环境 | Job | 完整门／平台门／归档 |
|---|---|---|
| verify (ubuntu-latest) | [111274747433](https://github.com/yiwer/server-facility/actions/runs/37147633803/job/111274747433) | 全部success |
| verify (windows-latest) | [111274747609](https://github.com/yiwer/server-facility/actions/runs/37147633803/job/111274747609) | 全部success |

该候选组合包括实际HTTP限流与ASYNC去重、限流普通jar有限堆消费者；ZIP/目录完整性、平台链接与清理失败分支、独立JDK ZIP读取器及有限堆大文件消费者；成熟CSV解析器、独立Python生成语料、实际行列字段/字节预算及普通jar消费者。原平台、JSON、上传、加密、资源周期与工具链负控一起执行。具体场景与本地计数分别见[09报告](ticket-09-rate-limit-contract.md)、[14报告](ticket-14-io-integrity.md)、[15报告](ticket-15-bounded-csv.md)。

root核对GitHub API精确SHA、run/job每一步success及归档metadata；原JSON保存在`.verification-results/ci-io-csv-rate/`。未下载归档内部文件，CI精确JUnit数、覆盖率、资源数值与jar哈希以各环境原始报告为准，不把本地数据写作Linux观测。09本机all末尾缺少负控JDK的历史失败仍保留；此次两端设置真实JDK21的完整all已通过。

| Artifact ID | 名称 | 字节数 | 整体SHA-256 | 到期时间UTC |
|---|---|---:|---|---|
| 11283057442 | java25-ubuntu-latest-c2f0f6b4118a3a059993ef53f2d62f547151c560 | 4127965 | 058dd8695ccf80caff9c4eec6d61a43abd66ce366455b98959fca897d89fed46 | 2026-11-02T19:25:50Z |
| 11282987968 | java25-windows-latest-c2f0f6b4118a3a059993ef53f2d62f547151c560 | 4264389 | aa3d2e4feaea631cdd59aec94cab9620a7ab4297a6e37e1ae1afe6fa05d767fb | 2026-11-02T19:28:43Z |

表中digest属于整个artifact，不是库jar。工作流归档各自summary、质量报告、依赖账目与消费者输入，未发布制品。
