# crypto 加解密门面设计（§10 roadmap 新组件）

- **状态**：Draft（brainstorming 产出，待用户复核）
- **日期**：2026-07-04
- **范式依据**：§10 roadmap「加解密门面 crypto | 新子包 + JDK/BC optional | 静态门面范式」；
  对标既有 `hash` 簇（纯 JDK `Hashing` 静态门面，无 bean/无装配）。
- **算法范围决策**：**对称 + 摘要核心（纯 JDK）**——AES-256-GCM 对称加解密 + HMAC-SHA256 +
  AES 密钥生成/PBKDF2 口令派生 + Base64/Hex 编解码。**不含** RSA 非对称、**不含**国密 SM 系列、
  **不含** BouncyCastle 依赖（均留待后续按同范式延伸）。

---

## §1 目标与非目标

### 目标
把 JCE（`javax.crypto` / `java.security`）臭名昭著的易错 API 收进一个**窄接口、安全默认、不可误用**的深模块静态门面，覆盖服务端约 90% 的"加解密"实际需求：
- 对称加解密（字符串 / 字节数组）
- 密钥全生命周期（生成 / 口令派生 / 导出导入 / 盐生成）
- 消息认证码（HMAC）
- 加密 I/O 的伴生编解码（Base64 / Hex）

### 非目标（YAGNI）
- **RSA 非对称加解密 / 数字签名**——Option B，本次不做；后续可在同门面追加 `rsaEncrypt/sign` 等（仍纯 JDK）。
- **国密 SM2/SM3/SM4**——Option C，需 BouncyCastle optional + `@ConditionalOnClass` 门控，本次不做。
- **口令哈希（BCrypt/Argon2）**——单向认证，非"加解密"（可逆）语义，属 Spring Security `PasswordEncoder` 范畴，排除。
- **可调旋钮（properties）**——迭代次数、密钥长度固化 OWASP 推荐值；门面职责是安全默认，非提供选项。
- **autoconfiguration / Spring bean**——crypto 是无状态算法调用，无可替换策略、无需注入，与 `hash` 一致零装配。

---

## §2 架构定位

```
cn.code91.facility.crypto           ← 新顶层子包（独立 ArchUnit slice）
├── CryptoUtil.java                 ← 静态门面（深模块：窄接口 + 宽实现）
└── package-info.java               ← 包文档
```

- **零 web 依赖**：与 `web.*` 无任何边 → 不可能形成顶层 slice 环（沿用限流/幂等的避环纪律）。
- **依赖**：仅 `error`（`FacilityErrorType`/`WrappedError`）+ `result`（`Result`）——与 `hash` 完全相同。
- **被依赖**：下游应用代码（敏感字段加密落库、令牌加解密、Webhook 签名校验等）。
- **自动装配保持 11 个**：不新增 `AutoConfiguration.imports` 行，无 `FacilityCryptoAutoConfiguration`，无 `FacilityCryptoProperties`。

---

## §3 API 面（窄接口）

`public final class CryptoUtil`，私有构造抛 `UnsupportedOperationException`（对标 `Hashing`/`HttpClients`）。
所有可失败方法返回 `Result<T, WrappedError>`，从不抛异常。

### 3.1 对称加解密（AES-256-GCM）——核心

```java
static Result<String, WrappedError> encrypt(String plaintext, SecretKey key);
static Result<String, WrappedError> encrypt(byte[] plaintext, SecretKey key);   // 返回 Base64 字符串
static Result<String, WrappedError> decrypt(String base64Cipher, SecretKey key);      // → 明文 String(UTF-8)
static Result<byte[],  WrappedError> decryptToBytes(String base64Cipher, SecretKey key);
```

设计约束：
- **算法唯一 `AES/GCM/NoPadding`**，不暴露 mode/padding 参数 → 调用方无法选 ECB/CBC。
- **IV**：每次 `encrypt` 由 `SecureRandom` 生成 **12 字节**（96-bit，GCM 推荐长度），**前置拼进密文**；
  认证标签 128-bit（GCM 默认由 JCE 追加）。输出 = `Base64(IV ‖ ciphertext+tag)`。
  IV 由门面内部管理 → 调用方无从复用 nonce（GCM 最致命陷阱被根除）。
- **输出**：标准 Base64 字符串（非 URL-safe），存储/传输友好。
- **失败通道粗粒度**：错误密钥、密文被篡改（`AEADBadTagException`）、IV 长度不足、Base64 畸形
  ——全部映射为单一 `CRYPTO_DECRYPT_ERROR`，不透露"哪一步失败"（避免 padding-oracle 类信息泄漏）。
