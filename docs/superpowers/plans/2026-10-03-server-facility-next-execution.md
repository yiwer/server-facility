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

- 2026-10-04：merger核读09数值/回收/HTTP公开路径和两份summary，从干净8cfaa6e以--no-ff合入最终 `a83c2c6d6053a221184dc402859ea0204d0248d8`，merge `1845ed6451432473ddc479488f999dce7e065a81`。src/POM/verification/原Workflow与被测 `50d492d9160476052560910db5d1c1664e92d4ad` 一致。
- Windows主库1478/0/0/0、原覆盖率/5架构/依赖门、普通jar/core/crypto/新限流64MiB消费、全部JSON/Web/依赖矩阵/5资源周期均PASS；jar SHA `daad8d310de94ac375e00b61df2e5b6d78254374c5744de51ed943767d905a69`。第一次all最后缺真实非25JDK环境变量，原summary保留FAIL；同源码补跑prerequisites的checksum/missing/wrong三负控PASS。两份日志分别为ticket09的20261004-025443-462-all和20261004-030212-643-prerequisites，见[报告](../../verification/ticket-09-rate-limit-contract.md)；不偷换为一次all成功。
- 中央登记ADR0032/0014适用部分替代及37条实际ADR，并补CI归档新RateLimitConsumer.java的一行输入路径。没有改变产品/质量门或重跑同源1478测试，没有push。09仅Linux verification-pending；12/29/33的后续业务计费组合独立登记，15将同步此tip进行必要整合验证。

- 2026-10-04：merger核读14实际summary与OwnedPaths/PathIo/ZIP关闭发布/清理源码，无合并阻断。从干净c32e72e以--no-ff合最终 `6f545441eabf0a6ac0b4e8672d36ac6895e8d5e9`，merge `cf524ae4199f1478e89a1d56b686e196eb0c8e27`；此时src/POM/verification/workflow与被测 `58a1e83319a094224edc3d71fdeb2c34ef36d304` 相同。Windows integration 1507/0/0/0，原门/5架构/依赖、普通jar/矩阵/三负控PASS，64MiB的32/128MiB IO/200失败消费者通过，详见票14报告；仅Linux pending。
- 随后合CSV最终 `e906cbb3000859c9da1c9ca3cbc93331da8bff48`，merge `4a0802ab53fb55e602a2b644bdc898d5e17565a0`。CSV精确被测 `e1f078a6507d5a3f2dee00edd7ecfd4d83f45566` Windows all --fresh 1506/0/0/0，原门/5架构/依赖、全部消费者/矩阵/资源/三负控PASS；jar SHA `3694669e7f79d473d46746dfb895ab0517d8662647ae1cf1d917ca9ff82d802e`。CSV新必需图无Tika/POI使用IO2.20.0/Codec1.19.0已真实运行；64MiB/4m行76MB及200失败稳定，详见票15报告。
- 两分支共享c32e72e；产品变更分别限IO和CSV/POM，冲突只为CHANGELOG相邻条目及Verify相邻consumer方法/调用。merger保留io/csv/rate-limit全部独立方法和CI归档路径，javac --release25验证合并runner成功（`.verification-results/merge-14-15-runner`）。中央登记0037/0038、0021仅CSV部分替代、39条实际ADR与正预算政策。
- 按root协调没有为两份已验证源码重复全门；两者合并后是新组合，**这里不宣称合并后的同源测试通过**，root下一批Windows/Linux CI负责确认。09/14/15均保留新Linux verification-pending，31/33未来组合责任独立登记。未push，主checkout将在本中央提交后释放。


## 2026-10-04 限流／ZIP／CSV跨平台闭合

`c2f0f6b4118a3a059993ef53f2d62f547151c560`在现有CI run37147633803两个OS的all、platform与归档全部成功。09/14/15据此closed；精确job/artifact/digest见`docs/verification/ticket-09-14-15-ci.md`，原API JSON本地保存。未下载artifact内容，不混用本地与CI精确计数。当前共16票closed，其余继续沿依赖图实施。


## 票27集成与独立应用边界

- 2026-10-04：merger从干净 `5bdfcb0c624653bd8f1b48f167867b187a041749` 以 `--no-ff` 合入最终 `9e8e9377f122767841269b3aa9453427d8a7be4d`，merge `14dbec37dee57e20e7a854bc708e32bd409a2670`，无冲突。相对实际被测 `b2fcfbf4cee05b1176cca3373dbc9dd35b67fd3e` 只有3文档变化；主树src/POM/templates/verification/workflow/Wrapper与其完全相同。
- 核读 `.verification-results/20261004-040949-437-all/summary.txt` 为PASS：库1535/0/0/0、模板47/0/0/0，原覆盖率/5架构/依赖门、独立空格/Unicode路径构建、真实可执行包两线程模式HTTP、必需coverage缺失负控、既有普通jar/平台矩阵/资源循环/3工具链负控全部通过。精确证据见[27报告](../../verification/ticket-27-secured-template.md)。普通jar SHA `0e71d830446956894058e910c73a4c82599bdeb8557471aa86bcba797cdeb98c`；可执行模板SHA `974d26a86c6339c0a0042e6f2383d5c1fd73f5f6a62f6f2828a7c2962ce07469`。
- 中央登记ADR0050与40条实际ADR；0027/0029保留原理由，新增Security/Actor/异步接合补充关系。27保持verification-pending，Linux由root现有CI验证；28–30业务持久协议和33最终候选组合独立承担，不把它们反向当成本票实现前置。
- 合并不改已测产品，未重复同源全门，未push。产品merge tip已先交票11同步启动其最终全门；本次后续中央修改仅文档。


