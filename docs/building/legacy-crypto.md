# 受约束的加密原语与历史读取

CryptoUtil是同步、内存中的纯JDK原语，不持有应用上下文、线程池、文件、连接或密钥仓库。公开方法没有删改签名。持久协议继续为`Base64(12字节IV || ciphertext || 16字节tag)`；密码派生继续PBKDF2WithHmacSHA256、210000次、256位输出，salt单独保存。**不要通过修改全局迭代常量来升级已有存储。**

这是历史兼容协议，不将210000描述为当前用户密码认证的推荐工作因子。本模块不做密码认证、KMS、自动轮换、版本化envelope或keyId/AAD调度。本次没有改变写入格式，因此没有未知版本或新旧envelope回滚状态。新的存储政策若需要不同算法/参数，须单独设计带元数据的协议并先证明旧reader和迁移回滚。

## 公开入口及政策

| 入口 | 输入、结果与资源契约 |
|---|---|
| encrypt(String/byte[], key) | UTF-8或原始bytes；自动随机IV；成功返回原协议Base64；null/一般provider失败为CRYPTO_ENCRYPT_ERROR。先在应用入口限制原文大小，String转UTF-8会另分配。 |
| decrypt / decryptToBytes | 原Base64协议；空明文合法；不足28字节直接拒绝；错误key、salt/password导致的错误key、篡改和编码错误均为相同CRYPTO_DECRYPT_ERROR。UTF-8文本读取保留JDK的非法序列替换语义；二进制数据用decryptToBytes。 |
| deriveKey | 固定210000次、32字节输出；口令可为空，salt不可空；null/一般provider失败为CRYPTO_KEY_ERROR。输入已物化，应用必须先限制口令/salt；不能靠取消Future保证JCE停止。 |
| aesKeyFromBytes / importKey | 仅16/24/32字节AES；原字节被SecretKeySpec复制。importKey在解码前拒绝超过44字符，保留合法padding/无padding形式；错误均为CRYPTO_KEY_ERROR。 |
| generateAesKey / generateSalt / exportKey | 默认256位key、16字节salt；导出密钥是敏感操作。非Result入口可抛参数/配置异常，不适合作为无校验的请求入口。 |
| hmacSha256(byte[]/String) | HMAC-SHA256、小写hex；String使用UTF-8；key不可空；失败为CRYPTO_MAC_ERROR。应用限制消息/key字节规模。 |
| base64Encode/Decode、hexEncode/Decode | 保留原编解码协议及空数据；Decode失败为CRYPTO_DECODE_ERROR。内存/计算随输入增长，先限制已读取的输入，不能先完整解码再做入口预算。Hex保留Character.digit接受的字符集合，本票未收紧此兼容行为。 |

所有Result错误只带稳定错误类型，不附原始cause或输入；原cause删除是本次明确的诊断变化。程序`Error`继续传播，不能把OOM或provider程序故障解释为正常输入错误。工具不写秘密日志；调用方只记录稳定错误码和自有请求关联。粗粒度失败不等于恒定时间实现，随机IV也不是无限调用无碰撞保证。

## 实际消费与预算

可编译的[普通jar消费者](../../verification/crypto-consumer/CryptoConsumer.java)实现一个持久回执读写场景。应用入口采用以下有限政策：payload ≤1MiB；Base64输入 ≤1398140字符；口令 ≤1024个UTF-16单元；salt ≤64字节；并发4。解密后再校验实际payload长度，处理Base64三字节分组的边界。新写入与历史字面回执均通过相同reader。

这些是消费应用的显式预算，不是给旧API暗加的新全局限制。旧数据若超出当前业务预算，使用受控离线迁移程序，先确认记录/凭据来源及规模再调用原reader；保留原记录直到独立验证迁移成功，不自动降级算法或重写损坏记录。HTTP层还须在物化String/数组前限制请求体，并限制可触发派生的请求并发；不应每个请求重新派生同一应用密钥。

消费者在64MiB/45秒独立JVM处理64轮1MiB成功读写和失败，再以事件屏障同时运行4个worker，每个16轮256KiB。每次仅保留当前结果，executor显式关闭。另有32MiB/20秒子进程重复100次拒绝16MiB的非法key；这个case在原实现会OOM，在新实现不解码大key。上述证据不证明任意大小输入、任意provider或无限并发安全。

## 独立兼容证据

- [历史样本及来源](../../src/test/resources/crypto/README.md)：旧实现生成的三项字面样本，含空、ASCII与Unicode数据；固定派生key先经独立JCE和.NET验证。
- CryptoLegacyContractTest读取这些样本，并由独立JCE reader读取当前写入；不会重新生成金样迎合新实现。
- CryptoInputBoundaryTest采用seed `170040`，覆盖每个IV/ciphertext/tag字节并追加512次单bit变异；全部截断、错误密码/salt、畸形编码和不同AES key长度均从公开API验证。
- HMAC沿用RFC 4231 Test Case 2的字面期望；provider故障使用真实JDK AES访问抛异常的SecretKey，以及子进程内移除provider，不绕过JCE签名校验。

具体源码、运行环境、TDD失败与成功记录见本票验收报告。架构决策为[ADR-0040](../adr/0040-legacy-crypto-reader-policy.md)。
