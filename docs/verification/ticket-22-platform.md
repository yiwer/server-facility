# 票 22：Boot 4 平台与工具链子集证据

日期 2026-10-04。非发布集成复核完成，票22 `closed`，解除23前置。本票允许的窄迁移边界是 **工具链/依赖子集通过、主库 Jackson 编译明确失败**，不是全库绿色或可发布版本。Linux 与完整同产物保证由 24 闭合；22 未执行目标 Linux 验证，不借用 04/05 旧平台结果。

## 被测提交与最终同步

- 独立探针、runner、workflow 和初始目标模型：`0f15f1309b10dafc0684642cc4abe3e608580ae9`，开始时工作区干净。`java verification/Verify.java platform --fresh` 成功；日志 `.verification-results/20261004-002422-214-platform/`。探针及 runner 在后续 05 同步中没有变化，已逐路径确认 diff 为空。
- 合入 05 后的根源码、测试与 POM：`7e4215b1dd96309fc71f3a35eb282f6b7e18308d`。实际 `clean verify` 失败于主 compile，**68 个 Jackson 类型诊断、未执行 testCompile/Surefire**；逐条文件/行列/类型/owner 见 [精确交接](ticket-22-jackson-diagnostics.md)。没有达到 javac 默认100条截断，没有非 Jackson 诊断混入。
- 已合入最新 integration `2304a57103b8c6f6a0791a783b80440dd652c1b0`，本树合并提交 `fac1a33c028753e5b61a4e17dcaae987227eb7f1`。对比 7e4215b 的 `src`、`pom.xml`、`verification`、workflow 均无变化；只新增中央文档/CI关闭记录，因此不重复同源全量测试。
- 04 新增 `FacilityHttpErrors` 的 mapper 公开类型与自动装配 fallback 纳入 23；05 移除私有 413 mapper，诊断由69减为68。05 显式 `junit-jupiter-params` 保留，由目标 BOM 解析6.0.3；05三个真实HTTP场景的容器自动装配进口已迁移。
- 集成复核：先把只更新05 CI文档的 `bfbc3d9` 合入票22为 `7257aa54ae8fc29944adde806e227aa8fe728377`，再从干净 integration `bfbc3d9` 以 `--no-ff` 合入为 `be8ea22ce7cd909e60d7e13e9913b591143d070e`。主checkout的源码/POM/verification/workflow与被测7e4215b一致，独立探针与被测0f15f13一致；root另行审阅后确认符合22例外。仅补中央文档、ADR部分替代与状态，不重跑同源全门，不push。

## 环境与可重放命令

Windows 11 10.0 / amd64；Oracle JDK `25.0.4.1+1-LTS-5`；Wrapper Maven `3.10.0`；Asia/Shanghai、runner Locale zh_CN、源码/测试 UTF-8。Maven诊断运行时设置 `MAVEN_OPTS=-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US` 以保存可读的精确英文错误。没有数据库或外部业务服务。

```text
java verification/Verify.java platform --fresh
mvnw.cmd -B -ntp -C -s verification/settings.xml -gs verification/settings.xml -Dmaven.repo.local=<本树隔离仓库> clean verify
mvnw.cmd -B -ntp -C -s verification/settings.xml -gs verification/settings.xml -Dmaven.repo.local=<本树隔离仓库> help:effective-pom -Doutput=<报告>/effective-pom.xml
mvnw.cmd -B -ntp -C -s verification/settings.xml -gs verification/settings.xml -Dmaven.repo.local=<本树隔离仓库> dependency:tree -DoutputFile=<报告>/dependency-tree.txt
mvnw.cmd -B -ntp -C -s verification/settings.xml -gs verification/settings.xml -Dmaven.repo.local=<本树隔离仓库> dependency:resolve
```

最终根模型/编译日志在 `.verification-results/ticket-22/final-7e4215b/`：`root-verify.log`、`model-resolution.log`、`dependency-tree.log`、`effective-pom.xml`、`dependency-tree.txt`、`dependency-resolution.txt`。模型解析和 tree 均成功。合并调用中的 dependency resolve 也使用 outputFile，会覆盖 tree，因此已将该输出保留为 resolution 文件并单独重取真正树状 tree，没有把扁平列表冒充依赖图。

## 实际结果