## 票25与11联合候选集成

- 2026-10-04：merger从干净5674f6d以--no-ff合25最终6c9bfb672a23cdd4a8733ebf51a84a5c7290164b，merge `4bc7d93aff425ac56f022e2b1dea7dbd9afdd1a5`。25业务源码、POM及examples相对被测a9c6400cf2109722cc7e67ccb00589f794eaa9c8无差异；该来源Windows all --fresh真实PASS库1541/0/0/0、独立聚合应用14/0/0/0，共66命令，详见25报告。27消费者在25分支合入时已经保留，组合仍需候选CI。
- 随后以--no-ff合11最终9fa8ec04588b4918082cb7da68b59e14838aab5d，merge `2a363fd9bbab8c307e989ccca5a402674481a96b`。11最终与被测956081d303243e81557de61e54615f9f5608c374的src/POM/verification/templates/Workflow/Wrapper相同。核读20261004-042235-271-all/summary.txt PASS：1555/0/0/0、模板47/0/0/0，原覆盖率/5架构/依赖门、64MiB claim消费者、32MiB clone/close-tables/Clock Error探针及全部已有消费者/工具链负控通过，详见11报告。
- 冲突限于CHANGELOG相邻条目、Workflow输入归档与Verify相邻方法；保留partnerConsumer、claimConsumer、securedTemplate全部调用/方法和归档路径，清除11 CHANGELOG遗留冲突标记。合并runner以javac --release25编译通过，输出.verification-results/merge-25-11-runner。产品文件只做Git无冲突整合，不改业务。
- 中央登记ADR0034/0048与42条实际ADR，0017/0018仅按明确范围部分替代。11/25/27均verification-pending；不同来源的Windows计数不能合并为新来源通过，root下一批同源Windows/Linux CI验证联合候选。未重跑已通过的同源全门、未push；中央提交后释放main。


## 票16有界Excel集成

- 2026-10-04：先按root授权将Unicode JVM边界修复合入为7a78660并push；随后仅将诊断输出上限修复cherry-pick为49b3148并push供CI11取得真实失败摘要。旧Windows CI失败与局部14/47通过均保留在25报告，没有把诊断重跑视为业务通过。
- 16从该公共修复冻结源码 `99ae71adabb6ada6c3a346ea142c7bf666b7a25d` 执行Windows `all --fresh`，`.verification-results/20261004-053114-083-all/summary.txt` RESULT=PASS。库1600/0/0/0、5原架构/依赖门、instruction24375/26289、line4775/5072、branch2555/3014；92命令全部通过，含普通jar/4 Excel图/完整平台/partner14/template47/资源/全部负控。jar SHA `12c2113e54d8ec3552753ff408e41f49fce0bf24690f05e2d5805ae03cb81bef`。
- 正式报告记录真实12MiB属性SAX前分配OOM的RED与64KiB事件间读取预算GREEN；64MiB child处理400,000行、200失败，线程8→8、保留堆13,559,904 vs13,969,056，无自有临时文件遗留。独立xlwt/XlsxWriter样本及openpyxl最终导出oracle均有证据。没有新XML词法器、全局POI临时策略或通用报表框架。
- 后同步49b3148仅为Verify失败tail10,000→3,000及25报告；产品/POM/测试/消费者/templates/examples/工作流与被测99ae71a相同。最终票分支 `cdefb01da542549240f8e4a0fb2c886e002dca69` 从干净49b3148以--no-ff合入，merge `7ab12b9cca78dec2f35269dd65f168b58f39c887`，无冲突。未重复相同产品全门。
- 中央登记0039及0021明确部分替代、43条实际ADR。16保持verification-pending：新Excel源Linux及POSIX文件占用待CI；11/25/27原CI问题仍按其真实状态处理。31/33未来业务/候选组合责任独立，不反向阻塞本票。Excel合并未push，中央提交后释放main。


## 2026-10-04 claim／Excel／受保护模板跨平台闭合

- CI12 run37156503739 的精确候选 `2b06f527a24602842721c4ed800ad71bca255319` 在 Windows/Ubuntu 的 all、platform、归档全部 success，原API JSON保存 `.verification-results/ci-11-16-27/`；精确 job/artifact/digest及证据限制见[验收报告](../../verification/ticket-11-16-27-ci.md)。没有下载artifact内部文件，不把Windows本地计数当Linux观测。
- 11、16、27据此closed，正式票合计19项closed。历史CI8–11失败记录继续保留；Unicode原生agent/argv与短路径问题均有实际RED/GREEN和最终CI证据，未删门。12、28–31与33继续负责自身后续组合。
- 25新增Inventory `[null]` 产品修复在CI12之后才以 `6ff81c371b1fe171dc11a88b4d6d5c331e94e0c2` 合入，15测试组合待CI13，本票仍verification-pending。此次仅提交验收与状态文档，未修改产品或重复已通过的全门，未push。


## 票26应用观测联合候选

