# 13: 上传在探测、保存和失败时保持完整与有界

**What to build:** 现有上传入口同时兑现大小、类型、可信存储名和清理政策，MIME 探测后保存的正文与原输入一致。

**Blocked by:** None (can start immediately)

**Status:** closed

**当前闭合记录（2026-10-04）**：`80670fa`的Windows/Ubuntu `all --fresh`与独立平台控制全部通过，见[同源CI证据](../../../docs/verification/ticket-24-ci.md)。下列实施记录中“待24/Linux”等为各阶段历史状态，现由此记录闭合；未实施的下游能力仍按各自票负责。

**Traceability:** FR-01、FR-06、FR-09；AC-10、AC-12

## Acceptance criteria

- [x] 区分展示名与存储键，避免覆盖他人文件和路径/链接逃逸；错误通过声明的通道返回。
- [x] 集中声明流所有权、暂存和成品可见性、大小/类型允许政策，取消或拒绝不无界 drain 输入。
- [x] Tika 目标版本升级与流契约回归一并交付，实际版本复核后纳入账本；探测不等于安全审查。
- [x] 同时登记公共 Hashing 的支持输入、空值/空输入和未知算法契约；保留或显式迁移旧差异，MD5 仅作明确兼容用途，不用于密码存储或安全完整性声明。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常/边界：空文件、N−1/N/N+1、多字节；a.txt、空名、路径、设备名、尾空格、长 Unicode 和同名并发。
- [x] 接合：可/不可 mark 输入经探测和保存后长度及已知摘要一致，伪扩展名/未知类型/容器混淆可解释。
- [x] 故障/资源：读、写、探测、提交失败及取消；半成品不暴露、自有临时资源清理、借用流按约定处理。
- [x] 平台：真实目录与链接逃逸在 Windows/Linux 验证；权限不足标为未验证，不能静默通过。Windows NTFS/junction/实际删除占用已完成，Linux hardlink/symlink/权限拒绝等待集成 CI。
- [x] hash 公共入口：已知向量、File/byte[] 等继续支持形态、空文件与空 byte[] 的既有差异、未知算法、读失败和历史 MD5 使用的处置，不能只以上传 hash 一致代替该包验收。

## Scope boundary

修复现有受控上传，不建对象存储 SPI、documents 平台或跨库文件事务。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。

## Implementation record

2026-10-04 领取；工作分支 `codex/ticket-13`，初始集成基线 `1c61c1b4c3539a4396794fed3c8c3f3379c7fb2c`。公共测试入口为 SafeUpload、MimeTyping、Hashing 及其真实文件系统效果；沿用户已确认的设施公共接口执行逐项 RED → GREEN。日志位于 `.verification-results/ticket-13`，不以未执行场景关闭验收。

2026-10-04 实现提交 `efea850`，冷 Tika 初始化取消修复 `395b1f0`；合入正式目标平台集成 `7e16819` 后被测源码 `101b3b3bbf4ff17414e7ae845d4791020992159b`。最终 `mvnw.cmd -B -ntp clean verify`：1400/0/0/0，5 原架构规则、88/88/75 原门及依赖分析全部通过；Boot4.1.1/Spring7.0.9/Jackson3.1.5/Tomcat11.0.24/JDK25。报告 [ticket-13-upload-integrity](../../../docs/verification/ticket-13-upload-integrity.md) 逐项映射 Q01–Q10 与原始 RED/GREEN、94 个相关测试、8 次真实 multipart 请求和 96MiB 堆/256MiB 上传进程证据。

ADR0036 保留 ADR0001 optional/按需调用理由，明确同卷硬链接、owned root、生成存储键、有限默认预算和 borrowed stream 迁移。实际 OS 拒绝删除时保留 suppressed 首因与自有残留的恢复责任，不伪称绝对清理。Linux 关键平台证据未取得，故 verification-pending；24 负责普通 jar/optional 矩阵，31 负责上传到 CSV 的交接，33 负责候选组合重验，不反向创建 13 的实现依赖。

2026-10-04 最终已同步票 18、06 中央 tip `5faff896d04a1b15ed10310be81bed91a14121b7`，被测源码 `e698642be82eb9d6d036e09a06dab21face34929`。01:49:16 +08:00 完整 `clean verify` 为 **1433/0/0/0**，原 5 架构/88-88-75 覆盖率/依赖门通过；最终 jar SHA-256 `dac3a1dc91d4706d3144336a2504bf4daacd5073f04db467e6f6ca1b4268cd34`。日志与 XML 已归档 `.verification-results/ticket-13/final-with-request-clean-verify.log` 和 `with-request-artifacts/`。本次没有执行独立普通 jar integration runner，不以 06 的消费者结果代替 24 对新上传/Tika 组合的验证；本票仍因实际 Linux 分支缺证保持 verification-pending。
