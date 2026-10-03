# Ticket 13：上传完整性、MIME 与公共 Hashing 验证

本报告区分已执行的 Windows / Boot 3 中间平台证据与尚未完成的 Linux / 目标平台证据。票 13 尚不 closed；不把未来 31/33 的组合验收反向列为本票实现依赖。

## 固定源码与完整质量门

- 初始集成基线 `1c61c1b4c3539a4396794fed3c8c3f3379c7fb2c`；产品、测试与迁移 checkpoint `efea850000dc50be05970a899470c1828b62058a`。后续报告提交只修正文档/注释；目标平台同步另记于下。
- 命令 `mvnw.cmd -B -ntp clean verify`，2026-10-04 01:17:42 +08:00 完成，61 秒；**1386 tests / 0 failures / 0 errors / 0 skipped**，含原 5 条架构规则。JaCoCo 原 88%/88%/75% 门及 dependency analyze 全通过，没有 ignore 或跳过。
- 环境：Windows 11 amd64 / E: NTFS，Oracle JDK `25.0.4.1+1-LTS-5`，Maven Wrapper `3.10.0`，Asia/Shanghai、zh_CN、默认文件编码 UTF-8；Boot `3.5.16` / Spring `6.2.19` / Tomcat `10.1.55` / Servlet `6.0` / JUnit `5.12.2`。子进程 stdout/stderr 显式 UTF-8。
- 普通 jar SHA-256：`44185ba5d19e1c9112b83eb20a9a09936732e702878d85ef70be0f14a797ef70`。
- 日志 `.verification-results/ticket-13/baseline-clean-verify.log`，原始 Surefire XML / JaCoCo XML 已复制至 `baseline-artifacts/`，不会被下一次 clean 删除。依赖树、effective POM 分别为 `baseline-dependency-tree.txt` / `baseline-effective-pom.xml`。

| JaCoCo bundle | Covered / Total | 实测 | 原门槛 |
|---|---:|---:|---:|
| INSTRUCTION | 18152 / 19487 | 93.1493% | 88% |
| LINE | 3717 / 3969 | 93.6508% | 88% |
| BRANCH | 1828 / 2125 | 86.0235% | 75% |

相对初始 1323 项净增 63 项；upload/mime/hash 共 92 项通过。保留原 29 项，旧 SafeUpload 三处按用户文件名保存的断言按批准的 UUID 存储键迁移，未删测。独立 HTTP 场景是 1 个 JUnit 测试内的 8 次真实请求，不把请求数误写成测试总数。

## 契约与证据映射

测试路径均相对于 `src/test/java/cn/code91/facility/`；详细 RED/GREEN 日志位于 `.verification-results/ticket-13/`。