- 2026-10-04：核读票26冻结 `72a37b6e1981e12a5dc292e22d55c28d5b93c5a8` 的 `.verification-results/20261004-054819-067-all/summary.txt`，92命令RESULT=PASS。Windows库1614/0/0/0、模板52/0/0/0、partner14/0/0/0，原88/88/75、5架构/依赖门、普通jar/真实Web/观测两应用/5资源周期/全部负控通过；库jar SHA `e97cedb5ab07ac9cabe638bf001ded6a5bb2f22ac521351fe09755d3ebc59595`。精确范围见[26报告](../../verification/ticket-26-host-observability.md)。
- 交接 `f11ab410bb0264b336f622ffce3d80896044747d` 已包含main6ff81c3；merger先将CI12文档a271061同步为 `e941a38ec002dcd579114fc2f33918fca3adf119`，再从干净a271061以--no-ff合入，merge `1b65e35643d897fa6f3ae8dc8a3800d10c8f5e4b`。合入后的src/POM/templates/examples/verification/workflow与交接分支完全相同，无冲突或额外产品改动。
- 中央登记0049、44条实际ADR及0010/0020/0022/0027/0029明确部分替代范围，历史理由保留。26冻结门不含后续Windows短路径修复及25第15项库存null场景，25/26均保持verification-pending，由本次授权push启动的CI13联合验证；不把多个来源的本地结果拼成新候选通过。未重复已通过的同源全门。

## 2026-10-04 外部HTTP与观测跨平台闭合

集成90098104ec8bb0edcc3eb93db848d4f2f0f51207通过现有CI13 run37157891623的Windows job111305028022与Ubuntu job111305028159；完整all、独立platform和artifact归档全部success。25/26 closed，当前21张票closed。公开元数据与精确范围见[25/26 CI报告](../../verification/ticket-25-26-ci.md)；不把本地1541/1614或14/15/52计数充作Linux精确值。07/08/10/12/19/20/28–33及最终双轴审查继续执行，最终33仍须单一候选全组合。


## 票28持久业务模块集成

- 2026-10-04：merger核读28 `.verification-results/20261004-061826-432-all/summary.txt`（冻结4a5ad5d，92步骤PASS）及 `.verification-results/ticket-28/final-template/summary.txt`（最终e0fd5b3，模板76/0/0/0、质量门、PostgreSQL真实打包HTTP/重启、普通jar一致与coverage负控PASS）。只对整秒预算守卫及两个回归进行root批准的最终模板完整子集复验，报告明确不冒称最终来源又执行全库all。
- 从干净 `ca6816ccb2e781854bd628bcf91801835e6f0a66` 以--no-ff合入 `0a6b18578bd566f6ca07a094caffe3d5820cf203`，merge `917505f7c20dc76b9de3c681443b62150a479d93`；最终分支比被测 `e0fd5b38844122e1a97b8bc816a93c079ccb8d9b` 仅2个票/报告文件，合入后src/POM/templates/verification/workflow与被测完全相同。最终模板jar SHA `b62ead82dd2b6283ccd24720fcc0606027091b955968ba2afc432e618373b574`。
- 中央登记ADR0051对0050的扩展及45条实际ADR；README/构建入口明确integration/resources/all需要PG_BIN与PostgreSQL18.6固定原生工具，fast/库verify不需数据库。库未增加持久依赖；迁移为同受控DataSource、必须成功应用V1/V2。此前只读审阅的不同Flyway数据库/空目录问题已有真正RED和修复后GREEN。
- 28仍verification-pending，root将用最终同源Windows/Linux CI闭合；29/30自身命令事务及进程故障协议独立承担。25/26的CI13闭合状态已同步中央入口，正式closed仍21项。未重复同源库全门，未push。


## 票07本地互斥集成与CI14保留失败

- 2026-10-04：merger核读07报告和`.verification-results/20261004-064526-873-all/summary.txt`；冻结`df7f7889d558d37af7a2daec0a7432862de670d1`的Windows 97命令PASS、库1637/0/0/0、原覆盖率/5架构/依赖门、旧SPI普通jar消费者及64MiB 250000次新key轮转均通过。该来源不含28 PostgreSQL，不能作为合并后联合证据。
- 从干净`f4837ee1311b775b8890878177dade647d05562d`以--no-ff合入最终`4b4a89c6efd7ad05103784d65d53d85ed427eadf`，merge`1f6109a77a7b5dbfc778e7425c6f443b161b57ac`，无冲突。合入产品/测试/runner与07交接分支完全相同；中央整理0030/0016部分替代、46条实际ADR。07保留verification-pending，不重复冻结来源已通过的全门。
- CI14 run37159629444在精确候选f4837ee结束：Ubuntu all/platform/归档success，Windows all失败而platform/归档success。公开annotation定位到template-packaged-http的数据库未ready及cleanup断言，已交28实施代理诊断，尚不能判定底层原因。28保持pending、29未释放，正式closed仍21项。原始metadata与限制见[CI14记录](../../verification/ticket-28-ci.md)。07已集成但push等待必要28修复，避免把已知失败候选再次作为验收。


## 票12授权HTTP重放集成