| 边界 | 结果与证据 |
|---|---|
| Jupiter / Platform | 6.0.3；`PlatformTest` 2项、`TechnologyModulesTest` 1项，真实 Surefire JUnitPlatformProvider 发现 |
| ArchUnit JUnit 6 | 1.5.1；`EngineArchitectureTest` 2条规则，真实导入Java25 record |
| 正向发现 | **5 tests / 0 failures / 0 errors / 0 skipped**；`platform-positive/surefire-reports` |
| Jupiter 故意失败 | 同样5项；恰好 `targetPlatformAndJupiterAreActuallyLoaded` 失败，进程exit1；`platform-jupiter-negative/surefire-reports` |
| ArchUnit 故意失败 | 同样5项；恰好 `engine_negative_control` 失败，进程exit1；`platform-archunit-negative/surefire-reports` |
| 字节码/processor | 实际读取 classfile **69.0**；Lombok builder 编译并执行；Boot4配置processor生成 probe.limit/probe.label metadata |
| JaCoCo / dependency analyzer | JaCoCo0.8.15处理Java25并记录fixture的9条已执行指令；dependency3.11.0读取实际classfile、failOnWarning成功。探针覆盖比例不是库覆盖率 |
| 技术模块 | 实际编译并加载14种 Boot 类型，核对来源为相应4.1.1 JAR；含保持原包的Filter/Task/Message与迁移的Jackson/Tomcat/MVC/context/ErrorPage |
| 根目标依赖 | Boot4.1.1 / Spring7.0.9 / Jackson3.1.5 / annotations2.21 / JUnit6.0.3 / Tomcat11.0.24 / Servlet6.1.0；无Jackson2 core/databind或旧Java8模块；完整图在最终报告目录 |
| 根质量门 | 实际编译报68项已逐条登记的Jackson错误。主库测试/覆盖率/依赖analyze/五条架构规则、普通jar及独立消费者未执行成功，不能记为绿色 |

探针普通jar SHA-256：`c54d59bbb1b331c2db5f0176567072ad554c51cc1a11ebaa45d598f7bf6783fa`。它不是 facility 库制品。fresh 报告保存 probe jar、metadata、effective POM/tree、fixture所有源码/POM和逐文件 SHA；summary 明示 `scope=toolchain-only`。最终根模型另有自己的精确源 SHA。

### TDD 与失败证据

原始日志均保留于 `.verification-results/ticket-22/tdd/`，不放 target 避免 clean 删除。

- `01-red-platform`：旧 Boot3.5.16 与目标4.1.1断言冲突；`02-green-platform` 同一断言在目标 BOM 通过。
- `03-red-archunit`：缺引擎/API不能编译；`04-green-archunit` 添加JUnit6适配器后两引擎实际执行。
- `05-red-processors`：Lombok依赖缺失导致编译失败；`06-green-processors` 补齐依赖和显式processor后，生成builder、metadata及69.0 classfile通过。
- `07-red-dependency-analyzer`：明确报告3个聚合传递API与2个聚合依赖；`08-green-dependency-analyzer` 仅复用根已有的精确聚合/引擎例外后通过，没有宽泛ignore。
- `09-red-boot4-http-imports`：合入04后暴露旧错误页包，和Jackson共72项；按官方JAR归属修正后移除这些非Jackson诊断。
- `10-red-technology-modules`：只有core/starter-test无法编译拆分技术类型；`11-green-technology-modules` 显式模块后5项通过。随后空仓库完整模式同时完成双引擎负向控制。

## Q01–Q10 / J01 映射与后续责任

| 标准 | 本票证据 / 范围 |
|---|---|
| Q01 契约追踪 | FR01/02/10、AC01/02/09/13：版本/模块/引擎/字节码/处理器探针、实际依赖图、逐条Jackson owner；ADR0045部分替代0024 |
| Q02 正常与边界 | 正确/旧平台版本、缺技术模块、实际classfile major/minor、发现完整性。未改业务null/默认/≤0政策，相关业务边界归原能力票及23/24 |
| Q03 真实接合、J01平台 | 真实Central解析、javac/processor/两个真实引擎/分析插件，无内部mock。真实应用HTTP/Bean让位/缺类不是本子集，交23/24，不提前标绿 |
| Q04 故障与并发 | 两引擎各自明确故障控制；精确失败项和exit验证。工具链迁移无新增并发状态机；主库并发语义未改 |
| Q05 有界资源 | Surefire probe 256MiB；runner Maven每步1200秒期限，超时结束子进程树；fixture输入固定小模型，下载/证据仅本树隔离目录。实际HTTP/流预算归24复验 |
| Q06 独立样本 | 期望版本/类型模块来源为官方POM/JAR；21 literal JSON金样未改，兼容与wire差异归23；不以自写自读替代样本 |
| Q07 可重放 | 固定14类型有限枚举、固定正/负引擎控制与命令；没有新增解析/数值/业务状态机，不增无意义fuzz框架 |
| Q08 环境诊断 | 上述精确SHA、Windows/JDK/Locale、隔离仓库、完整日志、input/jar SHA和依赖；Linux未执行，24负责同产物跨平台 |
| Q09 质量门 | 根88/88/75、五条ArchUnit、failOnWarning保持；只改ArchUnit adapter精确坐标。05旧平台1323项，目标主库0项执行因compile受阻；独立5项不混入主库计数 |
| Q10 可审阅 | 代码/ADR/账本/精确诊断/本报告同票；仅使用22明确允许的非发布例外。全门欠项必须23/24关闭，不进入主线/发布 |

24 的缺类组合清单与 Servlet6.1 新redirect/Charset包装器覆盖责任见 [迁移登记](../building/platform-migration-inventory.md)。23 须清空68项Jackson主编译错误，并处理尚未执行的JSON/HTTP测试和独立consumer里的旧 mapper/builder/converter签名；新的非Jackson问题不得泛化归入这份例外。
