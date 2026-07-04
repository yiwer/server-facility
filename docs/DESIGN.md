# server-facility 设计

## 1. Deep module 哲学

server-facility 遵循 Ousterhout 的 **deep module** 原则:接口窄、实现宽。消费方看到的是
少量易记的入口 —— 静态门面(`IdUtil`、`JsonUtil`、`LogUtil`、`DateUtil`、`LocaleUtil`、
`CopyUtil` …)、值类型(`Result<T,E>`、`Tuple`、`Triple`)与一组自动装配 bean —— 背后
是被反复打磨、覆盖边界的实现。设计目标是让"引一个依赖就少写一大片样板",而不是暴露
可配置旋钮的大工具箱。

三条一以贯之的取向:

- **错误显式化**:可预期失败一律走 `Result<T,E>` 的错误通道,不用 `null`、不靠受检异常
  穿透。序列化、日期解析、文件 IO 等失败点全部返回 `Result`。
- **降级安全**:自动装配的每个 bean 都 `@ConditionalOnMissingBean` 兜底,消费方声明同类
  bean 即覆盖;配置属性不用 `@Validated`(ADR-0013),消费方即便没有校验 provider 也能启动。
- **窄依赖**:主源码不依赖 logback(ADR-0011)、error 包纯 JDK(ADR-0010),Web/XSS/MIME
  等重依赖一律 optional,按需引入。

## 2. 包簇依赖地图

20 个顶层功能子包按责任聚类(另有 `id.support`/`json.support`/`web.*` 等下层子包),依赖自底向上单向流动(ArchUnit `packages_are_cycle_free` 守护):

```
              autoconfigure  ← Spring Boot 装配入口(6 个 @AutoConfiguration)
                   │  依赖各组件包,自身不被任何主包依赖(ArchUnit 守护)
   ┌───────────────┼─────────────────────────────────────────┐
 web.*           async        json / copy / date / number / …  ← 组件层
   │               │                     │
   │           result(值)         result / error
   │
 result / error / log / json / locale / mime / path          ← web.* 依赖的基座
   │
 error(纯 JDK) ← structure(纯值) ← common          ← 叶子层
```

- **叶子层**:`error`(纯 JDK,C1 断环)、`structure`(纯值,C2 断环)、`result`。
- **组件层**:各能力簇,依赖基座与叶子,互不横向依赖。
- **装配层**:`autoconfigure` 依赖组件包组装 bean;ArchUnit 规则
  `autoconfigure_is_not_depended_on_by_main_packages` 保证它不被反向依赖 —— 组件包不感知
  自己如何被装配,配置属性类与消费组件同包(见断环 C3)。

## 3. 三组包循环断环(spec §4.4)

迁移前源仓存在三处包循环,本工程逐一断开并以 ArchUnit 规则锁定:

| 循环 | 成因 | 断法 |
|---|---|---|
| **C1** `error → locale → context → error` | 错误消息在 error 包内做 i18n 解析,拉入 locale/context | error 包退回纯 JDK,i18n 解析上移到展示边界(`LocaleUtil.localize`);ADR-0010 |
| **C2** `common → structure → copy → common` | `WrappedContainer`/`WrappedDataType` 横跨 structure 与 copy | 二者零消费者,直接 drop;structure 退为纯值叶子 |
| **C3** `web → autoconfigure`(properties 反向边) | 5 个 web properties 曾住 `autoconfigure.properties`,web 组件依赖它 → 又被 autoconfigure 依赖 | properties 归位到各自消费组件同包(trace/repeatable→`web.filter`,access-log→`web.interceptor`,exception→`web.exception`,cors→`web`);`autoconfigure.properties` 包消亡 |

四条 ArchUnit 规则(`ArchitectureTest`,随测试套运行):`packages_are_cycle_free`、
`error_package_depends_only_on_jdk`、`main_code_does_not_depend_on_logback`、
`autoconfigure_is_not_depended_on_by_main_packages`。

## 4. 自动装配范式

6 个 `@AutoConfiguration` 经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
注册:`Core`、`Id`、`Json`、`Locale`、`Async`、`Web`。共同约定:

- **兜底不抢占**:每个 bean `@ConditionalOnMissingBean`(按类型或名称),消费方声明的同名/
  同类型 bean 永远优先。
- **按类型让位 Boot**:`FacilityAsyncAutoConfiguration` 的 `facilityAsyncExecutor` 条件为
  `@ConditionalOnMissingBean(TaskExecutor.class)`,并 `@AutoConfigureAfter(TaskExecutionAutoConfiguration)`
  —— 让 Boot 的 `applicationTaskExecutor` 先注册,facility 仅在缺失时兜底(ADR-0002)。
- **i18n 聚合抢注 primary**:`FacilityLocaleAutoConfiguration` 以 `@AutoConfigureBefore(MessageSourceAutoConfiguration)`
  注册 `@Primary` 的 `AggregatedMessageSource`(名为 `messageSource`),把各模块贡献的具名
  `MessageSource` bean 聚合为一个;`facilityMessageSource` 提供 facility 自带的 i18n 文案
  (basename `i18n/facility-messages`,含 base + en/zh_CN/zh_TW 四份,`fallbackToSystemLocale=false`
  使非中英 locale 确定性回落英文 base)。
- **Web 条件门**:`FacilityWebAutoConfiguration` 整体 `@ConditionalOnWebApplication(SERVLET)`,
  各组件再由 `facility.web.*.enabled` 单独 `@ConditionalOnProperty` 开关。

## 5. ADR 索引

13 条架构决策记录(`docs/adr/`);0001-0008 为源仓继承决策,0009 起为本工程决策。

| ADR | 决策 |
|---|---|
| 0001 | jsoup / tika-core 声明为 Maven optional |
| 0002 | 异步线程池按类型匹配注入,`@AutoConfigureAfter` 让位 Boot |
| 0003 | RFC 7807 ProblemDetail 双轨(`use-problem-detail` 开关) |
| 0004 | `FacilityException` 接口解耦异常层次 |
| 0005 | `LogUtil` Throwable 参数对齐 SLF4J 末位 |
| 0006 | `LogUtil` 内部状态 `compareAndExchange` 消除 ABA |
| 0007 | `Result.empty()` 表达"成功但无值" |
| 0008 | SnowId `parseTimestamp`/`parseInfo` 改 instance 方法 |
| 0009 | Result/Tuple/Triple 纯别名精简 |
| 0010 | 错误消息边界本地化,error 包纯 JDK(C1 断环) |
| 0011 | 删除 `LogUtil.setLevel`,主源码零 logback 依赖 |
| 0012 | `formatMessage` 委托 SLF4J `MessageFormatter` |
| 0013 | 配置属性不用 `@Validated`,构造器兜底 |
| 0014 | 限流令牌桶 + `RateLimiter` SPI(Seam),web 集成分离 `web.ratelimit` 避环 |
| 0015 | 缓存 `CacheUtil` 门面复用 Spring `CacheManager`,Caffeine + spring-context-support 成对 optional |

## 6. 质量门

- **测试**:775 项,含 4 条 ArchUnit 架构守护;`mvn verify` 全绿。
- **覆盖率**:JaCoCo check 绑 `verify`,BUNDLE 级 INSTRUCTION/LINE ≥0.80、BRANCH ≥0.65
  (实测 82.3% / 81.9% / 69.8%),达标即门,退化即红。
- **依赖账目**:`maven-dependency-plugin` `analyze-only` 绑 `verify` 且 `failOnWarning` ——
  used-undeclared / unused-declared 必须清零(运行时 SPI / 聚合传递依赖显式 ignore 并注明理由)。
