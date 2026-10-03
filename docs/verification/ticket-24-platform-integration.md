# 票 24：目标平台真实消费者接合

状态：in-progress，最终同源 Windows all 与 Linux CI 待记录。决策 [ADR-0047](../adr/0047-boot4-consumer-integration.md)。完整平台门不能用独立工具链探针或旧平台报告替代。

## 实现与可复现入口

在票 23 目标迁移基础上，24 只修复两个已复现的接合缺口：Servlet 6.1 response wrapper 新重载绕过旧捕获/charset 规则；只存在 Caffeine、缺少 context-support 时未按已有契约注册任何 CacheManager。其他变更是实际消费者及证据归档，不扩展缓存、锁、幂等业务协议。

```text
java verification/Verify.java all --fresh
java verification/Verify.java platform --fresh
```

JDK 25，`JAVA_HOME` 指向该 JDK；`VERIFY_WRONG_JAVA_HOME` 指向真实 JDK 21。Windows PowerShell 手动传 `-D...` 时应将整个参数单引号引用。runner 使用固定 Wrapper、空 settings、按次隔离 repo/cache；所有消费者仅使用安装 jar，新增矩阵还逐字节核对安装 jar 与本次根构建 jar。

## 反例与已完成的局部验证

本票原始日志根为 `E:/GenCode/server-facility-worktrees/ticket-24/.verification-results/ticket-24/`，最终报告另按 runner 时间目录归档。

| 公开契约 | RED | GREEN / 断言 |
|---|---|---|
| Servlet 6.1 Charset 在 writer 后冻结 | `01-charset-red.log`，HTTP 响应头错误变为 ISO-8859-1 | `02-charset-green.log`：首次及重放头保持 UTF-8，独立预期字节 C3 A9 |
| 三种新 redirect 入口不得保存无完整 Location 的副本 | `03-redirect-red.log`，5 种 boolean/status/full 调用重放错误返回 302/307 | `04-servlet61-green.log`：容器首次 Location/状态/clearBuffer 正确，后续同 key 保持 409，不重放旧 prefix；连同既有捕获契约 21/0/0/0 |
| Caffeine-only 真实缺类图 | `05/06-caffeine-only-*`，普通 jar 无 CacheManager；基线 jar SHA 与票 23 一致 | `10/11-caffeine-only-*` 普通 jar GREEN；`07-caffeine-fast-red.log`→`08-caffeine-fast-green.log` 快速配置 7/0/0/0 |
| 实际非 Web 五图与应用覆盖 | `matrix-first` 保留新 fixture 未声明 primary 的 MessageSource 按类型查询失败 | 修正合法 fixture 的 primary，不修改产品；`matrix-second` 的 5 图 / 11 JVM 均通过 |
| 06/18 平台接合 | `13-integrated-06-18.log` 对应 `.verification-results/20261004-015441-693-integration` | 1375/0/0/0、原5架构/依赖/覆盖率门、core/非Web/JSON金样、3种真实Web、5图11场景、所有先决条件通过；此轮 Web fixture 在运行期间补显式入站 trace 政策，属于中间验证，最终证据另列 |
| 双引擎 / processor / classfile | `.verification-results/20261004-020052-096-platform`，来源 744301d | 5项正向、Jupiter与ArchUnit各精确1项负控，14技术类型归属、配置processor/Lombok、JaCoCo与依赖分析通过；scope 仅工具链 |

`12-integrated-06-18.log` 是 PowerShell 未引用 `-Dfile.encoding` 的命令行错误，JVM 未启动 runner；修正命令后的证据单独保留，没有把它记为产品测试失败或通过。

## 契约、场景与归属

