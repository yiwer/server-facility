# Phase 6 计划:Web 簇迁移 + async 继承 rework(最后一个大块)

- **状态**:待执行
- **来源 spec**:docs/superpowers/specs/2026-07-02-server-facility-migration-design.md(§4.4 C3、§5、§6、§8)
- **分支**:feat/phase-6-web(自 master)
- **基线**:master @ P5 合并后;559 `@Test` + 4 `@ArchTest` = 563 绿(grep 实数,勿手算)
- **实施纪律**:沿用 P2-P5 全部硬条款——RED/GREEN 为原始 mvn 粘贴、行号与提交文件交叉核对、迁移源测试失败=实施者的错、改源=任务失败(rework 任务除外,rework 范围以本计划为准)、git 提交一律 PowerShell 工具、迁移提交与 rework 提交分开。

## 0. 证据清单(计划作者已核,实施者复核)

| 事实 | 出处 |
|---|---|
| web 树 29 文件 = 25 实 + 4 package-info(web/download/upload/util) | find 实测 |
| web 测试 9 文件 = 45 `@Test`(2/1/13/20/1/3/1/2/2) | grep -c 实测 |
| 5 个 web properties 在源 `autoconfigure.properties`,全部 `@Validated`;Trace/Exception 零约束注解,其余仅 `@Min(0)` | 全文已读 |
| properties 消费者:TraceIdFilter、RepeatableRequestFilter、AccessLogInterceptor、Abstract/DefaultGlobalExceptionHandler;Cors 无组件消费者(仅装配层) | grep -rl 实测 |
| AbstractGlobalExceptionHandler 有 7 处裸 `error.*` i18n 键(L180/186/197/210/221/237/248);源/目标 bundle(26 键)**均无**这些键;GlobalExceptionHandlerTest 4 处裸键断言(L200/222/237/251) | grep 实测 |
| 目标 `LocaleUtil.translateMessage` 的 `.orElse(key)` 只兜"无 MessageSource bean";bean 在场缺键 → NoSuchMessageException 穿透 @ExceptionHandler | LocaleUtil.java 已读 |
| 源 web 树零 `org.apache.catalina/tomcat` import → jakarta.servlet-api 置换安全 | grep 实测 |
| jsoup 不在 Boot BOM;源仓经 beacon 父 pom pin **1.18.3** | 本地 BOM + 父 pom 实测 |
| FacilityWebAutoConfiguration 139 行、6 个 `@ConditionalOnProperty` 注解(+1 import 行);内联 FQN 引用 SessionUserClearInterceptor(标准替换覆盖) | 全文已读 |
| DefaultAsync 两个 DI 构造器(no-arg + 2-arg)目标仓**零使用**;executor no-op 机制 = `of()` L79 即时解析 `eff` 进闭包,字段 `executor` 在 submit 路径永不被读 | grep + 全文已读 |
| FacilityAsyncAutoConfiguration 无 `@AutoConfigureAfter`;`cn.code91.*` 字母序先于 `org.springframework.*` → facilityAsyncExecutor 先注册,Boot `applicationTaskExecutor`(@ConditionalOnMissingBean(Executor.class))回避 → 消费方静默丢失 applicationTaskExecutor | P5 终审 + 源码已读 |

## 1. 决策记录(计划级,终审对账)

