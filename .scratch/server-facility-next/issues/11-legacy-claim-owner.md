# 11: 扩展旧幂等协议以拒绝迟到 owner 覆盖

**What to build:** 继续支持的独立 claim 协议可辨别拥有者、内容冲突与不可用；旧执行者迟到完成时不能覆盖新执行结果。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-04、FR-09；AC-06、AC-12

## Acceptance criteria

- [x] 先扩展 owner/generation、scope/fingerprint 及取得/处理中/重放/冲突/不可用的契约，并保持旧消费者迁移路径。
- [x] 完成与释放只接受相应执行资格；lease 和结果保存期分开，失败不能伪装成功。
- [x] 新增安全入口供票 12 迁移；旧入口未迁移前不宣布整体 HTTP 幂等安全，也不提前删除。
- [x] 声明 CAS 只保护记录更新，不能取消旧业务副作用；内存实现不承诺重启或分布式恢复。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/边界：首次占位、重放、异内容、到期前/等于/后、容量不足、未知/错误 owner、重复 complete。
- [x] 并发：A 过期、B 取得且完成、A 迟到写回，被拒且 B 结果不变；用屏障和可控时钟复现。
- [x] 故障：完成写入失败、关闭/失效、重复释放不授权新副作用；错误类别和状态保持可解释。

## Scope boundary

这是旧协议的 expand 票，与新模板同库事务无依赖关系。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。


## 领取记录（2026-10-04）

工作树 `E:/GenCode/server-facility-worktrees/ticket-11`，分支 `codex/ticket-11`，起点09中央 `c32e72e86de7e4f708e0b423c18f84c955ab683a`。ADR0034。沿用已批准公共claim接口、外部Clock和普通jar消费者seam，不重复请求确认；日志保存 `.verification-results/ticket-11`，不借用09结果作为11验证。

## 实施与验证（2026-10-04）

实现c5ea828、审阅故障/资源修复06db48b；最终被测源956081d303243e81557de61e54615f9f5608c374已包含27产品14dbec3，随后550b510仅合27中央文档5674f6d。ADR0034与docs/building/qualified-claims.md明确永久命令绑定、PROCESSING lease例外、新旧隔离及12/29边界。

本票Windows完整 `java verification/Verify.java all` PASS：`.verification-results/20261004-042235-271-all/summary.txt`；根1555测试、0失败/错误/跳过，模板47独立测试与可执行jar HTTP也PASS；覆盖率指令93.449%、行94.130%、分支86.004%，架构5项与依赖/工具链负控全部通过。普通jar新旧SPI二进制fixture、A/B屏障、seed110034、64MiB churn/close、32MiB复制OOM/Clock Error/大表close均通过。jar SHA256=0fe1fa4fb2c0058f09a4ee022ff6ea54e51dd6cbe08869e74606e56e10edc6a2。

审阅与修复前首轮all、失败RED及一次手动JVM参数引用失败均保留，详见docs/verification/ticket-11-qualified-claims.md。CI12 的同源 Windows/Ubuntu 完整门、平台门及归档均已通过，Q08/Q10剩余平台证据闭合，本票closed；精确来源与API记录见 [CI验收报告](../../../docs/verification/ticket-11-16-27-ci.md)。12、29、33自身HTTP/事务/最终组合验收独立登记，不反向作为11前置。