| 需求 / 接合 | 可观察证据 |
|---|---|
| FR01 / AC01 / J01 | Wrapper/JDK25、每个普通 jar class 69.0、无 BOOT-INF、imports与配置metadata；root effective POM/tree和独立引擎探针 |
| FR02 / AC02 / J02 | `PlatformConsumer` 的 minimal / no-jackson-module / caffeine-only / context-support-only / cache-pair 真正 Maven 依赖图；不存在 Servlet/MVC、validation provider、Tika/POI、JUnit、context runner；缺 Jackson 模块仅使 JSON 装配让位 |
| FR02 / AC09 / J02 | 用户 CacheManager、具名 primary MessageSource、JsonMapper/Jsons/普通Executor；两个无 primary mapper 确定拒绝并包含两个候选名，显式 primary 恢复；cache/idempotency 显式关闭 |
| AC02 / AC09 / J03 | `PlatformWebConsumer` default/user/disabled 真实 Tomcat HTTP：同应用 mapper 与全部 JSON converter 身份，用户命名政策实际输出、ServletContext 单次注册及实际 trace→repeatable→capture，413 在错误边界内、ERROR 404安全JSON |
| J04 / J05 / 03、06接合 | 全库 Async/请求作用域回归；实际 Boot 平台/虚拟线程选择；旧 JSON 金样两个同时存活context交错、关闭及重建；本票不替代26的MessageSource/日志多应用政策收缩 |
| AC12 / J08 / J14 | Servlet6.1 charset/redirect 真实首次/重放；原有SSE/下载/捕获预算/失败资源测试原样保留；旧 JSON 字面样本未变，只有等价escaping/属性顺序按值比较 |
| J12 / 13普通产物接合 | `PlatformUploadConsumer` 使用真实生产 Spring MultipartFile接口、普通jar，有/无Tika图核对虚假元数据、实际5字节保存/4字节拒绝、MIME政策及缺必需detector明确失败、成功文件归调用方删除且stage为0 |
| J13 | 无POI不阻断普通应用；格式引擎与Excel语义仍由16负责；未宣称完整文件业务验收 |
| J15 / J16 | 本次all重跑各已集成能力资源及OS分支，有限进程/字节预算见下；最终跨能力稳态与Windows/Linux同候选组合由33汇总 |

J06/07/09/10/11/17 的授权幂等、计费、数据库事务/恢复、宿主外部HTTP与持锁任务实际生命周期分别由12/09/28–30/25/07负责，本票没有实现这些新协议，不将装配成功当作这些接合通过。

## Q01–Q10 与边界

| 项 | 验收内容 |
|---|---|
| Q01 | 上表关联FR/AC/J与公开HTTP、MultipartFile、应用装配、普通产物；两个研究反例均有先RED后GREEN；ADR0047登记0015/0028/0045/0046关系 |
| Q02 | redirect所有三个新重载与clearBuffer真/假；Charset选择前/后；缓存两项缺任一/均有；默认/合法覆盖/歧义/primary/关闭；旧空/null/Unicode/数值/时间金样仍随全门 |
| Q03 | 根快速装配回归保留，真实Maven图+独立JVM+Tomcat HTTP；不借助库test classes或MockMultipartFile |
| Q04 | 两个mapper歧义确定失败、实际redirect状态转换；现有取消/IO/异常/权限和资源测试由全门复跑；本票不新增业务并发协议，故不另造随机调度测试 |
| Q05 | 非Web256MiB/45s，Web256MiB/60s，JSON金样256MiB/90s；HTTP5s、Tomcat4线程；upload128MiB/45s、文件最多5字节；runner超时只终止自己的进程树，context/client关闭；all额外5次独立应用启停 |
| Q06 | 保留21旧平台字面金样及来源；UTF-8 é 固定 C3 A9、redirect由官方Servlet契约给预期；上传用字面hello及虚假PDF提示，结果不由本实现生成expected |
| Q07 | 此票为有限依赖与重载矩阵，穷举5图/11非Web/3Web/2上传图；没有新增算法需独立fuzz。23的固定seed字段流性质与13字节样本仍在全门 |
| Q08 | 每次保存revision/worktree、JDK/OS/Locale/时区、模型、classpath、输入和SHA；失败原日志不覆盖；无宿主秘密入fixture |
| Q09 | 原88/88/75、五架构规则、dependency failOnWarning不变；新根测试7项（Charset1、redirect5、Caffeine1），无删除/skip；独立消费者不混入JUnit计数 |
| Q10 | 产品、测试、ADR、版本/兼容盘点、执行记录同票；Linux未执行前仍verification-pending，03/05待验证项不会由本机结果提前关闭 |

## 最终执行记录

待同步票17并记录最终被测SHA、all日志、普通jar SHA、JUnit发现及覆盖率。Linux与Windows CI必须引用同一集成SHA及artifact；本机没有可用Linux执行环境，此处不能勾选Linux通过。
