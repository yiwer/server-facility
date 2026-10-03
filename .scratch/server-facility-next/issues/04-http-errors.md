# 04: 在真实 HTTP 链路统一安全错误与显式兼容协议

**What to build:** API 调用者在 MVC、Filter 和异常派发失败时得到准确状态与安全 ProblemDetail；旧客户端仅通过显式兼容路径获得原 envelope。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-03、FR-09；AC-04、AC-05、AC-12

## Acceptance criteria

- [x] 新默认成功 DTO、失败 ProblemDetail 的 code/detail/字段错误/traceId 契约明确，并可供 Security 复用。
- [x] 覆盖状态及必要响应头，区分坏请求、multipart 过大、业务拒绝和内部返回值错误。
- [x] 内部 cause 留在服务端；任意异常消息、输入秘密与调试栈不直接进入生产响应。
- [x] 明确已提交响应、用户 advice 覆盖及兼容模式的处理，不二次写坏响应。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/协议：400/404/405/406/409/413/415/422/429/500/503 的适用场景与 Allow、Retry-After 等头；无需人为制造不适用状态。
- [x] 接合：实际 Servlet 请求经过 Filter/MVC/ERROR dispatch、用户 mapper/advice、locale；禁止仅直接调用 handler 作证。
- [x] 故障：秘密哨兵注入异常、cause、字段输入；响应已 flush、序列化失败、multipart 坏数据分别验证。
- [x] 兼容：旧 HTTP 200 envelope 金样与新协议金样分开，记录明确切换和 superseding ADR。

## Scope boundary

身份认证实现由票 27 交付；本票提供其错误策略并在接合时复验。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


## 实施与真实验证（2026-10-04，Windows；跨平台 CI 已闭合）

- 公共策略 `FacilityHttpErrors.response/write` 在 MVC、Filter 和 REQUEST/ASYNC/ERROR 边界共享；默认安全 RFC 9457，显式 legacy 200 金样。标准 Spring 状态及必要头、字段预算、traceId、本地化与宿主 mapper/advice 均有实际请求证据。
- 17 个真实 Tomcat HTTP 场景验证 400/404/405/406/409/413/415/422/429/500/503、401/403 adapter复用、Boot/宿主部分错误映射、坏 multipart与63/64/65字节上传、serializer自身失败、flush前后Writer及旧实体头、16并发请求locale/trace隔离。身份系统仍归27；流和重放接合归05/12。
- 新决策 [ADR-0027](../../../docs/adr/0027-safe-http-error-policy.md) 明确替代旧 ADR-0003 的默认/泄露/状态映射范围；保留旧 handler/构造兼容入口并弃用自动trace展示。迁移见 `docs/USAGE.md` 的 HTTP 错误段落。过滤顺序与05/06/12/27复用约定已写入ADR。
- 实现提交：`52daa5e` 测试底座、`d1dee03` 安全策略、`6eea3a7` 边界修复；`0fdafe2` 已合入integration `ee2e9cc`。`2272f08` 补金样/119/120/121字段断言；最终 `37ee5f5` 以RED→GREEN修复循环和过深ServletException解包/日志诊断，支持63/64层、拒绝65/10000层与环且不修改原cause。已包含integration `5428982`。
- 完整 `clean verify` 和普通jar integration runner：**1276/0/0/0**，5个架构规则、原88/88/75门、依赖分析通过。指令93.1880%、行93.3512%、分支86.5100%。最后加强的相关断言 **76/0/0/0**。
- 独立消费者 configured/override/invalid、constructed/injected JSON/HTTP、多应用mapper与关闭重建，以及工具链负向检查全通过；本票普通jar SHA-256 `d7269428cbba095a193c67d79ac5b813102cd9b750b487fb9b207e0a4e56a7c2`。
- 原始RED/GREEN/完整日志在 `.verification-results/ticket-04/`；完整runner报告 `.verification-results/20261004-000725-683-integration/`，隔离repository第三方依赖预热、fresh=false，本票SNAPSHOT重新构建安装。全部命令、环境、Q01–Q10/J03/J14映射、测试数迁移说明见 [验证报告](../../../docs/verification/ticket-04-http-errors.md)。

集成 `66bf4d04be3b40a5d81a80fa418cbe213c2307f4` 已通过 GitHub Actions 37136353128 的 Windows/Ubuntu `all --fresh`，root 已通过 API 核对确切 SHA、全部步骤和归档；[CI证据](../../../docs/verification/ticket-04-ci.md) 闭合 Q08/Q10，票04标为 **closed**。各环境计数留在原始报告，不以本机1276代替Linux精确数。Boot4/Jackson3目标平台验证归22–24，未把当前Boot3证据当作目标平台完成。
