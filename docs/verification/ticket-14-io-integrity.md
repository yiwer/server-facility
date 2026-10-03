# 票14：ZIP 与目录完整性验证

2026-10-04。当前状态：Windows实现与完整integration通过，待集成同提交的Linux CI；不把已完成票24的历史CI当成本票证据。

## 候选与环境

- 被测源码 `58a1e83319a094224edc3d71fdeb2c34ef36d304`，包含09中央集成 `c32e72e86de7e4f708e0b423c18f84c955ab683a`。运行前工作树干净；后续报告提交不改变产品、测试或runner。
- Windows 11 10.0 amd64，Oracle JDK `25.0.4.1+1-LTS-5`，Asia/Shanghai、zh_CN。使用固定Wrapper与隔离Maven设置，不引用IDE classpath。
- 命令：设置JDK25 `JAVA_HOME`/PATH和真实JDK21 `VERIFY_WRONG_JAVA_HOME` 后，`java verification/Verify.java integration`。
- 完整原始证据：`.verification-results/20261004-030710-501-integration/`；TDD及先前失败：`.verification-results/ticket-14/`。`summary.txt` 为 `RESULT=PASS`。
- 普通jar SHA-256：`14bb34c0c79768a7f0f2bda7258c553c18fbc102ae0353f7490369c819713269`。未声称字节可重现构建。

## 结果

1507 tests，0 failures / errors / skipped；5项原架构规则、依赖分析通过。JaCoCo指令 `20232/21680=93.3210%`，行 `4096/4362=93.9019%`，分支 `2123/2479=85.6394%`；原88%/88%/75%门未改。

完整integration还运行默认/覆盖/非法配置消费者、core/crypto/io/rate-limit普通jar消费者、JSON两应用及金样、实际Web与上传、5个Maven生产依赖图/11个独立JVM、checksum/缺JDK/真实JDK21三项负控。此模式不额外运行all模式的5次应用重启循环；本票专属I/O资源测试已经在独立进程运行，最终33负责同候选全部组合。

I/O公开行为测试合计41项。原 `PathIoTest` 4项和 `ZippingTest` 6项保留；后者“missing仍成功”改为明确新完整性契约、目录预期新增真实子目录entry。删除2项直接调用包内SizeVisitor的实现测试，用PathIo公共入口的真实总和、访问拒绝、遍历失败、预算及极端size测试替代；未删失败契约或降低发现数量。

## 契约与场景

| 要求 | 公开测试与观察 |
|---|---|
| Q01 / FR06、FR09 / AC10、AC12 | `ZippingIntegrityTest`复现重复basename、缺失/读取失败返回假成功，现整次Err；`PathIoFailureTest`证明不可访问不返回零，遍历异常不继续删除父目录。ADR0037、USAGE与CHANGELOG明确迁移。 |
| Q02 正常与边界 | ZIP空目录、递归、Unicode；输入/输出/条目/深度N−1/N/N+1；名字1023/1024/1025 UTF-8 bytes；null/空、非法预算、Long.MAX_VALUE/负provider大小；文件系统根可统计但不可递归删除。 |
| Q03 / J13 真实接合 | 实际Windows文件系统、JDK ZipFS不支持hardlink负控、独立JDK `ZipFile`读取完整entry和内容；ordinary jar单独编译/运行，显式证明无Spring/SLF4J。 |
| Q04 并发与失败 | 两个publisher受屏障控制，发布前目标不可见，只有一个完整winner；既有target sentinel保持原样。受控NIO输入在验证后消失、读失败、读/close双失败、程序Runtime/Error；文件系统输出write/close故障；取消阻塞源及close/发布边界。 |
| Q04 路径与清理 | 实际Windows junction根外输入/输出/祖先/环全部拒绝；Windows真实NOSHARE_DELETE阻止stage清理，保留原始read失败并附suppressed，断言实际残留后释放句柄清理。Linux分支为真实POSIX目录权限拒绝，尚待CI执行。 |
| Q05 有界资源 | ZIP默认10k entries、256MiB实际读/完整写、64层、entry名字1024 UTF-8 bytes；PathIo默认10k后代、256MiB逻辑文件bytes、64层。固定8192-byte copy buffer，N+1检查，零长度read有进展；无线程池/全局路径缓存。 |
| Q06 独立兼容 | 输出由库外标准 `ZipFile`解释，核对独立预期payload及SHA-256，而非调用自有反序列化器；空ZIP的22-byte EOCD来自格式固定最小值。旧entry相对路径/UTF-8仍可读取；新增空目录与严格失败是明示变更。 |
| Q07 可重放 | `IoConsumer` seed=140037，64个不同Unicode树及payload，独立reader核对entry/count/body/完整统计；遇错日志给round，源代码随CI artifact归档。 |
| Q08 环境 | 本节给准确commit/JDK/OS/时区、artifact与命令；真实Linux路径/权限未验证，不标通过。 |
| Q09 质量门 | 原覆盖率、架构、依赖门全绿，41个I/O测试发现和旧2项内部测试替代有说明。 |
| Q10 交付 | 代码、公共Limits、迁移、ADR、测试、runner及证据同票。Linux关键语义缺证据前状态保持verification-pending。 |