| 契约 | 公开测试与实际断言 | 主要证据 |
|---|---|---|
| 实际字节 / 组合政策 | `UploadByteBudgetTest`：错误/未知声明长度、空内容、4/5/6 对 5 字节预算、UTF-8、0/负/Long.MIN/MAX_VALUE；无限输入在 N+1 停止且关闭一次。`UploadTypeIntegrityTest`：类型与大小同时生效、只开一次非 mark 输入，PNG 前缀保存完整 | red-01/02/04 → green-01/02/04b；最终全门 |
| MIME 借用所有权 | `MimeStreamContractTest`：mark/reset 当前偏移恢复且不关闭，原始不可 mark 在零读取前 Err；64 KiB 停止、reset 失败、IO/runtime/Error 首因及 suppressed，不发生 self-suppression；旧 String 重载不吞 IO | red-03/17 → green-03/17；最终全门 |
| 内容与容器混淆 | PNG 内容配伪 PDF 名/媒体类型仍识别 PNG；JDK ZipOutputStream 独立构造 ZIP 配 `.xlsx`，只认 zip、xlsx allowlist 拒绝；错误不能伪装 octet-stream 满足政策；无限文本不匹配 PNG 在 64 KiB 拒绝 | `UploadTypeIntegrityTest`；compat-14 / 最终全门 |
| 展示名 / 路径 | `UploadPathContractTest`：a.txt、空/null/空白、路径穿越、CON/COM1、尾空格、路径片段、长 Unicode/emoji。允许的展示名不成为文件系统组件，返回固定 UUID.upload | red-06 → green-06；contracts-19；最终全门 |
| 真实链接逃逸 | Windows 实际 PowerShell Junction（非 mock）；目标链接及其不存在子目录都拒绝，外部 sentinel 不变。Linux 分支使用真实 symbolic link，待 CI；不以无权限 skip 通过 | `UploadPathContractTest`；最终全门 |
| 并发 / 发布 | 两线程同展示名由 barrier 同时开始，各得到完整独立文件；旧文件不覆盖；固定 UUID 碰撞不能删除已有目标；真实 ZipFS 不支持 hardlink 返回 Err 且无 stage | `UploadPublicationTest` / `UploadCommitBoundaryTest`；red-05/09 → green-05/10 |
| 关闭 / 回滚 / 首因 | 输入 close 失败不能交出成品；部分 write、output close、探测读+close 双故障；RuntimeException/UnsupportedOperationException/AssertionError 传播但清 stage；hardlink 成功后 stage unlink 失败回滚自己的新目标 | `UploadFailureContractTest` / `UploadCommitBoundaryTest`；red-10/18 → green-10/18；contracts-19 |
| 真实占用清理边界 | Windows NOSHARE_DELETE 实际占用 stage：读失败仍为首因，删除失败 suppressed，stage 明确存在；释放本测试锁后显式删除。Linux 以实际目录写权限拒绝，待 CI。没有声称清理能突破 OS 权限 | `UploadFailureContractTest`；faults-11b；最终全门 |
| 协作取消 | 真实阻塞源停在 latch，完成前没有成品；Thread.interrupt 后返回 InterruptedIOException，保留标志、只读一次、关闭一次、root 无残留。不承诺强行中断不协作流 | `UploadFailureContractTest`；faults-11b；最终全门 |
| 临时结果生命周期 | a.txt 短名可用、固定临时前缀、10 MiB 实际预算；成功交调用方删除，不用 deleteOnExit；保存/MIME 自己打开的输入关闭，独立低层 borrowed 流不关闭 | `UploadTemporaryOwnershipTest` / byte/type tests；red-07 → green-07 |
| 真实 Servlet HTTP | 复用票 04 EmbeddedServletApplication、Tomcat loopback 随机端口、生产错误策略和真实 multipart resolver。已知长度/chunked 各 0/4/5/6 UTF-8 字节，400/200/200/413，正文逐字节十六进制金样、无内部异常泄漏，parts/root 最终清空 | `UploadHttpContractTest`；http-13 / 最终全门 |
| 公共 Hashing | 保留 File/byte[] 方法；独立 abc / empty SHA-256、MD5 常量，空 File 标准值而空/null byte[] Err；未知/null 算法、目录读失败、预中断保留 flag、关闭后文件可删除 | `HashingTest` / `HashingContractTest`；red-08 → green-08；最终全门 |
| 缺 optional Tika | 独立 JVM 删除 classpath 的 tika-core：无类型保存前后可用；类型 allowlist 调用确实 NoClassDefFoundError，无成品/stage，不降级允许。保持 ADR0001 按需调用契约 | `UploadResourceContractTest` / missing-tika-child.log |

Hashing 的 MD5 查询覆盖 `src/main/java`：仅两个公共 MD5 便利方法自身，没有生产内部调用。保留其外部兼容签名并在类、package、USAGE 和 CHANGELOG 明示非安全用途，不宣称消除未知消费方的旧协议。

## 有界进程与可重放生成

`UploadResourceProcess` 是测试专用入口，使用公开 SafeUpload + Hashing。父进程要求 50 秒内自然退出，JUnit 上限 60 秒；超时强制结束。独立 JVM：`-Xmx96m -XX:MaxDirectMemorySize=16m -XX:ActiveProcessorCount=2`。先 8 MiB 预热，再 64/256 MiB 实际文件；一次最多一个 256 MiB 文件，源以固定缓冲生成 ASCII a，不分配等大数组。每次使用错误元数据 `getSize=-1` / PDF 名和媒体类型，显式精确大小 + text/plain allowlist，确认实际长度、独立 SHA-256 后删除。

预期 SHA-256 由 CPython hashlib 独立计算（2026-10-04）：64 MiB 为 `fae972222d455a2eaee1661ad9625502ec3bfc5ec38b87a6eec5afd5107331b5`；256 MiB 为 `b4a0226ee3f9b159ac06a86332dca0d90a04adef7f88934aa2a75be2a011d504`。标准空/abc 向量另由固定协议常量断言，不依赖本实现写后读作为唯一证据。

基线完整门中的进程观测：

| 时点 | GC 后 retained heap（字节） |
|---|---:|
| 8 MiB 预热后 | 11939520 |
| 64 MiB 完整上传后 | 11962240 |
| 256 MiB 完整上传后 | 11969472 |
| 100 次交替 IO/runtime 临时上传失败后 | 11971592 |

堆上限 100663296 字节，最终增长 32072 字节，小于事先登记的 16777216 字节阈值。线程 7 → 7，temporaryFiles=0；进程自然退出码 0。该证据验证输入扩张及有限重复故障，不表示任意并发/磁盘总量或整机长稳。设施不新增池/队列/连接，宿主负责 admission、I/O 超时和成功成品的保留。

性质样本为 seed `0x13b0d1`，48 个 1..4096 字节样本，可/不可 mark 交替，经组合政策保存后逐字节相等。复跑命令 `mvnw.cmd -B -ntp -Dtest=UploadTypeIntegrityTest test`。无需新的性质测试框架。

## TDD 故障记录与质量说明

