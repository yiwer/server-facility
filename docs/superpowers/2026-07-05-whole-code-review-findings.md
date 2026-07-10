# 全库代码评审发现清单(2026-07-05,基线 master bb96902)

> **交接说明**:本清单出自一次全库多维度架构评审(四路分簇侦察 + 控制器亲读枢纽 + 量化指标)。
> 处理时遵守既定 SDD 工作流(记忆 server-facility-migration.md 自动加载:分支/TDD/审查/终审/台账/提交纪律/mvn -o/doc-truth 红线/gate 0.88/0.75)。
> 行号为评审时点近似值,**以符号(类名/方法名)定位为准**。带【决策】标记的条目动手前先向用户呈现选项(AskUserQuestion,超时用推荐)。
> 评审时全库状态:1147 测试绿 / ArchUnit 5 规则 / 覆盖 92.9(instr)/85.7(branch)/92.7(line) / 29 包 / 21 ADR / 11 装配。

> **处置总账(2026-07-06 复审收口,本文档转历史存档)**:F1-F34 已全部落地——五批次 --no-ff 合并
> master(批1 d3218df/批2 4f3164e/批3 24cf521/批4 02f3faa/批5 e864c9f),F37/F39/F40 随批次 4 落地;
> 复审独立核验:1172 绿 / ArchTest 5/5 / 覆盖 93.7-87.0-93.5,批 2-4 对账 19/20、宪法批 12/12,
> 复审残余(F21 的 USAGE 侧、DESIGN 覆盖率近似值、CHANGELOG 迁移须知)已随收口提交补齐。
> **维持 defer 的 roadmap 篮**:F35(masking:error 路径 post-handler 对称测试/未闭合引号兜底/
> per-type 开关/+86)、F36(excel-csv:SAX 流式读/Map-POJO 形态/分隔符可配/多 sheet)、F38
> (ratelimit-cache:caffeine-only @Cacheable 边界/acquire 两段读折叠/per-call acquire 非正参数)、
> C3 六 WARN 点 ListAppender 守护测试、Async 真取消(F6 已诚实化)、F4 对抗语料入测试库、
> RateLimitResult record 自身 javadoc 补哨兵语义(2026-07-10 审计的错误处理残余两项均已修复划出:
> AsyncRequestTimeoutException 归 503、裸 TypeMismatchException 归 400 且 ConversionNotSupported 保 500,
> 见下方补记)。后续工作以此篮为准,勿再整文档重扫。

> **补记(2026-07-09,post-collector 新发现并落地,不在原 F1-F40 扫描范围)**:**未匹配路由 404 语义修正**。
> `NoResourceFoundException`(SF 6.1+ 未匹配路由/静态资源默认抛)与 `NoHandlerFoundException` 原落兜底
> `@ExceptionHandler(Exception.class)` 被判 `code=500`(统一模式 HTTP 200)/HTTP 500(problemDetail)且以
> ERROR 记「系统异常」——扫描/探测/拼错 URL 污染错误日志。新增 `AbstractGlobalExceptionHandler.handleNotFound`
> 按真实语义归 404(双模式)+ i18n `facility.web.error.not_found`(四语)+ 测试 4 例(`HandleNotFoundTests`×3、
> `notFoundKey_resolvesInEnAndZh`×1),日志降 WARN。commit `00c96e0`,全量 **1176 绿**。单独记录以存真。

> **补记(2026-07-09,post-collector 新发现并落地,不在原 F1-F40 扫描范围)**:**FacilityException 缺失 messageKey
> 击穿统一响应契约**。`handleFacilityException` 原用裸键 `LocaleUtil.translateMessageWithArgs`(不 catch
> `NoSuchMessageException`),MessageSource 在场但 key 缺失(消费方 error type 忘登记 i18n 键)时异常逃出
> `@ExceptionHandler` → 容器 500/HTML(默认与 problemDetail 双模式)。改经 C1 边界本地化入口
> `LocaleUtil.localize(errorType, args)`(ADR-0010,内部已 catch 并回退 `getDefaultMessage()` 模板)。
> 连带:缺键 message 由裸键变为 defaultMessage 渲染,更新 2 个既有断言。测试 `HandleFacilityExceptionMissingKeyTests`×2。
> commit `4b5629b`,全量 **1178 绿**。单独记录以存真。