- 2026-10-04：merger先对5c759b5产品进行标准/spec短审，无阻断，范围与局限记录在工作树外coordination/ticket-12-premerge-review.md。随后核读最终`.verification-results/20261004-071310-376-all/summary.txt`：精确源`3563d920f355db61c1fa9249efd8eefc1da5ac1a`（已含07/d28072f），Windows all/fresh=false完整PASS，库1667/0/0/0、模板76/0/0/0，原质量/5架构/依赖门、全部消费者与真实Security重放/32绑定512轮转/2应用、PG打包重启及资源/负控通过。库jar SHA256 `faa5383527384643f490496239d6d305f000eb6284804aa8b6cd5c62f770589f`。
- 从干净d28072f以--no-ff合最终`2542365a677b60f73825a14a1e2c952df0d1dd1c`，merge`f9dfa782a65e889e50f13cd0ace23250fc408c37`，无冲突。相对被测源仅5文档/票变化；合并产品/测试/runner与交接分支完全相同。中央登记0035、0017/0034明确部分替代与47条实际ADR；CI归档补足07历史源码和12新consumer输入，不修改执行逻辑或原质量门。
- 12维持verification-pending等待Linux，07/28仍按各自CI状态等待；正式closed仍21项。历史首轮12因旧disabled消费者断言失败的真实记录保留。28本机PG成功不能覆盖CI14 Windows失败；此次尚未push，等待28必要修复一起执行授权CI15。19 Map中断修复已定向GREEN，将同步本集成后执行最终必要门。


## 票32 Cookie与HTML片段集成

- 2026-10-04：merger核读冻结`1f307a3c14a77daa65d978586e64192f36c4895e`的`.verification-results/20261004-072426-683-all/summary.txt`：Windows all --fresh共100命令PASS，库1655/0/0/0、模板76/0/0/0，原质量门/5架构/依赖、两个HTML普通jar图、64MiB深度10000和10000次成功/拒绝及工具链负控通过。库jar SHA256 `0531defe321757dc46e913ca48c5be933622ec79219dca48a2c0372c89f1cd04`。该冻结来源未包含12与28后续CI修复，报告分别保留验证范围。
- 从干净1d6377d以--no-ff合最终`3dda390fee86a1497d7c624169e7e282f2926a7b`，merge`e5e8f028310cdf07e009e7b0f95977e48ae1a6b1`，无冲突；src/POM/verification/workflow与32交接分支完全相同。对比冻结源，32自己的Cookie/HTML产品、测试、POM和消费者未变化，其余差异为已验证12的同步及输入归档。中央登记0055和48条实际ADR，0001的optional理由继续有效，无需标为替代。
- 32保持verification-pending，07/12/28同样按各自未完成CI状态登记，closed仍21项。HTML、锁与重放consumer归档全部保留。未重跑已通过的相同源码全门；等待28必要修复一起push既有CI验证联合候选，不等待19的独立最终门。


## CI15候选：07/12/28/32

- 2026-10-04：merger核读28的CI14修复报告与final-template/final-process两份PASS summary；相同业务代码保留原76项与原质量门，开发进程清理后仅重验三项CLI和真实打包两模式。最后的非空properties目录反例证明删除失败不会跳过native stop。未声称后来32/POM合并后的库jar仍是先前1667来源。
- 从干净2e79ee7以--no-ff合`c03f3b138d4632e360e7825235ba82d5f8ebdd03`，merge`baeed369d4cde9640ff006b139e3413a2c496df7`，无冲突，src/POM/templates/verification/workflow与交接树相同。保留全部HTML、重放、锁及现有消费者；组合Verify javac和diff-check通过。本次按用户授权直接push已有CI，不等待19的独立全门。
- 07/12/28/32仍verification-pending，closed仍21项，CI14失败不擦除。下一同源CI15负责新组合双平台结果；19将同步本候选执行Map回调中断修复后的最终完整门，20独立实施不改此候选。


- CI15最终更新：e3382ce/run37163228381为Ubuntu全部success、Windows all/template-build失败而platform/归档success；76模板中并发原生迁移场景出现ConcurrentModificationException，尚未执行新CLI，不足以判断原启动假说。原始API metadata与精确限制见[28 CI报告](../../verification/ticket-28-ci.md)。已用followup正式唤醒28实施代理修复；07/12/28/32不提前close，29继续等待。19冻结全门与20独立TDD继续，不改正在被测的源码。


## 票19显式DTO映射集成

- 2026-10-04：root短审的Map key.copy→value.copy中断缺口已通过两个公开路径真实RED，最小检查后80项回归GREEN。随后冻结`4bcad8726e1b4379a0a56fe0804d1164086ae7e8`（含07/12/32/28 CI14修复）执行Windows all --fresh110命令，`.verification-results/20261004-075535-739-all/summary.txt`为PASS。库1712/0/0/0、5架构/原依赖/覆盖门通过，指令26467/28471、行5136/5443、分支2879/3390；库jar SHA256 `5853fd0737501be5a63f0d0efbbb59823cf820c7dcd219d76c7e72d97100cef2`。
- 显式具名DTO值和真实字段演进负控、旧普通jar编译的相同binary、新普通jar64MiB/512seed/2000轮/200Error资源门均通过（retained1651624→1659896bytes，threads7→7）；模板76/partner15、新PG三CLI/打包两模式及全部既有消费者/平台/负控通过。确切证据与支持范围见[19报告](../../verification/ticket-19-explicit-mapping.md)。此次本地模板通过不否认CI15已有Windows并发迁移CME。
- 文档完成并同步最新main3e8e3aa后，19最终`2c50f9c18ac9c76676359d39440383fb35fc470a`与被测src/POM/examples/verification/templates/workflow/Wrapper零差异。从干净3e8e3aa以--no-ff合入，merge`97c55425302dbeec707edd0775fbacefbb8d0ca9`，无冲突，中央登记0042和49条实际ADR。
- 19为verification-pending，Linux/新联合CI尚缺；closed仍21项。未重复已通过的相同源码全门，暂未push，等待28真实CME修复一起执行既有CI。20在独立树继续应用自有时间/容量与有界模式政策，未混入本候选。


