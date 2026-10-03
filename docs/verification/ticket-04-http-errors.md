# 票 04：安全 HTTP 错误的 Windows 与独立消费者证据

日期：2026-10-04。实现检查点 `d1dee03`、边界修复 `6eea3a7`、最终解包/诊断补丁 `37ee5f5`。最终源验证 HEAD `37ee5f5` 已包含integration `5428982`（票03/21与CI文档）；后续只更新证据。后续集成 `66bf4d0` 已取得 Windows/Ubuntu CI 全绿与归档，[CI记录](ticket-04-ci.md) 闭合本票；下文保留本机证据来源。

## 执行与产物

在 `E:\GenCode\server-facility-worktrees\ticket-04` 执行：

```powershell
.\mvnw.cmd -B -ntp clean verify
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
$env:VERIFY_WRONG_JAVA_HOME = 'C:\Users\yiwer\AppData\Local\Temp\server-facility-research-tools\jdk21\jdk-21.0.12.1+1'
& "$env:JAVA_HOME\bin\java.exe" verification/Verify.java integration
.\mvnw.cmd -B -ntp '-Dtest=GlobalExceptionHandlerTest,HttpErrorContractTest' test
```

- 完整 `clean verify`：**1276 测试，0 失败、0 错误、0 跳过**；5 条 ArchUnit 规则、dependency analyze 与原 JaCoCo 门通过。
- 指令 `17059/18306 = 93.1880%`，行 `3482/3730 = 93.3512%`，分支 `1693/1957 = 86.5100%`；最低门仍为 88/88/75，没有调整 ignore 或跳过条件。
- 集成 runner 报告 `.verification-results/20261004-000725-683-integration/summary.txt` 为 `RESULT=PASS`，再次完整构建/安装同一源到本工作树隔离 repository，实际执行 configured/override/invalid 普通 jar consumer、constructed/injected JSON HTTP consumer，以及 checksum/缺失 JAVA_HOME/真实 JDK21 拒绝验证。
- 隔离 repository 预热时仅复制了票 21 缓存中不位于 `cn/` 的第三方依赖；本票 facility jar 必须重新构建并安装，未使用其他工作树的 SNAPSHOT。此次 `fresh=false`，不替代 CI 的 `all --fresh`。
- 普通 jar SHA-256：`d7269428cbba095a193c67d79ac5b813102cd9b750b487fb9b207e0a4e56a7c2`。独立消费者加载此隔离仓库 jar，不引用库的源目录或测试类。
- 环境：Oracle JDK `25.0.4.1+1-LTS-5`，Wrapper Maven `3.10.0`，Windows 11 / amd64，`Asia/Shanghai`、`zh_CN`、UTF-8；Boot `3.5.16`、Spring `6.2.19`、Jackson `2.21.4`、Tomcat `10.1.55`。有效 POM、依赖树、消费者源码/金样/hash、Surefire XML、JaCoCo 与进程日志由 runner 归档。
- 最终补丁的选定运行：`GlobalExceptionHandlerTest` 59 + `HttpErrorContractTest` 17 = **76/0/0/0**（green-12）。随后再次完整构建/安装与普通jar集成验证，以上覆盖率和哈希来自最终补丁。
- 历史完整1274结果/旧hash保存在 `verify-integrated.log` 和 `20261003-235534-445-integration`；它们只代表cause解包审阅补丁之前，不与本节最终产物混用。

本票原始 RED/GREEN 及验证日志在忽略的 `.verification-results/ticket-04/`，Maven clean 不删除它们；原始工具缓存/日志不提交源码。测试输出中的 SECRET-INPUT 是人工哨兵，不是真实秘密。

## 行为与证据

