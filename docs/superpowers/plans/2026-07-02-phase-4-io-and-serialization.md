# server-facility P4 IO 与序列化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 迁移 io/path/mime/json/copy 五簇 + Json 装配;执行 CopyUtil 拆分(837 行 → API + 包私有 AutoCopyEngine);C2 断环以 drop 闭合(WrappedContainer/WrappedDataType 零消费方);convert 包改判 drop(零消费方的数据库契约)。

**Architecture:** 依赖:io→error/log/result;path→error/result(+spring-core StringUtils);mime→error/result(+tika optional);json→error/log/result;copy→common/log。全部前置已迁。顺序:T1 依赖 → T2 io+path → T3 mime → T4 json → T5 copy 迁移 → T6 CopyUtil 拆分(独立 commit)→ T7 Json 装配 → T8 spec 勘误。TDD:测试先落(红)→ 源码 → 绿 → 提交。

**Tech Stack:** 同 P3;新增依赖:jackson(databind/jdk8/jsr310/parameter-names,compile,BOM 管版本)、tika-core(optional,3.2.3——BOM 不管,自管属性)。

## Global Constraints

- 继承 P1-P3 计划全部约束:`$SRC`=`D:\STELE\beacon\beacon-support\beacon-facility`、`$DST`=`D:\Yiwer\code\server-facility`、包名映射 `cn.hbads.beacon.facility`→`cn.code91.facility`(ordinal)、UTF-8 无 BOM、每任务 `mvn test` 全绿才 commit、commit 尾行 Co-Authored-By、**git 提交一律 PowerShell 工具**、**RED/GREEN 原始 mvn 粘贴且行号可交叉核对(拼凑=任务失败)**、**brief 列明的每处编辑都必须执行(审查做机械重建对账)**。
- **标准迁移命令**(PowerShell,同前;含 FQN 引用的文件同样适用——如 CopyUtil 内 `cn.hbads.beacon.facility.log.LogUtil.warn` 会被一并替换)。
- **测试计数纪律**(grep 实数,已核):迁移 PathIoSizeVisitorTest 2 / JsonsRegistryTest 5 / JsonUtilTest 10 / InputStreamSerializerTest 2 / CopyUtilNullCopyTest 2 / FacilityJsonAutoConfigurationTest 3(共 24);新写 ZippingTest 6 / PathIoTest 4 / FilenamesTest 10 / MimeTypingTest 8 / TypeRefTest 5 / InputStreamDeserializerTest 4 / CopyUtilAutoCopyTest 10(共 47)。链:**T2 后 401 → T3 后 409 → T4 后 435 → T5 后 447 → T6 后 447(纯结构)→ T7 后 450**。不符即 STOP。
- **禁止事项**:不迁移 `WrappedContainer`/`WrappedDataType` 及其 2 个测试(C2 drop);不迁移 `convert` 包(改判 drop);json 簇除包名替换外零改动;`CopyTrait` 仅 1 处 @see 编辑;T5 迁移 commit 不得改 CopyUtil 结构(拆分属 T6)。

## 复核结论(spec §5 遗留 review 项,本计划定案)

1. **C2 收尾 = drop**:`WrappedContainer`/`WrappedDataType` 在**整个 beacon 仓库零消费方**(仅自家 package-info 提及)——按 spec §5 行 3 授权选项 drop(同 coordinate 先例)。C2 环随之闭合,structure 保持纯 Tuple/Triple(P1 已就位),无需搬家。其 2 个测试文件(4 例)不迁。
2. **convert 包改判 drop**(spec §5 行 15 keep+review → drop,授权见判定图例"复核结论可改判"):`TypeConverter`/`BidirectionalConverter` 是 fromDb/toDb **数据库契约**,beacon 全仓零消费方;USAGE"基于 Spring ConversionService"系文档失实(纯接口,无 Spring 关联)。未来 database 模块出现时随模块重建。
3. **zip-slip 复核 = 不适用**:`Zipping` 只有压包(zipFiles/zipDirectory),无解压能力;zip-slip 是解压侧漏洞。`zipDirectory` 条目名经 `relativize` 正常。若 roadmap 增加解压能力,须做条目路径逃逸校验。
4. **CopyUtil 拆分 = 执行**(spec §5 行 16):837 行 UtilityClass 含两个独立职责——CopyTrait 集合拷贝 API 与反射 autoCopy 引擎,接缝干净(autoCopy 单入口)。拆法:**公共 API 面零变化**(CopyOptions/CopyException 保持嵌套),仅私有反射机件移出为包私有 `AutoCopyEngine`(~340 行)。autoCopy 原本零测试——T5 先补 10 个行为测试钉住,T6 独立 commit 拆分(P0+P1 终审的迁移/rework 分离要求)。
5. **package-info 重写×5**:io/path/mime 无依赖声明(旧格式),json 漏 log,copy 声称"nothing"实依赖 common+log——全部按 house 格式据实重写(P1 教训)。

---

### Task 1: P4 依赖落 pom

**Files:**
- Modify: `D:\Yiwer\code\server-facility\pom.xml`

- [ ] **Step 1: properties 区插入 tika 版本** —— old:

```xml
        <archunit.version>1.3.0</archunit.version>
```

new:

```xml
        <archunit.version>1.3.0</archunit.version>
        <tika.version>3.2.3</tika.version>
```

- [ ] **Step 2: 依赖区插入(jakarta.validation-api 之后、test 注释之前)** —— old:

```xml
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
```

new:

```xml
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>

        <!-- ===== P4 序列化簇所需 ===== -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jdk8</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-parameter-names</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-core</artifactId>
            <version>${tika.version}</version>
            <optional>true</optional>
        </dependency>
```

