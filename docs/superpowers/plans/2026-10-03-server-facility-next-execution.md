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

记录按实施时点保留；较早的 pending 状态由后续有确切 SHA 的通过记录更新，逐票当前状态仍以正式票为准。

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
- 2026-10-03：包含票 01/02/03 的 0e8d586 通过 GitHub Actions 37132912477 的 Windows/Ubuntu 完整验证与归档，见 [票 02/03 CI 记录](../../verification/ticket-02-03-ci.md)。03 仅保留目标 Boot 4 复验待票 24，不反向依赖票 33。当前并行实施票 04、05、21。
- 2026-10-03：merger 从干净 `9fc7005bbbee3c5d76b426258f45c971c45c55a7` 以 `--no-ff` 合入票21 `a93992c7f5177c5eb984ef9a7f970bd9686f0d4a`，合并提交 `c7d1b77494718defe90e0f3bf7ef9a567f1e8ef5`，无冲突。实现提交 `57df02a`；被测干净 HEAD `a18b45f233741f9e88ec005bfcdff1fdc71afb47` 已包含前述 integration，最终票提交只补充证据与23交接。
- 21 Windows 原生 `java verification/Verify.java all` 通过：1265 tests、0失败/错误/跳过、5原架构规则，instruction93.8787%、line93.7636%、branch87.2832%，依赖门通过；最小普通jar消费者、JSON constructed/injected真实HTTP、两应用交错调用及关闭重建、5次独立JVM生命周期和3项工具链拒绝均通过。原始日志在 `E:\GenCode\server-facility-worktrees\ticket-21\.verification-results\20261003-233628-520-all`，TDD日志同工作树 `.verification-results/ticket-21-tdd`，详见 [票21证据](../../verification/ticket-21-json-expand.md)。
- 合并复核：主checkout的`src`、`pom.xml`、`verification`、`.mvn`、Wrapper与workflow同被测`a18b45f`完全一致；JSON两模式实载普通jar SHA均为`61a10a50d223dd760f073bf7cd915b1761aeb4766f6e9475846012d2b11f6116`，与构建报告一致。中央文档登记ADR-0044与27条实际ADR数量、更新测试快照；未修改产品实现、未重跑同源码全量测试、未推送。
- 21 保留 `verification-pending`，仅等待合入后新增场景的Linux CI证据以闭合Q08/Q10，不能借用此前01/03绿色；workflow已归档JSON消费者源码/POM/effective-POM/tree、输入/输出金样及各自SHA。22/23只进入当前非发布集成线，24恢复完整平台门后才进入候选发布；21的通过不表示Boot4/Jackson3已完成。
- 2026-10-03：root 推送的 ee2e9cc 通过 [GitHub Actions 37134465187](https://github.com/yiwer/server-facility/actions/runs/37134465187) 的 Ubuntu/Windows 完整 `all --fresh` 与归档，新增 JSON constructed/injected 真实 HTTP 消费者在两端均执行。见 [票21 CI证据](../../verification/ticket-21-ci.md)。21 已关闭，22 的前置阻塞解除；目标 Boot4 平台尚未宣称通过。
- 2026-10-04：merger 从干净 `5428982` 以 `--no-ff` 合入票 04 最终 `6c05dc8ab63ac9fdbedaf5c7d47f4945060962d3`，合并提交 `3722ba0f1ec1f0761ed587f8a2ca8384c9f2cac6`，无冲突。最终被测产品提交 `37ee5f5`，后续票提交只记录证据；Windows 完整 `clean verify` 与普通 jar integration runner 均为 **1276/0/0/0**，5 架构规则及原覆盖率/依赖门通过。instruction93.1880%、line93.3512%、branch86.5100%；详见 [票04证据](../../verification/ticket-04-http-errors.md)。
- 合并复核：`src`、`pom.xml` 与 `verification` 与 `6c05dc8` 及被测 `37ee5f5` 相同；主 checkout 未改产品。中央登记 ADR-0027、ADR-0003 部分替代范围、28 条实际 ADR 数量和此次测试来源；未重跑同源全门，未推送。票04继续 `verification-pending`，合入后 Windows/Linux `all --fresh` CI由root触发并闭合，不能借用票21绿色；05/06/12组合及24目标平台仍明确各自责任。

- 2026-10-04：merger 从干净 `66bf4d0` 以 `--no-ff` 合入票05最终 `ee45b78c88f4e88aee7ccf15c957f03a6995c449`，合并提交 `1c61c1b4c3539a4396794fed3c8c3f3379c7fb2c`，无冲突。逐路径确认 `src`、`pom.xml`、`verification` 与被测 `5a59d2f23f36d38f088753232d125db7e3e9878a` 相同。Windows 完整 `clean verify` 为 **1323/0/0/0**、五条架构规则与原覆盖率/依赖门通过；instruction92.9939%、line93.3940%、branch86.1614%，真实HTTP/96MiB子进程见 [票05证据](../../verification/ticket-05-bounded-web-streams.md)。
- 中央登记 ADR-0028/0017部分替代关系、29条真实ADR数量与1323测试来源，修正C2中repeatable旧无界例外为已批准正预算。未改产品、未重复全量测试、未推送。票05保留 `verification-pending`，新增Linux场景及Servlet6.1迁移门分别由集成CI和24提供，不能借票04绿色关闭。
- root 推送的 `66bf4d04be3b40a5d81a80fa418cbe213c2307f4` 已通过 [GitHub Actions 37136353128](https://github.com/yiwer/server-facility/actions/runs/37136353128) Windows/Ubuntu全部步骤与归档。root通过API核对SHA、步骤和artifact，未下载内部日志，故未推定Linux精确测试数；[票04 CI证据](../../verification/ticket-04-ci.md) 闭合Q08/Q10，票04关闭。这个提交不包含05新增场景，05待办保持。
- root 推送的 `2304a57103b8c6f6a0791a783b80440dd652c1b0` 已通过 [GitHub Actions 37137011984](https://github.com/yiwer/server-facility/actions/runs/37137011984) Windows/Ubuntu的完整`all --fresh`与归档，包含05新增真实HTTP和受限堆进程。见[票05跨平台证据](../../verification/ticket-05-ci.md)。05仅剩24负责的Boot4/Servlet6.1重载与目标平台复验，Linux不再待办；当前22/23平台中间态仍不能作为发布候选。
- 2026-10-04：merger先将干净 integration `bfbc3d9b3e2d25185c53153470fdc77bfcce19d2` 的05 CI文档同步至票22，分支提交 `7257aa54ae8fc29944adde806e227aa8fe728377`，再从同一干净integration以 `--no-ff` 合入为 `be8ea22ce7cd909e60d7e13e9913b591143d070e`，无冲突。主checkout的 `src`、`pom.xml`、`verification`、workflow与最终被测 `7e4215b1dd96309fc71f3a35eb282f6b7e18308d` 一致；独立探针与runner仍等同其被测 `0f15f1309b10dafc0684642cc4abe3e608580ae9`。
- 22 Windows空仓库 `platform --fresh` 通过5项独立正向测试、Jupiter/ArchUnit各自恰好1项故意失败控制、14种实际技术类型加载、69.0 classfile、Lombok/config processor、JaCoCo及dependency analyzer。原始日志 `E:/GenCode/server-facility-worktrees/ticket-22/.verification-results/20261004-002422-214-platform/`；最终根effective POM/tree/resolve成功，根真实 `clean verify` 停在 **68条Jackson主编译错误**，逐项交23，未执行主库testCompile/Surefire。最终根日志在同树 `.verification-results/ticket-22/final-7e4215b/`；完整Q01–Q10和诊断见[票22报告](../../verification/ticket-22-platform.md)与[精确清单](../../verification/ticket-22-jackson-diagnostics.md)。
- root独立审阅确认22满足批准的中间迁移例外；中央登记ADR0045、0024平台版本部分替代、30条ADR以及当前Boot4/Jackson3中间态。22 **closed**，解除23前置；68条类型编译交23，主库完整门、同产物Windows/Linux/消费者和Servlet6.1新重载接合交24，不能用这些已分配责任反向阻塞22。旧平台1323项仅保留为参照，不标为当前目标全绿。本次未改产品、未重跑同源全门、未push。

- 2026-10-04：root复核23核心diff并接受handoff；merger从干净 `042006daaed1451476d355d1a3199eb249202825` 以 `--no-ff` 合入 `1e216d1980505d37974d4559a502c38813b23f80`，合并提交 `04dbd961c2b6f1d100f5e2b42174a66762bc95c4`，无冲突。`src`、POM、verification、workflow、Wrapper与被测 `07682f458549dce36ed786ae55b25a958162541f` 完全一致。
- 23 Windows目标 `all` 全PASS：1335/0/0/0、原5架构与覆盖率/依赖门、普通jar非Web/真实HTTP金样、两应用交错/关闭/重建、5轮独立JVM资源周期与三项工具链负控。instruction92.786%、line93.325%、branch85.979%；jar SHA256 `d5b408f6b1bbed62df88b37cc53ff121718bfb600b206eb5c3531d01ecf4c894`。原始证据 `E:/GenCode/server-facility-worktrees/ticket-23/.verification-results/20261004-011808-650-all`，含冻结金样、消费者输入、POM/tree、日志与普通jar；[完整报告](../../verification/ticket-23-jackson3.md)。
- 68条旧Jackson编译缺口清零，额外Spring7源/ProblemDetail协议/error.path迁移按报告分别登记。中央登记ADR0046/0044部分替代、31条ADR以及当前目标本机绿色；23 **closed**，解除24前置。24仍负责Linux、Servlet6.1新重载、完整缺类/覆盖/注册矩阵；03/05的待验证状态保持。本次未修改产品、未重复同源全门、未push。

- 2026-10-04：merger 从干净 `7e168199a812fba6396540922241036d85767d8e` 合入票18。此前只读复核4项契约修复与独立消费者无阻断，浅不可变 package 文档收尾已含。完整被测源码 `5c29047b4b25cce27e67f752db64b29380cad984` 的 Windows `integration` 为1339/0/0/0、5架构/原覆盖率/依赖门通过；普通jar核心消费者64MiB/seed180041/512、普通与JSON真实HTTP/双应用消费者、三项负控均PASS。
- 18票据与报告按真实summary闭合后合入，产品/POM/verification与被测源码一致；中央登记ADR0041及32条实际ADR，保留0007/0010理由。本票纯核心值没有文件系统或服务端平台政策，18 **closed**；后续24/33候选平台组合独立登记，未声称本次Linux已运行。未改产品、未重复同源全门、未push。

- 18准确合并链：票分支最终 `0b174dfd1db9f399ba467612a6dfa0f845f35f92` → `--no-ff` 合并 `cc262357f3f8d444a2027bf17f1ca589bdc6559a` → 中央文档 `8ca516c345928a996737ac568b32f5b617526f1a`。
- 2026-10-04：按协调顺序先合票06（分支 `030622b4aba3b760b8a0727d69423cf3f3337b06`，已包含8ca516c），再让13同步复验，避免两票循环追tip。06被测源码 `ee95d0743d817424a293a29dd8fa719d54e91470` 的 Windows integration 为1368/0/0/0，5架构、原覆盖率/依赖门、普通jar/core/JSON真实HTTP双应用及工具链负控全部PASS；summary位于ticket-06工作树`.verification-results/20261004-014223-240-integration`。合并后src/POM/verification与被测源码一致。
- 中央登记ADR0029及0014仅来源假设部分替代，实际33条ADR。06保持verification-pending，仅本次Linux CI待闭合；目标Servlet6.1自身生命周期已验证，JWT归27，候选组合归33。未重复同源完整测试，未push。

- 06准确合并链：`--no-ff` 合并 `3f3487e1497d2c24fa12c0dea66f7a2ef82964c8` → 中央登记 `5faff896d04a1b15ed10310be81bed91a14121b7`。
- 2026-10-04：13先同步18、06并完成复验；最终被测源码 `e698642be82eb9d6d036e09a06dab21face34929` 的 Windows `mvnw.cmd -B -ntp clean verify` 为 **1433/0/0/0**，instruction92.8138%、line93.3884%、branch85.2437%，5原架构/原覆盖率门/依赖分析全部通过。普通jar SHA256 `dac3a1dc91d4706d3144336a2504bf4daacd5073f04db467e6f6ca1b4268cd34`；8次真实multipart请求、96MiB堆256MiB上传/100次故障、缺Tika与冷取消子进程见[票13报告](../../verification/ticket-13-upload-integrity.md)。
- merger从干净 `5faff896` 以 `--no-ff` 合入票13最终文档提交 `99a0eabc40519d8a63d1754f404747c3e79372da`，合并 `283f018635195bbafa57f028f4927770ea0eb35e`；src/POM/verification/Wrapper与被测源码相同。中央登记ADR0036、34条实际ADR和正上传预算例外，保留0001 optional理由。没有重复同源码全门，没有push。
- 13保持verification-pending：Linux hardlink/symlink/权限拒绝分支待root CI，24负责新上传/Tika普通jar完整optional矩阵。本次13的clean verify未执行独立integration消费者，不以06旧消费者结果代替；31上传至CSV、33候选组合独立登记，不反向创建实现依赖。

- 2026-10-04：merger复核17实际summary、密码原语diff与独立消费者，无阻断；从干净 `d4922df` 以 `--no-ff` 合入最终 `1234f2ae12438cb00f979a64c4f3875cda21d5df`，merge `538e7d9709ed928a9b46e3643d700b31243e842d`。src/POM/verification/Wrapper与被测 `6f7f06c10aee2717a90f783fb938df1b0e31156f` 完全相同，后续中央文档不改产品。
- 17 Windows integration 1442/0/0/0，5原架构/原覆盖率/依赖门、ordinaryjar/core/crypto64MiB消费者/JSON真实HTTP双应用/3工具链负控全PASS；jar SHA256 `2fa7c58a20b14ddc6fd65eb5da3465f6955b9e3db44e628b132de1280d456098`。原始summary在ticket-17 `.verification-results/20261004-015807-675-integration`，详见[报告](../../verification/ticket-17-crypto-legacy.md)。本轮integration没有all额外资源周期。
- 中央登记0040/0019部分替代、35条实际ADR。17仍verification-pending，仅同源Linux CI待24闭合；原协议/KDF参数不变、应用预算由可执行消费者展示。未重复同源码完整门，未push；主checkout释放，24可由此tip继续验证。

- 2026-10-04：merger从干净 `ddb7680` 以 `--no-ff` 合入24最终 `768c5267929604353695c06e47328abe50791485`，merge `78309a138080617b36ab074f6d10f80c50b21905`。根src/POM/Runner/Workflow与被测 `31e77656472cefa497804ac6da6aacec16a754ff` 相同；仅两个consumer的EOF多余空行已在最终分支删除，无执行语义变化。
- 核读 `.verification-results/20261004-020830-494-all/summary.txt`：Windows空仓库all--fresh PASS1449/0/0/0，原5架构/覆盖率/依赖门、普通jar/core/crypto/JSON双应用、3Web、5依赖图11JVM、Tika有无上传、5资源周期、3负控全过；jar SHA256 `4e1012daa5fa4a363c9c9d3827d4d0ee2bcaf5e0bc997f2c6bb94989f58ddac5`。独立platform工具链输入同源子集另已通过，见[报告](../../verification/ticket-24-platform-integration.md)。
- 中央登记0047和36条实际ADR：0047补全0015成对依赖装配、0028新Servlet6.1入口、0045/0046的平台验收责任，保留旧决定理由，没有虚构整票superseded。03/05/06/13/17/24仍待同源Linux适用证据；未改产品、未重跑同源码全门、未push。主checkout释放，root负责推送CI和准确闭合。


## 2026-10-04 同源Boot4平台门闭合

root将`80670fac2fed7068e364bfdd8dd4bcae97e76fd3`推送现有CI。Run37143955128两个OS的all --fresh、platform --fresh和归档全success；精确job/artifact/digest见`docs/verification/ticket-24-ci.md`。03/05/06/13/17/24据实closed，25/26/27解除24依赖；不代表所有33票已完成。未下载artifact内部文件，不混用本地/CI数值。
