# 票 06：请求来源与上下文作用域验收

日期：2026-10-04。实现与 Windows 验证交付；目标平台完整消费者结果见下表。Linux 的同源 CI 尚待集成者收集，票据保持 verification-pending。JWT/Security 原生认证由票 27 负责，最终跨票候选由票 33 验证。

## 来源与环境

- 工作树 `E:/GenCode/server-facility-worktrees/ticket-06`，分支 `codex/ticket-06`。
- 旧平台最终实现 `9e3a5578779e44835a476ae0135f9db94a33d592`；Boot 3.5.16 / Spring 6.2.19 / Tomcat 10.1.55 / Servlet 6.0 / Jackson 2.21.4。
- 最终目标被测提交 **`d010234dd4afc9c092de3dd0109e7507a89cb8e3`**，运行开始时工作树干净，包含 integration `7e168199a812fba6396540922241036d85767d8e`。后续仅提交本报告和票据。Boot 4.1.1 / Spring 7.0.9 / Tomcat 11.0.24 / Servlet 6.1 / Jackson 3.1.5 / JUnit 6.0.3。
- Windows 11 amd64、Oracle JDK 25.0.4.1、Wrapper Maven 3.10.0、JaCoCo 0.8.15；时区 Asia/Shanghai。旧命令使用 zh_CN；目标 runner 显式 en_US / UTF-8。
- 本票不使用数据库，不新增生产线程池、网络客户端或外部资源。HTTP fixture 使用真实 localhost Tomcat 和 JDK HttpClient/Socket。

| 入口 | 实际结果 |
|---|---|
| 初版旧平台普通 jar runner，精确 `36738c955cdb10192579ac198b511e884a51e7f3` | PASS，1346/0/0/0，原全部门；`.verification-results/20261004-011115-993-integration`。此结果早于 MDC 失败补充，不代替最终来源 |
| 旧平台最终 `clean verify`，精确 `9e3a557` | PASS，1352 tests / 0 failures / 0 errors / 0 skipped；原 5 架构规则、88/88/75 覆盖率和 dependency analyze 门；`.verification-results/ticket-06/final-reviewed-old-platform-verify.log` |
| 目标平台公共契约子集，`1e2b138` | PASS，33/0/0/0；真实 HTTP 14、MDC 隔离子进程 5、IP 4、trace 7、作用域 3；`target-platform-request-contracts.log` |
| 目标首次普通 jar integration runner，精确 `bc3657e` | PASS，1364/0/0/0、原全部门、普通 jar 非 Web/JSON 真 HTTP 双应用消费者及三项工具链负控；归档 `.verification-results/20261004-013339-502-integration` |
| 最终目标普通 jar integration runner，精确 `d010234` | **RESULT=PASS**，1364/0/0/0，5架构规则、原覆盖率/依赖门；非 Web configured/override/invalid、JSON constructed/injected 真 HTTP 与双应用关闭重建、三项真实工具链负控全部通过；`.verification-results/20261004-013843-605-integration` |

最终覆盖率：指令 `18241/19642 = 92.867%`，行 `3702/3964 = 93.391%`，分支 `1871/2187 = 85.551%`；原 88/88/75 门保留。相对目标集成1335项净增29项：新IP 4、作用域3、真HTTP 14、隔离MDC 5、trace新增3；原测试未删除或跳过，最终测试数1364。原无条件XFF期望按明确迁移改为默认peer。

普通 jar SHA-256：`b69d5f799cb8f0c23de1796afadc6d3bf8b2e3584c74386ef8a7b1fb04a5fc32`；两次目标 runner 产物相同，最后一步仅补测试预算极值。最终完整日志 `final-target-integration-runner.log`；根测试、effective POM、依赖树、consumer构建与HTTP日志、字面金样和summary均在最后归档目录。