## CI16候选：同JVM测试宿主日志配置修复

- 2026-10-04：合入28最终81ef972，merge9a8cd45。真实12轮原3 errors→同12轮GREEN定位并修复共享Logback property map配置竞争；原Flyway双应用屏障不变，测试宿主逐事件委托标准listener，不串行化应用刷新。当前3dfe963来源库1712与完整独立模板78/原质量门/三CLI/打包重启及负coverage通过，核读summary为PASS。仅诊断annotation随后压至实际encoded2737/1072/1072字符并保留最深首因，Verify javac与真实RED XML验证通过。
- 中央产品/模板/runner与交接树相同；19、07、12、32既有消费者全部保留。按授权立即push CI16，不等待独立20或08；07/12/19/28/32保持pending、closed21，29继续等待实际双OS成功。完整来源和范围见[CI15修复报告](../../verification/ticket-28-ci15-fix.md)与[联合CI记录](../../verification/ticket-28-ci.md)。


## 票08显式本地缓存集成

- 2026-10-04：核读6881088的原all与d0afe97完整尾门。库1729/0/0/0、5架构/原88/88/75/依赖门及先前消费者通过，jarSHA256 ce9edfb7198d82eee5ab40007718881552dc72370d19ed033567dfefb9f6a608；原all在no-Jackson显式mapper场景的旧消费者断言冲突处FAIL，原记录保留。修正fixture后，尾门先强制库src/POM/.mvn不变及target/repository jar同SHA，再完成61步5图24场景、partner、完整模板78、PG三CLI、两模式打包重启、5资源与工具链负控，summary为PASS(tail scope only)。不把组合证据改称某次all全绿。
- 从干净207c0cc以--no-ff合最终f71c7daf318d7e1ed5cc09bab6d54987897a8bdf，merge e6341b73af30d1947285633745806d55383467e1，无冲突；合入src/POM/verification/templates/workflow与交接分支同源。中央登记0031/0015与0047限定更新、50条实际ADR。仅短审无阻断，完整33双轴审仍独立进行。
- 08保持verification-pending待新Linux CI；运行中的CI16源码207c0cc不含08，不能用于08闭合。20与10在各自冻结来源全门，不因本次合入重复未改源码；下一联合CI验证组合。此次不单独push，待后续批次。


## CI16双平台闭合

- 207c0cc / run37165514455：Windows111327487229与Ubuntu111327487395的all、platform、归档均success，UTC更新00:49:22Z；原始公开run/jobs/artifacts JSON保存main .verification-results/ci-16。完整元数据与只读artifact metadata限制见[联合报告](../../verification/ticket-07-12-19-28-32-ci.md)。
- 07/12/19/28/32 closed，正式累计26；历史CI14/15失败保留，29前置解除并以followup唤醒实施。08在该来源之后合入，不算此次通过；10/20仍独立验证，33负责最终单候选组合和双轴审查。仅文档闭合，不重复同源已过全门，不单独push。


## 票20显式输入与有限保留集成

- 冻结ff33c5d3f71be27a837940542cbc1c93f9f14e9b（含CI16源207c0cc）的Windows all --fresh116命令PASS：库1736/0/0/0、5架构/依赖/88/88/75原门通过，覆盖26541/28549指令、5147/5454行、2907/3418分支；jarSHA16d80a33f6e73ac077e55916b72e690680a32111f2760a4dd8848f5ed71b0410。普通jar64MiB的10000轮成功/语法失败保留5608112→5608112bytes、线程7→7；JDK-only应用三环境、模板78/partner15、PG三CLI/打包双模式重启与全部资源/负控通过。详见20报告。
- final f6760067e5524a1e16b0bda648045edc2a80de14在通过后同步08/中央docs至e561bc4；20产品/测试/应用/Java消费者与冻结源零差异，组合Verify编译通过。从干净e561bc4以--no-ff合入，merge8f8f2ef5a783faf7879402f3f81766c0968d6beb，无冲突，合入所有产品/runner与handoff相同。中央登记0043、51条实际ADR并保留cache/value/mapping/HTML等消费者。
- 20状态verification-pending，仅待下一同源Linux/联合CI；本地1736与jarSHA不冒作后来08组合已验证。08同待新CI，10正在交接，closed仍26。未重复无关合并导致的相同20门，下一CI17统一验证组合。


## 票10标识政策集成与CI17候选

- 核读fa26fc3d2031ffce9470c41ff483fdeb33989699的Windows all --fresh113命令PASS，库1733/0/0/0、5架构/原88/88/75/依赖、全部ordinary消费者/模板78/PG三CLI/打包两模式/资源与负控通过。jarSHA cc8c8a405a4e747b459c0d1eb850aa7794c90610c593d2c906b3431dc14ef312；ID64MiB固定seed2048/10000轮/平台和虚拟模式，保留1933944→1963184bytes；UUID实际3context及原0ee9d54普通jar历史样本均登记。
- 最终35179046f2d0b1c0a1156bd38c0c59ee1585fe94已同步e561bc4，ID源码/测试/消费者不变。20中央登记1af4f1f后--no-ff合10，merge cc24fe9fb312ba6ad2c481880432710652d5922c；仅CHANGELOG与workflow相邻插入冲突，双方内容保留。组合Verify javac、全树冲突标记与diff-check通过，保留id/value/cache/mapping/HTML/HTTP/PG全部消费者和归档。
- ADR0033与0023 Superseded理由保留，实际52条ADR。08/10/20仍verification-pending，源各自清晰；按授权push既有CI17验证新组合，未把多个本地来源拼成新来源已过。CI16已闭合的26票状态不变。