> **补记(2026-07-10,post-collector 错误处理面同类遗漏审计并落地,不在原 F1-F40 扫描范围)**:对 404/NoSuchMessage
> 两 bug 归纳的失效模式(A:handler 内部调用抛异常逃出 advice;B:兜底 Exception.class 遮蔽 Spring 默认解析器致
> 状态错配)全面排查,堵死全部孪生:A2 全 9 处裸键 translateMessage 改 resolveErrorMessage fallback(含兜底自身
> 无退路场景,RED 实证三处穿透);B1 MethodArgumentTypeMismatch→400、B2 HttpMediaTypeNotAcceptable→406(各配
> 四语新键)、B3 ErrorResponseException/ResponseStatusException honor 预期状态(统一包络 code=状态值,PD 透传
> status/headers/body);A1 buildProblemDetail 抽 safeInstanceUri 硬化畸形路径。测试 +12,commit `f91ea1b`,
> 全量 **1190 绿**。残余(AsyncRequestTimeoutException/裸 TypeMismatchException)已入上方 defer 篮;
> C1/C2 信封不一致维持 F16/F17/F18 既有决议。单独记录以存真。

> **补记(2026-07-10,defer 篮清项)**:**AsyncRequestTimeoutException 归 503**。它实现 ErrorResponse
> (自带 503)但不继承 ErrorResponseException 类,@ExceptionHandler 按类层级匹配、接口无效——审计批 B3
> 状态透传覆盖不到,须专门 handler。新增 handleAsyncRequestTimeout(统一包络 code=503/PD HTTP 503,
> WARN 记录)+ i18n `facility.web.error.async_timeout`(四语)+ 测试 3 例。commit `ef2968c`,全量
> **1193 绿**。defer 篮该项划出,余 裸 TypeMismatchException。单独记录以存真。

> **补记(2026-07-10,defer 篮错误处理残余收官)**:**裸 TypeMismatchException 归 400 + ConversionNotSupported
> 保 500**。成对拦截防子类陷阱:父类 400(Spring 默认解析器同语义,复用 type_mismatch 键,propertyName null
> 渲染 "null" 与 media_type 约定一致)、子类 ConversionNotSupportedException 系服务端转换器问题保 500+ERROR,
> 免遭父类 handler 误判。匹配三层 MATME>CNS>裸 TME。测试 +3,commit `79ca781`,全量 **1196 绿**。
> 2026-07-10 错误处理审计残余至此**全部清零**。单独记录以存真。

---

## P0 功能性 bug(行为与文档/配置承诺矛盾,优先修)

**F1. LogUtil 的 DEFAULT_LOGGER 门控使 per-package 日志级别配置失效**(控制器亲读确认)
- 位置:`log/LogUtil.java` 全部 9 个公共方法的首行早退检查(`if (!DEFAULT_LOGGER.isXxxEnabled()) return;`)。
- 现象:`DEFAULT_LOGGER` 绑定 `cn.code91.facility.log.LogUtil` 自身 logger(继承 root)。root=INFO 而业务配 `logging.level.com.myapp=DEBUG` 时,`LogUtil.debug()` 在第一道门被短路,业务包的 DEBUG 配置对 LogUtil 通道完全无效。既有测试只操纵 root,恰好测不到。
- 修法:早退检查改用调用方 logger(先解析 caller 再 gate),或删除 DEFAULT_LOGGER 预检仅保留 per-caller 检查;**同时做 B 部分**——`getCallerClassName()` 的 `Thread.currentThread().getStackTrace()`(全栈捕获)换 `StackWalker`(`getInstance(RETAIN_CLASS_REFERENCE).walk(...)`),抵消去掉预检的热路径成本。
- 测试要求:root=WARN + 指定包 DEBUG → 该包经 LogUtil.debug 必须产出事件(ListAppender);性能不做断言但 ADR/javadoc 记录 StackWalker 选型。
- 关联:P2 轮以来的「DEFAULT_LOGGER 门控 caveat」carry-forward 至此升格为修复。

