# 06: 固定请求身份、代理信任与上下文清理

**What to build:** 消费应用明确知道请求 IP、trace 和身份上下文来自何处，不因伪造头、派发切换或线程复用改变主体。

**Blocked by:** None (can start immediately)

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-03、FR-05、FR-09；AC-04、AC-07、AC-08、AC-12

## Acceptance criteria

- [ ] 可信代理列表决定何时采用转发头，原始请求头不直接成为身份；认证 Principal 的来源和适配边界明确。
- [ ] 兼容 SessionUser 上下文覆盖 SYNC/ASYNC/ERROR 及短路清理，嵌套 MDC 恢复；新模板使用 Security 原生上下文。
- [ ] trace/header 长度、非法字符和信任政策有界；不覆盖宿主已建立的有效观测上下文。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：公网直连、可信单/多代理、IPv4/IPv6，用户策略覆盖。
- [ ] 边界：伪造 XFF、空白/重复/超长/控制字符头、复杂 Unicode，不产生伪造可信主体。
- [ ] 生命周期：复用线程中 A 后接匿名 B，Filter 短路、Callable、DeferredResult、超时、ERROR、断开，身份与 trace 不串用。

## Scope boundary

不签发 JWT；生产资源服务器由票 27 独立交付，Cookie/HTML 输入兼容由票 32 交付。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。