## CI17 双平台观测失败保留

- f081f2db7ecc14783e81f667b18586ba98d3a316 / run37166952423 已 completed/failure，UTC 更新 01:18:19Z。Ubuntu111331738095 和 Windows111331738274 的 all 均在 template-build 失败，独立 platform 与归档均 success。前者 deferred HTTP 503，后者 trace 为空，两个模板摘要均为 78/1/0/0；具体注解与 artifact metadata 限制见 [CI17 报告](../../verification/ticket-08-10-20-ci.md)。
- 08/10/20 仍 verification-pending，closed 保持26。根代理在独立 fix-ci17 树诊断标准观测生命周期，不凭 503 扩大 deadline；后续同源 CI 才能完成联合验收。fb1bdee 推荐文档/YAML修正晚于本次来源，尚未 push，不计 CI17 覆盖。正式31/33与30预研的范围均不混入本次结论。

## CI18 候选：标准 trace context 只读夹具修复

- 从干净0816c7a以--no-ff合入7080ec82f6ce7c4396ccd8e5d5ac6ec3f40a5c90，merge87fc4b1aedcb067291445a85a5560762e4155a97。相对中央仅2测试文件和诊断报告；src/POM/生产模板/runner/workflow均无变化，最终测试源码与4dfd4c2相同。原CI17双平台失败保留，不改成通过。
- 核读实际日志：e9328d5完整模板clean verify为80/0/0/0、原覆盖率门通过；之后4dfd4c2仅加共用90s join deadline/finally取消其他worker，最终2CPU观测7/0/0/0通过，平台/虚拟各1600请求。两次来源分别记录，不声称80项完整门包含后续清理补丁。真实RED说明currentSpan读取会在Servlet结束竞争中重建Brave span；夹具改为标准currentTraceContext只读关联，原SERVER span/parent/采样/assertions保留。详见[修复报告](../../verification/ci17-observation-read-fix.md)。
- 按用户既有授权推送CI18，最终双OS完整门负责新组合，08/10/20继续verification-pending、closed26。此候选包含fb1bdee推荐文档/YAML修正；31升级与30进程预研在coordination，不冒充正式验收。

## CI18双平台闭合

- 250ce2de2b38901c621318f8d871486c772fea34 / run37168561735 在UTC2026-10-04T01:52:05Z完成success。Ubuntu111336599366、Windows111336599415的all、platform、artifact归档均success；公开原始run/jobs/artifacts/annotations保存main .verification-results/ci-18。完整artifact ID/digest及未下载内部内容的限制见[联合验收](../../verification/ticket-08-10-20-ci18.md)。
- 08/10/20共同完成标准闭合，三票closed，累计29。CI17失败及真实trace读取竞争RED/GREEN完整保留；本次来源含推荐示例与最终观测清理修复。每票本地测试数/覆盖率/jar SHA按原冻结来源保留，未冒作本次Linux内部日志。
- 29工作树已在此前领取并实施，中央状态同步为in-progress；其冻结f6a3a9b的完整门正在独立执行，不计CI18验收。30仍受29阻塞，31受30阻塞，33受剩余票与31阻塞。正式31/33未开始；候选artifact身份与模板升级预研不算验收。
- 本次仅中央状态与证据文档更新，实际ADR仍52条；没有改产品、降低门槛或重跑相同源码已过全门，纯docs暂不单独push。后续docs提交不是CI18被测SHA。

## 29事务命令集成与CI19候选

- 2026-10-04：从干净`2bff4f2098f51c8e81582028e5a6811aed43471d`以--no-ff合入最终`bc7ce170a6114c3c430080f5458e26bc8932a7d4`，merge`edc4eaac1e8443a21f5da0c56a9736c222963bc7`，无冲突。产品、模板、runner、全部既有消费者、workflow与Wrapper等于交接树；中央登记ADR0052，共53条实际ADR，扩展0051而不替换独立claim/HTTP协议。
- 核读f6a3a9b完整all PASS135命令、库1774；4c3ba76最终产品/template门PASS106/0/0/0、原质量门/三CLI/真实打包双模式/cleanup与coverage负控，包SHA a3259e4d89a504f2e09cb2b59a8170a25f10055b5c08363ad868cd63b4fff58e。普通库src/POM未变、jar仍0ad7ca80df4b8a8ddf2f5def024d6ccf4dc9ca21a68b6850c70e03ac90456b8b。三段来源见[29报告](../../verification/ticket-29-transactional-commands.md)，不冒称最终all已运行。
- 最后34c06cb仅Postgres测试夹具和scope测试变化。实际46日志18/0/0/0、47日志4/0/0/0；48 XML为预期2测试/1failure/1error/0skip，保留原primary-assertion-sentinel及suppressed PostgreSQL清理错误，abort不能掩盖cleanup失败。原40全模板门在fast shutdown的79.458s checkpoint失败保留；修复自有数据库生命周期，不增加60s预算或关闭fsync，未重复无变化产品完整门。
- 29设verification-pending，Q10待本次既有CI19双OS最终组合。累计closed仍29；30/31/33分别保留依赖边界。按用户授权推送一次新实现候选，Linux结果未返回前不关闭29。31中立harness/升级稿预研位于工作树外coordination，未混入本次源码、验收或交付计数。