**F2. SnowIdGenerator `throwOnClockBackwardsExceedThreshold=false` 在大幅回拨时不生效**
- 位置:`id/support/SnowIdGenerator.java` nextId() 回拨分支(~L135-139)与 `spinUntil`(~L159-169,硬编码 `SPIN_TIMEOUT_MILLIS=1000`)。
- 现象:用户显式配置「超阈值回拨不抛」,但 delta>1s 时 spinUntil 超时仍抛 `ClockBackwardsException`——配置承诺自相矛盾。`false` 分支大 delta 无测试。
- 【决策】修法方向:a) 配置为 false 时 spin/park 直到追上(无超时,需文档记无界等待风险);b) false 时改等待带上限+超限返回特殊失败(签名不允许,nextId 返 long——则只能抛,等于配置无意义,应删配置);c) 保留 1s 上限但 javadoc/properties 文档如实收窄「false 仅对 ≤1s 的超阈回拨生效」。推荐 a(语义最忠实)+ 测试用注入 clock 验证。

**F3. TokenBucketRateLimiter `permitsPerSecond=0` 除零 → retryAfter=Long.MAX_VALUE**
- 位置:`ratelimit/TokenBucketRateLimiter.java` acquire(~L58);`FacilityRateLimitProperties` 声明「>0」但不校验。
- 修法:与 F13 一并——构造器守卫 `permitsPerSecond<=0`/`capacity<=0`/`maxBuckets<=0` 抛 IllegalArgumentException(对齐 SnowIdGenerator 先例与 ADR-0013 承诺);补边界测试。

**F4. Filenames.sanitize 把合法文件名误判为穿越**
- 位置:`path/Filenames.java` sanitize(~L34-43),`cleaned.contains("..")` 过粗。
- 现象:`report..final.pdf` 这类不含路径分隔符的连续点文件名被拒。
- 修法:改按路径段检测(按 `/` 拆段后 `segment.equals("..")` 才算穿越);测试:合法双点名放行 + 各种穿越形态(`../`、`..\\`、`a/../b`)仍拦截 + 既有安全测试全绿。安全敏感,审查要求实施者证明拦截面没有缩小。

**F5. CopyUtil 对 null key 的 map entry 静默丢弃**
- 位置:`copy/CopyUtil.java` processMapEntry(~L420-426):`throwOnNullCopy=false` 时直接 return,entry 消失、无日志。
- 【决策】a) 保留 drop 但补 WARN + javadoc 显著记载(最小);b) null key 原样拷入(HashMap 支持 null key);c) 计入 err。推荐 a(行为不变风险最低)+ 测试锁定条目数语义。

**F6. Async.timeout() 不取消底层计算**(诚实化为主)
- 位置:`async/DefaultAsync.java`(~L262-274):`orTimeout` 只让返回的 future 超时,`supplyAsync` 的底层任务继续跑完(虚拟线程静默泄漏语义)。
- 修法:真取消是架构级改动(需贯穿可取消句柄)→ **本轮只做诚实化**:timeout() javadoc + USAGE 明确「超时仅影响观察侧,底层计算不被中断,资源密集任务慎用」;真取消记 roadmap。若实施者评估 `whenComplete` 级联 cancel 成本低且语义安全,可提方案给用户。

---

## P1 安全面

**F7. TraceIdFilter 反射未校验的入站 X-Trace-Id 进 MDC 与响应头**
- 位置:`web/filter/TraceIdFilter.java`(~L40-53)。
- 风险:日志伪造/注入(CRLF、超长、控制字符入 MDC);与全库对 XFF 的警惕口径反差。
- 修法:校验(长度上限如 64、白名单 `[0-9A-Za-z_-]`),不合法按「缺失」处理走重新生成;测试:CRLF 注入串/超长/非 ASCII → 响应头与 MDC 均为新生成值;合法值原样透传。

