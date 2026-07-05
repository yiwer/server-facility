# crypto 加解密门面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 server-facility 新增纯 JDK 静态门面 `CryptoUtil`——AES-256-GCM 对称加解密 + HMAC-SHA256 + AES 密钥生命周期 + Base64/Hex 编解码，安全默认不可误用。

**Architecture:** 单顶层子包 `cn.code91.facility.crypto`，深模块静态门面（对标 `hash` 簇的 `Hashing`）：私有构造抛异常，所有可失败方法返回 `Result<T, WrappedError>` 从不抛异常。无 Spring bean / 无 autoconfiguration / 无 properties（自动装配保持 11 个）。仅依赖 `error` + `result`。

**Tech Stack:** Java 21，JCE（`javax.crypto` / `java.security` / `java.util.Base64`），JUnit 5 + AssertJ，Maven（`spring-boot-dependencies` 3.5.10 BOM），JaCoCo。

## Global Constraints

- **纯 JDK，零第三方**：只用 `javax.crypto` / `java.security` / `java.util`，**不引入 BouncyCastle**（SM 系列留待未来 `@ConditionalOnClass` 延伸）。
- **静态门面范式**：`public final class CryptoUtil`，私有构造 `throw new UnsupportedOperationException()`；**无 bean / 无 `FacilityCryptoAutoConfiguration` / 无 `FacilityCryptoProperties`**；`AutoConfiguration.imports` 保持 11 行不动。
- **返回 `Result` 从不抛异常**：可失败方法一律 `Result<T, WrappedError>`；null/畸形入参返 `err` 而非 NPE（编解码/导出等纯变换助手除外，其契约为非 null）。
- **错误码占 `FacilityErrorType` 500400–500499 段**（HTTP 500300–399 与 Web 500500–599 之间空档），新增 5 个；4 个 i18n bundle 同步。
- **对称唯一 `AES/GCM/NoPadding`**：不暴露 mode/padding 参数；IV 12 字节每次 `SecureRandom` 新鲜、前置拼进密文；tag 128-bit；输出标准 Base64。
- **`CRYPTO_DECRYPT_ERROR` 粗粒度**：错误密钥/篡改/IV 不足/Base64 畸形/null 全映射为它，不泄漏失败原因。
- **PBKDF2WithHmacSHA256、210_000 迭代、256-bit**；AES 密钥 256-bit；盐 16 字节由调用方管理。
- **hex 小写输出**（对齐 `Hashing` 约定）。
- **覆盖 gate 0.88**（line/instr ≥0.88，branch ≥0.75）；目标 `CryptoUtil` 100% 行/分支。
- **提交纪律**：用 PowerShell 提交，commit message **不含 ASCII 双引号**（用 CJK 标点「」()）；末尾附
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。
- **Maven 命令离线**：`mvn -o ...`。

---

### Task 1: 错误码/i18n 地基 + CryptoUtil 骨架 + Base64/Hex 编解码

**Files:**
- Modify: `src/main/java/cn/code91/facility/error/FacilityErrorType.java`（在 `HTTP_STATUS_ERROR`(500304) 之后、`WEB_NOT_IN_REQUEST_CONTEXT`(500500) 之前插入 crypto 段 5 枚举）
- Modify: `src/main/resources/i18n/facility-messages.properties`（追加 5 键，英文）
- Modify: `src/main/resources/i18n/facility-messages_en.properties`（追加 5 键，英文）
- Modify: `src/main/resources/i18n/facility-messages_zh_CN.properties`（追加 5 键，简体）
- Modify: `src/main/resources/i18n/facility-messages_zh_TW.properties`（追加 5 键，繁体）
- Create: `src/main/java/cn/code91/facility/crypto/CryptoUtil.java`
- Create: `src/main/java/cn/code91/facility/crypto/package-info.java`
- Test: `src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`

**Interfaces:**
- Consumes: `Result.ok(T)` / `Result.err(WrappedError)` / `result.isOk()` / `result.isErr()` / `result.get()` / `result.getErr()`；`WrappedError.of(ErrorTypeInterface)` / `WrappedError.of(ErrorTypeInterface, Exception)`；`FacilityErrorType`（本任务新增 5 枚举）。
- Produces（供 Task 2-5）：
  - `FacilityErrorType.CRYPTO_ENCRYPT_ERROR`(500400) / `CRYPTO_DECRYPT_ERROR`(500401) / `CRYPTO_KEY_ERROR`(500402) / `CRYPTO_MAC_ERROR`(500403) / `CRYPTO_DECODE_ERROR`(500404)
  - `CryptoUtil.base64Encode(byte[])→String` / `base64Decode(String)→Result<byte[],WrappedError>`
  - `CryptoUtil.hexEncode(byte[])→String` / `hexDecode(String)→Result<byte[],WrappedError>`

- [ ] **Step 1: 在 `FacilityErrorType` 插入 crypto 错误码段**

在 `HTTP_STATUS_ERROR(...)` 枚举常量之后、`// ==================== Web相关错误 (500500-500599) ====================` 注释之前，插入：