## 独立进程资源数据

`verification/io-consumer/IoConsumer.java`仅用已安装普通jar；`-Xmx64m`，外部runner总deadline60秒。每次归档输入为真实32MiB、128MiB文件，输出分别220796、855140 bytes；独立reader比较完整解压字节数和源文件SHA-256。live heap分别2853736、2337456 bytes。随后20轮预热和200轮超预算失败，逐次断言stage被删除、目标不存在；全部路径最终可删除，验证句柄已释放。

200轮测量前/后live heap：2335416 / 2336552 bytes，线程数7→7，整个consumer耗时6741ms并自然退出。预先登记阈值为live heap<40MiB、相对预热增量≤8MiB、线程数不增加；不是无限输入性能保证，也不把压缩后的ZIP bytes当输入规模。

## TDD与证据修正

RED/GREEN日志按01–18保存：重复basename、缺失文件、读取故障、目标位置/覆盖、links、实际输入/最终输出bytes、entries、depth/空目录、目录访问/遍历故障、名字预算、PathIo entries/bytes/depth/cancel/links、null output/程序异常、close到publish取消等。

两处夹具失败明确剔除：初始03测试的mock NIO provider未调用JDK25默认metadata helper，根本没到read，不能证明产品读取缺陷；改为CALLS_REAL_METHODS并断言reachedRead，在原实现上重新得到 `03-read-failure-red-confirmed.log`。初始11的ZipFS输入不支持NOFOLLOW_LINKS，正例先失败，不能证明名字规则；改为支持该选项的外部provider夹具，以ZipFS合法Path表达宿主无法表示的名字，在旧源码重新得到 `11-entry-name-red-confirmed.log`。原无效日志保留，没有伪报为有效RED。

先前完整 `980e6c6` 在 `.verification-results/20261004-025948-794-integration/` 得1477全绿；随后补名字N±1/真实链接环并同步09，以当前58a1e83完整重跑得到1507，未拼接不同源码的局部结果作为最终通过。

## 保证边界

命名空间必须由应用可信拥有，链接拒绝不是恶意并发rename沙箱，也不是输入快照。只在完整ZIP关闭后hardlink不覆盖发布；不支持时失败，无copy fallback。成功归档归调用方；创建的父目录可保留，文件系统拒绝删除时也可能残留stage或完整目标，只有Result.ok是成功信号。递归删除逐项生效，失败不回滚已删除节点。取消为协作，不能强制结束拒绝响应中断的provider；已发布后的取消不会撤销成功。原始诊断可含路径，不得直接暴露HTTP detail。
