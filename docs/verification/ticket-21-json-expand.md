# 票 21：JSON 扩展执行证据

状态：closed，Windows 本机验证及合入后的 [Ubuntu/Windows CI](ticket-21-ci.md) 已通过。下文保留本机验证时点的环境和结果；当时待 CI 项已由文末闭合记录补齐。原平台仍为 Boot 3.5.16 / Jackson 2.21.4，未替换 Boot/Jackson 主版本。应用归属及迁移边界见 [ADR-0044](../adr/0044-json-application-scope-expand.md)，完整影响清单见 [迁移登记](../building/platform-migration-inventory.md)。

## 基线与 TDD

专属工作树 `E:\GenCode\server-facility-worktrees\ticket-21`，分支 `codex/ticket-21`。扩展前基线 `731598b`，Oracle Java `25.0.4.1`，Windows 11 / Asia-Shanghai，Wrapper Maven 3.10.0。Maven 依赖/安装坐标使用本工作树 `.verification-results/repository`，不使用并行工作树的共享 SNAPSHOT。

原始日志位于 `.verification-results/ticket-21-tdd`，不会被 clean 删除。`01-baseline-install.log` 是未修改生产代码时原平台的完整 clean install（包含测试、覆盖率、架构及依赖门）成功；基线 jar 另存 `baseline-731598b.jar`，SHA-256 `a98575a48a9d15d36dedd29b5aa88fc91448ae9d2b828505e9cbedfa1b53f0a6`。`11-baseline-constructed.log` 用该 jar 完成同一独立 Web consumer 的默认和定制协议样本。

| 循环 | 红灯证据 | 绿灯证据 / 公开契约 |
|---|---|---|
| 应用注入 | `03-injection-red.log`：JsonsApplicationScopeTest 缺 Jsons bean；`13-consumer-injected-red.log`：旧普通 jar 的真实应用缺 bean 启动失败 | `05-injection-green.log`：新注入入口与旧装配 4 项通过；两应用政策、关闭重建 |
| 构建期入口 | `12-builder-red.log`：公开 customizeBuilder 不存在，testCompile 失败 | `14-builder-green.log`：24 项通过；builder 政策覆盖 preset，原 JsonConfig 用例保持通过 |
| 字段输出预算 | `15-stream-output-red.log`：带预算构造器不存在 | `16-stream-output-green.log`：3 项通过；N−1/N/N+1、源关闭、旧入口 |
| 字段输入预算 | `17-stream-input-red.log`：带预算构造器不存在 | `18-stream-input-green.log`：8 项通过；补位/无补位 Base64 和解码边界 |
| 边界及重放 | 以上循环后增加契约边界回归 | `19-json-boundaries.log`：34 项通过；callback 顺序/null、用户 bean、根流/字段所有权、I/O 失败、未知长度 N+1 探测、≤0兼容、seed 210025 /128例 |

初始手写 HTTP 金样经基线探针校正了两项真实差异：UTF-8 Jackson generator 转义非 BMP，String 输出保留原字符；默认 global advice 的旧 envelope 是 HTTP 200。因此消费者分别保存 wire 样本，并显式启用已有 ProblemDetail 配置再冻结真实 400。未把这些旧缺陷伪报为新修复；失败探针日志 `04/07/09` 保留。最终 goldens 是独立字面文件；测试不在运行时调用同一个 mapper 生成 expected。

## 完整验证

实现提交 `57df02a`；被测提交 `a18b45f233741f9e88ec005bfcdff1fdc71afb47` 已合入 integration `9fc7005`（包含 01/02/03），仅 CHANGELOG 顶部冲突，已保留两侧条目。运行开始工作区干净。之后只补充票状态/执行证据/23 交接文档，没有改变被测 Java、POM、runner 或 CI workflow。