```java
    // ==================== 加解密错误 (500400-500499) ====================

    /**
     * 加密失败
     */
    CRYPTO_ENCRYPT_ERROR(
            500400,
            "facility.crypto.encrypt_error",
            "加密失败"
    ),

    /**
     * 解密失败（粗粒度：错误密钥/密文篡改/IV 不足/Base64 畸形均归此，不泄漏失败原因）
     */
    CRYPTO_DECRYPT_ERROR(
            500401,
            "facility.crypto.decrypt_error",
            "解密失败"
    ),

    /**
     * 密钥处理失败（口令派生/导入/长度非法）
     */
    CRYPTO_KEY_ERROR(
            500402,
            "facility.crypto.key_error",
            "密钥处理失败"
    ),

    /**
     * 消息认证码计算失败
     */
    CRYPTO_MAC_ERROR(
            500403,
            "facility.crypto.mac_error",
            "消息认证码计算失败"
    ),

    /**
     * 编码解析失败（Base64/Hex 畸形）
     */
    CRYPTO_DECODE_ERROR(
            500404,
            "facility.crypto.decode_error",
            "编码解析失败"
    ),
```

- [ ] **Step 2: 4 个 i18n bundle 各追加 5 键**

`facility-messages.properties` 与 `facility-messages_en.properties` 末尾各追加：

```properties
# crypto (500400-500499)
facility.crypto.encrypt_error=Encryption failed
facility.crypto.decrypt_error=Decryption failed
facility.crypto.key_error=Key processing failed
facility.crypto.mac_error=Failed to compute message authentication code
facility.crypto.decode_error=Failed to decode input
```

`facility-messages_zh_CN.properties` 末尾追加：

```properties
# crypto (500400-500499)
facility.crypto.encrypt_error=加密失败
facility.crypto.decrypt_error=解密失败
facility.crypto.key_error=密钥处理失败
facility.crypto.mac_error=消息认证码计算失败
facility.crypto.decode_error=编码解析失败
```

`facility-messages_zh_TW.properties` 末尾追加：

```properties
# crypto (500400-500499)
facility.crypto.encrypt_error=加密失敗
facility.crypto.decrypt_error=解密失敗
facility.crypto.key_error=金鑰處理失敗
facility.crypto.mac_error=訊息鑑別碼計算失敗
facility.crypto.decode_error=編碼解析失敗
```

- [ ] **Step 3: 写失败测试 `CryptoUtilTest`（编解码 + 私有构造）**

创建 `src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`：

```java
package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.result.Result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CryptoUtil - 纯 JDK 加解密门面(AES-GCM/HMAC/密钥/编解码)")
class CryptoUtilTest {

    // ==================== 私有构造 ====================

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throws() throws Exception {
        var ctor = CryptoUtil.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(ctor::newInstance).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Base64 ====================

    @Test
    @DisplayName("base64 编解码往返")
    void base64_roundTrip() {
        byte[] data = "hello 加密".getBytes(StandardCharsets.UTF_8);
        String encoded = CryptoUtil.base64Encode(data);
        Result<byte[], ?> decoded = CryptoUtil.base64Decode(encoded);
        assertThat(decoded.isOk()).isTrue();
        assertThat(decoded.get()).isEqualTo(data);
    }

    @Test
    @DisplayName("base64 解码畸形输入 → CRYPTO_DECODE_ERROR")
    void base64Decode_malformed_err() {
        Result<byte[], ?> r = CryptoUtil.base64Decode("!!!not base64!!!");
        assertThat(r.isErr()).isTrue();
        assertThat(CryptoUtil.base64Decode(null).isErr()).isTrue();
    }

    // ==================== Hex ====================

    @Test
    @DisplayName("hex 编码为小写、编解码往返")
    void hex_roundTrip_lowercase() {
        byte[] data = {(byte) 0xAB, 0x01, (byte) 0xFF};
        String hex = CryptoUtil.hexEncode(data);
        assertThat(hex).isEqualTo("ab01ff");
        Result<byte[], ?> decoded = CryptoUtil.hexDecode(hex);
        assertThat(decoded.isOk()).isTrue();
        assertThat(decoded.get()).isEqualTo(data);
    }

    @Test
    @DisplayName("hex 解码奇数长度/非法字符/null → CRYPTO_DECODE_ERROR")
    void hexDecode_malformed_err() {
        assertThat(CryptoUtil.hexDecode("abc").isErr()).isTrue();     // 奇数长度
        Result<byte[], ?> bad = CryptoUtil.hexDecode("zz");           // 非法字符
        assertThat(bad.isErr()).isTrue();
        assertThat(bad.getErr()).isInstanceOf(cn.code91.facility.error.WrappedError.class);
        assertThat(((cn.code91.facility.error.WrappedError) bad.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_DECODE_ERROR);
        assertThat(CryptoUtil.hexDecode(null).isErr()).isTrue();
    }
}
```

- [ ] **Step 4: 运行测试确认失败（编译不过——CryptoUtil 未定义）**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: FAIL，编译错误 `cannot find symbol: class CryptoUtil`。

- [ ] **Step 5: 实现 `CryptoUtil` 骨架 + 编解码**

创建 `src/main/java/cn/code91/facility/crypto/CryptoUtil.java`（本步只落编解码 + 常量 + 私有构造；加解密/密钥/HMAC 由 Task 2-4 追加）：