**F8. InMemory 锁的 maxLocks 清空防护会静默打破在途持锁互斥**
- 位置:`lock/InMemoryDistributedLock.java`(~L52-57);测试 `InMemoryDistributedLockTest.maxLocks_exceeded_clears`(~L112-125)自身演示了该行为但未把后果写进文档。
- 【决策】a) 锁簇改 fail-closed:超限时**拒绝新建**(tryLock 返 false)而非 clear-all,互斥承诺不破(限流/幂等保持 clear-all 的 fail-open 语义,不对称有理:锁是正确性组件,限流是保护组件——写进 ADR-0016);b) 保持 clear-all 但 ADR-0016/javadoc/USAGE 显著记载「超限清空会打破在途互斥」。推荐 a。同类审视 `InMemoryIdempotencyStore.maxEntries` 清空(在途 PROCESSING 记录被清 → 并发重复执行窗口)是否同样 fail-closed。

**F9. AccessLogInterceptor 记录可伪造 IP 无警示**
- 位置:`web/interceptor/AccessLogInterceptor.java`(~L50-56)。
- 修法:javadoc 补一句对齐既有 4 处 XFF caveat 口径(纯文档)。

**F10. RateLimiterUtil 降级哨兵 remaining=Long.MAX_VALUE 可能透传到响应头**
- 位置:`ratelimit/RateLimiterUtil.java`(~L66)。
- 【决策】a) 改 `-1` 表「未知/降级」+ javadoc(轻 breaking:数值语义变化,0.1.0 窗口可接受);b) 维持 + USAGE 警示勿直接透出。推荐 a。

---

## P2 doc-truth 违约、死配置与健壮性

**F11. Collects 类注释「返回值永远不为 null」与 `longListToLongArray(null)→null` 矛盾**(测试已锁定 null 行为)
- 位置:`common/Collects.java` L17 vs ~L165-171。修法:类注释收窄如实(行为已被测试锁定,勿改行为)。

**F12. NullSafe 类注释「永远不抛 NPE」与 `computeOrElse` 的 requireNonNull 矛盾**(测试已锁定抛出)
- 位置:`common/NullSafe.java` L16 vs ~L97-101。修法:同上,类注释如实收窄(标明哪些方法对函数参数 fail-fast)。

**F13. ADR-0013 承诺的「构造器兜底守卫」三簇落空**
- 位置:`InMemoryDistributedLock(int maxLocks)`、`InMemoryIdempotencyStore(int maxEntries)`、`TokenBucketRateLimiter(...)` 构造器均无 `<=0` 校验;ADR-0013 决策 2 明示守卫由构造器兜底(引 SnowIdGenerator 先例)。
- 修法:三构造器补 IllegalArgumentException 守卫 + 边界测试(与 F3 同一批);ADR-0013 无需改(兑现承诺即可)。注意 `maxLocks=0` 现状是「每次调用都先 clear 再建」的荒谬行为,守卫后消除。

**F14. AccessLog 死配置:logHeaders / slowThresholdMillis 从未被消费**
- 位置:`web/interceptor/FacilityWebAccessLogProperties.java`(L12/L14)+ `AccessLogInterceptor` 的 `@SuppressWarnings("unused") props`。
- 【决策】a) 实现 slowThresholdMillis(超阈值升 WARN 并标记 slow)+ 删除 logHeaders(实现成本高、日志泄漏风险大)+ README 装配开关表同步;b) 两者都删;c) 两者都实现。推荐 a。

**F15. Cache 属性在 ConcurrentMap 回退时静默失效**
- 位置:`autoconfigure/FacilityCacheAutoConfiguration.java`(~L71-76)。
- 修法:concurrentMapCacheManager 装配分支加一次性 WARN(「facility.cache.default-ttl/maximum-size 仅 Caffeine 后端生效,当前回退 ConcurrentMap 已忽略」)+ USAGE 一句;装配测试断言日志(或至少行为注释)。

**F16. IdempotencyInterceptor 在响应非 ContentCachingResponseWrapper 时静默 no-op**
- 位置:`web/idempotency/IdempotencyInterceptor.java` afterCompletion(~L109-114)。
- 修法:该分支加 WARN(Filter 未装配/顺序错的配置故障信号;占位卡 PROCESSING 到 TTL);补一个「拦截器单独存在、无 Filter」的失配测试锁定 WARN+不崩。

