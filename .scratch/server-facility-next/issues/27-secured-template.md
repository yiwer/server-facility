# 27: 交付能独立启动且默认受保护的 API 模板

**What to build:** 从模板实例化的独立应用启动真实 MVC 端点，用 JWT resource server 验证身份；生产缺配置不自动放行，本地开发身份显式选择。

**Blocked by:** 04 在真实 HTTP 链路统一安全错误与显式兼容协议；24 把目标平台集成为可发布的真实消费者组合

**Status:** ready-for-agent

**Traceability:** FR-03、FR-07；AC-03、AC-04、AC-07

## Acceptance criteria

- [ ] 默认应用只选一条 MVC 路径，声明 JDK/网络等先决条件、启动/打包/健康检查和配置方式。
- [ ] 验证可信 issuer/audience，业务操作取得明确 actor；标准 Security/trace 上下文替代 SessionUser 静态默认路径。
- [ ] 401/403 与票 04 协议一致；非敏感演示端点不代表业务默认匿名。
- [ ] JWT 测试使用受控真实签名/JWK 来源，无需真实用户凭据；不创建身份提供商。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常：干净实例化、独立构建启动、有效 token 调用、合法本地配置。
- [ ] 负向：无 token、错误签名、过期/未生效、错误 issuer/audience、权限不足、生产缺可信配置。
- [ ] 接合/生命周期：JWK 更换/不可达时策略可解释；Filter/Security/MVC 错误、Callable/DeferredResult/超时/ERROR/断连后下一请求无残留。
- [ ] 配置：平台线程默认与显式虚拟线程模式、代理边界、应用重启及用户定制均有实际 HTTP 验证。

## Scope boundary

此票可以不接数据库；持久化用例在票 28，标准身份必须先可验证。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。
