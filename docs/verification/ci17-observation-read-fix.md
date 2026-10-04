# CI17：读取 trace context 的竞争修复

2026-10-04。此报告记录测试夹具缺陷及修复，不将 CI17 的失败改写为通过。CI17 来源、两套操作系统的失败和归档元数据见[联合记录](ticket-08-10-20-ci.md)。生产 TaskDecorator、Security、Servlet 超时和采样政策均未修改；Java assertions 保持开启。

## 已复现的原因

`StandardTracingFixture.Endpoint.scope()` 原本通过 `tracer.currentSpan().context().traceId()` 读取关联信息。Brave 6.3.1 返回 LazySpan；读取其 context 会进入 `Tracer.toSpan`，这不是纯读取。它先取得仍在 pending 表中的非 local-root context，而请求线程恰好结束该 Security 子 span 后，随后 `_toSpan` 试图再次建立它，触发 `PendingSpans` 的 parent 断言。异步任务因此未完成 DeferredResult，2 秒后客户端得到 503。

依据为[当前 Brave Tracer 源码](https://github.com/openzipkin/brave/blob/6.3.1/brave/src/main/java/brave/Tracer.java)、[LazySpan 源码](https://github.com/openzipkin/brave/blob/6.3.1/brave/src/main/java/brave/LazySpan.java)和[PendingSpans 源码](https://github.com/openzipkin/brave/blob/6.3.1/brave/src/main/java/brave/internal/recorder/PendingSpans.java)，并有本地真实 HTTP 堆栈佐证。并非根据异常文字直接推测，亦未通过关闭 assertions 或扩大时间预算规避。

`.verification-results/ci17/07-overlap-red.log`：单个真实应用、8 个并发客户端，每个最多 200 次 DeferredResult 请求，JVM 限 2 个处理器。两条 worker 堆栈均为 `PendingSpans.getOrCreate → Tracer._toSpan → Tracer.toSpan:349 → LazySpan.context → BraveSpan.context → Endpoint.scope`；随后真实 `AsyncRequestTimeoutException` / HTTP 503。一个 worker 在第 192 次请求断言失败，JUnit 结果为 1 个 error。实际失败日志及 Surefire 报告保留，不能把这些请求计为全部成功。

## 诊断与最小修复

此前保留的对照：原始 5 项测试通过；同步/Callable/Deferred 三路径各重复 100 次、两线程模式通过；显式等待初始 Servlet dispatch 全部退出后再执行 worker 也通过。临时屏障确认捕获的是 `spring.security.http.secured.requests`，而且 child span 已结束。完全结束后 Brave 会重新装饰 context；触发断言的是读取和结束的竞争窗口。单应用并发复现排除了“必须两个应用才失败”的假设，完整 worker 栈也排除了仅仅是机器较慢的解释。

前述 01–06 诊断曾借用票 20 的已安装普通 jar，只用于缩小问题；07 及后续采用当前集成源构建的隔离普通 jar。该次 fixture 构建来源 runtime 与 `0816c7a721c07f738c3fdd531d32e710a07e0a2c` 相同，jar SHA256 `0ad7ca80df4b8a8ddf2f5def024d6ccf4dc9ca21a68b6850c70e03ac90456b8b`。这是 compile/jar/install-file 的诊断制品，不声称该命令重新运行了库的全质量门。

修复只让夹具通过标准 `tracer.currentTraceContext().context()` 读取 trace ID，避免为了检查关联而重新创建 span。仍检查真实 HTTP 200、指定 W3C trace ID、活动 Observation，以及独立 Brave handler 收到的 SERVER span、字面 parent 和 kind；两个同时存活应用的不同采样政策及关闭隔离断言保留。新增并发回归分别在平台/虚拟线程执行 8 × 200 次请求；采样测试先断言 HTTP 状态，避免将错误正文的缺字段误报为 trace 格式错误。

独立短审没有发现契约削弱，提出压力测试失败时其他任务仍可能继续的问题。后续 `4dfd4c2` 给所有 Future 共用一个 90 秒 join deadline，并在 finally 中取消所有任务、shutdownNow。原 HTTP 单次预算和中断语义不变。短审原稿在协调目录 `ci17-context-read-short-review.md`，不替代最终 Standards/Spec review。

临时屏障和带标签的诊断输出已从测试源移除，源码保存在忽略的 `diagnostic-inputs` 目录，第三方来源文本保存在 `sources`。没有提交诊断探针、依赖缓存或数据库数据。

## 已执行验证与范围

- `08-context-read-green.log`：7 个观测测试全部通过，失败/错误/跳过为 0，2 CPU；两个新增场景分别完成 1600 次请求。
- 冻结 `e9328d5516797c9ad186aac86969287225bf6ebd`、工作树干净后执行完整模板 `clean verify`：`09-full-template.log`，80 项全部通过，失败/错误/跳过为 0。原覆盖率门保持 88%/88%/75%；实际 instruction 1963/2002、line 269/277、branch 182/215。原始 Surefire、JaCoCo 和可执行 jar 已保存到 `09-full-template-evidence`，避免后续 clean 删除。
- 该完整模板门先于上述 Future 清理补丁；最终观测回归和下一次 CI 结果单独记录，不能冒称早先完整门包含后续源码。
- 冻结 `4dfd4c230df62505b5dd2c49df8beade3bfae34d` 后执行最终 `StandardObservationHttpTest`：`10-final-observation-green.log`，7 项、失败/错误/跳过均为 0，2 CPU，包含共用 deadline 和取消清理；再次完成两线程模式各 1600 次请求。

本地环境为 Windows、Oracle JDK 25.0.4.1、Wrapper Maven 3.10.0、真实原生 PostgreSQL 18.6。所有后续集成闭合仍以最终提交的 Windows/Ubuntu CI 为准；这份诊断报告本身不关闭 08/10/20。

## 后续双平台结果

最终集成源250ce2d的CI18已完成Windows/Ubuntu全部all、platform和归档，含本修复最终测试源；08/10/20据此闭合，见[CI18验收](ticket-08-10-20-ci18.md)。该结果不改变上述每次本地验证的精确范围，也不把CI17失败改为通过。
