# 一致性宪法 design(批次 5,F25-F31/F34/F28)

- 状态:Approved(九项决策经 AskUserQuestion 逐项拍板,均取推荐;2026-07-06)
- 源起:docs/superpowers/2026-07-05-whole-code-review-findings.md P3-A;0.1.0-SNAPSHOT 是 breaking 最后窗口
- 目标:把全库隐性惯例升格为成文宪法(DESIGN 新章节),并在窗口内落地三个 breaking 修正;除明示三处外**行为零改动**。

## 1. 宪法条款(落 docs/DESIGN.md 新章节「一致性宪法」)

**C1 null 契约(F25)**:数据参数 null → null-safe(返回 null/空容器/回退值,按类型语义);函数型/必需依赖参数 null → fail-fast(requireNonNull);IO/解析/外部世界交互 → Result 通道。存量与条款不一致处(Numbers.setScale null→null vs NumberFormat.format null→"";MimeTyping detect(File) Result vs detect(byte[]) 返裸 String——null/空数组前置回退 FALLBACK,Tika.detect(byte[]) 无受检异常故无吞异常路径;吞 IOException 回退的是 detect(InputStream,String)。〔勘误 2026-07-06:findings F25 原文「detect(byte[]) 吞异常回退」失实,Task 3 实施对照源码+javap 纠正〕)**均已被测试锁定,不改行为**——在各类级 javadoc 如实汇总自家契约(Patterns 类级补 null-safe 汇总一句)。

**C2 「无限制」拼法(F26)**:统一措辞「**≤0 = 不限制**」(properties javadoc/USAGE/注释全库同一拼法);`RepeatableRequestWrapper(HttpServletRequest)` 便利构造器 `Long.MAX_VALUE`→`0`(行为等价:限流判定为 `max > 0` 才限制;既有测试锁定负数=不限制继续成立)。不引入新公共常量(YAGNI)。

**C3 降级日志政策(F27)**:装配期/低频防护动作/配置故障信号 → WARN(现:锁 executeWithLock 降级、锁/幂等 fail-closed 拒绝、限流 clear-all、cache ConcurrentMap 回退、幂等失配、CopyUtil null key drop);每请求高频路径的预期降级 → 静默(现:LockUtil.tryLock/unlock 无 bean、RateLimiterUtil、CacheUtil、HttpClients 无定制 bean)。实施时逐点审计现状对照条款:符合→记入 DESIGN 清单;违背→列出待裁(预期现状已全部符合)。

**C4 门面命名双家族(F28)**:`XxxUtil` = 静态门面(可能有状态/Spring 边缘/装配交互:LogUtil/IdUtil/CacheUtil/LockUtil/RateLimiterUtil/CryptoUtil/MaskUtil/CopyUtil/JsonUtil/ExcelUtil/CsvUtil…);复数名词 = 纯函数无状态工具(Collects/Filenames/Patterns/Numbers/Jsons/Zipping/HttpClients…)。新组件按此归家族;**存量零改名**(breaking 无强理由)。边界规则写 DESIGN;HttpClients 虽复数但依赖 RestClient bean——如实标注为「历史例外,按门面对待」(诚实记载,不粉饰)。

**C5 可空性标注(F29)**:公共 API 的可空参数/返回值用 `jakarta.annotation.Nullable`(既有先例 common/copy/context);本批为消费面最高的 **error/result 簇补标**(Result/WrappedError/Tuple/Triple 的可空访问器与工厂参数);其余存量「触碰即补」渐进,不做全库铺开。

## 2. 三个 breaking 修正(0.1.0 窗口内,均已拍板)

**B1(F30)**:`ErrorTypeInterface.formatFallback(Object[], IllegalArgumentException)` public default → **接口 private 方法**(Java 9+;全库唯一调用点 format() L141;假想重写者为零)。
**B2(F31)**:删除 `getSeverity()` 与嵌套枚举 `ErrorSeverity`(全库零覆盖零外部消费);`getDetailedDescription()` 输出去 severity 段(格式串同步);如 getDetailedDescription 亦零消费,保留但如实(不扩大删除面)。
**B3(F34)**:`NullSafe.allNotNull(空数组)` false→**true**(vacuous truth,对齐 lang3 惯例);`null` 入参仍返 false(null-safe 不变);改锁定测试+javadoc。同族 `anyNull`/`allNull` 等如有空数组语义,同步审视对齐全称/存在命题惯例并如实文档(有行为改动须列明)。

## 3. 验收

- 三 breaking 各有 RED→GREEN(B1 编译面/B2 删除面用编译+grep 证零残留/B3 行为翻转测试);
- DESIGN 新章节五条款 + C3 审计清单落地;C1 三处类级 javadoc 如实;C2 全库拼法 grep 零残留「Long.MAX_VALUE 表无限制/0 表无限制」歧义;C5 error/result 簇 @Nullable 补标(编译零警告);
- `mvn -o clean verify` 全绿、gate 0.88/0.75、ArchUnit 5/5;README/USAGE/DESIGN 计数与口径同步;
- 分支 feat/consistency-constitution,完整 SDD(计划→子代理→审查→opus 终审→merge --no-ff)。

## 4. 不做(明示出界)

- MimeTyping detect(byte[]) Result 化、NumberFormat.format(null) 行为改动(仅文档);
- 全库 @Nullable 铺开;存量改名;「无限制」公共常量;F27 静默点改首次 WARN。
