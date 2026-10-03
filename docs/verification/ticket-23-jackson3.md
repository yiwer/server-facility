# 票 23：Jackson 3 应用所有权与协议验收

日期：2026-10-04。实现与本机验收完成，等待非发布集成复核；Linux、Servlet 6.1 新重载、完整缺类/覆盖矩阵归票 24。本报告不宣称目标跨平台候选已经完成。

## 精确来源与命令

- 初始集成：`042006daaed1451476d355d1a3199eb249202825`，票 22 closed。
- **最终被测实现：`07682f458549dce36ed786ae55b25a958162541f`**；运行开始时 working tree 干净。后续报告/票据提交不改变产品、POM、测试或验证入口。
- 工作树：`E:/GenCode/server-facility-worktrees/ticket-23`，分支 `codex/ticket-23`。
- Windows 11 amd64 / Oracle JDK `25.0.4.1` / Wrapper Maven `3.10.0`；Locale `en_US`、时区 `Asia/Shanghai`；Java 21 负控为 `21.0.12.1+1`。
- 完整证据：`.verification-results/20261004-011808-650-all`；逐步 TDD 原始日志：`.verification-results/ticket-23/01-baseline-red.log` 至 `19-runner-all.log`。两目录都在工作树内且不被 Maven clean 删除。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.4.1'
$env:VERIFY_WRONG_JAVA_HOME = 'C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1'
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
& "$env:JAVA_HOME/bin/java.exe" -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US verification/Verify.java all
```

结果：**RESULT=PASS**。使用本树隔离 repository（非 `--fresh`）；不使用其他工作树的同坐标 SNAPSHOT。root 全量 `clean install` 包含 verify 的测试、覆盖率和依赖分析门。没有增加 skip/ignore、降低阈值或扩大预期失败豁免。

## 完整门与普通产物

| 检查 | 实际结果 / 证据 |
|---|---|
| 主/测试编译、测试发现 | 1335 tests / 0 failures / 0 errors / 0 skipped；`04-library.log`、`surefire-reports` |
| 架构 | 原 5 条 ArchUnit 规则全部执行通过；JUnit Jupiter/ArchUnit 6 真实发现 |
| 覆盖率 | 指令 `17389/18741 = 92.786%`；行 `3565/3820 = 93.325%`；分支 `1760/2047 = 85.979%`；原 88/88/75 门通过 |
| dependency analyze-only | PASS；没有放宽 failOnWarning 或既有约束 |
| 依赖 | root/Web consumer effective POM 和 dependency tree 都归档；core/databind 为 tools.jackson 3.1.5，annotations 为原 com.fasterxml 2.21，无 Jackson 2 databind/core/三种旧 Java8 模块 |
| 原生产物 | 普通 jar、无 BOOT-INF；209 个 class 全部 major 69、无 preview；imports/metadata 验证通过 |
| 非 Web consumer | configured / user override / invalid configuration 三条路径通过，classpath 仅安装依赖 jar |
| JSON HTTP consumer | constructed 与 injected 均 PASS；同一个普通 jar 和相同字面金样 |
| 重复资源周期 | 5 次独立 JVM 应用启动/使用/关闭，256 MiB heap、每个进程 45 秒截止，均正常退出 |
| 工具链负控 | 校验和错误、缺 JAVA_HOME、真实 JDK 21 均按预期拒绝，未跳过 |

普通 jar SHA-256：`d5b408f6b1bbed62df88b37cc53ff121718bfb600b206eb5c3531d01ecf4c894`。来源为本树 `target/server-facility-0.1.0-SNAPSHOT.jar`，与本树隔离 repository 中安装 jar 相同；JSON 两条 consumer 日志各自打印实际加载路径和同一 hash。证据目录的 `artifacts/server-facility-0.1.0-SNAPSHOT.jar` 保留该产物。

测试总数从集成前 1323 增至 1335：JsonFailureBoundaryTest +5、JsonParserBudgetTest +2、JsonStreamBudgetTest 净 +3、JsonBuilderCustomizationTest +1、JsonsApplicationScopeTest +1。原测试没有删除/跳过；Java8 支持、程序错误及非正数字段预算的旧断言按批准的显式迁移更新，不作为无说明的断言放宽。

## 68 个诊断及额外平台发现

[22 精确清单](ticket-22-jackson-diagnostics.md) 的 9 个文件、68 个诊断全部清除：FacilityJsonAutoConfiguration 5、FacilityWebAutoConfiguration 1、Jsons 15、JsonUtil 8、InputStreamDeserializer 6、InputStreamSerializer 6、JsonConfig 21、TypeRef 2、FacilityHttpErrors 4。最终 root clean install 的 main/test compile 均成功，未将剩余编译错误转交 24。

实际暴露的额外项分别登记，不能混称旧 Jackson 错误：

- Spring 7 `HttpHeaders.containsKey` 改 `containsHeader`；测试夹具 ParameterValidationResult / NoResourceFoundException 改官方新构造签名。
- 新 converter 接收不可变 JsonMapper；旧 setter 换成构造时传入。Spring 标准 converter 的 ProblemDetail mixin 保留扩展字段；fallback 采用标准 mapper，宿主 mapper 仍原样复用。
- Spring 7 ProblemDetail 默认 type 为 null；库显式设置 `about:blank` 保留票 04 金样，不放宽 HTTP 状态或安全字段。
- Boot error path/include-* 使用新 `spring.web.error.*`，真实 ERROR 派发仍验证 host error path 与秘密隔离。

## 契约、反例与 TDD

| 切片 | 首次红灯 / 修复后证据 |
|---|---|
| 应用 registry 不改写静态入口 | `04-scope-red.log`，原单例相同断言失败；`05-scope-green.log` 10 项通过；最终应用 scope 4 项、auto 3 项 |
| payload/cause 不进入结果或日志 | `06-secret-red.log`；`07-secret-green.log`；最终 5 项边界测试涵盖所有表示形式、真实输入/输出 I/O、日志、unsafe cause |
| 用户 codec 程序错误 | `08-programmer-red.log` 包装后被吞；`09-programmer-green.log`；真实 serializer/deserializer 在 WRAP_EXCEPTIONS true/false 均传播原 IllegalStateException，mapper feature 未改变 |
| 正字段预算 | `10-budget-red.log` 非正数未拒绝；最终 10 项流预算测试通过，包括空/null、N−1/N/N+1、字段/根流所有权、I/O 失败和复用、固定 1MiB 无参预算、seed 210025 的 128 个案例 |
| Base64 类型与内容预算 | `17-field-type.log` 数字被作为 Base64 接受；增加 JSON string token 验证后通过。解码前长度闸门维持 N+2 分配界，返回严格 ≤N |
| inclusion 迁移 | `18-field-type-inclusion.log` 发现 Map null 内容策略遗漏；builder 同时设置 value/content inclusion 后保留旧字面期望 |
| parser 文档/字段预算 | JsonParserBudgetTest 2 项；Boot 标准 factory customizer 实际生效，100k 字段/100k 文档在读入 4k 以前拒绝并关闭源；无字符串预算的对照可成功读取 75k 解码字段，受限 mapper 失败后可重用 |
| 不可变发布和用户配置 | 后续 build 改政策不影响已发布 mapper；显式 JsonMapper bean 保持同一实例；用户 Jsons bean 优先；null callback 立即失败 |

曾在新配置错误测试中错误地假定 Jackson 3 默认 FAIL_ON_EMPTY_BEANS 开启，故 `12`/`13`/`14`/`15` 日志保留该失败；最终测试显式启用该宿主 feature，再验证 InvalidDefinitionException 传播（`16-error-definition.log`）。没有为错误的测试假定改变产品默认或删除断言。

## 金样与实际 HTTP

原金样全部未修改，来源为票 21 在 `731598b` 普通旧产物验证过的人工字面文件。消费者没有从当前 mapper 生成 expected。`json-golden`、`JsonConsumer.java`、consumer POM/effective POM/tree 与实际日志一并归档。

`14-json-consumer-constructed.log` 验证默认/定制两个应用；`15-json-consumer-injected.log` 在两个应用同时存活时交错请求、关闭第一个、保留第二个、重建第一个并继续验证。真实 `/mvc` 和 `/service` 分别匹配原 paired fixture，UTF-8 POST、泛型、未知字段、溢出、空/坏/尾随 JSON 及 HTTP 状态均断言。允许差异仅为等价 non-BMP escape 和对象属性顺序；数值/字符串类型、精度语义、日期/时区、null/Optional 政策保持。旧默认尾随值接受变成比较应用的显式标准 customizer，custom 应用仍拒绝；库不恢复全局宽松默认。

根 HttpErrorContractTest 的 17 条真实 HTTP 场景也全部通过。HTTP fixture 的标准 converter 和应用 mapper 在构建时接合，不在运行时给共享 mapper 设置属性。SpringContextHolder 的旧兼容警告不是 JSON 作用域查找路径；Jsons 与新 registry 均按实例拥有政策。

## Q01–Q10 与接合边界

| 标准 | 本票证据 |
|---|---|
| Q01 | FR-01/08/09、AC-02/11/12 对应公开类型、应用配置、错误/资源、普通消费者；ADR-0046 和迁移文档记录替代 ADR-0044 决策1–3 |
| Q02 | null/empty、泛型/非法/溢出、Unicode、Date/timezone、正预算边界及非正数退出均有测试 |
| Q03 | 真实 ordinary jar、MVC/服务出口、HTTP 错误、两 context；J01/J05/J14 本票部分通过 |
| Q04 | 用户 codec、输入/输出 I/O 失败及失败后的关闭；两 context 明确交错并关闭重建，无随机 sleep。无数据库/事务/网络重试算法，本票不适用其故障模型 |
| Q05 | 字段 N+1、解码 N+2、parser 读入界、root/field 所有权；heap/thread/deadline 和重复 JVM 周期 |
| Q06 | 保留旧字面文件和独立普通产物；明确 mutable callback、静态作用域和字段预算破坏面 |
| Q07 | 原固定 seed 210025/128 案例和有限类型/预算矩阵；可按上述 test selector 重放 |
| Q08 | 精确源、产物 hash、JDK/OS/Locale/timezone/POM/tree/日志齐全；预期错误 cause/日志无秘密；Linux 明确未验证 |
| Q09 | 全库 1335/0/0/0、原覆盖率/5架构/dependency 门，无 skip 或阈值修改 |
| Q10 | 代码、ADR、USAGE、迁移说明与本报告同票交付；非发布集成复核后可解除24实现前置，不把24待验项作为23循环前置 |

24 明确待验：Linux `all` 和目标工具链组合；缺 Servlet/Jackson/validation/Tika/POI/Caffeine/context-support 矩阵与用户覆盖/注册顺序；05 的 Servlet 6.1 新 sendRedirect/Charset 重载捕获与预算。25/26 分别继续 RestClient 宿主 builder 与其他静态门面迁移。06/13/18 并行票尚未合入此精确产物，后续集成者需要按合入后的实际源执行对应全门。