**F17. RepeatableRequestFilter 的 catch 作用域把 doFilter 也网罗**
- 位置:`web/filter/RepeatableRequestFilter.java`(~L45-54)。
- 现象:下游任何 PayloadTooLargeException 被误判为本 filter 的 413。
- 修法:try 收窄到 wrapper 构造;测试:下游抛同型异常不被转 413(穿透原语义)。

**F18. RepeatableRequestFilter 内联 new ObjectMapper() 忽略宿主 Jackson 配置**
- 位置:同上(~L28-29,`ERROR_MAPPER`)。
- 【决策】a) 维持隔离 mapper(413 错误体是固定形状,不受宿主定制影响是可辩护的确定性选择)+ 注释记为有意;b) 改经 JsonUtil/上下文 ObjectMapper。推荐 a(加注释即可)。

**F19. RepeatableRequestWrapper.getInputStream() 未覆写 available()**
- 位置:`web/filter/RepeatableRequestWrapper.java`(~L69-92)。
- 修法:匿名 ServletInputStream 补 `available()` 返回剩余字节;测试断言读前 available==body 长度、读尽后==0。

**F20. IdUtil 非闩锁回退:Spring 未就绪时每次生成 ID 都过全局锁+bean 查找**
- 位置:`id/IdUtil.java` getIdGenerator(~L88-119)。
- 【决策】性能税 vs「容器迟到也能接上」的语义。a) 维持(语义优先)+ javadoc 记性能特征;b) 失败负缓存(如 volatile 时间戳,N 秒内不再查)。推荐 a(除非有实测热点)。

**F21. SpringContextHolder 多 context 先到先得语义**
- 位置:`context/SpringContextHolder.java`(~L212-224):第二个 context 注入被静默忽略(仅 WARN)。
- 修法:javadoc/USAGE 显著记载多 context(尤其测试)语义与 `SpringContextHolderTestSupport.reset()` 的适用场景。纯文档。

**F22-F24. 装配风格小项**(一批处理,多为文档/低风险)
- F22:http 簇无 `facility.http.enabled` 开关(其余四簇有)——【决策】补开关求对称 or DESIGN 记为有意差异(依赖 classpath 而非用户开关)。推荐补开关(matchIfMissing=true,零破坏)。
- F23:FacilityHttp/FacilityCache 装配无显式 @AutoConfigureAfter 声明(对比 Json/Async/Locale 有)——当前无依赖故无害;加注释记「无跨簇顺序依赖」即可。
- F24:maxBuckets/maxLocks/maxEntries 防护在并发突发下可短暂超限(软上界)——javadoc 一句「advisory bound」。

---

## P3-A 一致性宪法(建议单独 brainstorming,一次性统一;0.1.0-SNAPSHOT 是 breaking 最后窗口)

**F25.** null 契约不统一:`Numbers.setScale(null)→null` vs 同包 `NumberFormat.format(null)→""`;`MimeTyping` 一门面两哲学(detect(File) 走 Result,detect(byte[]) 裸 String 吞异常回退 FALLBACK)〔勘误 2026-07-06:detect(byte[]) 实无吞异常路径(Tika.detect(byte[]) 无受检异常,仅 null/空数组前置回退);吞 IOException 的是 detect(InputStream,String)——宪法批 Task 3 对照源码+javap 裁定〕;`Patterns` 全员 null-safe 但类级 javadoc 未汇总契约。
**F26.** 「无限制」三种拼法(properties 文档 0 / 便利构造器 Long.MAX_VALUE / 测试锁定负数)→ 统一常量与语义。
**F27.** 降级日志政策成文:哪些降级 WARN(现:锁 executeWithLock、三个清空防护)、哪些静默(现:tryLock/unlock/RateLimiterUtil/CacheUtil/HttpClients 全部)——定政策、对齐实现、写 DESIGN。
**F28.** 门面命名家族收敛(XxxUtil / 复数名词 / @UtilityClass / 手写私构造)——至少 DESIGN 里宣布双家族边界规则;改名属 breaking,须用户拍板。
**F29.** 可空性表达统一:@Nullable(jakarta)只在 common/context 用,Result/WrappedError/Tuple 靠散文——选一套铺开。
**F30.** `ErrorTypeInterface.formatFallback` 从接口契约面降回实现细节(breaking:删 public default);连带 **F31**:`getSeverity()/ErrorSeverity` 全库零覆盖零消费——删除(YAGNI)或接线进异常处理器日志级别,【决策】推荐删除。
**F32.** 债 1 重开评估:`SpringContextHolder.getBean` 失败的 bean 名/类名只进 args,`getFormattedMessage` 永远看不到(模板无占位符)——排障痛点从消费方视角再次确认。选项:a) 维持决议关闭但 USAGE 写明「用 getArgs()/toString() 排障」;b) getFullMessage 附 args 段(评估路径泄漏面后)。
**F33.** JsonConfig 四个预设的 javadoc 补「有意不含的特性」清单(acceptEmptyStringAsNull 默认关)。
**F34.** `NullSafe.allNotNull(空数组)→false` 与业界惯例(空集合全称命题为真)相反——已 javadoc;【决策】维持+显著文档 or 对齐惯例(breaking)。

