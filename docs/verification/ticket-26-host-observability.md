# 26 — 应用消息、日志与标准观测验证

2026-10-04。状态 in-progress；以下为已执行的 TDD / 局部接合证据，完整同源门和 Windows/Linux CI 尚待执行，不拼接成全门。

## 契约与已执行证据

| 共同标准 | 场景、入口与证据 |
|---|---|
| Q01 | FR02/03/08/09，AC04/07/11/12；MessageSource、SLF4J/Logback、标准Micrometer、真实Servlet/普通可执行jar为预先批准 seam。ADR0049及应用观测迁移文档记录所有权。 |
| Q02 | 应用bundle优先/缺key/Locale回落、构造注入不歧义、错误参数不匹配、非法method/route/控制字符/长Unicode、日志后端RuntimeException、核心错误安全本地化、no-trace incident reference。 |
| Q03 | HostMessageSourceContractTest真实Spring/Boot组合；HttpErrorContractTest真实Tomcat验证WrappedError→BusinessException→本地化ProblemDetail。模板GreetingController真实HTTP注入MessageSource，领域模块保持纯Java。 |
| Q04 | 模板两应用不同实际采样策略、关闭一个后另一个继续；标准Callable/DeferredResult在平台/虚拟线程均同时存在Observation和Tracer scope，Brave最终SERVER span与字面W3C parent一致。已有执行拒绝、队列/运行取消、部分MDC安装失败及原异常保留回归继续执行。 |
| Q05 | 诊断输出method/route/class/字段数沿有限边界，超长异常内容不复制到响应或日志；资源消费者与完整同源进程仍待执行。MessageSource/SLF4J本身为宿主组件，本票不宣称能抢占任意恶意后端。 |
| Q06 | 标准Logback实际event捕获、Spring消息源、真实HTTP、Brave completed SERVER span作为独立oracle；不以自造trace文本的来回转换代替实际scope。 |
| Q07 | Unicode/控制字符/格式输入确定性表、不同locale/trace样本与后端异常注入；不自造消息格式或W3C算法。 |
| Q08 | `.verification-results/ticket-26/` 保存各轮RED/GREEN与fixture失败；密码/token/SQL/上传哨兵不进设施HTTP/访问/下载诊断。当前还未记录完整候选哈希和OS总结果。 |
| Q09 | 原88/88/75门及0失败/0跳过要求保留；完整门尚待执行。模板继续使用25的官方offline instrumentation和原class架构检查，子进程贡献覆盖率。 |
| Q10 | 迁移文档、可执行应用、本票兼容入口齐备；ADR/票据最终状态等完整门与跨OS证据，当前不关闭。 |

## TDD记录及非产品失败

01宿主bundle、02默认注入、03显式Locale、04访问日志单管线、05异常隐私、06日志后端故障、07多context/输入边界、08标准trace读取、09incident关联已记录。08曾错误使用没有ProblemDetail mixin的测试mapper；更正后产品RED有效。临时恢复源文件时旧mtime令Maven复用RED class，clean后104项通过；不作为产品flaky隐藏。

10核心错误HTTP本地化实际RED（只返回Bad Request）→21项相关测试GREEN。11下载原失败在DEBUG输出SQL/token/上传哨兵实际RED→去掉重复日志后8项GREEN；第一次测试误写Result.unwrapErr导致编译失败，单独保留，更正为公开getErr后才有产品RED。

12模板标准W3C请求无trace的RED→Boot标准starter GREEN。13 Callable/DeferredResult仅MDC有值但Tracer无scope的RED→应用私有ObservationThreadLocalAccessor后8项GREEN。14本地化Hello→Bonjour产品RED→构造注入成功；结合旧失败生命周期测试时错误要求无span incident UUID必须是32hex，按既定UUID诊断契约修正（允许标准16/32hex trace或UUID）。15最初采集到Security内部span而错误套用SERVER parent断言，改为明确采集SERVER；两应用采样隔离、标准parent、HTTP失败与executor故障合计21项GREEN。

模板接合使用独立复制的依赖仓 `.verification-results/repository`，当前库通过compile/jar:jar/install-file提供fixture；该命令不等于质量门。第一次PowerShell未引用`-DpomFile=pom.xml`导致参数拆分失败，更正后安装成功，日志保留。后续完整all负责重新构建、测试、安装和独立消费同一来源。

## 迁移与边界

见 [应用观测](../building/application-observability.md) 和 [ADR0049](../adr/0049-application-owned-observability.md)。旧入口保留且标注弃用；默认路径不再走静态日志分发、聚合MessageSource或旧TraceIdFilter。旧聚合的委托无环、静态cache/开关的生命周期仍属显式兼容调用方责任。没有创建通用审计或秘密检测系统。