1. **Servlet API**:`jakarta.servlet-api`(optional,BOM 管版本)取代源仓 `tomcat-embed-core`。依据:web 树零 tomcat 内部 API;库脚手架不应绑定容器实现。
2. **jsoup**:pin `<jsoup.version>1.18.3</jsoup.version>`(optional)。依据:Boot BOM 不管;1.18.3 为源仓实证组合。
3. **properties 归位映射(C3 收口)**:Trace/RepeatableRequest→`web.filter`,AccessLog→`web.interceptor`,Exception→`web.exception`,Cors→`web` 根(无组件消费者,属 web 簇横切配置)。`autoconfigure.properties` 包就此消亡。
4. **ADR-0013 适用于全部 5 个 web properties**:去 `@Validated`;**同时删除失效的 `@Min(0)` 与 jakarta.validation import**(未启用校验的约束注解=对读者撒谎),约束语义移入字段 javadoc。与 FacilityIdProperties 不同,web properties 全部退化安全(maxBodyBytes ≤0 = 不限制、slowThreshold/maxAge 负值无害),**不加**构造器守卫。
5. **异常 i18n 键**:裸 `error.*` → `facility.web.error.*`(7 键 × 3 locale 补入 bundle)。依据:既有 26 键全部 `facility.*` 前缀;库 bundle 裸键与消费方应用键冲突风险。同步改 7 处主源字面量 + 4 处测试断言。修复"缺键穿透"在 web 簇的全部触发面(translateMessage 穿透议题本体仍留 P7)。
6. **PageBaseResponse 语义对齐**:私有构造 `super(200, "", data, "SUCCESS")` → `super(200, SUCCESS_MESSAGE, data, "")`(BaseResponse.SUCCESS_MESSAGE 降为 package-private 复用)。依据:与 `BaseResponse.ok()` 的 message="success"/description="" 对齐;0.1.0 未发布态可断。
7. **DefaultAsync DI 构造器删除**:no-arg 与 (Executor, List) 构造器零使用且 `computation=null` 是 NPE 陷阱;装配层从未注册 DefaultAsync bean(测试有断言)。类 javadoc"支持通过 Spring DI 注入"随之重写。
8. **executor no-op 修复设计**:`computation` 类型 `Supplier<CF<...>>` → `Function<Executor, CF<...>>`;`submit()` 传当前字段值(可为 null);`of()` 的 computation 在 **apply 时**解析默认(`exec != null ? exec : Executors.newVirtualThreadPerTaskExecutor()`);completed/failed/all/any 忽略参数;全部变换方法透传 `exec`。fluent `executor()` 由此真正生效且覆盖工厂参数。
9. **装配抢注修复**:`@AutoConfigureAfter(TaskExecutionAutoConfiguration.class)`。Boot 先注册 applicationTaskExecutor(是 TaskExecutor)→ facility 的 `@ConditionalOnMissingBean(TaskExecutor.class)` 回避 → "只兜底不抢占"(ADR-0002 本意)。ADR-0002 加补记。

## 2. 任务列表

### T1 async 两条继承 rework(sonnet;2 提交)

**提交 1:executor() 流式设置器 no-op(TDD)**
- RED:改造 `AsyncTest.executor_setsExecutor` → 用命名线程工厂 `Executors.newSingleThreadExecutor(r -> new Thread(r, "async-custom-executor"))`,断言任务内 `Thread.currentThread().getName()` **等于** `"async-custom-executor"`(现断言 isNotNull 空洞);新增 `executor_overridesFactoryExecutor`(`supply(s, poolA).executor(poolB)` → 运行于 poolB 线程名)、`executor_afterMap_routesUpstreamComputation`(`supply(...).map(identity).executor(pool)` → 源计算仍路由至 pool)。原始 mvn 粘贴 3 条失败。
- GREEN:按决策 8 改 `DefaultAsync`(字段/私有构造/of/completed/failed/all/any/map/flatMap/recover/recoverWith/timeout/peek/peekErr/submit 共 15 处 lambda/签名);按决策 7 删两个 DI 构造器并重写类 javadoc 字段表(computation 描述改"提交时注入生效执行器");`Async.executor()` javadoc 补"submit() 时生效,覆盖工厂方法给定的执行器"。全量绿。本提交 `@Test` **+2**。

**提交 2:装配抢注压制 Boot applicationTaskExecutor(TDD)**
- RED:`FacilityWebAutoConfigurationTest` 不动,在 `FacilityAsyncAutoConfigurationTest` 新增联合 runner 测试:`AutoConfigurations.of(FacilityAsyncAutoConfiguration.class, TaskExecutionAutoConfiguration.class)` → 断言 `hasBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)` 且 `doesNotHaveBean("facilityAsyncExecutor")`。当前字母序 facility 先行 → applicationTaskExecutor 缺席 → RED。
- GREEN:`FacilityAsyncAutoConfiguration` 加 `@AutoConfigureAfter(TaskExecutionAutoConfiguration.class)` + javadoc 补充;既有 3 测不回归。`docs/adr/0002-*.md` 补记(字母序抢注陷阱 + 联合测试实证)。本提交 `@Test` **+1**。

