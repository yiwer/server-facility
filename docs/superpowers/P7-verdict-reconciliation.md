# P7 §5 逐包判定对账(收口验收)

> 对账时点:P7 收口(feat/phase-7-finalization);全量 775 测试绿,JaCoCo gate 达标,dependency:analyze 零 warning。
> 逐包核对 spec §5 判定表(23 包)与目标仓 `cn.code91.facility.*` 实况,全部证据由 grep/find 实测。

## 1. keep / keep+rework / keep+review 落实(20 包)

| # | 包 | 判定 | 落实状态 | 证据 |
|---|---|---|---|---|
| 1 | `result` | keep+rework | ✅ 存在;别名精简 ADR-0009 | `result/` present;ADR-0009 |
| 2 | `error` | keep+rework | ✅ C1 断环(纯 JDK)ADR-0010 | ArchUnit `error_package_depends_only_on_jdk` 绿 |
| 3 | `structure` | keep+review | ✅ Tuple/Triple keep;Wrapped* drop | `structure/` present;`WrappedContainer`/`WrappedDataType` find 无 |
| 4 | `common` | keep | ✅ Collects/NullSafe | `common/` present |
| 5 | `context` | keep | ✅ SpringContextHolder | `context/` present |
| 6 | `id` | keep | ✅ IdUtil/SnowIdGenerator | `id/` present |
| 7 | `date` | keep+rework | ✅ commons-lang3 清除 | `commons-lang3` 全仓 0 引用 |
| 8 | `number` | keep(拆) | ✅ Numbers/NumberFormat/NumberUnits;ChineseNumbers drop | `ChineseNumbers` find 无 |
| 9 | `hash` | keep | ✅ 纯 JDK | `commons-codec`/`hutool` 0 引用 |
| 10 | `pattern` | keep | ✅ Patterns/CommonPatterns | `pattern/` present |
| 11 | `path` | keep | ✅ Filenames(危险扩展名 P7 加强) | `path/` present;T1 f5b6c82 |
| 12 | `io` | keep | ✅ PathIo/Zipping | `io/` present |
| 13 | `mime` | keep | ✅ MimeTyping;tika optional | `mime/` present |
| 14 | `json` | keep | ✅ Jsons/JsonsRegistry/JsonUtil | `json/` present;P7 覆盖率 38%→94% |
| 16 | `copy` | keep+rework | ✅ CopyUtil | `copy/` present |
| 17 | `locale` | keep+rework | ✅ AggregatedMessageSource;C1 承接 | `locale/` present;i18n 4 bundle |
| 18 | `log` | keep+rework | ✅ SLF4J MessageFormatter ADR-0012;零 logback ADR-0011 | ArchUnit `main_code_does_not_depend_on_logback` 绿 |
| 19 | `async` | keep | ✅ 虚拟线程 + 拦截器链 | `async/` present |
| 22 | `web` | keep 全簇 | ✅ 9 子簇全迁;servlet API 换 jakarta.servlet-api | `web/*` present;`org.apache.tomcat` 0 引用 |
| 23 | `autoconfigure` | keep+rework | ✅ 6 装配;C3 断环(properties 归位) | ArchUnit `autoconfigure_is_not_depended_on_by_main_packages` 绿;imports 6 行 |

## 2. drop 落实(3 包 + 3 成员)

| 项 | 判定 | 落实 | 证据 |
|---|---|---|---|
| `coordinate` 全包 | drop | ✅ 不存在 | find 无 |
| `convert` 全包 | drop(P4 改判) | ✅ 不存在 | find 无 |
| `validate` 空包 | drop | ✅ 不存在 | find 无 |
| `ChineseNumbers` | drop | ✅ 不存在 | find 无 |
| `WrappedContainer` | drop(C2) | ✅ 不存在 | find 无 |
| `WrappedDataType` | drop(C2) | ✅ 不存在 | find 无 |

## 3. 依赖收敛落实(spec §6)

