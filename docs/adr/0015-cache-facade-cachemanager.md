# ADR-0015: 缓存门面复用 Spring CacheManager SPI + Caffeine/ConcurrentMap 双后端

> 2026-10-04：[ADR0047](0047-boot4-consumer-integration.md) 补全成对依赖缺任一时的目标平台装配验证与实现条件，保留本记录的门面/optional理由；新TTL/容量政策仍归票08。

- **状态**:Accepted(2026-07-03)
- **源起**:限流+缓存实现计划 簇B(docs/superpowers/plans/2026-07-03-ratelimit-cache.md),spec §10 新组件

## 背景

server-facility 此前无缓存组件。spec §10 为服务端组件库新增缓存能力,需要在"自建缓存
实现"与"复用生态标准"之间取舍。Spring Framework 自带 `org.springframework.cache` 抽象
(`CacheManager`/`Cache` SPI + `@Cacheable`/`@CacheEvict` 等注解)已是事实标准,现成实现
丰富(`ConcurrentMapCacheManager` 纯 JDK 兜底、`CaffeineCacheManager`/`EhCacheCacheManager`/
`RedisCacheManager` 等第三方后端),且 `spring-context` 已是本库 P2 依赖,复用零增量成本。
自建缓存实现(仿照 `TokenBucketRateLimiter` 手写 `ConcurrentHashMap` + TTL)需要重新解决
过期、驱逐、并发控制等 Caffeine/Spring Cache 已经解决过的问题,且与消费方既有的
`@Cacheable` 注解生态割裂——若消费方已经在用 Spring Cache 注解,自建门面无法接入同一
份缓存状态。

单机默认后端选择上,与限流簇的令牌桶选择类似:Caffeine(高性能本地缓存,支持 TTL/
maximumSize/多种淘汰策略)能力强但非纯 JDK;`ConcurrentHashMap` 包装零依赖但无过期/
无驱逐机制。遵循既有 optional 依赖范式(ADR-0001):Caffeine 声明为 Maven optional,
classpath 存在时默认启用能力更强的后端,不存在时回退纯 JDK 兜底,消费方零额外配置即可
获得可用(尽管能力较弱)的缓存。

**实现中的探针实证发现**:`CaffeineCacheManager` 类实际位于 `spring-context-support`
模块的 `org.springframework.cache.caffeine` 包,并非 `spring-context`(经反编译验证,
`spring-context:6.2.15.jar` 只含 `org.springframework.cache.concurrent`/`.support`
两个纯 JDK 实现包,不含 `.caffeine` 包)。这意味着仅声明 `caffeine` 一个 optional 依赖
不足以让 `caffeineCacheManager` bean 正常工作——必须让 `caffeine` 与
`spring-context-support` 成对声明为 optional 依赖,且 `@ConditionalOnClass` 需同时探测
两个类的存在(`com.github.benmanes.caffeine.cache.Caffeine` 与
`org.springframework.cache.caffeine.CaffeineCacheManager`);否则消费方只添加 `caffeine`
而漏配 `spring-context-support`(或反之)时,若条件只探测 Caffeine 一个类会误判为满足,
进而在 bean 方法体内触发 `NoClassDefFoundError`——这与"缓存不可用不阻断业务"的既定哲学
相悖(应静默回退到 `ConcurrentMapCacheManager`,而非在装配期抛错)。Spring Boot 自身的
`CacheAutoConfiguration.CaffeineCacheConfiguration` 采用同款"双类同时探测"写法,印证此
判断并非过度设计。

## 决策

1. **`CacheUtil` 复用 Spring 标准 `CacheManager`/`Cache` SPI,不自建缓存实现**。委托
   `SpringContextHolder.getBean(CacheManager.class)`,提供 `get`/`put`/`evict`/`clear`/
   `getOrCompute` 五个操作;与消费方直接使用的 `@Cacheable`/`@CacheEvict` 注解共享同一个
   `CacheManager` bean,编程式(`CacheUtil`)与声明式(`@Cacheable`)两种用法可自由混用
   同一份缓存状态。
2. **装配层(`FacilityCacheAutoConfiguration`)提供两个互斥的默认 `CacheManager` bean**,
   均 `@ConditionalOnMissingBean(CacheManager.class)`——消费方声明自己的 `CacheManager`
   即可整体覆盖两者:
   - `caffeineCacheManager`:`@ConditionalOnClass(name = {
     "com.github.benmanes.caffeine.cache.Caffeine",
     "org.springframework.cache.caffeine.CaffeineCacheManager" })`——同时探测 Caffeine
     本体与其 Spring 适配类,缺一即回退;TTL 经
     `Caffeine.newBuilder().expireAfterWrite(FacilityCacheProperties#getDefaultTtl())`
     应用,容量上限经 `.maximumSize(getMaximumSize())` 应用。
   - `concurrentMapCacheManager`:`@ConditionalOnMissingClass(
     "com.github.benmanes.caffeine.cache.Caffeine")`——classpath 无 Caffeine 时的纯 JDK
     兜底(`ConcurrentMapCacheManager` 包装 `ConcurrentHashMap`),不支持 TTL/大小上限;
     `FacilityCacheProperties` 的 `defaultTtl`/`maximumSize` 两字段在此后端下被忽略
     (已在字段 javadoc 注明)。
