# 票 21：JSON 扩展执行证据

状态：实施中，完整合入后验证尚待执行。原平台仍为 Boot 3.5.16 / Jackson 2.21.4，未替换 Boot/Jackson 主版本。应用归属及迁移边界见 [ADR-0044](../adr/0044-json-application-scope-expand.md)，完整影响清单见 [迁移登记](../building/platform-migration-inventory.md)。

## 基线与 TDD

专属工作树 `E:\GenCode\server-facility-worktrees\ticket-21`，分支 `codex/ticket-21`。扩展前基线 `731598b`，Oracle Java `25.0.4.1`，Windows 11 / Asia-Shanghai，Wrapper Maven 3.10.0。Maven 依赖/安装坐标使用本工作树 `.verification-results/repository`，不使用并行工作树的共享 SNAPSHOT。

原始日志位于 `.verification-results/ticket-21-tdd`，不会被 clean 删除。`01-baseline-install.log` 是未修改生产代码时原平台的完整 clean install（包含测试、覆盖率、架构及依赖门）成功；基线 jar 另存 `baseline-731598b.jar`，SHA-256 `a98575a48a9d15d36dedd29b5aa88fc91448ae9d2b828505e9cbedfa1b53f0a6`。`11-baseline-constructed.log` 用该 jar 完成同一独立 Web consumer 的默认和定制协议样本。

| 循环 | 红灯证据 | 绿灯证据 / 公开契约 |
|---|---|---|
| 应用注入 | `03-injection-red.log`：JsonsApplicationScopeTest 缺 Jsons bean；`13-consumer-injected-red.log`：旧普通 jar 的真实应用缺 bean 启动失败 | `05-injection-green.log`：新注入入口与旧装配 4 项通过；两应用政策、关闭重建 |
| 构建期入口 | `12-builder-red.log`：公开 customizeBuilder 不存在，testCompile 失败 | `14-builder-green.log`：24 项通过；builder 政策覆盖 preset，原 JsonConfig 用例保持通过 |
| 字段输出预算 | `15-stream-output-red.log`：带预算构造器不存在 | `16-stream-output-green.log`：3 项通过；N−1/N/N+1、源关闭、旧入口 |
| 字段输入预算 | `17-stream-input-red.log`：带预算构造器不存在 | `18-stream-input-green.log`：8 项通过；补位/无补位 Base64 和解码边界 |
| 边界及重放 | 以上循环后增加契约边界回归 | `19-json-boundaries.log`：34 项通过；callback 顺序/null、用户 bean、根流/字段所有权、I/O 失败、未知长度 N+1 探测、≤0兼容、seed 210025 /128例 |

初始手写 HTTP 金样经基线探针校正了两项真实差异：UTF-8 Jackson generator 转义非 BMP，String 输出保留原字符；默认 global advice 的旧 envelope 是 HTTP 200。因此消费者分别保存 wire 样本，并显式启用已有 ProblemDetail 配置再冻结真实 400。未把这些旧缺陷伪报为新修复；失败探针日志 `04/07/09` 保留。最终 goldens 是独立字面文件；测试不在运行时调用同一个 mapper 生成 expected。

## 完整验证

待合入 integration 的票 03 最新 tip 后运行 `java verification/Verify.java all`。此状态不表示完整质量门或新 Linux 场景已通过；最终结果、SHA 和报告目录会补入本节。
