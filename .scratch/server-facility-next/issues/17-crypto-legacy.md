# 17: 升级加密政策时保持历史密文可读

**What to build:** 旧密文具有明确读取路径，新增策略不会因改变全局 KDF 参数而使历史数据失效；异常输入不泄露秘密或触发无界计算。

**Blocked by:** None (can start immediately)

**Status:** verification-pending

**Traceability:** FR-09；AC-12

## Acceptance criteria

- [x] 先建立旧格式/210000 次派生的历史金样和 reader，再决定是否改变新写入政策。
- [x] 新写入若改变格式，必须明确版本、KDF 元数据、keyId/AAD 的适用政策与回滚；不为完成本票强制新建 envelope 平台。保持原格式，无新envelope。
- [x] 错误密钥、损坏格式与篡改给出安全公开失败，内部诊断不输出 key/password/plaintext；只有引入新格式时才需定义未知版本失败。
- [x] 若引入外部可变 KDF 元数据，必须限制参数与计算预算，防止新写入政策被降级；若保持现有格式，验证固定 legacy 参数与原读取协议，不为满足测试额外建设元数据系统。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 兼容/互操作：旧版本生成的独立密文、已知向量、正确密码/salt；只有改变写入格式时增加新旧共存与回滚读取。
- [x] 边界：空/截断/畸形编码、错误 salt/password；仅在引入版本或可变 KDF 元数据时验证未知版本、超大参数及预算边界。
- [x] 故障：nonce/tag/ciphertext 篡改、错误 key、provider 失败；秘密哨兵不出现在公开输出或日志。
- [x] 性质：固定种子变异验证拒绝路径，不以有限随机样本宣称无限 nonce 唯一或恒定时间证明。

## Scope boundary

不做 KMS、用户密码认证或自动密钥轮换；持久数据改变需先有迁移证据。

## Implementation record

- 2026-10-04：root 从集成 `042006d` 创建独立 `codex/ticket-17`。先采用未变更旧 CryptoUtil 生成、独立 JCE 与 .NET 验证的三项固定历史密文，来源写入测试资源；不重新生成金样来迎合新实现。
- 读取政策保持 PBKDF2-HMAC-SHA256/210000、AES-GCM 的12字节IV与16字节tag、独立salt；不新增KDF元数据或密文envelope。先复现provider秘密泄漏、截断密文访问key、超长key解码OOM，再最小修复；ADR0040登记诊断政策和原始入口/应用预算责任。
- 目标源码`6f7f06c`已含integration `d4922df`，Windows完整integration PASS1442/0/0/0，原全部质量门、ordinaryjar/core/crypto/JSON真实HTTP和三项工具链负控通过。报告`docs/verification/ticket-17-crypto-legacy.md`登记Q01–Q10及全部raw路径。
- 剩余：同源Linux执行交24/33 CI闭合，未据Windows结果提前关闭票据。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。
