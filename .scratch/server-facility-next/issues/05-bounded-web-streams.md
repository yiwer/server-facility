# 05: 让普通下载与请求体处理保持流式和有界

**What to build:** 普通下载、SSE 和非目标请求不再经过全量响应捕获；需要重复读取的请求体及选定响应捕获都有明确预算和关闭责任。

**Blocked by:** None (can start immediately)

**Status:** closed

**当前闭合记录（2026-10-04）**：`80670fa`的Windows/Ubuntu `all --fresh`与独立平台控制全部通过，见[同源CI证据](../../../docs/verification/ticket-24-ci.md)。下列实施记录中“待24/Linux”等为各阶段历史状态，现由此记录闭合；未实施的下游能力仍按各自票负责。

**Traceability:** FR-03、FR-05；AC-05、AC-07、AC-10

## Acceptance criteria

- [x] 捕获仅限显式目标，非目标无需完整生成即可输出；单次注册及 wrapper 顺序明确。
- [x] Repeatable body 与响应捕获分别定义限额、未知长度、溢出和部分提交政策。
- [x] 消费者取消/断开时停止无意义工作并清理自身资源；流所有权公开。
- [x] 重复读取必须返回一致字节，明确 charset 与 reader/stream 组合规则；Servlet 非阻塞读取实现约定回调或确定拒绝，禁止 setReadListener 静默无动作。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：实际客户端在服务端生成完成前读到下载/SSE 前缀，HEAD、空体和错误派发遵守协议。
- [x] 边界：Content-Length 正确/缺失/失真、chunked、N−1/N/N+1、超大但受控输入与多字节边界。
- [x] 故障：慢读慢写、客户端断开、写失败、取消、已提交后异常，资源清理且不二次写入。
- [x] 资源：受限堆的独立进程里响应规模增加不会无界增长；不耗尽宿主模拟极端情况。Windows/JDK25/Boot3已完成（96MiB堆、64/256MiB普通与选定响应），新增Linux场景已由CI37137011984通过；仅待24的Boot4/Servlet6.1复验。
- [x] 请求协议：两次读取正文相同；合法 application/problem+json、伪 application/json-unknown、声明字符集/畸形 charset、非阻塞回调的完成/错误和重复注册按约定验证。

## Scope boundary

旧幂等资格与保存政策由票 12 接入；本票不建立全站响应缓存。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 实施与验证（2026-10-04）

- 已实现默认非目标直通、有界显式捕获与流所有权。Repeatable默认disabled，启用时预算必须正数；原0/负无界模式按批准资源政策退出，默认wrapper为10MiB。默认响应副本1MiB，超限只放弃副本并保留旧claim，不重新执行操作。旧手工ContentCaching保存旁路退出，方法签名保留。
- 实现`a2c5b4d`，边界/资源`e7d27d7`，合最新integration `66bf4d0`的提交`15bc0c7`；最终被测源码/POM`5a59d2f23f36d38f088753232d125db7e3e9878a`。后续只补证据。复用04真实Tomcat底座与最终共享错误策略，过滤器次序error+1/repeatable+2/capture+3，用户bean让位且单次注册。
- 最终 `mvnw.cmd -B -ntp clean verify`：**1323/0/0/0**，5原架构规则、instruction92.9939%/line93.3940%/branch86.1614%、原门及依赖分析通过。相对integration净增47项；首次全门的used-undeclared已通过显式test-scope junit-jupiter-params修复，未增加ignore。
- 真实HTTP证明下载/SSE在producer完成前读到前缀、真实异步SseEmitter、慢chunked输入、N−1/N/N+1和多字节/charset、标准400/413、HEAD/空体、ERROR派发、已提交异常不追加不保存prefix、客户端body取消与小窗口TCP RST提前停止64MiB复制并释放文件。
- 子进程96MiB堆/32MiB direct cap，8MiB预热后普通与选定响应各64/256MiB全部完成，保留堆最大17295320、较基线增长44448字节（阈值16MiB），进程退出0。固定seed `0x05b0d1` 的64个有界字节样本与所有RED/GREEN日志保存在 `.verification-results/ticket-05/`，clean未删除。
- [ADR0028](../../../docs/adr/0028-bounded-web-streams.md)部分替代0017的全局/无界捕获；USAGE/CHANGELOG有明确迁移。完整SHA/产物hash/环境/Q01–Q10/J03/J08/测试数变化和已发生失败见[验证报告](../../../docs/verification/ticket-05-bounded-web-streams.md)。

2026-10-04，root推送集成`2304a57103b8c6f6a0791a783b80440dd652c1b0`，新增场景在Windows/Ubuntu的完整`all --fresh`均通过且归档成功，见[跨平台证据](../../../docs/verification/ticket-05-ci.md)。各环境精确数值以各自原始报告为准，未用本地数字代替Linux观测。

仍为 **verification-pending**：仅待Boot4/Servlet6.1目标平台复验，Q08/Q10与资源整行未提前勾选。Servlet6.1新增sendRedirect重载及Charset重载会旁路旧wrapper方法，24须补入口并复验（ADR/报告已记录），不能把Boot3绿色当作目标平台通过。票12负责授权/scope、业务保存资格、过期重试与异步端点政策；J08后续接合、33最终扩大组合保持其主责，不反向建立05依赖33的循环。未发布制品。
