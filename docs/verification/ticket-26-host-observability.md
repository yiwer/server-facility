# 26 — 应用消息、日志与标准观测验证

2026-10-04。状态 verification-pending；Windows同源完整门已通过，Linux及合入后的Windows CI待执行。下列局部TDD证据与完整门分别登记，不拼接不同来源为最终候选。

## 契约与已执行证据

| 共同标准 | 场景、入口与证据 |
|---|---|
| Q01 | FR02/03/08/09，AC04/07/11/12；MessageSource、SLF4J/Logback、标准Micrometer、真实Servlet/普通可执行jar为预先批准 seam。ADR0049及应用观测迁移文档记录所有权。 |
| Q02 | 应用bundle优先/缺key/Locale回落、构造注入不歧义、错误参数不匹配、非法method/route/控制字符/长Unicode、日志后端RuntimeException、核心错误安全本地化、no-trace incident reference。 |
| Q03 | HostMessageSourceContractTest真实Spring/Boot组合；HttpErrorContractTest真实Tomcat验证WrappedError→BusinessException→本地化ProblemDetail。模板GreetingController真实HTTP注入MessageSource，领域模块保持纯Java。 |
| Q04 | 模板两应用不同实际采样策略、关闭一个后另一个继续；标准Callable/DeferredResult在平台/虚拟线程均同时存在Observation和Tracer scope，Brave最终SERVER span与字面W3C parent一致。已有执行拒绝、队列/运行取消、部分MDC安装失败及原异常保留回归继续执行。 |
| Q05 | 诊断输出method/route/class/字段数沿有限边界，超长异常内容不复制到响应或日志；完整同源92命令含独立资源消费者及5次应用启停已通过。MessageSource/SLF4J本身为宿主组件，本票不宣称能抢占任意恶意后端。 |
| Q06 | 标准Logback实际event捕获、Spring消息源、真实HTTP、Brave completed SERVER span作为独立oracle；不以自造trace文本的来回转换代替实际scope。 |
| Q07 | Unicode/控制字符/格式输入确定性表、不同locale/trace样本与后端异常注入；不自造消息格式或W3C算法。 |
| Q08 | `.verification-results/ticket-26/` 保存各轮RED/GREEN与fixture失败；密码/token/SQL/上传哨兵不进设施HTTP/访问/下载诊断。本地同源哈希、计数与OS记录见下。 |
| Q09 | 原88/88/75门及0失败/0跳过要求保留；Windows完整门已通过，合入后CI待执行。模板继续使用25的官方offline instrumentation和原class架构检查，子进程贡献覆盖率。 |
| Q10 | 迁移文档、可执行应用、本票兼容入口齐备；ADR已Accepted，票据等跨OS证据后关闭。 |

## TDD记录及非产品失败

01宿主bundle、02默认注入、03显式Locale、04访问日志单管线、05异常隐私、06日志后端故障、07多context/输入边界、08标准trace读取、09incident关联已记录。08曾错误使用没有ProblemDetail mixin的测试mapper；更正后产品RED有效。临时恢复源文件时旧mtime令Maven复用RED class，clean后104项通过；不作为产品flaky隐藏。

10核心错误HTTP本地化实际RED（只返回Bad Request）→21项相关测试GREEN。11下载原失败在DEBUG输出SQL/token/上传哨兵实际RED→去掉重复日志后8项GREEN；第一次测试误写Result.unwrapErr导致编译失败，单独保留，更正为公开getErr后才有产品RED。

12模板标准W3C请求无trace的RED→Boot标准starter GREEN。13 Callable/DeferredResult仅MDC有值但Tracer无scope的RED→应用私有ObservationThreadLocalAccessor后8项GREEN。14本地化Hello→Bonjour产品RED→构造注入成功；结合旧失败生命周期测试时错误要求无span incident UUID必须是32hex，按既定UUID诊断契约修正（允许标准16/32hex trace或UUID）。15最初采集到Security内部span而错误套用SERVER parent断言，改为明确采集SERVER；两应用采样隔离、标准parent、HTTP失败与executor故障合计21项GREEN。

模板接合使用独立复制的依赖仓 `.verification-results/repository`，当前库通过compile/jar:jar/install-file提供fixture；该命令不等于质量门。第一次PowerShell未引用`-DpomFile=pom.xml`导致参数拆分失败，更正后安装成功，日志保留。后续完整all负责重新构建、测试、安装和独立消费同一来源。

## 迁移与边界

见 [应用观测](../building/application-observability.md) 和 [ADR0049](../adr/0049-application-owned-observability.md)。旧入口保留且标注弃用；默认路径不再走静态日志分发、聚合MessageSource或旧TraceIdFilter。旧聚合的委托无环、静态cache/开关的生命周期仍属显式兼容调用方责任。没有创建通用审计或秘密检测系统。

## Windows 同源完整门

冻结来源 `72a37b6e1981e12a5dc292e22d55c28d5b93c5a8`，运行前工作树干净。2026-10-04 05:48开始，Oracle JDK25.0.4.1、Windows11/amd64、Asia/Shanghai、zh_CN、原生GBK，使用空独立Maven仓库运行 `java verification/Verify.java all --fresh`。原始证据 `.verification-results/20261004-054819-067-all/`，92个命令及预期失败控制全部通过，summary终值 `RESULT=PASS`。

- 库1614项，失败/错误/跳过均0；5项架构和依赖边界通过。INSTRUCTION 24497/26393、LINE 4793/5087、BRANCH 2579/3044，保持88/88/75门。
- 库jar SHA256 `e97cedb5ab07ac9cabe638bf001ded6a5bb2f22ac521351fe09755d3ebc59595`，仅标识该制品，不宣称字节可重现构建。
- 独立模板52项，失败/错误/跳过均0、原覆盖率门通过。普通可执行jar在平台及虚拟线程完成真实HTTP；W3C/Brave SERVER span、异步scope、语言与两应用采样隔离由StandardObservationHttpTest验证。jar SHA256 `2fabc4e37410a92135ccd86f39f8acd86f4ed1c2db0b774101089410b0a1c533`。
- 库外partner应用14项与原覆盖率门、实际双上游HTTP消费者通过；本轮不含后续25的第15项null库存测试。template/partner分别从含空格、中文、希伯来文的新目录构建，不引用原target。
- 无框架core/crypto、IO/CSV/Excel四依赖图、限流与claim进程、JSON两应用、Servlet、Tika有无、五依赖图十一JVM、5次启停/关闭与坏checksum/缺JDK/真实JDK21负控均通过。两独立应用缺覆盖率报告的负控均明确拒绝；offline测量不进入生产依赖图。

随后合入集成线新增的Windows短目录规范化和25库存null协议修复；它们不在上述冻结来源内，合入候选的联合OS验收由下一轮CI另行登记。