| 契约 | 可重放入口 / 结果 |
|---|---|
| 默认成功 DTO、错误状态与完整 ProblemDetail 金样 | `defaultHttpFailureHasProblemStatusAndNeverExposesItsCause`、`problemGoldenHasStableCodeTraceAndNoInputDerivedInstance`：真实 GET/POST，安全 detail、code/errors/traceId、opaque instance；原默认 200 为 red-01，green-01 修复 |
| 400/404/405/406/409/415/422/429/500/503 | `statusMatrixRetainsProtocolAndSafeBodies`、`methodRejectionRetainsTheFrameworkAllowHeader`：真实参数解析、无路由、媒体协商、业务状态、返回值校验、DeferredResult 超时；Allow/Accept/Retry-After；red-02/06 → green-02/06 |
| 413 与坏 multipart 400 | `realMultipartDistinguishesMalformedInputFromUploadLimit`：真实 Servlet multipart 解析，缺 boundary 拒绝，文件 63/64/65 字节验证 N−1/N/N+1；`contract-multipart.log` |
| Filter/MVC/ERROR 与 Boot/宿主错误页 | `filterFailureUsesTheSameSafeHttpProtocol`、`errorDispatchStaysSafeWhenHostRegistersOnlyOneStatus`、`bootStandardErrorMappingRemainsSafeWithDiagnosticPropertiesEnabled`：宿主仅注册404时502仍安全ERROR派发，Boot自定义error.path受尊重，include-message/stacktrace=always不泄露；red-03/04 → green-03/04 |
| 秘密输入、cause、字段消息 | `validationExposesFieldIdentityWithoutRejectedValueOrConstraintMessage` 和默认/legacy/status/serialization 各场景注入哨兵；字段仅稳定身份和安全文案，坏JSON不回显；red-07 → green-07 |
| 用户 mapper/advice/locale | `hostAdviceTakesPrecedenceOverDefaultAdvice`、`hostMapperAndLocaleApplyAtBothMvcAndFilterBoundaries`；16个并发MVC/Filter请求交错法语/英语与trace，`concurrentRequestsKeepLocaleAndTraceInTheirOwnResponse` 逐响应核验；red-08 → green-08、contract-concurrency |
| legacy HTTP 200 金样 | `explicitLegacyGoldenIsSafeEvenInDevProfile`：手写旧 BaseResponse 字段形状来自变更前 BaseResponse 序列化契约（base fd24519），显式配置且dev仍不公开栈；默认金样与旧金样分别断言 |
| 序列化失败、已提交响应、未提交Writer | `serializationFailureHasSafeFallbackEvenWhenErrorSerializerFails`、`committedResponseIsNeverOverwrittenOrAppended`、`uncommittedWriterAndEntityMetadataAreReplacedWithoutLosingSecurityHeaders`：错误serializer也失败时一次安全500回退；已flush只保留原12字节；Filter/MVC未flush Writer均重置正文/实体头并保留nosniff，no-store生效；red-09/10 → green-09/10 |
| 服务返回约束、消息源故障、重复解析 | `serviceReturnConstraintIsInternalFailureAndNeverAnInputError`（真实Bean Validation结果）、`messageSourceFailureFallsBackWithoutExposingItsDiagnostic`、`repeatedResolutionRetainsTraceInBothBodyAndNewResponseHeader`：red-11为400/抛诊断/缺header，green-11修复 |
| Security 可复用策略 | `authenticationAdaptersCanReuseStandardStatusAndChallengeWithoutAnErrorDsl`：真实filter adapter直接注入策略，401/403及WWW-Authenticate正确；真实认证身份链属于27，未冒称完成 |
| 公共旧handler入口、字段/数值预算 | 24异常族 × 默认/显式legacy共48动态契约；11个边界测试覆盖≤0/999/1000/1001/Long.MAX_VALUE重试等待、31/32/33字段、119/120/121字符、Unicode/坏路径/超长trace、提交后不写与兼容子类不公开栈 |
| 循环/过深异常链 | `servletCauseCyclesHaveSafe500WithoutRecursion`、`servletUnwrappingBudgetHandlesBoundaryAndDeepInput`：原cycle触发StackOverflow、65层错误保留409；red-12→green-12，63/64层保留409，65/10000层及cycle回退500，原cause对象不修改、日志诊断有界 |
| 独立普通jar与JSON接合 | runner 的 08–10 consumer、14–15 JSON consumer 全部OK；已有字面金样复用，包含真实HTTP、宿主mapper与两个存活应用关闭重建，无类路径捷径 |

## Q01–Q10 与接合矩阵

| 标准 | 本票覆盖 / 明确边界 |
|---|---|
| Q01 | FR-03/09、AC-04/05/12 对应上表；小公共策略 seam 与 ADR-0027 明确替代 ADR-0003 的适用部分 |
| Q02 | HTTP正常/坏输入/未知资源、字段/上传预算N−1/N/N+1、毫秒零/负/溢出边界、Locale、Unicode路径、缺cause消息、重复解析、默认和显式legacy |
| Q03 | J03 的 Filter/MVC/ERROR/multipart/serializer 实际localhost请求及J14独立旧/新金样通过；J01普通jar消费由本次runner复验；J03真实身份链27后续接合 |
| Q04 | 真实异步超时，mapper/消息源拒绝，flush前后确定注入，16并发请求语言/trace隔离；无随机sleep、无私有实现交互次数断言。网络写入取消不会发起第二次错误写入，完整断连/流资源接合由05负责 |
| Q05 | 输出字段≤32、字段标识≤120，cause解包/诊断检查≤64层，multipart测试上限64字节；策略不创建池/后台任务；真实服务器与HttpClient逐个关闭、临时目录由JUnit管理，HTTP有界并发16；宿主序列化策略与服务器整体字节/线程预算由05/容器配置负责 |
| Q06 | 旧BaseResponse和新ProblemDetail使用手写字面期望，不由被测生产serializer生成expected；独立JSON consumer沿用21已冻结样本；旧构造/handler方法保留但弃用不安全展示入口 |
| Q07 | 状态矩阵、24异常族双模式、有限数值/长度/Unicode样本可确定重放；无随机调度/seed依赖。此策略不实现通用解析器或分布式状态机，不新增性质测试框架 |
| Q08 | 环境、提交、依赖、哈希和命令见上；本机Windows证据真实，同一集成66bf4d0的Windows/Ubuntu CI已通过，见CI记录；所有诊断输入为人工哨兵 |
| Q09 | 全量1276/0/0/0，原门及5架构规则通过。相对integration1265净增11：旧GlobalExceptionHandlerTest的65项迁为59项（48公开双模式+11边界），新增17真实HTTP；旧不安全message/stack/default200与私有helper断言由批准的新协议/消费者测试替代，不隐藏失败 |
| Q10 | 产品、17真实HTTP场景、公开边界、ADR、USAGE迁移与本报告齐备；集成后CI证据已闭合，票04为closed |

票05/06/12合入时需复验filter顺序 trace / error(+1) / repeatable(+2)、一次注册和流/重放边界；票27完成真实Security认证/拒绝接合；Boot4/Jackson3目标平台由22–24负责。本票不把这些后续票的未完成范围标为已通过。