## P3-B 既有 roadmap defer 汇总(历轮终审已裁可缓,列全防漏)

**F35. masking**:error(String,Throwable) 的 post-handler 侧对称测试(warn 路径已锁,error 三行同构);未闭合引号值(截断 JSON)兜底策略;per-type 开关/自定义规则注册;分隔符卡号/+86 前缀。
**F36. excel/csv**:SAX 流式读(堆上限);Map/POJO API 形态;分隔符可配;多 sheet;USAGE 补一句「超限单元格写失败伴随 POI WARN 日志噪音」;CSV null 行双扫描已裁「无动作」(勿重复处理)。
**F37. crypto**:HMAC 空密钥路径 javadoc 有测无;hmacSha256 两重载 null 覆盖不对称;encrypt/decrypt javadoc「AES-256」实受 16/24/32(USAGE 已澄清,javadoc 可同步);USAGE 编解码示例 `bytes` 未定义占位。
**F38. ratelimit/cache 轮残留**:caffeine-only(缺 spring-context-support)边界下 @Cacheable 不降级(CacheUtil 路径会降)补测/文档;TokenBucketRateLimiter acquire 的 retryAfter 两段 synchronized 读可折叠单次计算;autoconfigure package-info 的 Cache 概述精确化为双类探测。
**F39. idempotency 轮残留**:非异常 4xx/5xx 响应(handler 直接 return ResponseEntity.status)也被固化为幂等首响回放至 TTL——行为可辩护但未文档化,补 USAGE/ADR-0017 一句。
**F40. Patterns**:findFirstAsMap/findAllAsMap 的 groupNames 对位置捕获组静默返全 null map(消费方陷阱)——按位置回填或抛错或文档警示,【决策】推荐文档警示 + roadmap 增强。

---

## 建议批次(每批一个 feat/fix 分支,完整 SDD)

| 批次 | 条目 | 性质 |
|---|---|---|
| 1 `fix/log-id-semantics` | F1(含 StackWalker)、F2 | 行为修复,TDD 严格,风险最高收益最大 |
| 2 `fix/web-hardening` | F7、F16、F17、F19、F9 | web 簇安全与健壮性 |
| 3 `fix/guards-doc-truth` | F3+F13、F4、F5、F11、F12、F14、F15 | 守卫兑现 + doc-truth 清账 |
| 4 `docs/decisions-batch` | F6、F8、F10、F18、F20-F24、F32、F33、F39、F40、F37 | 多为文档/一句话修+若干【决策】 |
| 5 brainstorming「一致性宪法」 | F25-F31、F34、F28 | 需用户逐项拍板,breaking 窗口内 |
| defer | F35、F36、F38 其余 | 维持 roadmap,除非用户点名 |

处理纪律提醒:每批合并后跑全量 `mvn -o clean verify` 并同步 README/DESIGN/USAGE 计数(测试数会变);ArchUnit 现为 **5** 条;凡改 FacilityErrorType 段/码,记得 `ErrorTypeInterfaceTest` 的码段上限守卫会咬人;凡动静态状态的测试守毒化纪律。