- [ ] **Step 3: 验证** Run mvn test → `BUILD SUCCESS`,`Tests run: 379`

- [ ] **Step 4: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add pom.xml
git -C D:\Yiwer\code\server-facility commit -m @'
build: P4 依赖(jackson 四件套 compile;tika-core optional)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 2: io + path 簇迁移

**Files:**
- Create(迁移): `$DST\src\test\java\cn\code91\facility\io\PathIoSizeVisitorTest.java`
- Test(新写): `ZippingTest.java`(io)/ `PathIoTest.java`(io)/ `FilenamesTest.java`(path)
- Create(迁移): `PathIo.java` / `Zipping.java` → `$DST\src\main\java\cn\code91\facility\io\`;`Filenames.java` → `...\path\`
- Create(全新内容): io 与 path 的 `package-info.java`

**Interfaces:**
- Consumes: P1 error/result;P2 log(Zipping→LogUtil)、spring-core(Filenames→StringUtils)
- Produces:`PathIo.deleteDirectory/directorySize`;`Zipping.zipFiles/zipDirectory`(均 Result);`Filenames.sanitize/extension/nameWithoutExtension/isDangerousExtension/checkExtension×2`(web 簇 SafeUpload 在 P6 消费)

- [ ] **Step 1: 新写 ZippingTest.java(6 用例)**

```java
package cn.code91.facility.io;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Zipping - Zip 打包")
class ZippingTest {

    @TempDir
    Path tempDir;

