# 32 — Cookie作用域与显式HTML片段政策

2026-10-04，verification-pending。Windows完整100命令门已通过，Linux及最终集成组合等待仓库CI。jsoup从1.18.3升级1.23.2，仍optional；ADR0055登记新政策，ADR0001的optional决定保留。

| 标准 | 公共契约、测试与证据 |
|---|---|
| Q01 | FR01/03/09、AC04/12；CookieUtil、XssUtil和真实Servlet HTTP沿已批准入口验证。默认SameSite、缺scope删除、非法SameSite注入、重复请求cookie和无界HTML都有实际RED。 |
| Q02 | CookiePolicyTest：空/null、Unicode/控制字符、4095/4096/4097头、整秒-1/0/1/400天与超限/极端Duration、prefix与组合、domain/path及重复值。HtmlPolicyTest：262143/262144/262145 UTF16、null/empty、策略必需、Unicode及自定义Safelist。 |
| Q03 | CookieHtmlHttpTest真实Tomcat：默认HTTPS、显式本机HTTP、宿主ResponseCookie、同scope删除、单头/多头重复、raw输入与显式HTML分别输出。普通jar消费者的有/无jsoup两个图通过，完整100命令门通过。 |
| Q04 | 所有验证拒绝在响应变动前；已有Set-Cookie不被覆盖、无截断；不可变政策不被删除操作改写。无库拥有的异步/锁/连接状态，取消/进程持久恢复不适用；容器I/O失败仍传播，由宿主生命周期管理。 |
| Q05 | 单头值4096ASCII；先检查组件长度后拼接。HTML解析前262144UTF16，parser默认有限stack；已形成输入和容器总请求预算归宿主。64MiB/2CPU/45秒资源消费者覆盖1万深度、固定seed512变体、1万成功+1万拒绝；正式jar结果见下。 |
| Q06 | 16个固定HTML样本在真实1.18.3/1.23.2独立执行；hostless-http、iframe文本两项差异明示，其余14不变。旧签名保留，SameSite/重复/删除空值/必需策略/输入预算及Spring Web写入依赖变化见迁移表。 |
| Q07 | seed320025、512 URI编码/大小写变体及固定恶意HTML样本；无自造parser，没有声称所有XSS上下文安全。 |
| Q08 | Windows11/amd64、Oracle25.0.4.1、zh_CN、Asia/Shanghai；原始记录`.verification-results/ticket-32`。异常不复制输入，首次GBK stdout丢Unicode记录保留且被UTF8重录替代，非BMP不靠终端目测证明。Linux待CI。 |
| Q09 | 原88/88/75、架构及依赖门不变且通过。旧删除测试从Java null改为空协议值，属于明确政策变化，不删测试。原7项jsoup样本未放宽且已在新版通过；完整库1655/0/0/0。 |
| Q10 | 代码、迁移、ADR、升级样本与命令同票交付；完整门未完成前不关闭。 |

## 局部执行与真实失败

原始日志按01–13编号；所有Maven局部命令使用仓库Wrapper、JDK25和ticket13共享依赖缓存，不安装变化中的snapshot到共享仓库。

1. 01默认Set-Cookie缺SameSite真实RED。第一次尝试Servlet Cookie.setAttribute后Spring Mock响应仍不序列化该属性，该失败保留在green命名日志（实际FAIL）；改用标准ResponseCookie形成头，green2的9项通过。
2. 02新完整scope入口缺方法的编译RED→写入和删除保留scope/flags，10项通过。
3. 03成品ResponseCookie允许任意SameSite串的注入反例RED→只接受标准值或明确缺省，11项通过。
4. 04不兼容None/Partitioned/prefix/path/小数与超长有效期RED→发送前政策检查，12项通过；旧-2在builder前拒绝。
5. 05完整header第4097字节未拒绝RED→组件长度预检和最终4096上限，13项通过。
6. 06请求同名首值/末值不一致RED→按目标/全量范围拒绝歧义、null值与空值区分，14项通过。
7. 07旧删除缺默认flags RED→同host-only HTTPS/Lax默认，空协议值；15项通过。
8. 08真实旧版hostless-http样本RED，新版保留预期安全HTML并移除无host href；XssUtil公共入口另行复现RED→POM升级GREEN，原7项与新增1项均通过。iframe文本期望的探索失败和两个真实版本日志完整保留，详见消费者README。
9. 09四个入口接受超长HTML RED→统一解析前边界，9项通过。
10. 10空HTML掩盖null策略RED→策略总是必需，10项通过。
11. 11旧无效名字出现在框架异常消息RED→仅构造阶段转换成固定无cause诊断，输出容器失败不捕获；26项通过。
12. 12真实HTTP与已实现行为的边界扩展：32项、0失败/错误/跳过。此轮是回归扩展，不虚构RED。
13. 13编译类资源预检通过：64MiB/2CPU，16样本、512变体、10000深度、5周期各2000成功/拒绝；retained基线2691744字节、最大2169216字节。这是`target/classes`预检，不能冒充普通jar或完整候选通过。

后续新增domain/极端Duration回归已在完整门通过。公共协议与兼容责任见[使用与迁移](../building/cookie-html-policy.md)、[独立样本来源](../../verification/html-consumer/README.md)。短设计审查位于工作树外coordination/ticket-32-design-review.md，已落实旧负值归一、前缀大小写、header预检及Partitioned分区限制；coordination/ticket-32-premerge-review-impl02.md未发现阻塞项，ADR格式建议已落实。这些短审查不替代最终双审。

## 冻结源码完整门

在干净源码`1f307a3c14a77daa65d978586e64192f36c4895e`执行`java verification/Verify.java all --fresh`，JDK为Oracle25.0.4.1，仓库Wrapper为Maven3.10.0；显式`PG_BIN`指向18.6本机工具，`VERIFY_WRONG_JAVA_HOME`指向负控制JDK21。该源码包含票07与当时已合入的票28，但不包含随后票12或Windows CI原生数据库启动修复。

原始证据：`.verification-results/20261004-072426-683-all`，外层日志`.verification-results/ticket-32/14-all-fresh.log`。100条命令均PASS，库测试1655、失败/错误/跳过均0，架构测试5；指令25021/26920、行4903/5195、分支2655/3120，原覆盖门及依赖门均通过。普通库jar SHA256为`0531defe321757dc46e913ca48c5be933622ec79219dca48a2c0372c89f1cd04`，模板jar为`c68ca17114ee880ada3be82c13ef6d6369f960f1a9c7bd11eea6634b6bb7507a`。这些哈希标识本次制品，不声称跨主机逐字节可复现。

HTML消费者使用该普通jar，类路径仅加入jsoup，确认Spring、Servlet及JUnit不在运行图。缺jsoup的独立进程按约定拒绝非空清洗请求；有jsoup进程在`-Xmx64m -XX:ActiveProcessorCount=2`和45秒墙钟上限下完成16个固定样本、seed320025的512变体、深度10000输入及5周期共10000成功/10000拒绝。显式GC后的retained基线2774280字节、周期最大2251688字节，满足基线+8MiB和绝对48MiB的观测门；这不等同于任意宿主GC或CPU最坏时间保证。相应原始日志为33–35，消费者输入与普通jar随CI归档。

完整门还通过独立Boot消费者、外部业务应用、认证/数据库模板、平台依赖矩阵以及校验和/缺JDK/错误JDK负控制。Linux与合并后的最终源组合仍须等待仓库CI；本票不会以这次Windows记录替代后续集成候选证据。