目标完整命令（PowerShell 的 `-D` 参数必须加引号）：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.4.1'
$env:VERIFY_WRONG_JAVA_HOME = 'C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1'
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
& "$env:JAVA_HOME/bin/java.exe" '-Dfile.encoding=UTF-8' '-Duser.language=en' '-Duser.country=US' verification/Verify.java integration
```

使用本树隔离 Maven repository，非 `--fresh`；只有第三方依赖缓存从既有验证目录预热，不复制其他工作树的同坐标 SNAPSHOT。本次 runner 重新 clean build/install 当前库，再用普通 jar 消费。首次启动命令遗漏 PowerShell 参数引号，JVM 将 `.encoding=UTF-8` 误当主类，尚未开始构建；失败保留在 `target-platform-integration-runner.log`，修正调用后的结果在 `target-platform-integration-runner-retry.log`。

## 契约、公开入口和反例

| 需求 / 场景 | 行为证据 |
|---|---|
| FR-03、AC-04，来源与认证边界 | `ClientIpPolicyTest`、`RequestBoundaryHttpTest`：公网 peer 默认、显式 IPv4/IPv6 CIDR、单/多代理从右往左解析、首个不可信跳停止；原始 XFF/X-User 不产生身份；宿主 Servlet Principal 和已认证包装 request 适配 |
| FR-05、AC-07，J04 生命周期 | 真实单 Servlet 工作线程 A→短路→匿名 B；单 MVC Callable 工作线程先 A 后匿名 B；初始 Servlet scope 退出、ASYNC 再派发、ERROR、DeferredResult 完成/超时、Callable 超时与实际退出、TCP RST 后复用均断言 holder 清理和 trace 正确 |
| FR-09、AC-08，预算和失败 | IP 无 DNS 的 literal-only 解析；32 跳/2048 字符/128 CIDR；无队列或永久 per-request registry；IP policy 失败不重复 ERROR 解析、仍清理身份。MDC adapter 部分安装失败在实际工作线程回滚，恢复失败保留首因 |
| AC-12，迁移 | [ADR-0029](../adr/0029-request-boundaries.md) 部分替代 ADR-0014 的无条件代理头假设；USAGE/CHANGELOG/限流入口 JavaDoc 说明默认变化、配置和兼容 holder 的非认证语义 |
| J03 HTTP 错误接合 | 真正容器 ERROR 和异步超时通过票 04 错误策略；500/502/503 状态正确，无 PRIVATE-POLICY/异常秘密哨兵；ERROR 派发头由边界内真实 filter 写出 |
| J07 伪来源防护，本票部分 | 未配置可信代理时任意伪 XFF 不能改变 `RequestUtil.getClientIp`；限流实际策略/计费组合留票 09/33 |
| 宿主优先和装配 | host `ClientIpPolicy` bean、host TraceIdFilter、trace 默认关闭仍保留身份清理；显式自定义 trace + 默认开关 false 不被 Boot 重复注册；内层观测 filter 和实际 worker 原生 MDC 优先且恢复 |

`RequestBoundaryHttpTest` 的 Principal/认证头仅为真实 Servlet 容器中的测试认证替身，不声称实现 JWT。DeferredResult 外部 producer 明确保持匿名，库仅在请求再次派发时安装快照；任意后台任务和 AsyncContext.start 的 SecurityContext/观测传播归宿主。

## TDD 与故障时点

逐步日志全部保留在 `.verification-results/ticket-06`，没有放在 `target`，Maven clean 不删除。

- `red-01` 至 `green-10`：来源默认、trusted proxy、trace 宿主值与恢复、请求边界、真实同步/Callable/DeferredResult 逐片加入；没有先写全部实现后补测试。
- `async-error-11`、`disconnect-12`、`async-error-disconnect-13`、`green-14-and-lifecycle`：用 latch/实际 socket 断连证明超时观察与实际工作退出不同；线程退出后才观察清理。
- `red-15` / `green-15`、`scope-16`、`host-policy-17`、`red-18` / `green-18`、`http-final-19`：宿主内层观测、嵌套 ERROR、策略抛出、配置与 worker 观测优先。
- `red-20` 复现 preProcess 中 get/put/回滚阶段失败留下 A 的 SessionUser；Spring 不保证为失败的 preProcess 调用 postProcess。`red-20-post` 复现恢复异常覆盖原业务失败。`green-20` 四种故障均恢复身份、保留原异常。
- `red-21` 复现 Servlet trace finally 覆盖原异常；`green-21` 五种 MDC 故障及相关公开场景通过。`red-22` / `green-22` 固定关闭默认 trace 时用户 bean 被自动重复注册。
- 目标平台不删测、不降低断言；只迁移新增 fixture 的 Boot 类型包。目标 33 项第一轮全部通过。
- `target-budget-edges.log` 追加配置名字1/127/128成功与null/空/129拒绝、trace1/63/64/65、CIDR数量127/128/129及IPv4/IPv6 /0/full-prefix字面边界，14项通过；无产品代码变化，随后固定 `d010234` 重新运行完整入口。

MDC 故障测试通过公开 SLF4J ServiceProvider SPI，在隔离 JVM 启动真实 HTTP 应用；`get/put/rollback/post/servlet` 五种模式各有 deadline 和独立日志。故障在 SessionUser 已安装或原失败已抛出的确定阶段触发，不依赖反射或 mock 私有方法。host adapter 可以在变更后抛异常，验证部分安装与首因/suppressed；若宿主 adapter 在恢复前拒绝操作，库不能保证修复它的内部数据，但身份先清理，失败向宿主传播。

## 预算、样本与复现

| 维度 | 已验证范围 |
|---|---|
| 代理链 | 31/32/33 跳、2047/2048/2049 字符、128/129 CIDR、IPv4/IPv6 前缀上下界和跨网段；重复头、空项、空白、控制字符、复杂 Unicode、主机名、缩写 IPv4、前导零、zone/端口/方括号回退或配置拒绝 |
| trace | 1–64 ASCII 值和 65 字符拒绝；重复/CRLF/Unicode；header-name / mdc-key 1–128 配置界；accept-inbound 与 generate-if-absent；无效宿主值作用域后原样恢复，不影响其他 MDC 键 |
| 性质 | 固定 seed `0x06b0d1`，256 个伪造/Unicode 转发头；不可信连接 peer 的解析结果恒定。期望 IP 为独立字面量和 CIDR 模型，非调用当前实现生成 expected |
| HTTP 工作资源 | Servlet 1 线程、MVC 1 worker / 4 等待项；latch 有截止时间。Callable 断连最大生成 256 MiB、8 KiB chunk，1 KiB socket receive buffer，实际 TCP reset 导致提前 I/O 失败；不分配 256 MiB 整体数组 |
| 隔离 MDC 子进程 | 每例 `-Xmx128m` / `-XX:ActiveProcessorCount=2` / 35 秒退出预算；父测试 45 秒界，失败销毁其自有子进程；成功后同 worker 请求 B 无 A 身份，最后关闭服务器和测试 executor |

可重放最小公开集合：

```powershell
.\mvnw.cmd -B -ntp '-Dtest=ClientIpPolicyTest,RequestBoundaryScopeTest,RequestBoundaryHttpTest,TraceIdFilterTest,MdcFailureHttpContractTest' test
```

## Q01–Q10 与完成边界

| 标准 | 本票证据 |
|---|---|
| Q01 | 上述 FR/AC/J03/J04/J07 映射及 ADR-0029；公开 Servlet/API、真实 HTTP 和宿主 SPI 反例 |
| Q02 | null/empty、默认、不信任、所有适用头/链/CIDR预算、Unicode/重复、嵌套/重入；IP 不依赖 Locale 或时间，未引入新的时间算法 |
| Q03 | 真实 Tomcat HTTP/ERROR/ASYNC、实际工作线程与 socket；目标普通 jar runner；无需数据库/事务 |
| Q04 | Callable/DeferredResult 超时、实际中断与迟到退出、TCP RST、宿主 IP/MDC 失败；屏障/队列断言复用，未使用随机 sleep 建立正确性 |
| Q05 | 上述有界输入/进程/线程；scope finally、实际 worker 回滚和服务器关闭；不新增库拥有的 executor |
| Q06 | IP/trace 预期为固定字面样本，安全迁移有明确 default/trusted 比较；本票不新增持久化/编码格式，无额外序列化金样需求；完整 runner 保留现有独立 JSON/HTTP 金样 |
| Q07 | 固定 seed 256 输入和有限失败阶段矩阵，可按公开 selector/子进程模式重放 |
| Q08 | 精确提交、平台、Locale/timezone、命令与持久证据；Linux 尚待同源 CI，不将 Windows 数字冒充跨平台结果 |
| Q09 | 全量测试/原覆盖率/5架构/依赖门见结果；保留旧测试，原无条件 XFF 断言按已批准安全默认明确迁移，没有 skip 或阈值修改 |
| Q10 | 代码、测试、ADR、迁移与报告同票提交；Linux CI 未收集前保持 verification-pending；27/33 的跨票职责单独登记，不伪报完成 |

票 24 继续目标平台条件装配/Servlet 6.1 API 组合，本票已在 Servlet 6.1 真正运行当前请求生命周期路径。票 27 提供真实 Security 认证/原生上下文；票 26/33 继续其他宿主观测和最终候选跨票组合。