Windows 原生命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1'
$env:VERIFY_WRONG_JAVA_HOME='C:\Users\yiwer\AppData\Local\Temp\server-facility-research-tools\jdk21\jdk-21.0.12.1+1'
& "$env:JAVA_HOME\bin\java.exe" verification/Verify.java all
```

报告目录 `.verification-results/20261003-233628-520-all`，`summary.txt` 为 `RESULT=PASS`。Java `25.0.4.1+1-LTS-5` / Oracle，Windows 11 10.0 amd64，Asia/Shanghai、zh_CN。使用本工作树隔离且已有基线依赖缓存的 repository，`fresh=false`；CI 入口仍执行 `all --fresh`。没有借用其他工作树 SNAPSHOT。

| 门 / 场景 | 实际结果 |
|---|---|
| clean install / Surefire | 1265 测试，0 失败、0 错误、0 跳过；比 03 的 1251 增加本票 14 项，无删除/隔离 |
| ArchUnit | 原 5 条规则被发现并通过 |
| JaCoCo | instruction 17054/18166 = 93.8787%；line 3443/3672 = 93.7636%；branch 1661/1903 = 87.2832%，原 88/88/75 门保留 |
| dependency analyze | No dependency problems found；没有新增根依赖或 ignore |
| 最小普通 jar consumer | configured / override / invalid 通过；额外 5 次独立 JVM 启动/使用/关闭通过，256 MiB /45秒截止 |
| Web JSON consumer | constructed 与 injected 均通过；同一金样同时用于默认/customizer、MVC/service，后者覆盖两个存活应用交错调用、关闭其中一个、重建、存活方再次调用 |
| 工具链负向 | 真 JDK21、错误 JAVA_HOME、错误 Maven distribution SHA 均按预期拒绝 |

最终普通 jar SHA-256：`61a10a50d223dd760f073bf7cd915b1761aeb4766f6e9475846012d2b11f6116`。`14-json-consumer-constructed.log` 与 `15-json-consumer-injected.log` 的 `JSON_ARTIFACT` 均记录这个 SHA，并从隔离 repository 的普通 jar 加载；全量报告 hash 与消费者 hash 一致。每个应用最多 4 个 Tomcat worker、请求截止 5 秒，Web JVM 最大 256 MiB、运行截止 90 秒，两模式均正常退出。

根完整依赖树 `dependency-tree.txt` SHA-256 为 `7026cfd0eb520818c40abf2f93cfa4d2eb901ecdd2f166565b773250a0aea25e`；Web consumer 的 `json-consumer-dependency-tree.txt` 为 `f0144548657cf4c1f73742d6272cd0a90e64551e4905adaba9c98d9b42bd27a9`。实际 Web 运行含 Boot 3.5.16、Framework 6.2.19、Jackson 2.21.4/annotations 2.21、Tomcat 10.1.55、Logback 1.5.34；根测试 Jupiter 5.12.2 / Platform 1.12.2、ArchUnit 1.5.1、AssertJ 3.27.7、Mockito 5.17.0。完整 effective POM 与 tree 已归档，不由版本摘要替代。

runner 同时归档 `JsonConsumer.java`、独立 POM、`json-golden/` 原始输入/输出样本及各样本 SHA。workflow artifact 新增这些文件与 consumer effective POM/tree，保留原 Surefire、JaCoCo、日志和 `target/*.jar`。本报告不把 artifact 配置等同于 Linux 执行成功。

## Q01–Q10 / 接合映射

| 标准 | 本票证据及边界 |
|---|---|
| Q01 契约追踪 | FR-01/AC-02：ordinary jar / 版本登记；FR-08/AC-11：JsonsApplicationScopeTest + 真实 consumer；FR-09/AC-12：旧构造/新注入同样本；AC-13：本节与完整门。替代决策为 ADR-0044；旧静态入口未删除 |
| Q02 正常边界 | default/customizer、用户 bean、空/null、Unicode、时区、数值溢出、未知/非法/尾随；流 N−1/N/N+1、≤0、空字段、补位/无补位；callback null fail-fast |
| Q03 真实接合 | 本票 J01/J14：两个独立 Maven consumer、真实 MVC GET/POST；J05 的 JSON 部分：两应用政策与关闭重建；跨缓存/locale/log/executor 的完整组合由 23/26/33 负责 |
| Q04 故障并发 | 确定时点 I/O 读/写失败、未知长度探测、错误配置/依赖缺席；多 context 启动/关闭边界交错可重放，不靠随机 sleep。JSON 构建/字段编解码是同步调用，无内部取消/重试状态机；用户在使用时并发突变 mapper 不在新入口契约内 |
| Q05 有界资源 | 字段输入最多 N+1 探测，Base64 解码前长度闸门将临时数组限制≤N+2（固定分组舍入开销），结果严格≤N；成功/失败均验关闭与复用。JSON 完整字符串读取预算由宿主另设；旧无参/≤0仍无界，23 必须收缩新推荐入口。真实进程/HTTP/线程预算见上 |
| Q06 独立样本 | 人工 UTF-8 字面 JSON 在 `731598b` 验证，当前构造与注入共享同一文件；泛型独立输入、非法/尾随输入、HTTP 入口，不以本实现写后读作为唯一证据 |
| Q07 重放 | JsonStreamBudgetTest seed=210025，128 例、1–32 字节限额，成功/超限/重复复用；命令 `mvnw.cmd -Dtest=JsonStreamBudgetTest test`，需 JDK25。没有发现需保存的最小失败输入 |
| Q08 环境诊断 | 本文及 summary 记录 commit、JDK/OS/Locale、源输入、完整依赖、SHA 与命令；Linux 新场景待 CI，不借用票01/03的旧绿色。错误日志仅人工测试数据；生产 payload 安全边界仍由23关闭 |
| Q09 质量门 | 1265/0/0/0、5 架构规则、原覆盖率与依赖门全通过；无 skip、宽泛 ignore、删测或失败重跑掩盖 |
| Q10 可审阅 | 实现、ADR、迁移账本、USAGE/CHANGELOG、金样、TDD 原始日志与本报告齐备；保持 verification-pending，合入后 Linux CI 原始报告取得前不关闭 |

J03 的错误 envelope 由 04、J11 出站 HTTP builder 由 25、J16 全环境矩阵及 J15 扩大长稳由各主责票/33 负责，不反向声明为票21已验证。平台 4 的模块/引擎/公开签名尚待22–24；目标版本可获取性已登记，不能用当前绿色代表目标平台已通过。

## 合入后 CI 闭合

集成提交 ee2e9cc27fd53b3c5d0044258e64577075a75a0c 已通过 GitHub Actions 37134465187 的 Ubuntu/Windows `all --fresh` 及归档。本报告以上 Q08/Q10 的待 CI 描述是本机验证时的历史状态；现由 [本票 CI 证据](ticket-21-ci.md) 闭合，票 21 已关闭。目标 Boot 4 验证仍归 22–24，本文的旧平台边界保持有效。
