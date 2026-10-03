# Ticket 01 执行记录：Java 25 中间基线

日期：2026-10-03。负责分支：`codex/ticket-01`。
实现提交：`82d8c6b`；合入当时最新集成线 `bf64995` 后的验证提交：`7d68039dc0b2b4ebadf5ca82ddd9fafe562789c4`。
本记录与后续票状态提交只改变文档，不改变上述实现。

## 范围与边界

FR-01/02/10，AC-01/02/13，J01。本票交付 Maven Wrapper、release 25、字节码工具、版本归属账本、独立普通 jar 消费样例与执行入口。
Boot 3.5.16 是中间平台；Boot 4/Jackson 3、完整可选依赖矩阵及各能力新政策归票 21–24 和对应能力票。
ADR-0024 替代 README 的 Java 21+ 基线；ADR-0014/0017 的非 Web 契约保留，仅修复配置类提前链接 Servlet 类型的问题。

## 执行环境

- Windows 11 10.0 amd64；Oracle JDK `25.0.4.1+1-LTS-5`，UTF-8、Asia/Shanghai、zh_CN。
- Maven `3.10.0`，Wrapper `3.3.4` only-script，固定 SHA-256 并预先核对 Central 官方 SHA-512。
- 负向 JDK：真实 Temurin `21.0.12.1+1-LTS`，下载包按 Adoptium 官方 SHA-256 校验。
- 库及消费者在当前 worktree 内专属空 Maven 仓库解析；个人 settings 和其他 worktree SNAPSHOT 均不参与。
- 宿主 Docker engine 未运行，WSL2 虚拟化先决条件不满足；本地没有 Linux 执行证据。已配置 GitHub Actions Windows/Linux 矩阵，待 root 推送并取得运行结果。

重放命令（从仓库根目录）：

```powershell
$env:VERIFY_WRONG_JAVA_HOME='C:\Users\yiwer\AppData\Local\Temp\server-facility-research-tools\jdk21\jdk-21.0.12.1+1'
java verification/Verify.java all --fresh
```

## TDD 与实际结果

1. 原 POM 通过主机 Maven 安装普通 jar，然后编译独立消费者。首次执行在 `expected Java 25 class major 69` 断言失败；原产物是 Java 21 字节码。该红灯先于 release 和字节码插件修改。
2. 升级工具链后，原 1196 测试（包括架构 5）全部通过。消费者的 class major 检查转绿，却在无 Servlet 的真实 classpath 报 `NoClassDefFoundError: jakarta/servlet/Filter`，来源为 `FacilityIdempotencyAutoConfiguration` 的方法签名。
3. 将幂等和限流的 Web bean 放入 `@ConditionalOnClass` + Servlet 条件的嵌套配置，既有 Web 装配测试继续通过；最小独立消费者 configured/override/invalid 均通过，全部 199 个产物 class 为 major 69、minor 0。
4. 验证脚本自身失败也保留：`20261003-223535-792-all` 在已通过库和消费者后遇到 PowerShell 本地化日志非 UTF-8；`20261003-224054-009-prerequisites` 遇到 Windows Wrapper 长缓存路径导致部分 jar 未提取（32 个对正常 56 个）；`20261003-224251-945-all` 在同一产品场景通过后遇到 PowerShell 对英文错误文字强制断行。已分别修复为容错解码原始日志、缩短独立 Wrapper 缓存路径、仅对诊断匹配忽略排版空白。没有删测、跳过或放宽产品断言。
5. `20261003-224709-979-prerequisites` **PASS**：错误 SHA-256 / 缺失 JAVA_HOME / 真实 JDK 21 的 Wrapper 子进程均退出 1，诊断分别匹配 SHA-256、JAVA_HOME 和 `server-facility requires JDK 25`；验证父进程退出 0。
6. 已提交、启动时工作区干净的 HEAD `7d68039dc0b2b4ebadf5ca82ddd9fafe562789c4` 最终完整执行：`.verification-results/20261003-224849-577-all/`，**`RESULT=PASS`，父进程退出 0**。从空依赖仓库和空 Wrapper 缓存完成全部库门、三个消费场景、五次额外 JVM 生命周期和三项先决条件失败检查。

最终结果：1196 tests、0 failures、0 errors、0 skipped、5 条原架构规则。
JaCoCo 指令 `16077/17173`（93.62%）、行 `3324/3561`（93.34%）、分支 `1533/1763`（86.95%）；
`All coverage checks have been met`，`No dependency problems found`。
普通 jar SHA-256：`434e17be77a5d863fe4921fc015dc9ba84aa3f71b1f750d8f0c8e0808cd83d87`。

