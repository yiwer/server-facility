# 票 05：有界 Web 流的 Windows 验证证据

日期：2026-10-04。下文保留实现时的Windows证据及待办快照；此后集成提交2304a57已通过[Windows/Ubuntu完整CI](ticket-05-ci.md)。当前仅待Boot4/Servlet6.1复验，故票仍为 `verification-pending`；不能把后续平台范围写成已通过。

## 固定源码、命令与质量门

- 被测源码/测试/POM 提交：`5a59d2f23f36d38f088753232d125db7e3e9878a`。实现检查点 `a2c5b4d`，传输/编码/资源补强 `e7d27d7`，依赖账目与迁移 `5a59d2f`。
- 已合入最新集成 `66bf4d04be3b40a5d81a80fa418cbe213c2307f4`，合并提交 `15bc0c7`。复用票 04 的 `EmbeddedServletApplication`（52daa5e → 本票536b8ce）与错误策略检查点（d1dee03 → 本票849d623）；合并时04冲突文件全部采用集成线最终版本，`web/exception` 产品/测试、ADR0027、POM与verification入口已逐路径核对。
- 在 `E:\GenCode\server-facility-worktrees\ticket-05` 执行 ` .\mvnw.cmd -B -ntp clean verify `，2026-10-04 00:16:28 +08:00 完成，36.257秒，**1323 tests / 0 failures / 0 errors / 0 skipped**。原5条ArchUnit、JaCoCo门与dependency analyze均通过。
- 环境：Windows 11 / amd64（10.0），Oracle JDK `25.0.4.1+1-LTS-5`，Wrapper Maven `3.10.0`，Asia/Shanghai、zh_CN、UTF-8；Boot `3.5.16`，Spring `6.2.19`，Jackson `2.21.4`，Tomcat `10.1.55` / Servlet `6.0`，Surefire `3.6.0`，JaCoCo `0.8.15`。
- 普通 jar：`target/server-facility-0.1.0-SNAPSHOT.jar`，SHA-256 `5dce0c4d7c58487ff9dc2df4df9c81f70c15ca5e4b71a5da9ed63dffd5db0589`。

| JaCoCo BUNDLE | Covered / Total | 实测 | 原门槛 |
|---|---:|---:|---:|
| INSTRUCTION | 17627 / 18955 | 92.9939% | 88% |
| LINE | 3591 / 3845 | 93.3940% | 88% |
| BRANCH | 1762 / 2045 | 86.1614% | 75% |

完整日志 `.verification-results/ticket-05/final-clean-verify.log`；Surefire XML及JaCoCo在target。所有TDD/网络/子进程日志从开始就保存在 `.verification-results/ticket-05/`，未被clean删除。第一次合并后的全门在 `full-verify.log`：1323测试和覆盖率均绿，但dependency analyze报参数化测试仅传递引入。随后显式增加BOM管理的test-scope `junit-jupiter-params`，没有增加ignore，完整重跑通过。相对集成1276净增47项；旧默认缓冲、无界预算、私有413 JSON、重复filter和手工ContentCaching保存断言已按批准的新契约迁移，未删除或跳过测试。

## 公共契约与可重放证据

下表类均在 `src/test/java/cn/code91/facility/`；HTTP测试统一复用票04真实Tomcat底座，实际绑定127.0.0.1/随机端口，经过生产自动装配与过滤链。