### T2 pom 依赖置换(haiku,hardened 条款;1 提交)
- `<jsoup.version>1.18.3</jsoup.version>` 入 properties;dependencies 增 **P6 段**:`jakarta.servlet-api`(optional,无 version)、`spring-web`(optional)、`spring-webmvc`(optional)、`jsoup`(optional,`${jsoup.version}`)。
- **删除** test 段 `spring-web` 条目及其两行注释(pom.xml L130-136)。
- 验收:`mvn verify` 全绿,测试数不变(559+4)。

### T3 web 无属性子簇迁移(haiku,hardened;1 提交,TDD:测试先迁编译 RED → 主源迁 → GREEN)
- 测试先迁:`PageQueryTest`(2)、`CookieUtilSecureTest`(2)、`RequestUtilClientIpTest`(2)。
- 主源标准替换迁移(13 实 + 3 package-info):argument/PageQuery;response/BaseResponse、PageBaseResponse;session/SessionKeyConstants、SessionUserHolder、SessionUtil;util/CookieUtil、RequestUtil、ResponseUtil、XssLevel、XssUtil、package-info;download/HttpFileResponses、package-info;upload/SafeUpload、package-info。
- **禁止**行为改动(PageBaseResponse quirk 保持忠实,T8 再改)。验收:本任务 `@Test` 增量 **+6**(对上一提交 grep 核数)。

### T4 properties 归位 + filter/interceptor 簇(sonnet;1 提交)
- 测试先迁:`RepeatableRequestFilter413Test`(1)、`RepeatableRequestWrapperLimitTest`(3)、`SessionUserClearInterceptorTest`(1)。
- 5 properties 按决策 3+4 归位新作(去 @Validated/死 @Min/validation import,前缀 `facility.web.*`,约束入 javadoc)。
- filter 簇迁移:PayloadTooLargeException、RepeatableRequestWrapper、RepeatableRequestFilter、TraceIdFilter(properties 同包 → 原 import 直接删除);interceptor 簇:AccessLogInterceptor、SessionUserClearInterceptor。
- `web/package-info.java` **重写**(非替换):properties 归位(C3 闭环)、spring-web/webmvc/servlet-api optional 依赖故事、子包清单。
- 验收:本任务 `@Test` 增量 **+5**。

### T5 exception 簇迁移(sonnet;1 提交)
- 测试先迁:`AbstractFacilityExceptionTest`(1)、`BusinessExceptionTest`(13)、`GlobalExceptionHandlerTest`(20)——**裸键断言忠实保留**。
- 主源:FacilityException、AbstractFacilityException、BusinessException、SystemException、AbstractGlobalExceptionHandler(properties 同包 import 删;示例 javadoc 同步)、DefaultGlobalExceptionHandler。
- 验收:本任务 `@Test` 增量 **+34**。

### T6 异常 i18n 键 rework(sonnet;1 提交,TDD)
- RED:新建 `WebErrorMessagesIntegrationTest`(Core+Locale runner):断言 `facility.web.error.system` 于 en 与 zh-CN 经聚合链可解析、`facility.web.error.missing_parameter` 带 {0} 参数渲染。当前缺键 → NoSuchMessageException → RED(原始粘贴)。
- GREEN:3 个 bundle 各 +7 键(措辞对齐既有风格,en/zh_CN/zh_TW);`AbstractGlobalExceptionHandler` 7 处字面量 `error.*` → `facility.web.error.*`;`GlobalExceptionHandlerTest` L200/222/237/251 断言同步。
- 验收:本任务 `@Test` 增量 **+2**;grep 全仓无裸 `"error.` 残留。

### T7 web 装配层(sonnet;1 提交,TDD:装配测试先迁 RED)
- `FacilityWebAutoConfigurationTest` 迁移(5 测;前缀行由标准替换覆盖,验证 `facility.web.trace.enabled=false`)。
- `FacilityWebAutoConfiguration` 迁移:标准替换 + **5 处 properties import 二次替换**(`autoconfigure.properties.X` → 决策 3 新 FQN);grep 验证 `@Bean` 数与源一致、6 个 `@ConditionalOnProperty` 前缀全为 `facility.web.*`。
- `ConfigurationPropertiesBindingTest` 迁回 web 用例:@EnableConfigurationProperties 增 `cn.code91.facility.web.filter.FacilityWebRepeatableRequestProperties`,新增 `repeatableRequestPropertiesParsesByteSize`(`facility.web.repeatable-request.max-body-bytes=20971520`)。
- imports 文件末尾追加第 6 行 `cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration`。
- `autoconfigure/package-info.java` 重写(6/6 装配全家福)。
- 验收:本任务 `@Test` 增量 **+6**;`ConfigurationPropertiesBindingTest` 类注释同步 ADR-0013 叙述。

