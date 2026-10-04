# 32: 明确 Cookie 与 HTML 清洗的兼容和安全边界

**What to build:** 使用现有 Cookie 和显式 HTML 清洗入口的消费者获得稳定可说明的结果，升级 jsoup 不悄悄改变策略，也不把清洗视为通用 XSS 保证。

**Blocked by:** None (can start immediately)

**Status:** closed

实现分支：`codex/ticket-32`，基于集成提交 `f4837ee`。公共测试入口沿用已批准的 CookieUtil、XssUtil 和真实 Servlet HTTP；ADR-0055 登记完整 Cookie 作用域与显式 HTML 片段清洗，保留 ADR-0001 的 optional 依赖决定。

**Traceability:** FR-01、FR-03、FR-09；AC-04、AC-12

## Acceptance criteria

- [x] Cookie 的 Secure/HttpOnly/SameSite、domain/path、有效期和删除政策公开，默认示例符合所选部署模式；消费者可显式定制。
- [x] 原始输入与显式清洗结果区分，不在任意字段上无条件改写合法业务值。
- [x] 升级 jsoup 与策略样本一并交付，版本纳入账本；记录不适用的上下文和 escaping 责任，不宣传清洗能覆盖所有 XSS。
- [x] 继续支持的旧 Cookie/清洗入口记录兼容变化与替代，不为新认证模板增加阻塞。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/接合：真实 HTTP Set-Cookie 与删除头验证作用域和 flags，应用默认/用户定制政策。
- [x] 边界：空/null 值、Unicode、超长内容、控制字符、重复 cookie、domain/path 边界、SameSite/secure 组合。
- [x] 清洗独立样本：安全片段、畸形 HTML、脚本/事件属性、危险 URI、嵌套与编码变体；安全内容不会被不必要改写。
- [x] 兼容/失败：新旧 jsoup 样本差异有理由，非法配置给出明确结果；记录清洗后的内容仍需按最终输出上下文处理。

## Scope boundary

不建设 HTML 安全沙箱或新认证体系；不让本票阻塞原生 JWT 模板。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## 执行证据

实现`d20c90f`，Windows完整门冻结源码`1f307a3c14a77daa65d978586e64192f36c4895e`，100命令PASS，库1655/0/0/0；普通jar两依赖图与64MiB消费者均通过。原始目录`.verification-results/20261004-072426-683-all`，TDD原始日志`.verification-results/ticket-32`。完整Q01–Q10映射、源码范围、制品哈希和资源边界见`docs/verification/ticket-32-cookie-html.md`。Q08 Linux与Q10最终集成候选CI尚待验证，故共同标准不提前勾选。


## CI16 closure — 2026-10-04

源码207c0cce7f67acf09bc29f40a1fbbfed4c01ac61 / run37165514455的Windows111327487229与Ubuntu111327487395完整all、独立platform及归档均success。此前本票局部/完整本地证据与本次同源跨平台门共同闭合适用Q01–Q10，状态closed。原始公开metadata、artifact范围和后续票边界见[联合验收](../../../docs/verification/ticket-07-12-19-28-32-ci.md)。历史失败不改写，后续33候选组合不冒作此次已执行。