```java
package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * <b>加解密静态门面</b>
 * <p>
 * 纯 JDK（{@code javax.crypto}/{@code java.security}）实现，把易错的 JCE API 收进窄接口、安全默认、
 * 不可误用的深模块。对称加解密唯一走 AES-256-GCM（IV 由门面内管，杜绝 nonce 复用）；所有可失败方法
 * 返回 {@link Result}，从不抛异常。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class CryptoUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;

    private CryptoUtil() {
        throw new UnsupportedOperationException();
    }

    // ==================== 编解码 ====================

    /**
     * Base64 编码（标准字母表）。
     *
     * @param data 非 null 字节数组
     * @return Base64 字符串
     */
    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 解码。
     *
     * @param base64 Base64 字符串
     * @return 解码字节；null 或畸形输入 → {@link FacilityErrorType#CRYPTO_DECODE_ERROR}
     */
    public static Result<byte[], WrappedError> base64Decode(String base64) {
        if (base64 == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR));
        }
        try {
            return Result.ok(Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR, e));
        }
    }

    /**
     * Hex 编码（小写）。
     *
     * @param data 非 null 字节数组
     * @return 小写十六进制字符串
     */
    public static String hexEncode(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Hex 解码。
     *
     * @param hex 十六进制字符串（偶数长度）
     * @return 解码字节；null/奇数长度/非法字符 → {@link FacilityErrorType#CRYPTO_DECODE_ERROR}
     */
    public static Result<byte[], WrappedError> hexDecode(String hex) {
        if (hex == null || (hex.length() & 1) == 1) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR));
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return Result.ok(out);
    }
}
```

创建 `src/main/java/cn/code91/facility/crypto/package-info.java`：