## CI19闭合29，释放30

- `ae7215eb6fea6c12aea9d4bb450aaf4ad21ab0fa` / run37172307215于UTC2026-10-04T03:08:36Z completed/success。Windows111347590655、Ubuntu111347590758的完整all、独立platform和归档均success。主树`.verification-results/ci-19/`保存初始/阶段与最终run/jobs/artifacts/annotations；[CI19报告](../../verification/ticket-29-ci19.md)列明artifact ID/digest和未下载归档内部内容的限制。
- 29的Q08/Q10及共同完成项闭合，票设closed，正式累计30。全部本地分阶段原始失败/修复/通过记录继续保留；本次成功才补齐最后测试夹具修复后的双OS全组合。只读公开API不能给出未下载报告的精确测试计数或内部jar跨平台同hash，33仍负责单候选制品证明。
- 30前置解除并置in-progress，基于CI19产品及此后纯文档状态提交开展真实进程丢失/双独立JVM/receipt清理恢复。31仍blocked30，33仍blocked剩余与31。31工具草稿的独立自测/短复核位于coordination，未成为正式31执行。
- 此次仅状态与证据文档提交，主树产品/测试/runner/workflow/Wrapper保持CI19被测内容，不另起本地全门，也不单独push纯docs。

## 30命令恢复与回执清理集成，启动CI20

- 2026-10-04：核读clean`cd79a19540d1c4b1ce6ed26af3e14205bf1d79c3`的`.verification-results/20261004-115313-477-all/summary.txt`，137命令、RESULT=PASS；库1774/模板130均0失败/错误/跳过，原coverage/5架构/依赖门、全部消费者、真实PG工具/打包两模式与负控通过。ordinary jar SHA0ad7ca80…56b8b、partner2b501f28…c73e6、template19a2c9bc…b8fab，完整数值与资源/Q映射见[30报告](../../verification/ticket-30-command-recovery.md)。
- 机器账本及原始证据记录19个child退出/PG会话归零、6精确kill、71个自有数据库DROP成功；最终语句取消5002ms对应5000ms服务端预算。原fast-stop/fsync/no-FORCE、正常HTTP与授权/事务政策不变。局部独立短审无产品阻断，完整PK保护是实际GREEN回归增强，不伪造RED；所有先前失败按来源保留。
- 从干净`3f25c38a680be498ead77bf29fb7d5caf30537f6`以--no-ff合最终`16214eb8ef09888f65c2a2ff4dd7fcb5546757f8`为`57c5028fc757baca0ce60d2770c92a710fec7909`，无冲突。交接相对冻结仅本票/报告两Markdown差异；集成产品/测试/模板/runner/workflow/Wrapper等于交接树。中央删除报告中未观测的CI19内部108项对比措辞，不改实测130项或任何源码。
- 登记ADR0053，共54条实际ADR，延续0052而不替换原事务协议；补CHANGELOG的维护/升级与不确定结果边界。30设verification-pending，Q08/Q10待CI20，closed仍30；31/33前置仍保留。用户已授权既有CI，推送一次新实现候选，不重复相同本地all。历史原始证据由root归档维护，中央未并发改其index。

## CI20闭合30，释放31

- `61094b53b23b5d00759df3e6837999bc63af0f44` / run37176585182于UTC2026-10-04T04:37:55Z completed/success。Ubuntu111360340295、Windows111360340365的完整all、独立platform和归档均success。主树`.verification-results/ci-20/`保存初始/阶段快照、最终run/jobs/artifacts及两个OS的annotations；[CI20报告](../../verification/ticket-30-ci20.md)列明artifact ID、归档digest和公开API的可见范围。
- 30的Q08/Q10及共同完成项闭合，票设closed，正式累计31张。cd79a195的137命令、库1774/模板130、19个child/6次kill/71个数据库scope及三类jar SHA继续仅归本地冻结来源；没有把公开CI元数据补写成未读取的内部日志或跨OS jar同hash证明。历史失败/真实RED和各阶段修复证据继续保留。
- 31的全部前置解除，保留ready-for-agent供正式领取；工作树外中立harness、升级稿与独立短复核仍属准备，尚未开始正式旧/新五类任务或历史升级验收。33仍等待31，并独立承担最终同一候选的全部接合、制品身份/可复现性及标准/规格双轴审查。
- 此次仅更新票、报告和中央状态，实际ADR仍54条。产品/测试/模板/runner/消费者/workflow/Wrapper保持CI20被测内容，不重跑相同来源的本地门，不单独push纯文档提交；后续状态提交不冒称CI20已验证其SHA。旧actions通知及Ubuntu未来镜像迁移通知交33冻结时核对。

## 31最终实现合入，完整本地门与CI21并行

2026-10-04后续候选登记（此时状态，最终闭合见下一节）：审查后的`b2cbb0f368d7187c87fb90b0bd11741c9d71e0a7`合入本集成线，包含模板最小连接池2前置政策、两个公开回归和严格workflow继承/重复testcase校验。该政策修正源于独立最大值1控制，默认4/1000ms不变，未解释或抹去本地18/19失败。owner完整本地20 all与集成CI22独立验证新候选，此时仍verification-pending；先前CI21成功只属于其准确来源。中央历史归档index继续由root维护，此次不关闭31、不开始33、不发布制品。

