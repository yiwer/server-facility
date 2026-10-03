# 票24：Boot4目标平台的同源跨平台闭合

2026-10-04，集成提交`80670fac2fed7068e364bfdd8dd4bcae97e76fd3`通过[GitHub Actions 37143955128](https://github.com/yiwer/server-facility/actions/runs/37143955128)。两端实际执行成功，关闭24平台门，以及03/05/06/13/17剩余的适用平台验证项。票33仍负责所有33票最终同候选组合；本记录不宣称尚未实现的业务协议通过，也未发布制品。

| 环境 | Job | all --fresh、platform --fresh、归档 |
|---|---|---|
| Windows | [111263954944](https://github.com/yiwer/server-facility/actions/runs/37143955128/job/111263954944) | 全部success |
| Ubuntu | [111263954988](https://github.com/yiwer/server-facility/actions/runs/37143955128/job/111263954988) | 全部success |

工作流固定Wrapper/JDK25及实际JDK21负控，使用Temurin配置`25.0.4+101.0.LTS`与`21.0.12+101.0.LTS`。两端完整库质量门、普通jar/core/crypto、Jackson字面金样与真实HTTP双应用、3种Web场景、5种实际Maven图/11个非Web进程、有/无Tika上传、5次应用资源周期和三项先决条件负控均由统一runner执行。独立platform步骤另外验证Jupiter/ArchUnit引擎正反控制、processor与字节码，不混入主库测试计数。

root通过GitHub API核对精确SHA、run/job/每一步success及两份归档metadata；原API JSON保存在`.verification-results/ci-ticket-24/`。没有下载归档内部文件，因此各CI环境精确JUnit数、覆盖率、堆观测与jar hash以其原报告为准。本地1449项与jar hash仅是[本机all报告](ticket-24-platform-integration.md)的数据，不代替Linux观测。

| Artifact ID | 环境 | 字节数 | 整体SHA-256 |
|---|---|---:|---|
| 11281582047 | Windows | 4050224 | a805a001de8e75b774904da1d7b1666f47432557bd46b0b7d5bee626ddda30e4 |
| 11281412714 | Ubuntu | 3928376 | 3283693acf9eb88a180ca1cafd32ee4ad4be6b12447c1cdcbaf88d1cb1dca5ae |

归档名为`java25-<os>-80670fac2fed7068e364bfdd8dd4bcae97e76fd3`，保留至2026-11-02。表中digest属于整份artifact，不是jar。工作流包含summary、Surefire/JaCoCo、有效POM/树、实际classpaths与矩阵输入，以及普通jar归档。

## 关闭范围

- 03：Boot4的实际Executor选择、平台/显式虚拟线程、deadline/取消/资源回归随全门完成。
- 05：Servlet6.1新增Charset及三种redirect重载在24实现并真实HTTP验证；原SSE/大流/断连/请求体边界随双端全门通过。
- 06：实际请求来源、代理政策、Callable/MDC异常安装与恢复、ASYNC/ERROR/断连在目标平台双端通过。
- 13：Windows NTFS/junction/占用与Linux hardlink/symlink/权限分支，流预算和cleanup/完整性回归；24补足普通jar Tika有/无依赖图。
- 17：固定历史reader、真实JCA故障、有限堆超长key拒绝及普通jar受约束回执消费在双端执行。
- 24：22/23迁移线恢复完整绿色，J01/J02和适用Servlet/消费者矩阵闭合；非BOM业务政策、授权/事务/恢复、其他未实施能力继续由对应票负责。
