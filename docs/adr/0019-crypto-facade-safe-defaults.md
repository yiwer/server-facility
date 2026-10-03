# ADR-0019: crypto 加解密门面——安全默认 AES-GCM、内管 IV、不透明失败通道、纯 JDK

> 2026-10-04：原始cause无安全代价、随机IV杜绝碰撞及所有入口never-throw的保证由 [ADR0040](0040-legacy-crypto-reader-policy.md) 部分替代。纯JDK、固定原语和历史协议的理由保留；以下正文为原决策记录。

- **状态**:Accepted(2026-07-04)
- **源起**:crypto 加解密门面实现计划(docs/superpowers/plans/2026-07-04-crypto-facade.md)

## 背景

server-facility 此前无加解密组件(`hash` 簇只做单向摘要)。§10 roadmap 将 crypto 列为「静态门面范式」
新组件。JCE(`javax.crypto`/`java.security`)是 JDK 内置但臭名昭著易错的 API——mode/padding 选择、IV
管理、失败原因暴露每一处都能酿成安全事故。本 ADR 记录把它收进深模块门面时的取舍。

## 决策

### 1. 静态门面,无 SPI/bean(对标 hash)

crypto 是无状态算法调用,无可替换策略、无需注入——与 `hash` 一致:`public final class CryptoUtil`
私有构造抛异常,无 `FacilityCryptoAutoConfiguration`、无 properties,`AutoConfiguration.imports`
保持 11 行。未来若需 KMS/Vault 密钥托管,可加 `CryptoKeyProvider` seam——**hypothetical,本次不建**(YAGNI)。

### 2. 对称唯一 AES-256-GCM,不暴露 mode/padding

门面只提供 `encrypt/decrypt`,内部固定 `AES/GCM/NoPadding`,**不接受算法/mode/padding 参数**——
调用方无从选到 ECB(确定性、泄漏明文模式的经典陷阱)。GCM 同时给机密性 + 完整性(AEAD),密文被
篡改时解密抛 `AEADBadTagException`。IV 12 字节每次 `SecureRandom` 新鲜生成、前置拼进密文,
**调用方无从控制 nonce** ——根除 GCM 最致命的 nonce 复用漏洞。tag 128-bit。

### 3. CRYPTO_DECRYPT_ERROR 粗粒度失败通道,不附加底层异常(与 encrypt 刻意不对称)

`decrypt`/`decryptToBytes` 把错误密钥、密文篡改、IV 长度不足、Base64 畸形、null 入参**全部**映射为
单一 `CRYPTO_DECRYPT_ERROR`,不区分「哪一步失败」。细分失败原因会给攻击者提供 padding-oracle 类的
判别信号(历史上 CBC padding oracle、Bleichenbacher 攻击均源于此),故刻意不透明。

该粗粒度保证**由「不附加底层异常」强制落地**,而不只是错误码相同这一层面:全部解密失败路径返回
`equals` 相等且**不含 cause** 的 `CRYPTO_DECRYPT_ERROR`——连 `WrappedError.getException()`、
`getFullMessage()`、`toString()` 都无法区分「Base64 畸形」「GCM 认证失败(密文篡改/错误密钥)」与
「守卫拒绝(null 入参/IV 长度不足)」三类场景(`getFullMessage()`/`toString()` 在 `exception` 非
null 时会拼接异常类名与消息,`equals()` 也逐字段比较 `exception`——若保留 cause,三者都会成为可
供探测的判别信号,粗粒度错误码将形同虚设)。此设计**刻意与 `encrypt` 不对称**——`encrypt` 失败
仍保留底层异常,因为加密失败不构成 oracle 攻击向量,cause 有助于消费方排障且无安全代价。回归测试
`aesGcm_decryptFailures_indistinguishable` 锁定该行为:畸形 Base64 与 GCM 认证失败(篡改)各自
产生的 `WrappedError`,`getException()` 均为 `null`,且两者 `equals`/`getFullMessage()` 全等。