```java
/**
 * <h2>cn.code91.facility.crypto</h2>
 *
 * <p><b>Purpose:</b> Encryption/decryption facade over the JDK JCE
 * ({@code javax.crypto}/{@code java.security}) — AES-256-GCM symmetric
 * encrypt/decrypt (IV managed internally, prepended to ciphertext, Base64 output),
 * HMAC-SHA256, AES key lifecycle (generate / PBKDF2-derive / export-import), and
 * Base64/Hex codecs. Safe-by-default: no mode/padding parameter is exposed, so
 * callers cannot select ECB; the GCM nonce is never caller-controlled.</p>
 *
 * <p><b>Entry classes:</b> {@code CryptoUtil}.</p>
 *
 * <p><b>Design (ADR-0019):</b> static facade, no Spring bean / no autoconfiguration
 * / no properties (crypto is stateless algorithm invocation with no replaceable
 * strategy — same shape as {@code hash}). {@code decrypt} maps every failure
 * (wrong key, tampered ciphertext, malformed input) to a single coarse
 * {@code CRYPTO_DECRYPT_ERROR} to avoid padding-oracle-class information leaks.
 * Pure JDK — SM2/SM3/SM4 are deferred to a future {@code @ConditionalOnClass}
 * BouncyCastle extension.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result<T, WrappedError>}); no third-party crypto library.</p>
 *
 * <p><b>Depended on by:</b> downstream application code (field-level encryption,
 * token sealing, webhook signature verification).</p>
 */
package cn.code91.facility.crypto;
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: PASS（5 个测试方法全绿）。

- [ ] **Step 7: 提交**

用 PowerShell（here-string，无 ASCII 双引号）：

```
git add src/main/java/cn/code91/facility/error/FacilityErrorType.java src/main/resources/i18n/ src/main/java/cn/code91/facility/crypto/ src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java
git commit -m @'
feat: crypto 门面地基(错误码 500400-500499 + i18n + CryptoUtil 骨架 + Base64/Hex 编解码)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
'@
```

---

### Task 2: 对称加解密核心（AES-256-GCM）

**Files:**
- Modify: `src/main/java/cn/code91/facility/crypto/CryptoUtil.java`（追加 encrypt/decrypt 方法 + 相关 import）
- Test: `src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`（追加加解密测试；用 `SecretKeySpec` 本地造密钥，不依赖 Task 3）

**Interfaces:**
- Consumes: `FacilityErrorType.CRYPTO_ENCRYPT_ERROR` / `CRYPTO_DECRYPT_ERROR`（Task 1）；`Result.map`。
- Produces（供 Task 3-5）：
  - `encrypt(String, SecretKey)→Result<String,WrappedError>`
  - `encrypt(byte[], SecretKey)→Result<String,WrappedError>`（返回 Base64）
  - `decrypt(String, SecretKey)→Result<String,WrappedError>`
  - `decryptToBytes(String, SecretKey)→Result<byte[],WrappedError>`

- [ ] **Step 1: 写失败测试（往返 / IV 非确定性 / 篡改 / 错误密钥 / null / 畸形）**

在 `CryptoUtilTest` 追加（含新 import）：

```java
    // (类顶部 import 追加)
    // import javax.crypto.SecretKey;
    // import javax.crypto.spec.SecretKeySpec;

    private static SecretKey key32() {
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) raw[i] = (byte) i;
        return new SecretKeySpec(raw, "AES");
    }

    @Test
    @DisplayName("AES-GCM 加解密往返(String)")
    void aesGcm_stringRoundTrip() {
        SecretKey key = key32();
        Result<String, ?> ct = CryptoUtil.encrypt("防重复扣款 payload", key);
        assertThat(ct.isOk()).isTrue();
        Result<String, ?> pt = CryptoUtil.decrypt(ct.get(), key);
        assertThat(pt.isOk()).isTrue();
        assertThat(pt.get()).isEqualTo("防重复扣款 payload");
    }

    @Test
    @DisplayName("AES-GCM 加解密往返(byte[])")
    void aesGcm_bytesRoundTrip() {
        SecretKey key = key32();
        byte[] data = {1, 2, 3, 4, 5};
        String ct = CryptoUtil.encrypt(data, key).get();
        Result<byte[], ?> pt = CryptoUtil.decryptToBytes(ct, key);
        assertThat(pt.isOk()).isTrue();
        assertThat(pt.get()).isEqualTo(data);
    }

    @Test
    @DisplayName("同明文+同密钥两次加密密文不同(IV 每次新鲜)")
    void aesGcm_nonDeterministic() {
        SecretKey key = key32();
        String a = CryptoUtil.encrypt("same", key).get();
        String b = CryptoUtil.encrypt("same", key).get();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("密文被篡改 → decrypt 返 err(GCM 认证)")
    void aesGcm_tamperDetected() {
        SecretKey key = key32();
        byte[] raw = Base64.getDecoder().decode(CryptoUtil.encrypt("x", key).get());
        raw[raw.length - 1] ^= 0x01;                       // 翻转末字节(认证 tag)
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThat(CryptoUtil.decrypt(tampered, key).isErr()).isTrue();
    }

    @Test
    @DisplayName("错误密钥 decrypt → err")
    void aesGcm_wrongKey() {
        String ct = CryptoUtil.encrypt("secret", key32()).get();
        byte[] other = new byte[32];
        other[0] = 99;
        assertThat(CryptoUtil.decrypt(ct, new SecretKeySpec(other, "AES")).isErr()).isTrue();
    }

    @Test
    @DisplayName("null 入参 → err 而非 NPE")
    void aesGcm_nullInputs() {
        SecretKey key = key32();
        assertThat(CryptoUtil.encrypt((String) null, key).isErr()).isTrue();
        assertThat(CryptoUtil.encrypt((byte[]) null, key).isErr()).isTrue();
        assertThat(CryptoUtil.encrypt("x", null).isErr()).isTrue();
        assertThat(CryptoUtil.decrypt(null, key).isErr()).isTrue();
        assertThat(CryptoUtil.decrypt("AAAA", null).isErr()).isTrue();
    }

    @Test
    @DisplayName("畸形密文(非 Base64 / 短于 IV) → CRYPTO_DECRYPT_ERROR")
    void aesGcm_malformedCipher() {
        SecretKey key = key32();
        assertThat(CryptoUtil.decrypt("!!!not base64!!!", key).isErr()).isTrue();
        String tooShort = Base64.getEncoder().encodeToString(new byte[8]);   // < 12 字节 IV
        Result<String, ?> r = CryptoUtil.decrypt(tooShort, key);
        assertThat(r.isErr()).isTrue();
        assertThat(((cn.code91.facility.error.WrappedError) r.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_DECRYPT_ERROR);
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: FAIL，`cannot find symbol: method encrypt(...)`。

- [ ] **Step 3: 实现加解密方法**

在 `CryptoUtil` 追加 import 与方法：

```java
// import 追加：
// import java.nio.charset.StandardCharsets;
// import java.util.Arrays;
// import javax.crypto.Cipher;
// import javax.crypto.SecretKey;
// import javax.crypto.spec.GCMParameterSpec;

    // ==================== 对称加解密(AES-256-GCM) ====================

    /**
     * AES-256-GCM 加密（UTF-8 明文）。IV 每次随机 12 字节前置拼进密文，整体 Base64 输出。
     *
     * @param plaintext UTF-8 明文
     * @param key       AES 密钥（见 {@link #generateAesKey()} / {@link #aesKeyFromBytes(byte[])}）
     * @return {@code Base64(IV ‖ ciphertext+tag)}；失败 → {@link FacilityErrorType#CRYPTO_ENCRYPT_ERROR}
     */
    public static Result<String, WrappedError> encrypt(String plaintext, SecretKey key) {
        if (plaintext == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR));
        }
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), key);
    }

    /**
     * AES-256-GCM 加密（字节明文）。
     *
     * @param plaintext 明文字节
     * @param key       AES 密钥
     * @return {@code Base64(IV ‖ ciphertext+tag)}；null 入参/失败 → {@link FacilityErrorType#CRYPTO_ENCRYPT_ERROR}
     */
    public static Result<String, WrappedError> encrypt(byte[] plaintext, SecretKey key) {
        if (plaintext == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR));
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Result.ok(Base64.getEncoder().encodeToString(out));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR, e));
        }
    }

    /**
     * AES-256-GCM 解密为 UTF-8 明文。
     *
     * @param base64Cipher {@link #encrypt} 的输出
     * @param key          AES 密钥
     * @return 明文；失败（错误密钥/篡改/畸形/null）→ {@link FacilityErrorType#CRYPTO_DECRYPT_ERROR}
     */
    public static Result<String, WrappedError> decrypt(String base64Cipher, SecretKey key) {
        return decryptToBytes(base64Cipher, key).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * AES-256-GCM 解密为字节。
     *
     * @param base64Cipher {@link #encrypt} 的输出
     * @param key          AES 密钥
     * @return 明文字节；失败 → {@link FacilityErrorType#CRYPTO_DECRYPT_ERROR}（粗粒度，不泄漏原因）
     */
    public static Result<byte[], WrappedError> decryptToBytes(String base64Cipher, SecretKey key) {
        if (base64Cipher == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
        }
        try {
            byte[] all = Base64.getDecoder().decode(base64Cipher);
            if (all.length <= GCM_IV_BYTES) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
            }
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
            byte[] ct = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return Result.ok(cipher.doFinal(ct));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR, e));
        }
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: PASS（新增 7 个加解密测试 + Task 1 的 5 个全绿）。

- [ ] **Step 5: 提交**

```
git add src/main/java/cn/code91/facility/crypto/CryptoUtil.java src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java
git commit -m @'
feat: crypto 对称加解密核心 AES-256-GCM(IV 内管随机前置/认证加密/粗粒度失败通道)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
'@
```

---

### Task 3: 密钥生命周期（生成/派生/导出导入/盐）

**Files:**
- Modify: `src/main/java/cn/code91/facility/crypto/CryptoUtil.java`
- Test: `src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`

**Interfaces:**
- Consumes: `FacilityErrorType.CRYPTO_KEY_ERROR`（Task 1）；`encrypt`/`decrypt`（Task 2，用于端到端往返测试）。
- Produces（供 Task 5 文档 / USAGE 示例）：
  - `generateAesKey()→SecretKey`
  - `aesKeyFromBytes(byte[])→Result<SecretKey,WrappedError>`
  - `deriveKey(String, byte[])→Result<SecretKey,WrappedError>`
  - `generateSalt()→byte[]`
  - `exportKey(SecretKey)→String`（Base64）
  - `importKey(String)→Result<SecretKey,WrappedError>`

- [ ] **Step 1: 写失败测试**

在 `CryptoUtilTest` 追加：

```java
    // ==================== 密钥生命周期 ====================

    @Test
    @DisplayName("generateAesKey 为 256-bit 且两次不同")
    void generateAesKey_256bit_unique() {
        SecretKey a = CryptoUtil.generateAesKey();
        SecretKey b = CryptoUtil.generateAesKey();
        assertThat(a.getEncoded()).hasSize(32);            // 256-bit
        assertThat(a.getEncoded()).isNotEqualTo(b.getEncoded());
    }

    @Test
    @DisplayName("生成密钥 → 加密 → 解密 端到端往返")
    void generatedKey_endToEnd() {
        SecretKey key = CryptoUtil.generateAesKey();
        String ct = CryptoUtil.encrypt("端到端", key).get();
        assertThat(CryptoUtil.decrypt(ct, key).get()).isEqualTo("端到端");
    }

    @Test
    @DisplayName("aesKeyFromBytes 合法 16/24/32 字节成功、非法长度 → CRYPTO_KEY_ERROR")
    void aesKeyFromBytes_lengthValidation() {
        assertThat(CryptoUtil.aesKeyFromBytes(new byte[16]).isOk()).isTrue();
        assertThat(CryptoUtil.aesKeyFromBytes(new byte[24]).isOk()).isTrue();
        assertThat(CryptoUtil.aesKeyFromBytes(new byte[32]).isOk()).isTrue();
        Result<?, ?> bad = CryptoUtil.aesKeyFromBytes(new byte[10]);
        assertThat(bad.isErr()).isTrue();
        assertThat(((cn.code91.facility.error.WrappedError) bad.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_KEY_ERROR);
        assertThat(CryptoUtil.aesKeyFromBytes(null).isErr()).isTrue();
    }

    @Test
    @DisplayName("deriveKey 同 password+salt 确定、不同 salt 不同、null → err")
    void deriveKey_deterministic() {
        byte[] salt = CryptoUtil.generateSalt();
        SecretKey k1 = CryptoUtil.deriveKey("pw", salt).get();
        SecretKey k2 = CryptoUtil.deriveKey("pw", salt).get();
        assertThat(k1.getEncoded()).isEqualTo(k2.getEncoded());          // 确定
        assertThat(k1.getEncoded()).hasSize(32);                          // 256-bit
        SecretKey k3 = CryptoUtil.deriveKey("pw", CryptoUtil.generateSalt()).get();
        assertThat(k3.getEncoded()).isNotEqualTo(k1.getEncoded());        // 不同 salt
        assertThat(CryptoUtil.deriveKey(null, salt).isErr()).isTrue();
        assertThat(CryptoUtil.deriveKey("pw", new byte[0]).isErr()).isTrue();
    }

    @Test
    @DisplayName("generateSalt 为 16 字节且两次不同")
    void generateSalt_16bytes_unique() {
        assertThat(CryptoUtil.generateSalt()).hasSize(16);
        assertThat(CryptoUtil.generateSalt()).isNotEqualTo(CryptoUtil.generateSalt());
    }

    @Test
    @DisplayName("exportKey → importKey 密钥往返相等；畸形 → CRYPTO_KEY_ERROR")
    void exportImport_roundTrip() {
        SecretKey key = CryptoUtil.generateAesKey();
        String exported = CryptoUtil.exportKey(key);
        Result<SecretKey, ?> imported = CryptoUtil.importKey(exported);
        assertThat(imported.isOk()).isTrue();
        assertThat(imported.get().getEncoded()).isEqualTo(key.getEncoded());
        assertThat(CryptoUtil.importKey("!!!bad!!!").isErr()).isTrue();
        assertThat(CryptoUtil.importKey(null).isErr()).isTrue();
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: FAIL，`cannot find symbol: method generateAesKey()` 等。

- [ ] **Step 3: 实现密钥方法**

在 `CryptoUtil` 追加 import 与方法：

```java
// import 追加：
// import java.security.NoSuchAlgorithmException;
// import java.security.spec.KeySpec;
// import javax.crypto.KeyGenerator;
// import javax.crypto.SecretKeyFactory;
// import javax.crypto.spec.PBEKeySpec;
// import javax.crypto.spec.SecretKeySpec;

    // ==================== 密钥生命周期 ====================

    /**
     * 生成 256-bit AES 密钥（{@link SecureRandom}）。
     *
     * @return 新 AES 密钥
     */
    public static SecretKey generateAesKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(AES);
            kg.init(AES_KEY_BITS, SECURE_RANDOM);
            return kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            // AES 是 JDK 强制算法，理论上不发生
            throw new IllegalStateException("AES KeyGenerator unavailable", e);
        }
    }

    /**
     * 用原始字节包装为 AES 密钥（fail-fast 校验长度）。
     *
     * @param raw 16/24/32 字节原始密钥
     * @return AES 密钥；null 或非法长度 → {@link FacilityErrorType#CRYPTO_KEY_ERROR}
     */
    public static Result<SecretKey, WrappedError> aesKeyFromBytes(byte[] raw) {
        if (raw == null || (raw.length != 16 && raw.length != 24 && raw.length != 32)) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
        }
        return Result.ok(new SecretKeySpec(raw, AES));
    }

    /**
     * 口令派生 256-bit AES 密钥（PBKDF2WithHmacSHA256，210_000 迭代）。盐须与密文一同持久化。
     *
     * @param password 口令
     * @param salt     盐（见 {@link #generateSalt()}）
     * @return 派生密钥；null 入参/空盐/失败 → {@link FacilityErrorType#CRYPTO_KEY_ERROR}
     */
    public static Result<SecretKey, WrappedError> deriveKey(String password, byte[] salt) {
        if (password == null || salt == null || salt.length == 0) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
        }
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return Result.ok(new SecretKeySpec(keyBytes, AES));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR, e));
        }
    }

    /**
     * 生成 16 字节随机盐（{@link SecureRandom}）。
     *
     * @return 盐字节
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * 导出密钥为 Base64（持久化/传输）。
     *
     * @param key 非 null 密钥
     * @return Base64 字符串
     */
    public static String exportKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64 导入 AES 密钥（{@link #exportKey} 逆操作）。
     *
     * @param base64Key Base64 密钥
     * @return AES 密钥；null/畸形 Base64/非法长度 → {@link FacilityErrorType#CRYPTO_KEY_ERROR}
     */
    public static Result<SecretKey, WrappedError> importKey(String base64Key) {
        if (base64Key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
        }
        try {
            return aesKeyFromBytes(Base64.getDecoder().decode(base64Key));
        } catch (IllegalArgumentException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR, e));
        }
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: PASS（新增 6 个密钥测试全绿）。

- [ ] **Step 5: 提交**

```
git add src/main/java/cn/code91/facility/crypto/CryptoUtil.java src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java
git commit -m @'
feat: crypto 密钥生命周期(AES 生成/PBKDF2 派生 210k/盐/导出导入,fail-fast 长度校验)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
'@
```

---

### Task 4: 消息认证码（HMAC-SHA256）

**Files:**
- Modify: `src/main/java/cn/code91/facility/crypto/CryptoUtil.java`
- Test: `src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java`

**Interfaces:**
- Consumes: `FacilityErrorType.CRYPTO_MAC_ERROR`（Task 1）；`hexEncode`（Task 1）。
- Produces：
  - `hmacSha256(byte[], byte[])→Result<String,WrappedError>`（hex 小写）
  - `hmacSha256(String, String)→Result<String,WrappedError>`（UTF-8 → hex 小写）

- [ ] **Step 1: 写失败测试（RFC 4231 已知答案向量）**

在 `CryptoUtilTest` 追加：

```java
    // ==================== HMAC ====================

    @Test
    @DisplayName("HmacSHA256 匹配 RFC 4231 Test Case 2 已知答案")
    void hmacSha256_rfc4231_knownAnswer() {
        // RFC 4231 §4.3: key=\"Jefe\", data=\"what do ya want for nothing?\"
        Result<String, ?> mac = CryptoUtil.hmacSha256("what do ya want for nothing?", "Jefe");
        assertThat(mac.isOk()).isTrue();
        assertThat(mac.get()).isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    @DisplayName("HmacSHA256 稳定、不同密钥不同、null → CRYPTO_MAC_ERROR")
    void hmacSha256_stableAndKeyed() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        byte[] k1 = "k1".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "k2".getBytes(StandardCharsets.UTF_8);
        String a = CryptoUtil.hmacSha256(data, k1).get();
        assertThat(CryptoUtil.hmacSha256(data, k1).get()).isEqualTo(a);      // 稳定
        assertThat(CryptoUtil.hmacSha256(data, k2).get()).isNotEqualTo(a);   // 密钥敏感
        Result<String, ?> bad = CryptoUtil.hmacSha256((byte[]) null, k1);
        assertThat(bad.isErr()).isTrue();
        assertThat(((cn.code91.facility.error.WrappedError) bad.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_MAC_ERROR);
        assertThat(CryptoUtil.hmacSha256("d", (String) null).isErr()).isTrue();
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: FAIL，`cannot find symbol: method hmacSha256(...)`。

- [ ] **Step 3: 实现 HMAC 方法**

在 `CryptoUtil` 追加 import 与方法：

```java
// import 追加：
// import javax.crypto.Mac;

    // ==================== 消息认证码(HMAC-SHA256) ====================

    /**
     * HMAC-SHA256（字节输入），hex 小写输出。
     *
     * @param data 消息字节
     * @param key  密钥字节（非空）
     * @return 小写 hex MAC；null 入参/空密钥/失败 → {@link FacilityErrorType#CRYPTO_MAC_ERROR}
     */
    public static Result<String, WrappedError> hmacSha256(byte[] data, byte[] key) {
        if (data == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_MAC_ERROR));
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return Result.ok(hexEncode(mac.doFinal(data)));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_MAC_ERROR, e));
        }
    }

    /**
     * HMAC-SHA256（UTF-8 字符串输入），hex 小写输出。
     *
     * @param data 消息
     * @param key  密钥
     * @return 小写 hex MAC；null 入参 → {@link FacilityErrorType#CRYPTO_MAC_ERROR}
     */
    public static Result<String, WrappedError> hmacSha256(String data, String key) {
        if (data == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_MAC_ERROR));
        }
        return hmacSha256(data.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -o test -Dtest=CryptoUtilTest`
Expected: PASS（新增 2 个 HMAC 测试全绿，`CryptoUtilTest` 共 20 个方法）。

- [ ] **Step 5: 提交**

```
git add src/main/java/cn/code91/facility/crypto/CryptoUtil.java src/test/java/cn/code91/facility/crypto/CryptoUtilTest.java
git commit -m @'
feat: crypto HMAC-SHA256 消息认证(hex 小写输出,RFC 4231 已知答案向量校验)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
'@
```

---

### Task 5: ADR-0019 + 文档三件套 + 台账 + 全量覆盖复验

**Files:**
- Create: `docs/adr/0019-crypto-facade-safe-defaults.md`
- Modify: `docs/adr/INDEX.md`（追加 0019 行）
- Modify: `README.md`（特性矩阵 + crypto 行；ADR 18→19；测试计数）
- Modify: `docs/DESIGN.md`（ADR 索引 + 计数同步；注明 crypto 静态门面无装配）
- Modify: `docs/USAGE.md`（crypto 使用小节 + 安全须知）
- Modify: `.superpowers/sdd/progress.md`（台账）

**Interfaces:**
- Consumes: 全部 `CryptoUtil` API + `FacilityErrorType` crypto 段（Task 1-4）。
- Produces: 无（收尾任务）。

- [ ] **Step 1: 写 ADR-0019**

创建 `docs/adr/0019-crypto-facade-safe-defaults.md`：

```markdown
# ADR-0019: crypto 加解密门面——安全默认 AES-GCM、内管 IV、不透明失败通道、纯 JDK

- **状态**：Accepted(2026-07-04)
- **源起**：crypto 加解密门面实现计划(docs/superpowers/plans/2026-07-04-crypto-facade.md)

## 背景

server-facility 此前无加解密组件（`hash` 簇只做单向摘要）。§10 roadmap 将 crypto 列为「静态门面范式」
新组件。JCE（`javax.crypto`/`java.security`）是 JDK 内置但臭名昭著易错的 API——mode/padding 选择、IV
管理、失败原因暴露每一处都能酿成安全事故。本 ADR 记录把它收进深模块门面时的取舍。

## 决策

### 1. 静态门面，无 SPI/bean（对标 hash）

crypto 是无状态算法调用，无可替换策略、无需注入——与 `hash` 一致：`public final class CryptoUtil`
私有构造抛异常，无 `FacilityCryptoAutoConfiguration`、无 properties，`AutoConfiguration.imports`
保持 11 行。未来若需 KMS/Vault 密钥托管，可加 `CryptoKeyProvider` seam——**hypothetical，本次不建**（YAGNI）。

### 2. 对称唯一 AES-256-GCM，不暴露 mode/padding

门面只提供 `encrypt/decrypt`，内部固定 `AES/GCM/NoPadding`，**不接受算法/mode/padding 参数**——
调用方无从选到 ECB（确定性、泄漏明文模式的经典陷阱）。GCM 同时给机密性 + 完整性（AEAD），密文被
篡改时解密抛 `AEADBadTagException`。IV 12 字节每次 `SecureRandom` 新鲜生成、前置拼进密文，
**调用方无从控制 nonce** ——根除 GCM 最致命的 nonce 复用漏洞。tag 128-bit。

### 3. CRYPTO_DECRYPT_ERROR 粗粒度失败通道

`decrypt` 把错误密钥、密文篡改、IV 长度不足、Base64 畸形、null 入参**全部**映射为单一
`CRYPTO_DECRYPT_ERROR`，不区分「哪一步失败」。细分失败原因会给攻击者提供 padding-oracle 类的
判别信号（历史上 CBC padding oracle、Bleichenbacher 攻击均源于此），故刻意不透明。

### 4. PBKDF2WithHmacSHA256 210_000 迭代，盐调用方管理

`deriveKey` 用 PBKDF2WithHmacSHA256、210_000 迭代（OWASP 2023 下限）、256-bit 输出。盐由
`generateSalt()`（16 字节）生成、**调用方负责与密文一同持久化**——门面不隐藏盐（派生需可复现）。
迭代次数固化不做 properties：门面职责是安全默认而非旋钮（与 ADR-0013「properties 暴露可调参数」
不同，此处「可调」本身就是安全反模式——消费方调低迭代数即削弱防护）。

### 5. 纯 JDK，无 BouncyCastle

AES-GCM/HMAC/PBKDF2/RSA 均在 JDK JCE 内，标准算法零第三方依赖即可覆盖。国密 SM2/SM3/SM4 需
BouncyCastle，留待未来 `@ConditionalOnClass(name = "org.bouncycastle...")` optional 延伸
（对标缓存 Caffeine 双类探测范式），本次不引入。

## 备选(否决)

- **门面暴露算法/mode/padding 参数**（灵活但易误选 ECB/CBC，安全反模式，否决）；
- **细分解密失败原因**（padding-oracle 类信息泄漏，否决）；
- **迭代次数做成 properties**（削防护的旋钮，门面应给安全默认，否决）；
- **本次引入 RSA/国密 SM**（YAGNI——对称+摘要覆盖约 90% 需求，非对称/SM 可按同范式后续追加，否决）；
- **口令哈希 BCrypt 纳入**（单向认证非「加解密」语义，属 Spring Security PasswordEncoder 范畴，否决）。

## 后果

- 消费方零额外依赖即可加解密（纯 JDK）；密钥存储/轮换是消费方责任（门面不托管密钥）。
- 安全默认不可误用：无 ECB、IV 内管、失败不泄漏——把 JCE 的易错面收窄为窄接口。
- `packages_are_cycle_free` ArchUnit 规则对 `crypto` 包保持绿（仅依赖 `error`/`result`，零 web 边）。
- **Carry-forward**：README/USAGE/DESIGN 三件套 crypto 特性矩阵、ADR 索引更新于本计划 Task 5 统一处理。
```

- [ ] **Step 2: 追加 `docs/adr/INDEX.md` 的 0019 行**

在 0018 行之后追加一行（对齐既有表格列格式；先读文件确认列结构再插入）：

```markdown
| 0019 | crypto 加解密门面——安全默认 AES-GCM、内管 IV、不透明失败通道、纯 JDK | Accepted | 2026-07-04 |
```

- [ ] **Step 3: 更新 README / DESIGN / USAGE 三件套**

先分别读三个文件，定位特性矩阵、ADR 索引、计数处，做精确编辑：

1. `README.md` 特性矩阵追加 crypto 行（与 `hash` 同格式，无装配开关列——crypto 恒可用无 properties）：
   `| crypto | AES-256-GCM 对称加解密 + HMAC + 密钥派生/管理 + Base64/Hex | 静态门面 `CryptoUtil`，纯 JDK，无需配置 |`
   并把 ADR 计数 18→19；测试计数按 Task 4 末 `mvn -o clean verify` 实测总数更新（勿臆造，见 Step 4）。
2. `docs/DESIGN.md`：ADR 索引追加 0019；注明 crypto 为静态门面无自动装配（autoconfig 保持 11）；
   ADR 计数处 18→19；测试计数与 gate（gate 0.88 不变）按实测同步。
3. `docs/USAGE.md`：新增「加解密 crypto」小节：

```markdown
## 加解密 crypto

纯 JDK 静态门面 `CryptoUtil`，无需任何配置（无 bean/无 properties），恒可用。

### 对称加解密（AES-256-GCM）

```java
SecretKey key = CryptoUtil.generateAesKey();                 // 或 deriveKey / importKey
String cipher = CryptoUtil.encrypt("敏感数据", key).orElse("");   // Base64(IV‖密文+tag)
String plain  = CryptoUtil.decrypt(cipher, key).orElse("");     // 失败(错误密钥/篡改)返 err
```

### 口令派生密钥（PBKDF2）

```java
byte[] salt = CryptoUtil.generateSalt();                     // 16 字节,须与密文一同持久化
SecretKey key = CryptoUtil.deriveKey("用户口令", salt).orElseGet(CryptoUtil::generateAesKey);
```

### 密钥导出/导入

```java
String exported = CryptoUtil.exportKey(key);                 // Base64,写入密钥库
SecretKey restored = CryptoUtil.importKey(exported).orElseThrow();
```

### HMAC 消息认证 / 编解码

```java
String mac = CryptoUtil.hmacSha256("body", "secret").orElse("");   // hex 小写
String b64 = CryptoUtil.base64Encode(bytes);
String hex = CryptoUtil.hexEncode(bytes);
```

### 安全须知

- **密钥存储是调用方责任**：密钥/盐**不得硬编码**进源码或配置，应取自密钥管理服务（KMS/Vault）或
  受控环境变量；`CryptoUtil` 只做算法调用，不托管密钥。
- **GCM nonce 由门面管理**：每次 `encrypt` 自动生成随机 IV 前置拼进密文，调用方无需也无从操心 nonce
  ——不要试图复用密文或自行拼 IV。
- **解密失败不透明**：错误密钥、密文篡改、畸形输入统一返 `CRYPTO_DECRYPT_ERROR`（防信息泄漏），
  排障请依赖服务端日志中的底层异常（`WrappedError.getException()`），勿把区分回给客户端。
- **国密 SM 系列未内置**：如需 SM2/SM3/SM4，须自行引入 BouncyCastle（本组件纯 JDK，不含）。
```

- [ ] **Step 4: 全量覆盖复验**

Run: `mvn -o clean verify`
Expected: `BUILD SUCCESS`；`Tests run: <N>, Failures: 0, Errors: 0`（记录实测 N，回填 README/DESIGN 计数）；
`All coverage checks have been met`（line/instr ≥0.88，branch ≥0.75）；`dependency:analyze` 无新增 warning；
`packages_are_cycle_free` / ArchTest 全绿。

若覆盖未达标（例如某分支未覆盖），补 `CryptoUtilTest` 用例后重跑，直到 gate 通过。

- [ ] **Step 5: 更新台账 `.superpowers/sdd/progress.md`**

追加 crypto 组件完成条目（组件清单、5 任务、ADR-0019、实测测试计数、覆盖达标、遗留 Minor 若有）。

- [ ] **Step 6: 提交**

```
git add docs/adr/ README.md docs/DESIGN.md docs/USAGE.md .superpowers/sdd/progress.md
git commit -m @'
docs: crypto 收尾(ADR-0019 安全默认 + README/DESIGN/USAGE 三件套 + INDEX + 台账 + 覆盖复验)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
'@
```

---

## 交付后

5 任务完成后：`feat/crypto` 分支含 crypto 门面全量（`CryptoUtil` + 20 测试 + 5 错误码 + i18n + ADR-0019 + 文档）。
进入 **terminal review**（opus 终审）→ 修复 Critical/Important → **finishing-a-development-branch**（`--no-ff` 合并 master）。