| 契约 | 测试入口 / 已验证行为 | 主要日志 |
|---|---|---|
| 非目标及时输出 | `web/idempotency/StreamingHttpContractTest`：下载/SSE服务端停在受控barrier时，客户端已读到前缀；真实异步SseEmitter先读first，再由测试发last并complete | red-01 → green-01；http-14 |
| 显式、有界、立即转发 | `ResponseCaptureContractTest`：4/5/6字节对5字节预算，活响应始终完整，N+1不能DONE；外层标准wrapper仍可定位捕获，已开始写入的prefix不能只保存tail | red-02 → green-02；red-10 → green-10 |
| 旧无界旁路退出 | 手工ContentCachingResponseWrapper不能再保存到DONE，缺有界filter保持旧PROCESSING；原公开方法签名保留 | red-16 → green-16 |
| Writer/stream/编码 | resetBuffer清除已编码内容，UTF-8 é字节一致；真实HTTP合法代理对拆开write/flush后仍为emoji，末尾孤立high surrogate在最终完成时成为0x3f；首响与重放字节一致 | red-03 → green-03；red-08 → green-08 |
| 部分提交与异常 | 真实HTTP flush前缀后抛异常：状态已提交保持200、仅prefix，不追加ProblemDetail；同key后续409而非重放prefix；非目标sendError走安全404 ERROR派发 | red-13 → green-13 |
| 输出故障 | `ResponseCaptureContractTest`：write、PrintWriter吞掉的I/O失败、flush失败，即使应用捕获后正常返回也不能把副本保存为DONE | http-14；最终全门 |
| Repeatable实际字节预算 | `RepeatableBodyContractTest`：N+1识别超限；声明-1/1/1000000均不能绕过5字节预算，最多读取6字节；输入IOException保留原对象，成功/超限/故障不关闭容器输入 | red-04 → green-04；最终全门 |
| 重读、charset、非阻塞 | 混合/重复reader和stream独立游标、原字节相同、防御副本；ISO-8859-1与默认UTF-8、四字节emoji、畸形UTF-8替换字符；包装时冻结charset；listener正常/空/重复注册均确定拒绝，不触发完成/错误伪回调 | red-04 → green-04；固定seed `0x05b0d1` 的64个0..4096字节样本；最终全门 |
| 选择及非法配置 | `RepeatableSelectionContractTest`、`BoundedWebRegistrationContractTest`：合法problem+json、大小写和text匹配，json-unknown/二进制不误选；非法charset400；默认disabled、0/负预算启用时启动/构造失败；宿主filter按类型让位，单次注册，error+1/repeatable+2/capture+3 | red-05 → green-05；red-17 → green-17 |
| 实际请求协议 | `RepeatableHttpContractTest`：空体、3/4/5字节与é边界，已知Content-Length及未知chunked，正确的重复十六进制正文；慢chunked writer由latch分两段提交；实际ISO charset、伪媒体类型和公共ProblemDetail400/413 | http-09；http-15 |
| 流所有权、取消 | `DownloadOwnershipContractTest`：文件/字节下载与preview不关闭容器输出；文件输入可删除，写失败不继续写，预先中断保持标志/零输出/Err | red-06 → green-06 |
| 真实慢消费/断连 | `DownloadHttpContractTest`：64MiB文件，HttpClient关闭body取消、1024字节接收窗口TCP RST两条路径均在全文件完成前停止、返回Err、完成finally、文件可删除；真实HEAD与空文件GET无body且Content-Length=0 | http-12 |
| 堆与捕获规模 | `StreamingResourceContractTest`独立96MiB JVM，实际HTTP普通/选定响应从64MiB增至256MiB，全部完整消费，捕获预算64KiB，保留堆增长小于16MiB硬阈值，进程自然退出 | green-13；heap-child.log；最终全门 |

反例RED均保留：旧全局缓冲令前缀等待超时；writer reset残留旧字符；旧重复体报告16384而非N+1、忽略charset/listener；+json漏纳/伪媒体误纳；旧下载关闭容器输出/中断时初始化失败；末尾代理字符丢失；late capture和已解析异常错误DONE；旧无界wrapper旁路；自定义filter与默认实例同时装配。`green-07-migration-regressions.log`首次仍有旧测试替换文本/旧顺序断言，随后corrected日志57项通过；这些失败没有被隐藏。

## 独立资源进程的预算与实测

`StreamingResourceProcess` 仅为测试进程入口，使用生产幂等自动装配、同一Tomcat底座和JDK HttpClient。参数：`-Xmx96m -XX:MaxDirectMemorySize=32m -XX:ActiveProcessorCount=2`，child截止50秒，超时强制结束；单次网络请求deadline15秒。测试先8MiB预热，再逐条普通/选定64MiB、256MiB（共648MiB有限流量），始终用8KiB消费缓冲；不分配等大文件或数组，不耗尽宿主。child默认捕获预算65536字节，不使用JaCoCo agent；父进程全库覆盖率另按原门验证。