    private Path file(String name, String content) throws Exception {
        Path p = tempDir.resolve(name);
        Files.createDirectories(p.getParent() == null ? tempDir : p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private List<String> entryNames(Path zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().map(ZipEntry::getName).forEach(names::add);
        }
        return names;
    }

    @Test
    void zipFiles_roundTrip_containsFlatEntryNames() throws Exception {
        Path a = file("a.txt", "AAA");
        Path b = file("b.txt", "BBB");
        Path out = tempDir.resolve("out/pack.zip");

        var result = Zipping.zipFiles(List.of(a, b), out);

        assertThat(result.isOk()).isTrue();
        assertThat(entryNames(out)).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void zipFiles_createsMissingParentDirectories() throws Exception {
        Path a = file("a.txt", "x");
        Path out = tempDir.resolve("deep/nested/dir/pack.zip");
        assertThat(Zipping.zipFiles(List.of(a), out).isOk()).isTrue();
        assertThat(Files.exists(out)).isTrue();
    }

    @Test
    void zipFiles_skipsNonexistentEntries() throws Exception {
        Path a = file("a.txt", "x");
        Path ghost = tempDir.resolve("ghost.txt");
        Path out = tempDir.resolve("pack.zip");

        assertThat(Zipping.zipFiles(List.of(a, ghost), out).isOk()).isTrue();
        assertThat(entryNames(out)).containsExactly("a.txt");
    }

    @Test
    void zipFiles_emptyOrNullList_returnsFileReadError() {
        Path out = tempDir.resolve("pack.zip");
        assertThat(Zipping.zipFiles(List.of(), out).getErr()
                .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
        assertThat(Zipping.zipFiles(null, out).getErr()
                .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
    }

    @Test
    void zipDirectory_recursive_usesRelativeForwardSlashEntryNames() throws Exception {
        file("root/top.txt", "1");
        file("root/sub/inner.txt", "2");
        Path out = tempDir.resolve("dir.zip");

        assertThat(Zipping.zipDirectory(tempDir.resolve("root"), out).isOk()).isTrue();
        assertThat(entryNames(out)).containsExactlyInAnyOrder("top.txt", "sub/inner.txt");
    }

    @Test
    void zipDirectory_missingSource_returnsFileNotFound() {
        assertThat(Zipping.zipDirectory(tempDir.resolve("absent"), tempDir.resolve("z.zip")).getErr()
                .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
    }
}
```

- [ ] **Step 2: 新写 PathIoTest.java(4 用例)**

```java
package cn.code91.facility.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PathIo - 递归路径操作(deleteDirectory/directorySize)")
class PathIoTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteDirectory_removesNestedTree() throws Exception {
        Path root = tempDir.resolve("tree");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/f.txt"), "x", StandardCharsets.UTF_8);

        assertThat(PathIo.deleteDirectory(root).isOk()).isTrue();
        assertThat(Files.exists(root)).isFalse();
    }

    @Test
    void deleteDirectory_nonexistent_isOk() {
        assertThat(PathIo.deleteDirectory(tempDir.resolve("absent")).isOk()).isTrue();
    }

    @Test
    void deleteDirectory_null_isOk() {
        assertThat(PathIo.deleteDirectory(null).isOk()).isTrue();
    }

    @Test
    void directorySize_sumsAllFiles() throws Exception {
        Path root = tempDir.resolve("sized");
        Files.createDirectories(root.resolve("sub"));
        Files.write(root.resolve("one.bin"), new byte[10]);
        Files.write(root.resolve("sub/two.bin"), new byte[32]);

        assertThat(PathIo.directorySize(root).get()).isEqualTo(42L);
    }
}
```

- [ ] **Step 3: 新写 FilenamesTest.java(10 用例)**

```java
package cn.code91.facility.path;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Filenames - 文件名安全")
class FilenamesTest {

    @Test
    void sanitize_plainName_passesThrough() {
        assertThat(Filenames.sanitize("report_2026.pdf").get()).isEqualTo("report_2026.pdf");
    }

    @Test
    void sanitize_stripsPathPrefixes_bothSeparators() {
        assertThat(Filenames.sanitize("dir/sub/name.txt").get()).isEqualTo("name.txt");
        assertThat(Filenames.sanitize("dir\\sub\\name.txt").get()).isEqualTo("name.txt");
    }

    @Test
    void sanitize_rejectsTraversal() {
        assertThat(Filenames.sanitize("../../etc/passwd").getErr()
                .isErrorType(FacilityErrorType.FILE_NAME_INVALID)).isTrue();
    }

    @Test
    void sanitize_replacesUnsafeChars() {
        assertThat(Filenames.sanitize("a<b>c:d\"e|f?g*h.txt").get()).isEqualTo("a_b_c_d_e_f_g_h.txt");
    }

    @Test
    void sanitize_blank_returnsInvalid() {
        assertThat(Filenames.sanitize("   ").getErr()
                .isErrorType(FacilityErrorType.FILE_NAME_INVALID)).isTrue();
        assertThat(Filenames.sanitize(null).getErr()
                .isErrorType(FacilityErrorType.FILE_NAME_INVALID)).isTrue();
    }

    @Test
    void extension_extractsWithoutDot_orNull() {
        assertThat(Filenames.extension("photo.JPG")).isEqualTo("JPG");
        assertThat(Filenames.extension("README")).isNull();
    }

    @Test
    void nameWithoutExtension_boundaries() {
        assertThat(Filenames.nameWithoutExtension("photo.jpg")).isEqualTo("photo");
        assertThat(Filenames.nameWithoutExtension(".gitignore")).isEqualTo(".gitignore");
        assertThat(Filenames.nameWithoutExtension("plain")).isEqualTo("plain");
    }

    @Test
    void isDangerousExtension_caseInsensitive() {
        assertThat(Filenames.isDangerousExtension("virus.EXE")).isTrue();
        assertThat(Filenames.isDangerousExtension("safe.txt")).isFalse();
        assertThat(Filenames.isDangerousExtension("noext")).isFalse();
    }

    @Test
    void checkExtension_varargs_caseInsensitive() {
        assertThat(Filenames.checkExtension("a.PNG", "jpg", "png")).isTrue();
        assertThat(Filenames.checkExtension("a.gif", "jpg", "png")).isFalse();
        assertThat(Filenames.checkExtension("noext", "jpg")).isFalse();
    }

    @Test
    void checkExtension_set_caseInsensitive() {
        assertThat(Filenames.checkExtension("a.WebP", Set.of("webp"))).isTrue();
        assertThat(Filenames.checkExtension("a.webp", (Set<String>) null)).isFalse();
    }
}
```

- [ ] **Step 4: 迁移 PathIoSizeVisitorTest.java(标准命令)**

`$SRC\src\test\java\cn\hbads\beacon\facility\io\PathIoSizeVisitorTest.java` → `$DST\src\test\java\cn\code91\facility\io\PathIoSizeVisitorTest.java`

- [ ] **Step 5: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol`(Zipping/PathIo/Filenames)

- [ ] **Step 6: 迁移 3 个主源文件(标准命令,零编辑)+ 写入 2 个 package-info**

1. `$SRC\...\io\PathIo.java` → `$DST\src\main\java\cn\code91\facility\io\PathIo.java`
2. `$SRC\...\io\Zipping.java` → `$DST\...\io\Zipping.java`
3. `$SRC\...\path\Filenames.java` → `$DST\src\main\java\cn\code91\facility\path\Filenames.java`

io/package-info.java(全新,源无依赖声明):

```java
/**
 * <h2>cn.code91.facility.io</h2>
 *
 * <p><b>Purpose:</b> Recursive {@code Path} operations that carry real logic —
 * directory tree deletion, best-effort directory sizing ({@code PathIo}), and
 * failure-tolerant zip packing ({@code Zipping}). Thin {@code java.nio.file.Files}
 * wrappers are deliberately NOT provided (callers use the JDK directly).</p>
 *
 * <p><b>Entry classes:</b> {@code PathIo}, {@code Zipping}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result}), {@code log} ({@code Zipping} logs skipped entries).</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.io;
```

path/package-info.java(全新,源无依赖声明):

```java
/**
 * <h2>cn.code91.facility.path</h2>
 *
 * <p><b>Purpose:</b> Filename safety — path-traversal defence ({@code sanitize}),
 * extension extraction and allow/deny checks. Pure string manipulation, no IO.</p>
 *
 * <p><b>Entry classes:</b> {@code Filenames}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result}, Spring core
 * ({@code StringUtils} path cleaning).</p>
 *
 * <p><b>Depended on by:</b> {@code web} ({@code SafeUpload} defence-in-depth,
 * arriving in P6), downstream application code.</p>
 */
package cn.code91.facility.path;
```

- [ ] **Step 7: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 401`(379 + 22),0 失败

- [ ] **Step 8: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 io/path 簇(PathIo/Zipping/Filenames),补 20 个行为测试

zip-slip 复核结论:不适用(只压不解);package-info 按实况补依赖声明。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 3: mime 簇迁移

**Files:**
- Test(新写): `$DST\src\test\java\cn\code91\facility\mime\MimeTypingTest.java`
- Create(迁移): `MimeTyping.java` → `$DST\src\main\java\cn\code91\facility\mime\`
- Create(全新内容): `package-info.java`

**Interfaces:**
- Consumes: P1 error/result;tika-core(optional——本模块自身测试可见)
- Produces:`MimeTyping.detect(File|InputStream|byte[]|InputStream+filename)/detectByName/getExtensionByMimeType/isImage/isDocument/isVideo/isAudio/isMimeTypeAllowed`、`FALLBACK` 常量(P6 SafeUpload/HttpFileResponses 消费)

- [ ] **Step 1: 新写 MimeTypingTest.java(8 用例)**

```java
package cn.code91.facility.mime;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MimeTyping - magic-bytes MIME 探测")
class MimeTypingTest {

    /** PNG 魔数 + 最小头部 */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0
    };

    @TempDir
    Path tempDir;

    private File pngFile() throws Exception {
        Path p = tempDir.resolve("img.bin"); // 故意不用 .png 扩展名——验证按内容探测
        Files.write(p, PNG_MAGIC);
        return p.toFile();
    }

    @Test
    void detectBytes_pngMagic_detectedByContent() {
        assertThat(MimeTyping.detect(PNG_MAGIC)).isEqualTo("image/png");
    }

    @Test
    void detectBytes_nullOrEmpty_fallback() {
        assertThat(MimeTyping.detect((byte[]) null)).isEqualTo(MimeTyping.FALLBACK);
        assertThat(MimeTyping.detect(new byte[0])).isEqualTo(MimeTyping.FALLBACK);
    }

    @Test
    void detectFile_pngWithoutExtension_detectedByContent() throws Exception {
        assertThat(MimeTyping.detect(pngFile()).get()).isEqualTo("image/png");
    }

    @Test
    void detectFile_missing_returnsFileNotFound() {
        File ghost = tempDir.resolve("ghost.bin").toFile();
        assertThat(MimeTyping.detect(ghost).getErr()
                .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
        assertThat(MimeTyping.detect((File) null).getErr()
                .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
    }

    @Test
    void detectByName_usesExtensionOnly() {
        assertThat(MimeTyping.detectByName("doc.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void detectStreamWithFilenameHint() {
        String mime = MimeTyping.detect(new ByteArrayInputStream(PNG_MAGIC), "anything.bin");
        assertThat(mime).isEqualTo("image/png");
    }

    @Test
    void getExtensionByMimeType_knownUnknownBlank() {
        assertThat(MimeTyping.getExtensionByMimeType("image/png")).contains(".png");
        assertThat(MimeTyping.getExtensionByMimeType("application/x-no-such-type-zzz")).isEmpty();
        assertThat(MimeTyping.getExtensionByMimeType("  ")).isEmpty();
        assertThat(MimeTyping.getExtensionByMimeType(null)).isEmpty();
    }

    @Test
    void predicates_imageAndAllowList() throws Exception {
        File png = pngFile();
        assertThat(MimeTyping.isImage(png)).isTrue();
        assertThat(MimeTyping.isDocument(png)).isFalse();
        assertThat(MimeTyping.isMimeTypeAllowed(png, Set.of("image/png"))).isTrue();
        assertThat(MimeTyping.isMimeTypeAllowed(png, Set.of("application/pdf"))).isFalse();
        assertThat(MimeTyping.isMimeTypeAllowed(png, Set.of())).isTrue();
    }
}
```

- [ ] **Step 2: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol: variable MimeTyping`

- [ ] **Step 3: 迁移 MimeTyping.java(标准命令,零编辑);写入 package-info**

`$SRC\...\mime\MimeTyping.java` → `$DST\src\main\java\cn\code91\facility\mime\MimeTyping.java`

package-info.java(全新):

```java
/**
 * <h2>cn.code91.facility.mime</h2>
 *
 * <p><b>Purpose:</b> Content-based (magic-bytes) MIME detection and file-type
 * predicates via Apache Tika. All modules needing type identification take it
 * from here — direct tika-core dependencies elsewhere are forbidden (ADR-0001).</p>
 *
 * <p><b>Entry classes:</b> {@code MimeTyping}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result}; tika-core (Maven
 * {@code optional} — consumers needing this package add it explicitly, ADR-0001).</p>
 *
 * <p><b>Depended on by:</b> {@code web} ({@code SafeUpload} / {@code HttpFileResponses},
 * arriving in P6), downstream application code.</p>
 */
package cn.code91.facility.mime;
```

- [ ] **Step 4: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 409`(401 + 8),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 mime 簇(MimeTyping),补 8 个行为测试(原零测试盲区)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 4: json 簇迁移

**Files:**
- Create(迁移): 3 个测试 → `$DST\src\test\java\cn\code91\facility\json\{JsonsRegistryTest,JsonUtilTest}.java`、`...\json\support\InputStreamSerializerTest.java`
- Test(新写): `...\json\support\TypeRefTest.java` / `InputStreamDeserializerTest.java`
- Create(迁移): 7 个主文件 → `Jsons/JsonUtil/JsonsRegistry` + `support\{InputStreamDeserializer,InputStreamSerializer,JsonConfig,TypeRef}`(标准命令,零编辑)
- Create(全新内容): `json\package-info.java`(源漏 log 依赖)

**Interfaces:**
- Consumes: P1 error/result;P2 log(Jsons→LogUtil);jackson 四件套
- Produces:`Jsons`(实例,全 Result API)/`JsonUtil`(静态门面+registry())/`JsonsRegistry`(4 内置 namespace+register)/`JsonConfig`(standard/prettyPrint/strict/canonical/withDateFormat 预设 Builder)/`TypeRef.ofList/ofSet/ofMap/ofStringMap/ofStringListMap/ofListStringMap/of`/InputStream 序列化对(T7 装配消费 JsonUtil.registry())

- [ ] **Step 1: 迁移 3 个测试(标准命令)**

1. `$SRC\src\test\java\cn\hbads\beacon\facility\json\JsonsRegistryTest.java` → `$DST\src\test\java\cn\code91\facility\json\JsonsRegistryTest.java`
2. `$SRC\...\json\JsonUtilTest.java` → 同布局
3. `$SRC\...\json\support\InputStreamSerializerTest.java` → 同布局

- [ ] **Step 2: 新写 TypeRefTest.java(5 用例)**

```java
package cn.code91.facility.json.support;

import cn.code91.facility.json.Jsons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TypeRef - 泛型类型引用工厂")
class TypeRefTest {

    private final Jsons jsons = new Jsons(JsonConfig.standard().build());

    @Test
    void ofList_deserializesTypedList() {
        List<Integer> list = jsons.deserialize("[1,2,3]", TypeRef.ofList(Integer.class)).get();
        assertThat(list).containsExactly(1, 2, 3);
    }

    @Test
    void ofSet_deserializesTypedSet() {
        var set = jsons.deserialize("[1,2,2,3]", TypeRef.ofSet(Integer.class)).get();
        assertThat(set).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void ofMap_deserializesTypedMap() {
        Map<String, Integer> map = jsons.deserialize(
                "{\"a\":1,\"b\":2}", TypeRef.ofMap(String.class, Integer.class)).get();
        assertThat(map).containsEntry("a", 1).containsEntry("b", 2);
    }

    @Test
    void ofStringListMap_nestedGenerics() {
        Map<String, List<Integer>> map = jsons.deserialize(
                "{\"xs\":[1,2]}", TypeRef.ofStringListMap(Integer.class)).get();
        assertThat(map.get("xs")).containsExactly(1, 2);
    }

    @Test
    void ofClass_plainType() {
        Integer value = jsons.deserialize("42", TypeRef.of(Integer.class)).get();
        assertThat(value).isEqualTo(42);
    }
}
```

- [ ] **Step 3: 新写 InputStreamDeserializerTest.java(4 用例)**

```java
package cn.code91.facility.json.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

@DisplayName("InputStreamDeserializer - Base64 → InputStream")
class InputStreamDeserializerTest {

    private static ObjectMapper mapperWithStreamModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(InputStream.class, new InputStreamSerializer());
        module.addDeserializer(InputStream.class, new InputStreamDeserializer());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    void validBase64_decodesToOriginalBytes() throws Exception {
        String json = "\"" + Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)) + "\"";
        InputStream in = mapperWithStreamModule().readValue(json, InputStream.class);
        assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void emptyString_yieldsEmptyStream() throws Exception {
        InputStream in = mapperWithStreamModule().readValue("\"\"", InputStream.class);
        assertThat(in.readAllBytes()).isEmpty();
    }

    @Test
    void invalidBase64_throwsIOException() {
        assertThatIOException().isThrownBy(() ->
                mapperWithStreamModule().readValue("\"@@not-base64@@\"", InputStream.class));
    }

    @Test
    void roundTrip_serializerThenDeserializer() throws Exception {
        ObjectMapper mapper = mapperWithStreamModule();
        byte[] payload = {1, 2, 3, 4, 5};
        String json = mapper.writeValueAsString(new ByteArrayInputStream(payload));
        InputStream back = mapper.readValue(json, InputStream.class);
        assertThat(back.readAllBytes()).isEqualTo(payload);
    }
}
```

- [ ] **Step 4: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol`(Jsons/JsonUtil/JsonsRegistry/TypeRef/...)

- [ ] **Step 5: 迁移 7 个主文件(标准命令,零编辑)+ 写入 json/package-info.java**

1. `$SRC\...\json\Jsons.java` → `$DST\src\main\java\cn\code91\facility\json\Jsons.java`
2. `$SRC\...\json\JsonUtil.java` → 同布局
3. `$SRC\...\json\JsonsRegistry.java` → 同布局
4. `$SRC\...\json\support\InputStreamDeserializer.java` → `...\json\support\`
5. `$SRC\...\json\support\InputStreamSerializer.java` → 同布局
6. `$SRC\...\json\support\JsonConfig.java` → 同布局
7. `$SRC\...\json\support\TypeRef.java` → 同布局

json/package-info.java(全新——源"Depends on: result, error"漏 log):

```java
/**
 * <h2>cn.code91.facility.json</h2>
 *
 * <p><b>Purpose:</b> JSON serialization as a first-class citizen — instance
 * {@code Jsons} (all methods return {@code Result}), multi-namespace
 * {@code JsonsRegistry}, zero-config static facade {@code JsonUtil}, mapper
 * presets ({@code JsonConfig}), generic type factories ({@code TypeRef}), and
 * Base64 {@code InputStream} (de)serializers. In Spring apps the default
 * namespace reuses Spring's auto-configured {@code ObjectMapper} so controller
 * output and {@code JsonUtil} output never diverge.</p>
 *
 * <p><b>Entry classes:</b> {@code JsonUtil}, {@code Jsons}, {@code JsonsRegistry},
 * {@code JsonConfig}, {@code TypeRef}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (error channel),
 * {@code log} ({@code Jsons} failure logging), Jackson.</p>
 *
 * <p><b>Depended on by:</b> {@code web} (arriving in P6), {@code autoconfigure}
 * ({@code FacilityJsonAutoConfiguration}), downstream application code.</p>
 */
package cn.code91.facility.json;
```

- [ ] **Step 6: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 435`(409 + 26),0 失败

- [ ] **Step 7: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 json 簇(Jsons/JsonUtil/JsonsRegistry/support 四件),补 9 个盲区测试

TypeRef/InputStreamDeserializer 原零测试;package-info 补漏 log 依赖。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 5: copy 簇迁移(autoCopy 行为测试先行)

**Files:**
- Create(迁移): `$DST\src\test\java\cn\code91\facility\copy\CopyUtilNullCopyTest.java`
- Test(新写): `CopyUtilAutoCopyTest.java`(同目录)
- Create(迁移): `CopyUtil.java`(零结构改动——拆分属 T6)/ `CopyField.java` / `CopyTrait.java`(1 处 @see 编辑)→ `$DST\src\main\java\cn\code91\facility\copy\`
- Create(全新内容): `package-info.java`(源"nothing"失实,实依赖 common+log)

**Interfaces:**
- Consumes: P1 common(Collects/NullSafe);P2 log(validateCopied 的 FQN LogUtil.warn——标准替换自动处理)
- Produces:`CopyUtil.copyList×4/copySet×4/copyMapValues×2/copyMapAll×2/autoCopy`、嵌套 `CopyOptions`(builder: skipNullElements/throwOnNullCopy/warnOnNullCopy)与 `CopyException`;`CopyTrait<S>` 接口;`@CopyField(ignore)`

- [ ] **Step 1: 迁移 CopyUtilNullCopyTest.java(标准命令)**

`$SRC\src\test\java\cn\hbads\beacon\facility\copy\CopyUtilNullCopyTest.java` → `$DST\src\test\java\cn\code91\facility\copy\CopyUtilNullCopyTest.java`

- [ ] **Step 2: 新写 CopyUtilAutoCopyTest.java(10 用例——autoCopy 原零测试,T6 拆分前钉住行为)**

```java
package cn.code91.facility.copy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("CopyUtil.autoCopy - 反射自动深拷贝(拆分前行为钉桩)")
class CopyUtilAutoCopyTest {

    static class Leaf implements CopyTrait<Leaf> {
        String tag;

        Leaf() {}

        Leaf(String tag) { this.tag = tag; }

        @Override
        public Leaf copy() {
            return new Leaf(tag);
        }
    }

    static class Base {
        String inherited;
    }

    static class Rich extends Base {
        String name;
        int count;
        int[] numbers;
        Leaf leaf;
        List<Leaf> leaves = new ArrayList<>();
        Map<String, Leaf> leafMap = new HashMap<>();

        @CopyField(ignore = true)
        String ignored;
    }

    static class NoDefaultCtor {
        final String v;

        NoDefaultCtor(String v) { this.v = v; }
    }

    private static Rich sample() {
        Rich r = new Rich();
        r.inherited = "sup";
        r.name = "n";
        r.count = 7;
        r.numbers = new int[]{1, 2, 3};
        r.leaf = new Leaf("L");
        r.leaves.add(new Leaf("a"));
        r.leafMap.put("k", new Leaf("v"));
        r.ignored = "cache";
        return r;
    }

    @Test
    void null_returnsNull() {
        assertThat(CopyUtil.<Rich>autoCopy(null)).isNull(); // 类型见证:无约束泛型在 assertThat 重载间歧义(勘误)
    }

    @Test
    void plainFields_copied() {
        Rich copy = CopyUtil.autoCopy(sample());
        assertThat(copy.name).isEqualTo("n");
        assertThat(copy.count).isEqualTo(7);
    }

    @Test
    void inheritedSuperclassFields_copied() {
        assertThat(CopyUtil.autoCopy(sample()).inherited).isEqualTo("sup");
    }

    @Test
    void ignoredAnnotatedField_skipped() {
        assertThat(CopyUtil.autoCopy(sample()).ignored).isNull();
    }

    @Test
    void copyTraitField_deepCopied() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        assertThat(copy.leaf.tag).isEqualTo("L");
        assertThat(copy.leaf).isNotSameAs(source.leaf);
    }

    @Test
    void primitiveArray_clonedIndependently() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        copy.numbers[0] = 99;
        assertThat(source.numbers[0]).isEqualTo(1);
    }

    @Test
    void copyTraitCollection_deepCopiedPerElement() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        assertThat(copy.leaves).hasSize(1);
        assertThat(copy.leaves.get(0)).isNotSameAs(source.leaves.get(0));
        assertThat(copy.leaves.get(0).tag).isEqualTo("a");
    }

    @Test
    void copyTraitMapValues_deepCopied() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        assertThat(copy.leafMap.get("k")).isNotSameAs(source.leafMap.get("k"));
        assertThat(copy.leafMap.get("k").tag).isEqualTo("v");
    }

    @Test
    void missingNoArgConstructor_throwsCopyException() {
        assertThatExceptionOfType(CopyUtil.CopyException.class)
                .isThrownBy(() -> CopyUtil.autoCopy(new NoDefaultCtor("x")))
                .withMessageContaining("no-arg constructor");
    }

    @Test
    void repeatedCalls_useCachedMetaConsistently() {
        Rich first = CopyUtil.autoCopy(sample());
        Rich second = CopyUtil.autoCopy(sample());
        assertThat(first.name).isEqualTo(second.name);
        assertThat(first.leaves.get(0).tag).isEqualTo(second.leaves.get(0).tag);
    }
}
```

- [ ] **Step 3: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol`(CopyUtil/CopyTrait/CopyField)

- [ ] **Step 4: 迁移 3 个主文件(标准命令);CopyTrait 执行 1 处编辑;写入 package-info**

1. `$SRC\...\copy\CopyUtil.java` → `$DST\src\main\java\cn\code91\facility\copy\CopyUtil.java`(**零结构改动**)
2. `$SRC\...\copy\CopyField.java` → 同布局
3. `$SRC\...\copy\CopyTrait.java` → 同布局,然后编辑(stele 时代残留 @see)—— old:

```java
 * @see cn.hbads.support.common.structure.StructUtil#copyList(java.util.List)
```

new:

```java
 * @see CopyUtil#copyList(java.util.List)
```

package-info.java(全新——源"Depends on: nothing"失实):

```java
/**
 * <h2>cn.code91.facility.copy</h2>
 *
 * <p><b>Purpose:</b> Deep-copy infrastructure — the {@code CopyTrait} contract,
 * {@code CopyUtil} collection deep-copy API (list / set / map, with
 * {@code CopyOptions} null-handling policy) and reflection-based
 * {@code autoCopy} with per-class metadata caching ({@code @CopyField} opt-out).</p>
 *
 * <p><b>Entry classes:</b> {@code CopyUtil}, {@code CopyTrait}, {@code CopyField}.</p>
 *
 * <p><b>Depends on:</b> {@code common} ({@code Collects} capacity math,
 * {@code NullSafe} emptiness checks), {@code log} (null-copy warnings).</p>
 *
 * <p><b>Depended on by:</b> downstream application entities requiring deep copies.</p>
 */
package cn.code91.facility.copy;
```

- [ ] **Step 5: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 447`(435 + 12),0 失败

- [ ] **Step 6: Commit(PowerShell 工具;迁移 commit——拆分在 T6 独立提交)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 copy 簇(CopyUtil/CopyTrait/CopyField),autoCopy 补 10 个行为钉桩测试

CopyTrait 修正 stele 残留 @see;package-info 据实声明 common/log 依赖;
CopyUtil 拆分属下一提交(迁移/rework 分离)。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 6: CopyUtil 拆分 rework(独立 commit)

**Files:**
- Create: `$DST\src\main\java\cn\code91\facility\copy\AutoCopyEngine.java`(包私有)
- Modify: `$DST\src\main\java\cn\code91\facility\copy\CopyUtil.java`

**Interfaces:**
- Consumes: T5 的 12 个 copy 测试(行为钉桩——拆分后必须原样全绿,断言零改动)
- Produces: 公共 API 面**零变化**;CopyUtil 缩至 ~470 行(API+CopyOptions+CopyException+集合内部),AutoCopyEngine ~360 行(反射机件)

**拆分规格(精确成员清单):**

从 CopyUtil **整体移动**到新文件 `AutoCopyEngine.java` 的成员(代码逐字搬移,仅按下述调整):
- 字段 `AUTO_COPY_CACHE`(ClassValue 匿名类,含 javadoc)
- 方法 `buildClassCopyMeta` / `determineAutoCopyStrategy` / `isCopyTraitType` / `deepCopyFieldValue` / `cloneArray` / `deepCopyCopyTraitArray` / `deepCopyCopyTraitCollection` / `deepCopyCopyTraitMapValues` / `deepCopyCopyTraitMapAll`(含各自 javadoc 与分节注释)
- 枚举 `AutoCopyStrategy`、类 `ClassCopyMeta` / `FieldCopyMeta`

AutoCopyEngine 骨架(成员填入其中;`copy` 方法体 = 原 autoCopy 方法体去掉 null 检查后原样):

```java
package cn.code91.facility.copy;

import cn.code91.facility.common.Collects;

import java.lang.reflect.*;
import java.util.*;

/**
 * <b>反射自动深拷贝引擎(包私有)</b>
 * <p>{@link CopyUtil#autoCopy} 的实现机件:类元数据 ClassValue 缓存、字段策略推断、
 * 各容器形态的深拷贝。从 CopyUtil 拆出(P4,spec §5 行 16——837 行双职责),
 * 公共入口与异常类型仍在 {@link CopyUtil}。</p>
 */
final class AutoCopyEngine {

    private AutoCopyEngine() { throw new UnsupportedOperationException(); }

    @SuppressWarnings("unchecked")
    static <T> T copy(T source) {
        Class<?> clazz = source.getClass();
        ClassCopyMeta meta = AUTO_COPY_CACHE.get(clazz);

        try {
            T target = (T) meta.constructor.newInstance();
            for (FieldCopyMeta fieldMeta : meta.fields) {
                Object value = fieldMeta.field.get(source);
                if (value == null) {
                    continue;
                }
                fieldMeta.field.set(target, deepCopyFieldValue(value, fieldMeta.strategy));
            }
            return target;
        } catch (CopyUtil.CopyException e) {
            throw e;
        } catch (Exception e) {
            throw new CopyUtil.CopyException("autoCopy failed for type: " + clazz.getName(), e);
        }
    }

    // …(上述成员逐字填入;CopyException 引用改为 CopyUtil.CopyException)…
}
```

CopyUtil 侧改动:
- `autoCopy` 方法体替换为委托 —— old(方法体两段):

```java
        if (source == null) {
            return null;
        }

        Class<?> clazz = source.getClass();
        ClassCopyMeta meta = AUTO_COPY_CACHE.get(clazz);

        try {
            T target = (T) meta.constructor.newInstance();
            for (FieldCopyMeta fieldMeta : meta.fields) {
                Object value = fieldMeta.field.get(source);
                if (value == null) {
                    continue;
                }
                fieldMeta.field.set(target, deepCopyFieldValue(value, fieldMeta.strategy));
            }
            return target;
        } catch (CopyException e) {
            throw e;
        } catch (Exception e) {
            throw new CopyException("autoCopy failed for type: " + clazz.getName(), e);
        }
```

new:

```java
        if (source == null) {
            return null;
        }
        return AutoCopyEngine.copy(source);
```

- 删除已搬移的全部成员与其分节注释;`autoCopy` 的 `@SuppressWarnings("unchecked")` 随方法体简化删除;清理不再使用的 import(`java.lang.reflect.*` 等——以编译通过为准);`buildClassCopyMeta` 内抛出的 `CopyException` 在引擎侧写作 `CopyUtil.CopyException` —— 异常类型不变(测试 `withMessageContaining("no-arg constructor")` 原样通过)。

- [ ] **Step 1: 执行拆分(上述规格)**
- [ ] **Step 2: 验证(纯结构 rework——12 个 copy 测试断言零改动全绿)** Run mvn test → `BUILD SUCCESS`,`Tests run: 447`,0 失败
- [ ] **Step 3: 行数核查** `(Get-Content src\main\java\cn\code91\facility\copy\CopyUtil.java).Count` 与 AutoCopyEngine 同——两者均应 < 520 行,记入报告
- [ ] **Step 4: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
refactor: CopyUtil 拆分——反射引擎移出为包私有 AutoCopyEngine(spec §5 行 16)

公共 API 面零变化(CopyOptions/CopyException 保持嵌套);
12 个行为钉桩测试断言零改动全绿。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 7: Json 装配迁移

**Files:**
- Create(迁移): `$DST\src\test\java\cn\code91\facility\autoconfigure\FacilityJsonAutoConfigurationTest.java`
- Create(迁移): `$DST\src\main\java\cn\code91\facility\autoconfigure\FacilityJsonAutoConfiguration.java`(标准命令,零编辑——json 无配置前缀)
- Modify: `$DST\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports`(追加 1 行)

**Interfaces:**
- Consumes: T4 的 JsonUtil.registry()/Jsons/JsonsRegistry
- Produces: Spring 应用中默认 namespace 复用 Spring ObjectMapper;`JsonsRegistry` bean 可注入

- [ ] **Step 1: 迁移测试(标准命令)**

`$SRC\src\test\java\cn\hbads\beacon\facility\autoconfigure\FacilityJsonAutoConfigurationTest.java` → `$DST\src\test\java\cn\code91\facility\autoconfigure\FacilityJsonAutoConfigurationTest.java`

- [ ] **Step 2: 验证"红"** Run mvn test → `BUILD FAILURE`,`cannot find symbol: class FacilityJsonAutoConfiguration`

- [ ] **Step 3: 迁移装配类(标准命令);imports 文件追加**

`$SRC\...\autoconfigure\FacilityJsonAutoConfiguration.java` → `$DST\src\main\java\cn\code91\facility\autoconfigure\FacilityJsonAutoConfiguration.java`

imports 文件编辑 —— old:

```
cn.code91.facility.autoconfigure.FacilityIdAutoConfiguration
```

new:

```
cn.code91.facility.autoconfigure.FacilityIdAutoConfiguration
cn.code91.facility.autoconfigure.FacilityJsonAutoConfiguration
```

- [ ] **Step 4: 验证"绿"** Run mvn test → `BUILD SUCCESS`,`Tests run: 450`(447 + 3),0 失败

- [ ] **Step 5: Commit(PowerShell 工具)**

```powershell
git -C D:\Yiwer\code\server-facility add src
git -C D:\Yiwer\code\server-facility commit -m @'
feat: 迁移 Json 装配(FacilityJsonAutoConfiguration + imports 注册)

默认 namespace 复用 Spring ObjectMapper,门面与 controller 出口一致。

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 8: spec §5 改判勘误(convert / Wrapped* 落定)

**Files:**
- Modify: `$DST\docs\superpowers\specs\2026-07-02-server-facility-migration-design.md`(2 处)

- [ ] **Step 1: 编辑 1(structure 行,C2 落定)** —— old:

```markdown
| 3 | `structure` | keep+review | `Tuple`/`Triple` keep(别名精简同上);`WrappedContainer`/`WrappedDataType` 复核必要性与去向(C2 断环要求其迁出 structure:并入 copy 簇 / 独立子包 / drop,复核时定) |
```

new:

```markdown
| 3 | `structure` | keep+review | `Tuple`/`Triple` keep(别名精简同上);`WrappedContainer`/`WrappedDataType` **复核结论:drop**(P4 定案——beacon 全仓零消费方,C2 环随之闭合;详见 P4 计划复核结论) |
```

- [ ] **Step 2: 编辑 2(convert 行,改判)** —— old:

```markdown
| 15 | `convert` | keep+review | 基于 Spring ConversionService;以"脚手架 API"标准复核 `TypeConverter`/`BidirectionalConverter` 的必要性与易用性 |
```

new:

```markdown
| 15 | `convert` | ~~keep+review~~ **drop**(P4 改判) | 复核实况:两接口是 fromDb/toDb 数据库契约(非 ConversionService,原描述失实),beacon 全仓零消费方;未来 database 模块出现时随模块重建 |
```

- [ ] **Step 3: 全量回归 + Commit(PowerShell 工具)**

Run mvn test → `BUILD SUCCESS`,`Tests run: 450`

```powershell
git -C D:\Yiwer\code\server-facility add docs
git -C D:\Yiwer\code\server-facility commit -m @'
docs: spec §5 改判勘误——convert 改 drop;WrappedContainer/WrappedDataType 落定 drop(C2 闭合)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## 验收清单(P4 出口)

- [ ] `mvn test` 全绿,450 个用例(P3 379 + io/path 22 + mime 8 + json 26 + copy 12 + 装配 3),0 失败 0 跳过
- [ ] `src` 无 `convert`/`WrappedContainer`/`WrappedDataType`/`coordinate` 目录或文件(drop 清单执行)
- [ ] CopyUtil 拆分完成:授权 13 成员全部移出(实测 CopyUtil 548 / AutoCopyEngine 309——初版"<520"系计划估算失准,勘误;548 = API+CopyOptions+CopyException+集合内部的自然体量,不为凑数越权裁剪);copy 公共 API 面与迁移前一致(嵌套 CopyOptions/CopyException 原位)
  (测量注记:PowerShell 5.1 `Get-Content` 无 `-Encoding UTF8` 对无 BOM 中文源码错切行——行数以 git/LF 计为准)
- [ ] imports 文件恰好 2 行(Id + Json);ArchUnit 四规则绿
- [ ] 五个新迁 package-info(io/path/mime/json/copy)依赖声明与 import 实况一致
- [ ] T5 迁移与 T6 拆分为独立 commit;提交信息无 `@` 包裹
