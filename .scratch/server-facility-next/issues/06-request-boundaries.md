# 06: 固定请求身份、代理信任与上下文清理

**What to build:** 消费应用明确知道请求 IP、trace 和身份上下文来自何处，不因伪造头、派发切换或线程复用改变主体。

**Blocked by:** None (can start immediately)

**Status:** closed

**当前闭合记录（2026-10-04）**：`80670fa`的Windows/Ubuntu `all --fresh`与独立平台控制全部通过，见[同源CI证据](../../../docs/verification/ticket-24-ci.md)。下列实施记录中“待24/Linux”等为各阶段历史状态，现由此记录闭合；未实施的下游能力仍按各自票负责。

**Traceability:** FR-03、FR-05、FR-09；AC-04、AC-07、AC-08、AC-12

## Acceptance criteria

- [x] 可信代理列表决定何时采用转发头，原始请求头不直接成为身份；认证 Principal 的来源和适配边界明确。
- [x] 兼容 SessionUser 上下文覆盖 SYNC/ASYNC/ERROR 及短路清理，嵌套 MDC 恢复；新模板使用 Security 原生上下文（模板实现归27/31，本票只提供Principal适配边界）。
- [x] trace/header 长度、非法字符和信任政策有界；不覆盖宿主已建立的有效观测上下文。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：公网直连、可信单/多代理、IPv4/IPv6，用户策略覆盖。
- [x] 边界：伪造 XFF、空白/重复/超长/控制字符头、复杂 Unicode，不产生伪造可信主体。
- [x] 生命周期：复用线程中 A 后接匿名 B，Filter 短路、Callable、DeferredResult、超时、ERROR、断开，身份与 trace 不串用。

## Scope boundary

不签发 JWT；生产资源服务器由票 27 独立交付，Cookie/HTML 输入兼容由票 32 交付。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 实施与真实验证（2026-10-04）

- 已交付显式 `ClientIpPolicy`、默认连接 peer 与可信 CIDR/XFF 有界解析；全程 literal-only 无 DNS。请求边界统一拥有 IP 快照、兼容身份和 trace，Filter/MVC/实际 Callable worker 有不同的清理时点；DeferredResult 外部 producer 不自动继承身份。
- 原生宿主 Principal/观测优先，TraceIdFilter 和来源策略用户 bean 覆盖；关闭默认 trace 时显式用户 bean 仍单次注册。异常恢复先清身份，再恢复所拥有的 MDC 键；部分安装失败与恢复失败均保留原异常和 suppressed。
- ADR-0029 明确部分替代旧0014无条件代理头假设；USAGE/CHANGELOG/公共JavaDoc同步迁移。没有新增 JWT、SecurityContext DSL 或生产 executor。
- 旧平台最终精确 `9e3a5578779e44835a476ae0135f9db94a33d592`：`clean verify` 1352/0/0/0，原质量门全过。最新Boot4集成 `8ca516c` 已同步，最终被测 `ee95d0743d817424a293a29dd8fa719d54e91470`：integration **PASS，1368/0/0/0**，原覆盖率/5架构/依赖门、非Web/真实HTTP双应用/纯Java核心消费者与三项工具链负控全过。完整来源和产物hash见 [本票报告](../../../docs/verification/ticket-06-request-boundaries.md)。
- Q01–Q07/Q09 的代码、公开场景和结果已映射；Q08/Q10 的同源 Linux CI 尚待集成者收集，故不标 closed。日志在本工作树 `.verification-results/ticket-06` 和报告中指定的完整 runner 归档目录；Maven clean 不删除。
- 27/31 完成真实 Security/模板，09/33 完成限流与候选组合；这些后续能力没有在本票使用伪认证头冒充交付。