最终全门中的 `heap-child.log`：

| 路径 | 实际完整传输字节 | GC后保留堆字节 |
|---|---:|---:|
| 预热基线 | 8388608 | 17250872 |
| 普通 | 67108864 | 17259224 |
| 普通 | 268435456 | 17277824 |
| 选定捕获 | 67108864 | 17292568 |
| 选定捕获 | 268435456 | 17295320 |

最大堆100663296字节，保留堆最大较基线增加44448字节，阈值16777216字节。最终子进程测试2.424秒内完成、退出码0。首次资源工作负载也完成，但JUnit按UTF-8读取GBK日志报MalformedInputException；已显式设child file/stdout/stderr=UTF-8，后续和最终全门均通过。此结果是有界HTTP响应扩张证据，不声称覆盖任意并发、已保存store总容量或宿主任意生产者；wrapper自身不创建池、后台线程或临时文件，文件/客户端/server归属均显式关闭。

## Q01–Q10、接合与迁移责任

| 标准 | 本票结果及边界 |
|---|---|
| Q01 | FR-03/05、AC-05/07/10映射上表；ADR0028部分替代0017全局/无界捕获，保留旧claim历史；迁移已写USAGE/CHANGELOG |
| Q02 | 正预算默认/0/负、N−1/N/N+1、已知/未知/失真长度、Unicode/charset/重读、空体、HEAD、失败前后提交与嵌套wrapper。Content-Length失真在Servlet流边界验证；HTTP实际framing由容器拥有，不声称读过其声明边界 |
| Q03 | 真实HTTP覆盖J03的本票413/400和部分提交、J08的流/选定捕获；启用旧幂等后普通下载与实际异步SSE仍及时输出。未另建测试框架。最终普通jar独立消费者/平台CI由root合入后`all --fresh`复验，当前本票没有重复声称该runner已执行 |
| Q04 | latch控制生产与消费，JDK客户端取消、TCP RST、输入/输出I/O故障、writer失败、已提交异常、解码边界；没有以随机sleep掩盖竞态 |
| Q05 | 请求10MiB/捕获1MiB默认正预算；N+1读界，响应超限丢副本，实际堆过程见上。N是payload上限，数组/防御副本有O(N)常数倍开销；全局并发/store总量属宿主及12 |
| Q06 | 手写原始字节、独立十六进制/UTF-8/ISO金样、真实HTTP协议断言；非专有序列化格式，无需另造格式writer。旧无界/default-enabled/ContentCaching旁路是批准的显式迁移，非伪称兼容 |
| Q07 | 固定seed0x05b0d1的64个0..4096字节样本和有限媒体类型/预算矩阵可复跑；不引入性质测试框架。业务claim并发状态机不在本票重写 |
| Q08 | SHA/环境/依赖/命令、成功及失败日志完整保留。Windows原生通过；Linux本票CI、Boot4/Servlet6.1未取得，不能替换为当前Boot3证明 |
| Q09 | 1323/0/0/0；5原架构规则、原88/88/75覆盖率、依赖账目通过。显式声明参数化测试依赖，未调ignore或跳过门 |
| Q10 | 产品、真实行为测试、资源程序、ADR与迁移同票交付；保持verification-pending直至必要环境证据闭合 |

票12仍负责旧幂等的授权/scope、业务保存资格、claim过期后的安全重试/恢复、异步端点资格与重放头政策；05只保证普通流不被缓冲、选定副本有界且不保存已检测的不完整传输，不宣称旧协议整体安全。J08后续与11/12、J03身份链27以及33候选组合按各票责任复验，不反向添加05依赖33的循环。

**票24必须闭合的Servlet6.1已知旁路**：`HttpServletResponseWrapper`新增三个sendRedirect重载以及`ServletResponseWrapper.setCharacterEncoding(Charset)`直接委托被包装响应，不经过这里覆写的旧入口。升级到Tomcat11/Servlet6.1后须补这些入口的重定向丢弃与writer字符集冻结语义，再跑HTTP/资源场景。此票旧平台不能引用不存在的方法。来源和迁移门详见ADR0028；目前不计为Boot4支持证据。