早期红灯的控制台输出在本任务工具记录中；首次 `clean` 删除了 `target/ticket-01` 下的早期日志，不能冒称这些原始文件仍在。
后续统一使用 `.verification-results`，避免 `clean` 删除证据或 Windows 打开的日志文件阻止 clean。

## 契约—测试—结果

| 契约 / 场景 | 公开验证入口与断言 | 证据 |
|---|---|---|
| 唯一 release 25 / 无 preview / 普通 jar | `example.Consumer` 逐 class 读 class-file header；断言 class=69/minor=0、无 BOOT-INF | consumer configured/override/invalid 日志 `ARTIFACT_OK` |
| imports、metadata 随产物 | 从实际加载的 repository jar 读取资源，真实 `@EnableAutoConfiguration` 启动 | 199 class 检查及消费者 context 成功 |
| 用户配置生效 | worker-id=3/data-center-id=2 影响生成 ID 的公开解析结果 | `CONSUMER_OK configured` |
| 合法用户 Bean 覆盖 | 应用自定义 generator，唯一 Bean，结果 worker=1/data-center=0 | `CONSUMER_OK override` |
| 越界配置 fail-fast | worker-id=4，启动失败，根因为明确范围错误 | `CONSUMER_OK invalid` |
| 最小真实 classpath | 仅普通库直接依赖，断言 Servlet/Validator/POI/Tika/Caffeine 缺席 | consumer POM/生成 classpath/进程命令 |
| 产物隔离 | 依赖 classpath 每项须为本次仓库 jar；不得引用库 classes/test-classes | runner 运行前断言及消费者 code-source 检查 |
| 干净构建与质量门 | `clean install`，1196 测试，失败/错误/跳过=0，原五规则按名字保留 | Surefire XML、JaCoCo XML、依赖分析日志 |
| 有界资源 | 五个顺序独立 JVM 启动/使用/关闭；各 Xmx256m、45 秒截止 | 五个额外 `CONSUMER_OK configured`，各正常退出 0 |
| 先决条件失败 | 损坏校验、无 JDK、真实 Java 21 | prerequisites 日志与 summary=PASS |
| Linux | 同一 `all --fresh` 命令，固定 Temurin版本，CI 保存报告与普通 jar | **待执行，不能将已配置记作通过** |

## Q01–Q10 适用性

| 要求 | 本票处理 |
|---|---|
| Q01 | 上表映射 FR/AC/J01；字节码红灯与缺 Servlet 反例均由独立消费者回归；ADR-0024 记录平台和装配边界 |
| Q02 | 配置正常/默认旧回归/worker 最大值 3/越界 4/覆盖 data-center 最小值 0；本票没有新增 null、字符串编码或数值 API，其他维度不适用 |
| Q03 | 独立 Maven 项目、隔离仓库、独立 JVM，使用真实 Boot 上下文，无库 test classpath |
| Q04 | 真实下载校验失败、缺 JDK、错误 JDK、配置失败；不存在新共享状态机/并发协议，线程交错语义由票 02/03 等负责 |
| Q05 | JVM 次数/堆/截止时间有上限，成功与非法配置进程正常退出；本票资源入口是生命周期冒烟，不冒称单 JVM 长稳、堆泄漏或 executor 取消已验证 |
| Q06 | class-file 69/minor 0 依据 JVM 格式，用户配置结果为独立常量预期；没有新增存储协议或旧 API 删除，无格式金样迁移需求 |
| Q07 | 构建与有限装配配置确定性重放；无新解析器或随机算法，无需引入 fuzz 框架 |
| Q08 | SHA/工作区状态、版本、OS、Locale、时区、命令、日志与依赖均归档；Linux 仍待 CI，故整项未闭合 |
| Q09 | 原 1196 项发现数无差异，0 删除、0 跳过；5 条架构约束按名称核验；JaCoCo 原 88/88/75 门与 dependency failOnWarning 保留，未扩大 ignore |
| Q10 | 代码、ADR、账本、fixture、runner、失败/通过证据随票提交；Linux 证据缺口保留 verification-pending，不标 closed |

## 残留与交接

唯一平台证据缺口是 Linux；CI 必须在合入后的具体 SHA 跑完整入口并记录成功/失败。
Windows 和 Linux CI 的 artifact 包括 summary、完整日志、effective POM、依赖树、Surefire、JaCoCo 与普通 jar。
仓库中不提交 Maven/JDK 下载、依赖仓库、临时二进制和原始运行日志。
