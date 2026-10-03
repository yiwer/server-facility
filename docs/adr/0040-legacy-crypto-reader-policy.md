# ADR-0040: 保留历史密文读取，收紧密码原语的失败输出

## Status

Accepted，2026-10-04，票17。部分替代 ADR-0019 对加密/KDF/MAC错误保留原始cause“无安全代价”的判断，以及“随机IV杜绝碰撞”“所有入口从不抛异常”的过度保证；保留纯JDK、固定原语和原密文协议。

## Context

历史存储只包含salt与Base64(IV+ciphertext+tag)，没有KDF元数据。直接升级全局迭代次数会让旧数据无法读取。另一方面，provider异常的文本可能含password/key/plaintext，WrappedError会把这些cause带入公开诊断；是否属于解密oracle并不决定这些数据是否可以泄漏。

## Decision

1. 旧格式和新写入都继续使用AES/GCM/NoPadding、12字节随机IV、128位tag；deriveKey继续PBKDF2WithHmacSHA256、210000次、256位输出。salt由调用方单独持久化。不在本票新增envelope版本、keyId、AAD或可变KDF元数据，没有格式升级或自动回滚问题。
2. 先固定未变更旧实现生成、独立JCE与.NET读取通过的三项密文。后续实现必须读这些字面样本，当前写入也由独立JCE reader验证，不用同一实现往返作为唯一兼容证据。
3. CryptoUtil的Result失败只携带稳定FacilityErrorType，不携带原始provider/decoder异常、业务参数或秘密日志。无效密钥、畸形/篡改密文均保持粗粒度解密失败；加密、派生、MAC、编码失败也去除原始cause。程序Error不捕获，非Result的校验/编码入口仍可抛程序异常，文档不得宣称从不抛。
4. 此失败政策牺牲底层诊断文本以防止秘密扩散。调用方可记录稳定错误码、操作名称和自有请求关联，不可把输入、密钥或原始provider错误作为默认日志字段。
5. 随机IV来自JDK SecureRandom，有限样本不能证明无限nonce唯一。密钥生命周期/调用量由应用负责；相等错误文本也不是恒定时间执行证明。JVM中的String和provider内部副本不能由此工具彻底擦除。
6. 不给原始历史读取入口暗加新长度上限；旧存量的大payload、长口令或salt仍有原读取路径。原始入口是同步内存原语，工作量随已物化输入长度增长，PBKDF2只执行固定210000次、输出32字节，不接受外部iteration/并行度/内存参数。它不是可以直接暴露给任意请求的资源隔离接口，也不承诺同步JCE可中断。应用在解码/派生前落实输入和并发预算：本票真实普通jar消费者采用1MiB payload、最多1398140个Base64字符、1024个UTF-16口令单元、64字节salt、4并发，成功、失败和上限两侧均验证。读取更大历史记录须在受控离线迁移作业按已有数据规模另设预算；不能把上限异常当作记录损坏后覆盖原数据。
7. 能从协议证明无效的输入提前拒绝：AES key Base64最多44字符，超出时不分配解码数组；完整GCM envelope至少28字节（12 IV+16 tag），截断时不访问密钥/provider。空明文仍合法；16/24/32字节密钥与合法无padding Base64继续可读。

## Verification

`CryptoLegacyContractTest`先在未修改实现上通过；`CryptoProviderIsolationTest`在64MiB、20秒独立JVM让真实JDK AES provider读取抛出秘密哨兵异常的SecretKey，再移除提供Cipher/SecretKeyFactory/Mac的provider，验证缺席失败。确认密钥接口真的被访问，并检查公开结果与输出。Oracle JDK拒绝未签名的JCE测试provider，因此不用自建provider，也不绕过签名校验。宿主JVM的provider注册表不改变。原测试、固定seed变异、输入资源边界及完整目标平台质量门同票交付，未运行不标通过。