逐条先公共反例 RED，再最小实现 GREEN；主要 RED 见上表。失败日志保留，未重跑覆盖：

- red-02 暴露 Long.MAX_VALUE 的 `remaining + 1` 溢出，产生无进展循环；保存当时线程栈，核对 PID 后只终止本次 Surefire。修复用不溢出的请求长度运算，测试源对零长度读 fail-fast。
- 首次 green-04 发现 BufferedInputStream 与底层双重 close；修复单一输入所有权，green-04b 通过。
- green-09 的 Mockito 默认静态 UUID mock 影响 UUID 内部调用，改 CALLS_REAL_METHODS；没有改产品以满足 fixture。
- faults-11 初次 fixture 使用 InputStream 默认 bulk-read 吞一次 IOException 后重复获取 Windows 锁；改显式 bulk-read，faults-11b 通过并释放测试锁。
- http-13 早期 servlet 注册阶段 `@Value` 未解析，日志显示字面 `${upload.parts}`；改取 Environment，最终全门确实使用独占 parts 目录。
- compat-14 三处旧展示名等于存储名的断言失败，按批准迁移改为 UUID 路径与内容断言。
- red-17 捕获 reset RuntimeException 覆盖首因及同对象 self-suppression；red-18 捕获 source UOE 被误归 provider 不支持、output close 被误归 READ，均已各自 GREEN。

## Q01–Q10 与后续责任

| 项 | 本票已执行内容与边界 |
|---|---|
| Q01 | FR-01/06/09、AC-10/12 的实际字节、所有权、错误与兼容映射上表；ADR0036 保留 ADR0001 optional 理由，无新全局策略/装配/存储 SPI |
| Q02 | 正数预算/默认/0/负/溢出、空/null、N−1/N/N+1、Unicode 名、UTF-8 字节及重复/并发；无时间/时区业务政策，故无 DST 矩阵 |
| Q03 | 公开单测 + 真实 NTFS/ZipFS + 8 次 Servlet HTTP；J12 的上传与检测部分完成，CSV consumer 失败/取消接合归 31/15 |
| Q04 | 读/写/检测/关闭/发布/cleanup 故障、同名屏障、阻塞源中断与实际占用；不以随机 sleep 证明交错。进程崩溃/断电事务不受支持且明确退出承诺 |
| Q05 | N+1 读取、N 磁盘、64 KiB sniff、固定 copy、无新增线程；96 MiB 进程规模/100 次故障稳态见上，不含宿主全局并发配额 |
| Q06 | 独立 PNG 魔数、JDK ZIP、CPython 大文件摘要、标准空/abc 摘要及旧 29 测试保留；新 storage key 与旧 MIME IO 失败通道显式迁移 |
| Q07 | 固定 seed 48 样本、有限路径/预算矩阵及 barrier；无新测试框架 |
| Q08 | 固定 SHA、依赖树、JDK/FS/时区、完整成功/失败日志归档；Windows 原生证据不冒充 Linux |
| Q09 | 1386/0/0/0，5 原架构规则、原覆盖率和依赖门通过，无删除或 skip |
| Q10 | 产品、测试、ADR、USAGE/CHANGELOG 与报告同票；必需 Linux/目标平台证据尚缺，因此不 closed |

实际解析 Tika `4.1.0` optional，传递 `commons-io 2.22.0`、`commonmark` 与两个扩展 `0.30.0`；源码只经 MimeTyping core detector，不新增解析器。Central POM 已实际获取，版本账本已更新。该包缺席的本票行为由独立 JVM 证明；普通 jar 消费和目标平台 optional 图由 24 接合，不能用本测试 classpath 代替。

尚未执行：Linux 的 hardlink/符号链接/权限拒绝分支以及目标 Boot 4/Servlet 6.1 下的完整门。由集成后 CI 与票 24 闭合；Linux runner 必须具备实际目录权限测试条件，root 权限绕过拒绝不能记为通过。J12 上传到 CSV 的所有权交接归 31，J15/J16 最终候选长稳/跨能力组合归 33，均与本票自身已执行证据分列。

## 目标平台同步

基线完成后，冷进程检查 `cold-mime-probe.log` 复现新取消缺陷：首次线程中断导致 Tika SAX parser pool 获取失败，MimeTyping 静态初始化抛 ExceptionInInitializerError，后续正常调用永久 NoClassDefFoundError。`red-20-cold-cancellation.log` 保留独立 JVM 反例；把目录加载移出类静态初始化，按需缓存且失败可重试。`green-20-cold-cancellation.log` 的 19 项初轮通过；随后扩充 byte[] 与 multipart 两条冷进程入口，最终结果另记。此修复发生在上述 1386 全门之后，不用旧全门冒充最终源码结果。

等待票 23 恢复可用集成 tip 后在 `codex/ticket-13` 合入并重新验证；不合入已明确处于编译红色的票 22 中间态后伪记通过。