3. **条件注解统一用字符串全限定名形式**(`name = "..."`),不用类字面量
   (`Caffeine.class`)。Spring Boot 的条件求值经 ASM 读取字节码常量池,对缺失的可选类
   是安全的——类字面量形式与字符串形式在此等价可靠(Spring Boot 自身的
   `CaffeineCacheConfiguration` 用的正是类字面量形式)。选用字符串形式纯粹是写法一致性
   考量:`@ConditionalOnMissingClass` 只提供字符串形式的 `value()`(没有 `Class[]`
   重载),两个互斥条件统一写法、且不必为仅供条件判断使用的类型额外 `import`。可靠性经
   `FilteredClassLoader(Caffeine.class)` 回归测试实证(见
   `FacilityCacheAutoConfigurationTest#filteredCaffeine_fallsBackToConcurrentMap`,验证
   隐藏 Caffeine 类后确实回退到 `ConcurrentMapCacheManager`)。
4. **`caffeine` 与 `spring-context-support` 成对声明为 Maven optional**(pom.xml"缓存
   簇所需"段)——`CaffeineCacheManager` 实际类位于 `spring-context-support`(非
   `spring-context`),二者必须同时存在于消费方 classpath 上 Caffeine 后端才能装配;
   `caffeineCacheManager` 的 bean 方法体直接 `import` 并使用 `Caffeine`/
   `CaffeineCacheManager`,故 `dependency:analyze` 视两者为"已使用"依赖,不需要
   `ignoredUnusedDeclaredDependencies` 兜底(与 B1 阶段 `CacheUtil` 本身不直接引用
   Caffeine、需要暂缓引入该依赖的情况不同)。
5. **缓存降级不阻断业务**:`CacheUtil` 在容器不存在 `CacheManager` bean 时,`get`
   返回空 `Optional`,`put`/`evict`/`clear` 变为 no-op,`getOrCompute` 直接调用 loader
   并返回其值(不缓存)。同理,`caffeineCacheManager`/`concurrentMapCacheManager` 均无法
   装配的边界场景(如 Caffeine 存在但 `spring-context-support` 缺失,反之亦然)下,容器
   内没有任何 `CacheManager` bean,`CacheUtil` 的上述降级路径同样兜底——不会抛出装配期
   异常,只是缓存能力整体缺席,业务方法照常执行(等价于直调 loader)。

## 备选(否决)

- **自建缓存实现**(仿 `TokenBucketRateLimiter` 手写 `ConcurrentHashMap` + TTL):需要
  重新实现过期/驱逐/并发控制,且与消费方既有的 `@Cacheable` 生态割裂,否决;
- **`@ConditionalOnClass` 只探测 `Caffeine` 一个类**(不探测 `CaffeineCacheManager`):
  消费方漏配 `spring-context-support` 时会在 bean 方法体内触发 `NoClassDefFoundError`,
  与"缓存不可用不阻断业务"哲学相悖,否决;
- **默认强制内置 Caffeine(非 optional)**:为所有消费方(含只需最简单缓存、或已有自己的
  Redis/EhCache 方案的场景)强塞一个依赖,与 optional 依赖范式(ADR-0001)冲突,否决;
- **`ConcurrentMapCacheManager` 手动补齐 TTL**(如包装定时清理线程):违背"纯 JDK 兜底
  越简单越好"的定位,且引入线程管理复杂度,否决——接受"回退后端无 TTL/无大小上限"作为
  已知限制,已在 `FacilityCacheProperties` 字段 javadoc 显式警示消费方。

## 后果

- 消费方零配置即获得可工作的 `CacheManager`(Caffeine 优先,ConcurrentMap 兜底);添加
  `caffeine` + `spring-context-support` 两个 optional 依赖即可从"无 TTL 兜底"升级到
  "有 TTL/容量上限"的生产级本地缓存,无需改动业务代码(`CacheUtil`/`@Cacheable` 调用点
  不变)。
- `FacilityCacheProperties` 的 `defaultTtl`/`maximumSize` 语义是否实际生效取决于最终
  装配的后端——字段 javadoc + 本 ADR 必须显式说明,否则消费方可能误以为纯 JDK 兜底也
  遵守 TTL。
- 消费方若只添加 `caffeine` 而漏配 `spring-context-support`(或反之),会静默回退到
  `ConcurrentMapCacheManager`(无 TTL)而非报错——不阻断启动,但也不会有任何提示;
- **Carry-forward**:README/USAGE 的 optional 依赖矩阵尚需在计划收尾阶段补充"caffeine
  + spring-context-support 成对添加"的说明,避免文档只提 `caffeine` 一项引发消费方
  漏配;本任务(B2)未涉及文档三件套,留给计划收尾步骤统一处理。