- **null/空入参**：`plaintext`/`key`/`base64Cipher` 为 null → 返 `err`（不抛 NPE）。

### 3.2 密钥生命周期

```java
static SecretKey generateAesKey();                                          // AES-256, SecureRandom(不会失败)
static Result<SecretKey, WrappedError> aesKeyFromBytes(byte[] raw);         // 校验 16/24/32 字节后包装
static Result<SecretKey, WrappedError> deriveKey(String password, byte[] salt);  // PBKDF2WithHmacSHA256
static byte[] generateSalt();                                              // 16 字节 SecureRandom
static String  exportKey(SecretKey key);                                   // Base64(key.getEncoded())
static Result<SecretKey, WrappedError> importKey(String base64Key);        // exportKey 逆操作
```

设计约束：
- `deriveKey`：`PBKDF2WithHmacSHA256`、**210_000 迭代**（OWASP 2023 下限）、256-bit 输出；
  **盐由调用方提供**（须与密文一同持久化，门面不隐藏盐）；password/salt 为 null 或 salt 空 → 返 `err`。
- `generateAesKey` 返回 JCE `SecretKey`（不会失败——正是 `encrypt/decrypt` 消费的类型，调用点无"raw byte[] 密钥"歧义）；
  `aesKeyFromBytes` **fail-fast 校验长度**（AES 合法 16/24/32 字节，其余 → `CRYPTO_KEY_ERROR`），
  不把非法长度拖到 `encrypt` 时才由 `Cipher.init` 抛 `InvalidKeyException`——与安全默认哲学一致。
- `exportKey`/`importKey`：密钥以 Base64 持久化/传输；`importKey` 对畸形 Base64 → `CRYPTO_KEY_ERROR`。

### 3.3 消息认证码（HMAC）

```java
static Result<String, WrappedError> hmacSha256(byte[] data, byte[] key);   // → 小写 hex
static Result<String, WrappedError> hmacSha256(String data, String key);   // UTF-8 → 小写 hex
```

- `HmacSHA256`，**hex 小写**输出（与 `Hashing` 的 hex 约定一致，便于比对/展示）；
- null 入参 → 返 `err`。

### 3.4 编解码伴生

```java
static String base64Encode(byte[] data);
static Result<byte[], WrappedError> base64Decode(String base64);   // 畸形 → CRYPTO_DECODE_ERROR
static String hexEncode(byte[] data);
static Result<byte[], WrappedError> hexDecode(String hex);          // 畸形/奇数长度 → CRYPTO_DECODE_ERROR
```

- 加密工作流恒需 Base64/Hex（密钥、密文、盐均为二进制需文本编码）；
- `base64Encode`/`hexEncode` 不会失败 → 直接返 `String`（对标 `Hashing.bytesToHex` 私有实现，此处公开）；
- 解码可因畸形输入失败 → `Result`。

---

## §4 错误处理

错误码占用 `FacilityErrorType` 空闲的 **500400–500499** 段（现 HTTP 500300–500399 与 Web 500500–500599 之间空档）：

| 枚举 | 码 | i18n key | 默认文案 | 覆盖场景 |
|---|---|---|---|---|
| `CRYPTO_ENCRYPT_ERROR` | 500400 | `facility.crypto.encrypt_error` | 加密失败 | 加密期任意异常、null 入参 |
| `CRYPTO_DECRYPT_ERROR` | 500401 | `facility.crypto.decrypt_error` | 解密失败 | **粗粒度**：错误密钥/篡改/IV 不足/Base64 畸形/null |
| `CRYPTO_KEY_ERROR` | 500402 | `facility.crypto.key_error` | 密钥处理失败 | `deriveKey`/`importKey` 失败、null |
| `CRYPTO_MAC_ERROR` | 500403 | `facility.crypto.mac_error` | 消息认证码计算失败 | HMAC 失败、null |
| `CRYPTO_DECODE_ERROR` | 500404 | `facility.crypto.decode_error` | 编码解析失败 | base64/hex 解码畸形 |

- 4 个 i18n bundle（base `facility-messages.properties` + `_en` + `_zh_CN` + `_zh_TW`）各补 5 键；
  base bundle 用英文（P7 确定性回落英文修复的既定纪律）。
- `WrappedError.of(FacilityErrorType, Throwable)` / `WrappedError.of(FacilityErrorType)` 两种构造按有无底层异常择用。

---

## §5 ADR-0019

**标题**：crypto 加解密门面——安全默认 AES-GCM、内管 IV、不透明失败通道、纯 JDK

