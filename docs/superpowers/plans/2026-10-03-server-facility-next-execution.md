# server-facility 下一代实施记录

集成分支：codex/server-facility-next。审查基线：0ee9d547022371ad31f885605e999de17ec22777。规格快照提交：6fb78dc6870ea240bb6abbda316fc035a495373f。

正式进度以各张本地票的 Status、未完成项和证据为准。本记录记录跨票集成决策与环境情况，不复制每张票的验收清单。

## 执行方式

- 每票独立分支与 worktree，开始前确认基于集成分支，完成前合入最新集成分支。
- 按已确认公共入口进行 TDD，每票的测试随实现交付；合入由 merger 角色复核并处理冲突。
- 01 优先提供 Java 25 构建与消费者基线；现有契约修复并行；21–24 形成目标平台迁移链。
- 全部任务完成后，从固定基线执行 Standards/Spec 双轴 code-review，修复结果再次验证。

## 环境

- 主机：Windows，JDK 25.0.4.1。
- 初始可用 Maven：临时工具目录中的 3.9.16；01 已加入正式 Wrapper 3.3.4，固定 Maven 3.10.0 与下载 SHA-256。
- Docker Desktop 引擎不可用；WSL 报告所需虚拟化功能未启用。用户已授权使用仓库 CI 进行 Linux 验证；01 已加入 GitHub Actions Windows/Linux 矩阵，运行 37131502759 已在两端成功，详见票 01 CI 记录。
- Windows 原生 PostgreSQL 18.6 测试二进制已从 Zonky Maven Central 分发下载，SHA-512 校验通过；仅解压到临时工具目录，不安装系统服务。官方项目支持 Windows，来源：https://github.com/zonkyio/embedded-postgres 。数据库场景仍需实际运行并记录结果。
- 已实际完成 Windows PostgreSQL 的 initdb、仅监听 127.0.0.1 的启动/status/fast shutdown 冒烟；进程已关闭，后续场景仍需独立事务与恢复验证。

## 集成记录

- 建立集成分支，提交已批准研究、PRD、测试策略与本地 tickets；具体当前状态以正式 issues 文件为准。
- 首批实现分支：codex/ticket-01、codex/ticket-02、codex/ticket-03，各自在独立 worktree 上执行 TDD。
- 2026-10-03：merger 从干净 `bf64995` 以 `--no-ff` 合入 `codex/ticket-01` 的 `59e4267`，合并提交 `fbdcd458955cd8b88be1fce4858d14b5b37a7e6e`，无冲突。实现提交 `82d8c6b`，已测试的干净 HEAD `7d68039`；合并不改变测试过的代码。
- 01 的 Windows `all --fresh` 已通过：1196 测试、0 失败/错误/跳过，五条原架构规则、原覆盖率门与依赖分析通过；独立消费者 configured/override/invalid、五次 JVM 生命周期与三项先决条件拒绝均通过。完整说明见 [票 01 执行记录](../../verification/ticket-01-windows.md)。
- 合并复核：`mvnw` Git 模式 100755，两个 Wrapper 脚本与配置在 index/worktree 均为 LF；主 checkout 实际执行 `mvnw.cmd --version` 成功返回 Maven 3.10.0 / JDK 25.0.4.1。workflow 从仓库根运行独立消费者入口，artifact 包含日志、有效配置、报告及 `target/*.jar`。没有重跑同代码完整测试，也未推送远端。
- 01 维持 `verification-pending`，待 root 推送合入后的确切 SHA、取得 Linux CI 实际结果并闭合 Q08/Q10。Boot 3.5.16 仍是中间站，不代表票 21–24 的目标平台迁移完成。

- 2026-10-03：merger 从干净 `e86e1e9` 以 `--no-ff` 合入 `codex/ticket-02` 的 `1546d87`，合并提交 `b9ba21984822ae62ed9e2845723883558cb6167a`，无冲突。实现提交 `49d18d3`；该分支已同步 ticket01 的 Java25/Boot3.5.16 基线和中央文档。
- 02 的 `mvnw.cmd -B -ntp verify` 在 `0e4f3ca` 通过：1215 tests、0失败/错误/跳过，ArchUnit、覆盖率门和依赖分析通过；instruction93.805%、line93.367%、branch87.382%。另有196消费者反序测试通过，17项Context生命周期测试覆盖关闭归属、父子关系、并行刷新、失败回滚、关闭竞争及固定seed状态序列。证据和Q01–Q10映射见正式票02及其 worktree 的 `target/evidence/ticket-02/integrated-verify.log`。
- 合并复核：主 checkout 的 `src`、`pom.xml`、`.mvn` 和 Wrapper 与完成分支一致；后续中央修改仅登记 ADR-0025、ADR-0006 的部分替代关系与文档数量。没有重复执行相同源码的全量测试，也没有推送。02为closed；01已由root推送并启动CI，Linux结果仍由root收集，不能据此提前关闭01。
- 2026-10-03：root 推送的 `e86e1e9` 通过 [GitHub Actions 37131502759](https://github.com/yiwer/server-facility/actions/runs/37131502759) 的 Ubuntu/Windows 完整验证，两端归档成功。票 01 已闭合 Q08/Q10 并关闭，解除票 21 的前置阻塞；[CI 证据](../../verification/ticket-01-ci.md) 记录确切 SHA、job 与 artifact 标识。当前已合入的票 02 仍需在最终候选跨平台组合中复验。
- 2026-10-03：merger从干净`731598b`以`--no-ff`合入`codex/ticket-03`的`b289d51664a3feb06d98b746a313f0ab4b265809`，合并提交`e32457fc10f56e1cc877c1208eb58b9f9167b91d`，无冲突。实现`68eb67f`、取消终态/入队竞态修复`05cf6bc`；root已审阅相关并发路径。
- 03最终被测源码`75ed834`的Windows `mvnw.cmd -B -ntp clean verify`通过：1251 tests、0失败/错误/跳过，instruction93.8400%、line93.7312%、branch87.1890%，原架构/依赖门通过。20轮RED→GREEN、256次取消/失败稳态、512次固定seed调度、128次完成/取消竞争及真实Boot platform/virtual接合详见[票03证据](../../verification/ticket-03-windows.md)。
- 合并复核：主checkout的`src`、`pom.xml`、`.mvn`与Wrapper与被测源码一致；中央提交仅登记ADR-0026/ADR-0002部分替代关系、26条ADR数量、1251测试快照及证据责任。没有重复执行同源码全量测试，也没有推送。
- 03保留`verification-pending`：此次Linux CI由root触发并收集；资源Required scenario整行未勾选，JDK25/Boot3.5.16资源与平台/虚拟线程已完成，Boot4由票24提供复验证据。J04 HTTP身份接合、J17锁接合以及33候选扩大长稳是各自主责票的后续责任，不反向添加03对33的前置依赖；未执行项目没有记为通过。
