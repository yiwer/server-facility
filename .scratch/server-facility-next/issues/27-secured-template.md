# 27: 交付能独立启动且默认受保护的 API 模板

**What to build:** 从模板实例化的独立应用启动真实 MVC 端点，用 JWT resource server 验证身份；生产缺配置不自动放行，本地开发身份显式选择。

**Blocked by:** 04 在真实 HTTP 链路统一安全错误与显式兼容协议；24 把目标平台集成为可发布的真实消费者组合

**Status:** verification-pending

**Traceability:** FR-03、FR-07；AC-03、AC-04、AC-07

## Acceptance criteria

- [x] 默认应用只选一条 MVC 路径，声明 JDK/网络等先决条件、启动/打包/健康检查和配置方式。
- [x] 验证可信 issuer/audience，业务操作取得明确 actor；标准 Security/trace 上下文替代 SessionUser 静态默认路径。
- [x] 401/403 与票 04 协议一致；非敏感演示端点不代表业务默认匿名。
- [x] JWT 测试使用受控真实签名/JWK 来源，无需真实用户凭据；不创建身份提供商。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常：干净实例化、独立构建启动、有效 token 调用、合法本地配置。
- [x] 负向：无 token、错误签名、过期/未生效、错误 issuer/audience、权限不足、生产缺可信配置。
- [x] 接合/生命周期：JWK 更换/不可达时策略可解释；Filter/Security/MVC 错误、Callable/DeferredResult/超时/ERROR/断连后下一请求无残留。
- [x] 配置：平台线程默认与显式虚拟线程模式、代理边界、应用重启及用户定制均有实际 HTTP 验证。

## Scope boundary

此票可以不接数据库；持久化用例在票 28，标准身份必须先可验证。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

2026-10-04 领取：基于 `8cfaa6e933b4a098f5d2d934ac9433b0ec18cbee`；04/24 已 closed（24 CI37143955128 Windows/Ubuntu）。批准的测试入口为独立应用真实HTTP、业务Module及公开配置，不另建私有测试接口；ADR0050。计划记录于 worktrees/coordination/ticket-27-security-plan.md。

2026-10-04 Windows 实现验收：冻结来源 `b2fcfbf4cee05b1176cca3373dbc9dd35b67fd3e`，已含 integration `5bdfcb0c624653bd8f1b48f167867b187a041749`。`java verification/Verify.java all --fresh` 完整 PASS，证据 `.verification-results/20261004-040949-437-all/`；库1535/0/0/0、独立模板47/0/0/0、实际 executable jar 平台/虚拟线程、coverage 缺失负控、现有普通 jar/optional 矩阵、生命周期及工具链负控全部通过。模板在 checkout 外的空格/中文目录独立构建；产物嵌套库 SHA 与本轮库完全一致。覆盖率模板指令95.81%/行94.26%/分支86.76%，原库质量门未放宽。

验收映射、TDD RED/GREEN、无效验证尝试及实际预算见 [票27报告](../../../docs/verification/ticket-27-secured-template.md)，应用信任与上下文所有权见 [ADR0050](../../../docs/adr/0050-secured-application-template.md)。数值/字符串 subject 的标准规范化碰撞保留两个真实签名金样，不声称原始 JSON 类型严格验证；JWK缓存及每次fetch预算边界已声明。

上方勾选对应 Windows 实际执行。共同完成标准 Q01–Q09 已映射；Q10 的同源 Linux CI 仍待 root 执行归档，故本票保持 `verification-pending`，不关闭、不提前解除28前置。仅 CI 配置和 Windows 证据不等于 Linux 已通过。本工作树没有发布或推送。