**决策**（背景/决策/备选/后果 四段，对标 ADR-0018）：
1. **静态门面无 SPI/bean**——crypto 是无状态算法调用，无可替换策略；与 `hash` 一致零装配。
   未来若需 KMS/Vault 集成，可加 `CryptoKeyProvider` seam（**hypothetical，本次不建**，YAGNI）。
2. **对称唯一 AES-256-GCM，不暴露 ECB/CBC**——安全默认；IV 由门面内管每次随机，nonce 复用陷阱被根除。
3. **`CRYPTO_DECRYPT_ERROR` 粗粒度**——不区分错误密钥/篡改/畸形，杜绝 padding-oracle 类失败原因泄漏。
4. **PBKDF2WithHmacSHA256 210_000 迭代**（OWASP 下限）、盐调用方管理（须与密文同存）。
5. **纯 JDK 无 BouncyCastle**——SM2/SM3/SM4 留待未来 `@ConditionalOnClass` optional 延伸（对标缓存 Caffeine 双类探测范式）。

**备选（否决）**：默认暴露算法/mode 参数（易误选 ECB，否决）；细分解密失败原因（信息泄漏，否决）；
迭代次数做成 properties（门面应给安全默认非旋钮，否决）；本次引入 RSA/SM（YAGNI，否决）。

- `docs/adr/INDEX.md` 追加 0019 行。

---

## §6 文档三件套（收尾统一处理）

- `README.md`：特性矩阵追加 crypto 行（无装配开关——无 properties/无 autoconfig，与 `hash` 一样恒可用）；
  计数勘误（ADR 18→19；测试数 +N；autoconfig 保持 11）。
- `docs/DESIGN.md`：ADR 索引追加 0019；注明 crypto 为静态门面无装配；三处计数同步（ADR、测试、gate 不变）。
- `docs/USAGE.md`：crypto 使用小节（加解密 / 密钥管理 / HMAC / 编解码示例 + 安全须知：
  密钥存储是调用方责任、密钥不得硬编码、GCM nonce 由门面管理无需操心）。
- `src/main/java/cn/code91/facility/crypto/package-info.java`（对标 `hash` 包文档模板）。
- `.superpowers/sdd/progress.md` 台账更新。

---

## §7 测试策略（覆盖 gate 0.88，目标 CryptoUtil 100% 行/分支）

`src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`：

| 维度 | 用例 |
|---|---|
| 往返 | `encrypt→decrypt` 复原原文（String & byte[] 两变体）；`decryptToBytes` 复原字节 |
| IV 非确定性 | 同明文+同密钥两次 `encrypt` 密文不同（证明 IV 每次新鲜） |
| 篡改检测 | 翻转密文某字节 → `decrypt` 返 `err`（GCM 认证） |
| 错误密钥 | 用不同密钥 `decrypt` → `err` |
| null/空 | null 明文/密钥/密文 → `err`（非 NPE） |
| 畸形密文 | 非 Base64、短于 IV 长度 → `CRYPTO_DECRYPT_ERROR` |
| 密钥生成 | `generateAesKey` 为 256-bit；两次调用不同；`aesKeyFromBytes` 合法 32 字节成功、非法长度(如 10) → `CRYPTO_KEY_ERROR` |
| 口令派生 | 同 password+salt → 同密钥（确定性）；不同 salt → 不同密钥；null 入参 → `err` |
| 导出导入 | `exportKey→importKey` 密钥相等往返；畸形 Base64 → `CRYPTO_KEY_ERROR` |
| HMAC | RFC 4231 HmacSHA256 已知答案向量；稳定性；不同密钥不同；null → `err` |
| 编解码 | base64/hex encode→decode 往返；畸形/奇数长度 decode → `CRYPTO_DECODE_ERROR` |
| 错误映射 | 各失败路径返回正确 `FacilityErrorType` |

---

## §8 交付顺序（TDD，由简到繁）

1. **错误码 + i18n**：`FacilityErrorType` 补 5 枚举 + 4 bundle 补 15 键（先建地基）。
2. **编解码**：`base64Encode/Decode`、`hexEncode/Decode`（无依赖，最简，先红后绿）。
3. **对称加解密核心**：`encrypt`/`decrypt`/`decryptToBytes` + IV 内管（组件心脏）。
4. **密钥生命周期**：`generateAesKey`/`aesKeyFromBytes`/`deriveKey`/`generateSalt`/`exportKey`/`importKey`。
5. **HMAC**：`hmacSha256` 两变体（RFC 4231 向量）。
6. **package-info + ADR-0019 + INDEX**。
7. **文档三件套收尾 + 台账 + 覆盖复验**。
