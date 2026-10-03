# 票17：历史密文读取与受约束原语验收

**2026-10-04 平台闭合**：本票适用待验证项已由`80670fa`的Windows/Ubuntu同源CI完成，状态closed。见[票24 CI证据](ticket-24-ci.md)。下文保留各次执行的来源、数值及当时状态，不以旧数字代替新环境观测。

状态：Windows目标平台实现与integration通过；同源Linux执行随票24/最终33 CI闭合。不是全量tickets或最终发布已完成的声明。

## 被测对象与环境

- 源码 `6f7f06c10aee2717a90f783fb938df1b0e31156f`，已合入集成 `d4922df`（含18/06/13）。后续本票变更仅为报告、changelog和票据。
- Windows 11 amd64；Oracle JDK25.0.4.1；Maven Wrapper固定3.10.0；UTF-8，zh_CN，Asia/Shanghai。
- 目标Boot4.1.1/Framework7.0.9/Jackson3.1.5/JUnit6.0.3；精确BOM及依赖树保存在本轮effective-pom.xml/dependency-tree.txt。crypto没有新第三方依赖。
- 执行：`java verification/Verify.java integration`，JAVA_HOME指向上述JDK，VERIFY_WRONG_JAVA_HOME指向本机JDK21负控。使用本票自己的初始空repository；命令未带`--fresh`，报告如实记fresh=false。
- 原始证据：`.verification-results/20261004-015807-675-integration/`。普通jar SHA256 `2fa7c58a20b14ddc6fd65eb5da3465f6955b9e3db44e628b132de1280d456098`。

## 本轮实际结果

`RESULT=PASS`。1442 tests，0 failures/errors/skipped；5条架构规则和依赖分析全过。JaCoCo instruction18832/20288=92.8233%，line3842/4114=93.3884%，branch1942/2279=85.2128%，未调整门槛。原25项crypto测试保留，增加9项，不删除/跳过测试。

普通jar非Web配置/覆盖/非法配置、core无框架消费者、新crypto无框架消费者、JSON构造/注入路径真实HTTP和两个应用关闭重建均通过。Wrapper校验和、缺失JDK、错误JDK三项真实负控通过。integration模式不含all/resources额外5次应用启动周期，本票不冒充已跑。

CryptoConsumer实际运行classpath仅为新安装普通jar与自己的classes，无Spring/Jackson/SLF4J/annotation runtime。64MiB/45秒子进程读取旧字面回执，验证payload、编码、口令/salt入口预算；64轮1MiB读写及失败，4个真实worker经屏障并发、各16轮256KiB；executor关闭后自然退出。标记为`CRYPTO_CONSUMER_PASS legacy=210000 max-bytes=1048576 rounds=64 workers=4 framework=absent`。

## TDD与独立样本

局部原始记录位于`.verification-results/ticket-17/`，选择当前crypto源码、公开Result/error依赖及JUnit6运行，不代替上述完整平台门。

| 轮次 | 结果与含义 |
|---|---|
| 01-legacy-before-change | 原25+历史样本2=27项绿；先证明固定旧reader，不改变期望迎合实现。 |
| 02-provider-* | 初版自建未签名JCE provider被Oracle JDK拒绝，SPI从未进入；这些是测试夹具诊断，不作为秘密泄漏的有效RED。另一次PowerShell路径regex失败也不是行为RED。保留失败记录，未绕过provider签名验证。 |
| 03-real-provider-secret-red-confirmed | 换成真实JDK AES provider访问抛秘密哨兵ProviderException的SecretKey，确认getEncoded确实进入；28项中1项因原cause泄漏失败。 |
| 03-real-provider-secret-green | 删除全部Result错误中的原始cause后28项绿；独立JVM移除实际Cipher/PBKDF2/Mac provider，三个缺席场景也安全失败，宿主registry未变。 |
| 04-truncated-red / green | 0..27字节截断envelope在原实现访问不可用key导致断言失败；按完整IV+tag最小28字节前置拒绝后29项全绿。 |
| 05-import-size-red / green | 32MiB子进程导入16MiB Base64 key：原实现在decoder分配时OOM；按最大44字符前置拒绝后30项全绿，100次重复无额外解码。 |
| 06-mutations-compatibility | 34项全绿；seed170040逐字节覆盖IV/ciphertext/tag并追加512次bit变异，所有截断、错误密码/salt、合法padding/无padding AES三种key长度和程序Error传播。 |

历史properties的SHA256仍为`71d444e88fbd7f0e6e656a8d02c2834df562e3bf49bd9eaad170a22c5e5b876f`。旧producer普通jar SHA256 `61a10a50d223dd760f073bf7cd915b1761aeb4766f6e9475846012d2b11f6116`；source与原始基线一致，生成和独立.NET/JCE证据见资源README及外部coordination记录。当前writer另由独立JCE reader读取，HMAC有RFC4231字面向量，不只做同一实现往返。

## 契约追踪与共同完成标准

| 要求 | 证据/适用范围 |
|---|---|
| FR-09、AC-12、Q01/Q06 | ADR0040、legacy-crypto迁移文档、历史producer样本、独立reader和普通jar业务消费。没有新envelope/KDF metadata，未知版本、可变参数降级、新格式回滚不适用。 |
| Q02 | 原25测试加CryptoInputBoundaryTest：null/empty/截断/畸形/Unicode、16/24/32key、错误salt/password；应用消费者校验输入上限和超限。旧raw入口不暗加新全局预算，仍有大历史记录读取路径。 |
| Q03 | 普通jar+JDK进程，真实JCA provider、真实并发；加密模块不涉及Servlet事务/数据库。 |
| Q04 | provider携密异常与provider缺席、安全错误通道、程序Error传播、屏障并发。同源Linux JCA行为尚待CI。不声称同步JCE取消等于终止计算。 |
| Q05 | 44字符key前置拒绝；28字节最短协议；固定210000计算参数；消费场景1MiB/1024口令单元/64字节salt/4worker/64MiB子进程。无工具拥有的流、临时文件或executor；消费executor关闭。raw API随已物化输入增长，不是任意请求的资源防线，应用须先限输入和并发。 |
| Q07 | seed170040、固定历史字面样本、RFC4231向量；不从有限样本推断无限nonce唯一或恒定时间。 |
| Q08 | 源码/环境/jar/依赖/完整命令和raw logs如上；秘密哨兵不出现在公开错误或子进程stdout/stderr；夹具错误不伪称产品RED。 |
| Q09 | 1442/0/0/0，原覆盖率/架构/依赖门全过；新增9项，既有测试保留。 |
| Q10 | 代码、ADR0040、使用/迁移文档和本报告同票；Linux由24同源CI实际闭合前保留verification-pending。 |