### 4. PBKDF2WithHmacSHA256 210_000 迭代,盐调用方管理,`clearPassword` 收窄口令留堆窗口

`deriveKey` 用 PBKDF2WithHmacSHA256、210_000 迭代(OWASP 2023 下限)、256-bit 输出。盐由
`generateSalt()`(16 字节)生成、**调用方负责与密文一同持久化**——门面不隐藏盐(派生需可复现)。
迭代次数固化不做 properties:门面职责是安全默认而非旋钮(与 ADR-0013「properties 暴露可调参数」
不同,此处「可调」本身就是安全反模式——消费方调低迭代数即削弱防护)。

`deriveKey` 内部构造的 `PBEKeySpec` 持有口令的 `char[]` 副本;方法体在 `finally` 块调用
`spec.clearPassword()` 主动清零该副本,缩小口令明文在堆上可被内存 dump/heap 分析恢复的时间窗口。
但这只收窄、不能根除,如实记录残余风险:入参 `String password` **本身不可清零**——JVM 字符串不
可变,`clearPassword()` 够不到已经存在的 `String` 对象。故完整的口令内存卫生**仍需调用方配合**:
避免长期持有明文口令 `String`(用完即弃引用,不缓存、不打日志、不落堆快照)。`deriveKey` 签名选
`String` 而非 `char[]` 是刻意的人体工程学取舍——多数调用方从配置/请求体拿到的就是 `String`,门面
在「完全防御」与「好用」之间选了后者,并在此如实记录残余风险,而非假装签名已根除该风险。

### 5. 纯 JDK,无 BouncyCastle

AES-GCM/HMAC/PBKDF2/RSA 均在 JDK JCE 内,标准算法零第三方依赖即可覆盖。国密 SM2/SM3/SM4 需
BouncyCastle,留待未来 `@ConditionalOnClass(name = "org.bouncycastle...")` optional 延伸
(对标缓存 Caffeine 双类探测范式),本次不引入。

## 备选(否决)

- **门面暴露算法/mode/padding 参数**(灵活但易误选 ECB/CBC,安全反模式,否决);
- **细分解密失败原因**(padding-oracle 类信息泄漏,否决);
- **迭代次数做成 properties**(削防护的旋钮,门面应给安全默认,否决);
- **本次引入 RSA/国密 SM**(YAGNI——对称+摘要覆盖约 90% 需求,非对称/SM 可按同范式后续追加,否决);
- **口令哈希 BCrypt 纳入**(单向认证非「加解密」语义,属 Spring Security PasswordEncoder 范畴,否决);
- **`deriveKey` 签名改 `char[] password`**(能让调用方自行清零入参,根除决策 4 的残余风险,但把
  「从 `String` 转 `char[]`」的负担转嫁给几乎所有调用方——多数口令来源(配置属性、请求体反序列化)
  天然是 `String`;门面选择承认残余风险并在文档披露,而非为理论上更彻底的防御牺牲普遍易用性,否决)。

## 后果

- 消费方零额外依赖即可加解密(纯 JDK);密钥存储/轮换是消费方责任(门面不托管密钥)。
- 安全默认不可误用:无 ECB、IV 内管、失败不泄漏——把 JCE 的易错面收窄为窄接口。
- `decrypt` 的不透明失败通道与 `encrypt` 保留 cause 刻意不对称,消费方排障解密失败需依赖已知输入
  复现,不能指望从 `WrappedError` 反查底层异常——USAGE 安全须知需明确告知这一点,避免消费方误以为
  是遗漏而在生产环境试图打印/上报该 null 异常。
- `deriveKey` 的 `String password` 签名把「口令留堆时长」的部分责任留给调用方(JVM 字符串不可清零
  的固有限制),USAGE 需如实披露而非只字未提。
- `packages_are_cycle_free` ArchUnit 规则对 `crypto` 包保持绿(仅依赖 `error`/`result`,零 web 边)。
- **Carry-forward**:README/USAGE/DESIGN 三件套 crypto 特性矩阵、ADR 索引更新于本计划 Task 5 统一处理。
