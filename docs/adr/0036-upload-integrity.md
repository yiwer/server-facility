# ADR-0036: 有界上传、可信存储键与显式流所有权

## Status

Accepted

日期：2026-10-04。落实已批准票 13；平台验证状态见该票与最终验证报告。

## Context

SafeUpload 原实现信任 MultipartFile 的长度并直接 transferTo 用户名称，类型与大小校验彼此分离；失败可能留下半成品，同名上传可能覆盖。Tika 4 的普通 InputStream facade 不再恢复调用方流位置；吞 IOException 返回 octet-stream 会把探测失败误当识别结果。Hashing 的空数组与空文件存在历史差异，必须明示。

ADR-0001 的 optional Tika、按需调用和无新自动装配决定保留。这里收紧上传和 MIME 的流契约，不引入对象存储 SPI、全局队列或文件事务框架。

## Decision

- 保留现有 SafeUpload 签名，集中到实际字节计数的保存路径。增加 `saveFile(MultipartFile, Path, long maxSizeBytes, Set<String> allowedMimeTypes)`；正数预算必须显式成立，≤0 返回 FILE_SIZE_EXCEEDED 并附 IllegalArgumentException。旧便利方法默认 10 MiB，仅作为小文件便利上限，不作为宿主容量规划。空上传继续拒绝，真实输入最多多读取 1 字节来发现超限；拒绝/中断不 drain。
- 用户原名与 customFileName 是展示名，不再是存储键。保留危险后缀与路径穿越拒绝；由服务端生成固定长度唯一存储键。调用者须使用返回 Path，不得按原名推导路径。
- MultipartFile.getInputStream 由设施打开并关闭；借用的底层 MIME InputStream 不关闭。保存只打开一次输入，探测与保存共享同一完整内容，不丢弃探测前缀。探测是类型提示，不是内容安全扫描；精确 MIME allowlist 不自动把容器 ZIP 当成 XLSX。
- 每项操作在同卷私有暂存中完成后才发布；应用必须独占并维护存储根目录，不能将该目录作为静态资源目录。拒绝预存链接/重解析逃逸。不承诺防御同权限进程在检查后恶意替换目录。
- 发布使用 `Files.createLink(target, stage)`：Windows NTFS 已实际验证同卷、完整文件可见且目标存在时不替换。暂存输入与输出都关闭后才创建链接；删除暂存链接成功才返回 Ok。暂存 unlink 失败回滚本次新目标，已有目标绝不删除；清理 IO 失败 suppressed 保留首因。JDK ZipFS 的不支持已实际验证为 Err 并清理，Linux 本地文件系统待 CI。不得用 ATOMIC_MOVE 推断目标一定不被替换，不支持必需语义时明确失败，不退化为对成品直接复制。不承诺任意 provider、断电持久性或跨系统事务。
- toTempFile 成功返回的文件由调用方删除，不使用 deleteOnExit 的进程全局登记；失败清理自有暂存并保留清理失败的原因。
- Hashing 保留 byte[] 空/null 返回 FILE_READ_ERROR、空 File 计算标准摘要的旧差异；算法失败进入 FILE_HASH_ERROR。MD5/SHA-1 只供旧非安全协议兼容；不可用于密码存储或对抗恶意篡改的完整性声明。
- Tika 固定 4.1.0 optional、只使用 MimeTypes core detector，不扫描 classpath detector 或解析容器。借用流至多探测 64 KiB，必须支持 mark/reset；不可 mark 在读取前返回 Err，调用者保留自己的 BufferedInputStream。旧 mark 被替换，reset 失败不能宣称位置恢复；主故障保留，reset 故障 suppressed。旧 String 返回类型的 IO 故障改为 UncheckedIOException；非检查程序故障清理后传播。

| 保留资源 | 单操作预算 / 生命周期 | 宿主责任 |
|---|---|---|
| 上传字节 / 临时磁盘 | 正数 N；输入至多 N+1 发现超限，stage 最多 N，双硬链接共享同一数据；默认 N=10 MiB | 总磁盘配额、并发、成功文件保留政策 |
| MIME 前缀 / 内存 | 64 KiB 前缀、至多约 64 KiB replay buffer、8 KiB copy 与 Tika 固定目录；与正文总量无关 | Tika 依赖与类型政策，不当安全扫描 |
| 临时文件 | 每操作 1 stage；失败清理，成功成品/临时结果交给调用方；不登记 deleteOnExit | OS 拒绝删除时的私有目录恢复；硬链接能力 |
| 线程 / 等待 | 不创建线程、池或全局队列；每次读检查中断，底层阻塞 I/O 必须协作 | 请求超时、任务取消、并发 admission |
| Hashing | File 固定读缓冲；byte[] 不复制，不新增总大小政策 | 文件尺寸、摘要是否满足业务安全需求 |

发布完成前取消/读取/关闭失败均不交出成功目标。取消不是跨系统事务，不能撤销已经成功返回的文件。清理只承诺尝试且保留原因，不声称能突破操作系统删除权限或共享锁；Windows 实际占用场景验证了该边界。

## Consequences

**Positive**：大小、类型、输入完整性和失败清理在同一路径兑现；同名并发无需调用者协调。

**Negative**：生成存储键、有限默认预算和显式临时清理是可见迁移。文件系统需满足声明的发布能力；可用性不通过降低安全语义换取。

**Carry-forward**：Windows/Linux 链接及提交证据分别记录；票 24 复核目标 Boot 4/Jackson 3 和 optional 依赖图，票 31 接合上传到解析，票 33 在最终候选统一重验，不能以未来组合替代本票证据，也不反向新增 13 依赖 31/33。

## References

1. [Tika 4 migration](https://tika.apache.org/docs/4.1.x/migration-to-4x/migrating-to-4x.html)
2. [JDK 25 Files](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html)
3. [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
4. 本地票 13、PRD v0.2 D-06/D-07、测试策略 Q01–Q10/J12/J15/J16。
5. [Windows CreateHardLinkW：同卷 NTFS 与共享权限](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createhardlinkw)
6. [Tika 4.1.0 发布下载](https://tika.apache.org/download.html)、[Central POM](https://repo.maven.apache.org/maven2/org/apache/tika/tika-core/4.1.0/tika-core-4.1.0.pom)