### T8 盲区测试 + PageBaseResponse 对齐(sonnet;2 提交)
**提交 1(盲区测试,≥45,实数 grep 为准)**:
- `SafeUploadTest` ≥8(@TempDir + MockMultipartFile):空文件 FILE_UPLOAD_EMPTY;危险扩展名(a.jsp)FILE_TYPE_NOT_SUPPORTED;穿越型文件名(`..\..\evil.txt`)结果必须落于目标目录内或 err(RV2-08 复核);正常保存内容一致;sizeCheck 超限 FILE_SIZE_EXCEEDED;typeCheck 不允许 mime;toTempFile 往返;isImage 魔数(1x1 PNG 字节)。
- `XssUtilTest` ≥6:BASIC 剥 `<script>` 保 `<b>`;NONE 剥全部;BASIC_WITH_IMAGES 保 `<img>`;RELAXED 保 `<table>`;null→null、""→"";isSafe 真/假(RV2-14 复核)。
- `HttpFileResponsesTest` ≥6(MockHttpServletResponse + @TempDir):中文名 Content-Disposition 含 `filename*=utf-8''%E4%…`(RFC 5987);preview→inline;内容字节一致 + Content-Length;缺文件 FILE_NOT_FOUND;downloadBytes 空数组 FILE_READ_ERROR。
- `SessionUtilTest` ≥6(RequestContextHolder + Mock 请求,@AfterEach resetRequestAttributes):getSession 不建新;setAttribute 自动建;getAttribute 类型过滤;removeAttribute;invalidate;非 web 上下文全 empty。
- `SessionUserHolderTest` ≥5:含跨线程隔离(新线程内 getUser empty)与 clear。
- `BaseResponseTest` ≥7:三个 ok 工厂;err(code,msg);err(ErrorType,args) 格式化+description;fromResult ok/err 桥接;isSuccess。
- `ResponseUtilTest` ≥4:writeJson 状态/CT/UTF-8/JSON 往返;指定状态码;setDownloadHeaders `+`→`%20` 与 RFC 5987 双头;setNoCacheHeaders 三头。
- `FacilityWebAutoConfigurationTest` 增 ≥3:repeatable-request.enabled=false / access-log.enabled=false → 对应 bean 缺席;cors 空 allowedOrigins 语义断言(RV2-05,读装配源码后定断言面,bean 恒注册则退为 enabled=false → bean 缺席)。
- package-info **新作 ×6**:argument/response/session/filter/interceptor/exception(依赖故事按目标态写)。
**提交 2(normalize rework,TDD)**:`PageBaseResponseTest` 断言 `of()` → message=="success" 且 description=="" → RED;按决策 6 改(SUCCESS_MESSAGE 降 package-private)→ GREEN。

### 收尾(控制器本人)
- fable 全分支终审(重点:C3 闭环后 ArchUnit 四规则、properties 无 @Validated 回归面、i18n 键三语一致、executor 修复无并发退化)。
- spec §8 P6 行勘误(如有);ledger + memory 更新;finishing-a-development-branch(先例:本地合并回 master)。

## 3. 验收总标准
- `mvn verify` 全绿;`@Test` 较基线 559 的增量 ≥102(逐任务底数 3/0/6/5/34/2/6/≥46),即实数 ≥661,外加 4 `@ArchTest`。每任务只对**增量** grep 核数,不写绝对数连锁(P1 教训)。
- ArchUnit 四规则通过(尤其 packages_are_cycle_free 与 autoconfigure 不被主包依赖——properties 归位后 web 不再触碰 autoconfigure)。
- grep 全仓:无 `cn.hbads`、无 `beacon.facility`、无裸 `"error.`、无 `@Validated`(main 树)、无 `autoconfigure.properties`。
- imports 文件 6 行;bundle 3 locale 各 33 键(26+7)。