- 2026-10-04：owner确认`57dc707e39a0d4670d49a431bf78284e05af3a2f`为最终实现冻结且无已知产品/runner待改，已包含main`0edb5198d640b5d2575b94f7e60cc635c42ca96d`。root授权在冻结完整all16仍运行时并行启动现有CI；从干净main以--no-ff合为`f72cad58da0f2d7797e7d8b2a89dfdf5665ac5bd`，无冲突，产品/模板/测试/消费者/runner/workflow/Wrapper与交接源零差异。
- 实际历史before78/after80、冻结旧/新五任务与后续独立workflow151项及打包两线程模式分别保留来源；10s请求回归和Apply拒绝已有局部证据。当前源码的完整all16及随后platform尚未完成，不写全门PASS，不把早期跳过测试构建或测量臂定向测试冒充最终门。详细来源见[31报告](../../verification/ticket-31-template-upgrade.md)。
- 已有CHANGELOG、ADR0054及INDEX内容保持；中央补DESIGN0054、55条实际ADR与31 verification-pending，保留未闭合复选框。0054扩展0050–0053并分离runtime0.2/template2026.10.0身份，不重标历史jar。按root确认修复25票报告的单个相对链接；没有修改执行逻辑、质量/时间预算或其他扫描诊断。
- 按既有授权推送一次实现候选启动CI21，公开run/jobs/artifacts/annotations与精确候选SHA另行登记。成功后仍须汇合owner最终证据才关闭31；累计closed仍31，33最终同一候选制品身份/接合矩阵/双轴审查未开始。历史证据归档由root维护，中央未改其index，也未发布制品或部署。
- 推送前all16实际在template-lineage处失败：Windows源码启动器将Unicode路径字符串损坏为`?`，触发InvalidPathException；已执行120命令/523.6s不被写成整门PASS。初始合并保留，未推送或启动失败CI。修复`1dc2912`将模板/workflow两处参数统一为ASCII file URI并由lineage解析；原失败模板目录12个links及新workflow目录的20文件overlay实际四命令PASS。owner新冻结`d4ac86bf162d35b4ea1e7d9abdc5ac99a30fb5db`已合最新main，再以--no-ff合为`ff0d832135186766a58968536d0dc6af3be61d6c`，相对初始merge仅两个验证源码与失败说明报告变化。root重新授权推送该修复来源，owner从同一冻结完整重跑all/platform；首次失败及新回归分别保留。

## CI22与本地20/21闭合31

- 2026-10-04：审查后的实现冻结`b2cbb0f368d7187c87fb90b0bd11741c9d71e0a7`从干净c26dc2d合入为`4470870b0d8ad50888dba34d85e38d624987dc91`并推送既有集成线，CI源相对冻结仅四个中央注册文档差异。新增连接池下限2与两个公开回归、严格继承/重复testcase身份检查均已包含，默认4/minIdle0/1000ms不变。
- 该冻结实际本地20 `all`为148命令、1988.219秒、RESULT=PASS；库1774/模板132/workflow153均0失败/错误/跳过，原coverage/架构/依赖门、真实打包双线程模式、资源与负控通过。21 `platform`为11命令、102.207秒、RESULT=PASS。两次均未带`--fresh`，私有依赖仓库与精确命令见[解析结果](../../verification/ticket31/final-local-results.json)，不写成空仓库运行。
- [CI22 attempt1](../../verification/ticket-31-ci22.md) / run37192581803核定head4470870，Ubuntu111407707501与Windows111407707703的all、platform、归档全部success。Ubuntu对应947/50/7秒、job1022秒；Windows1641/83/15秒、job1774秒，于UTC10:03:50完成。原30分钟预算不变；归档metadata与摘要位于main `.verification-results/ci-22`，没有下载CI归档内容或推定内部精确计数、四类JAR跨OS同字节。
- 最终clean交接`45d0809659cb547eadd862fff45638c3f294b8d0`已包含4470870，相对b2只有七个文档/报告文件变化。从干净4470870以--no-ff合入并登记本票closed、九项验收/场景完成，累计32票closed；33仍ready-for-agent，尚未实施，最终同一候选历史重验、四类制品身份、接合矩阵及双轴审查未预填通过。55条ADR计数不变。
- 原18观测HTTP超时、19默认池4启动超时及有限诊断未复现的事实完整保留；独立池1控制与实际RED→GREEN契约修正不解释或抹去二者。六轮本地报告6745文件已由owner持久保留并重开ZIP逐项校验，路径与digest见[保留索引](../../verification/ticket31/local-evidence-retention.json)。root另从clean45d0809完成筛选历史映射4811文件的逐文件源/副本SHA核验，receipt为main `.verification-results/history/ticket-31-preservation.json`，共享index此时41465文件/1716646235字节；不与保留`.bin`等原始文件的完整ZIP混称。冻结五任务、历史升级和本地诊断身份不变，merger未改root历史index。
- 此次最终关闭提交仅合入结果/保留报告和中央文档，不另跑相同产品的Java/Maven，不单独push触发重复CI；最后已推送/被CI22验证的是4470870，后续本地文档SHA不冒称已跑CI。33实施候选就绪后由root安排推送；无PR、release、master合并、制品发布或部署。