| 依赖 | 目标 | 落实 | 证据 |
|---|---|---|---|
| commons-lang3 | 移除 | ✅ | 0 引用 |
| hutool | 移除 | ✅ | 0 引用 |
| commons-codec | 移除 | ✅ | 0 引用 |
| tomcat-embed-core | 换 jakarta.servlet-api | ✅ | tomcat 0 引用;jakarta.servlet-api optional |
| jackson-core/annotations | 显式声明(P7) | ✅ | pom 显式声明;dependency:analyze 零 warning |
| spring-context/beans/core | compile 显式声明 | ✅ | pom present |

## 4. 三组断环落实(spec §4.4)

| 环 | 落实 | ArchUnit 守护 |
|---|---|---|
| C1 error→locale→context→error | ✅ error 纯 JDK,i18n 上移边界 | `error_package_depends_only_on_jdk` |
| C2 common→structure→copy→common | ✅ Wrapped* drop,structure 纯值叶子 | `packages_are_cycle_free` |
| C3 web→autoconfigure(properties) | ✅ 5 properties 归位组件同包 | `autoconfigure_is_not_depended_on_by_main_packages` |

## 5. P7 观察项 → roadmap(2026-07-03 技术债处理已闭合)

roadmap 技术债处理轮次(分支 feat/roadmap-tech-debt)对下列项逐一了结:

1. **FacilityErrorType 模板无占位符 —— ✅ 债1 决议关闭(不改行为)**:审视确认 args 三路可用 ——
   `getFormattedMessage()` 不含 args(面向用户 message 稳定、不泄漏路径)、`toString()` 已含
   `args=[...]`(服务端日志排障)、`getArgs()`/`getArgsList()` getter(编程访问)。无差别让 args 进
   面向用户 message 会泄漏 `FILE_NOT_FOUND`/`FILE_DELETE_ERROR`/`FILE_WRITE_ERROR` 的服务器路径
   (`file.getPath()`/`dir.toString()`),违反 ADR-0010 边界文案稳定原则。**维持 P7 文档收窄,现状完备无缺口**。
2. **`Jsons`/`JsonUtil` null 入参不对称 —— ✅ 债2 已修**(c706d5e):`deserialize(InputStream)` null 入参
   改为返 `Result.err`(与 String/byte[] 对称);类型参数 target/typeReference 保持 requireNonNull 编程契约。
3. **`NumberFormat.formatSize` 边界 —— ✅ 债3 已修**(1a98f9d):`<1024` 本已有 B 单位;浮点 exp 实测
   1024^n 无偏差;真问题超 PB 不进位已补 EB 单位(1 EB→"1.00 EB");负数走 B 分支不崩溃已文档化。
4. **覆盖率提门 —— ✅ 债4 已修**(609a705/163f55c/f163233):copy(CopyUtil 20%→98%、AutoCopyEngine 68%→98%)
   + web.util(RequestUtil/CookieUtil →100%)补测 +70,全局 line 81.9%→87.8%;gate 提至 0.85(branch 0.70)。
5. **`Jsons` 残留 catch 分支 / 其余低覆盖(LogUtil 62%、AbstractGlobalExceptionHandler 67%、Patterns 79%)**:
   非阻断,gate 0.85 已留缓冲;后续可继续逐簇补测。
6. **新发现:`AutoCopyEngine` Map key-only CopyTrait 回落 DIRECT**(30ad504 已修 javadoc):仅键实现 CopyTrait
   (值不实现)时无匹配策略、整体引用拷贝;javadoc 已精确化(非行为改动,记录设计边界)。

## 6. 收口结论

spec §5 全部 23 包判定 **100% 落实对账**(20 keep + 3 drop 包 + 3 drop 成员);三组断环 ArchUnit
锁定;依赖收敛达成;6 装配 + C3 归位闭合。质量门(775 绿 + JaCoCo 0.80 + dependency:analyze 零 warning)
与文档三件套齐备。**server-facility 迁移工程收口完成。**
