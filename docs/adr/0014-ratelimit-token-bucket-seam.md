# ADR-0014: 限流令牌桶算法选择 + RateLimiter SPI seam

- **状态**:Accepted(2026-07-03)
- **部分替代（2026-10-04）**：ADR-0029 替代无条件采用转发头作为 IP 的部署假设；默认连接 peer，显式可信 CIDR 才读取 XFF。令牌桶、RateLimiter SPI 及本 ADR 其余历史理由保留；限流新语义由对应票负责。
- **部分替代（2026-10-04）**：[ADR-0032](0032-local-rate-limit-contract.md) 替代决策1的数值/并发实现、决策3的无条件放行、决策4的整体清空、决策6的操作/身份组装；保留纯JDK本地令牌桶、SPI及Web分包理由。历史正文保留，不再作为这些旧行为的当前承诺。
- **源起**:限流+缓存实现计划 簇A(docs/superpowers/plans/2026-07-03-ratelimit-cache.md),spec §10 新组件

## 背景

server-facility 此前无限流组件。spec §10 为服务端组件库新增限流能力,需要在"纯 JDK
零强制依赖"与"生产场景可扩展到分布式"之间取舍算法与装配方式。业界常见限流算法:

- **令牌桶(token bucket)**:允许合理突发(消耗桶内存量),恒定速率补充,单机实现只需
  一把锁/一个原子状态,是 Guava `RateLimiter`、Bucket4j 等主流库的默认算法。
- **滑动窗口(sliding window)**:统计精度更高(无固定窗口边界突变问题),但需要维护
  时间序列(滑动日志或加权计数),内存/计算开销高于令牌桶。
- **固定窗口(fixed window)**:实现最简单,但边界突变允许 2 倍突发(临界问题),精度最低。

server-facility 定位为通用组件库,消费方既有单机部署也有多实例部署——单机场景令牌桶
"允许合理突发+实现成本最低"最贴合默认期望;多实例场景需要跨节点共享状态(如 Redis),
但该依赖不应成为库的默认强制项,与既有 optional 依赖范式一致(ADR-0001)。

## 决策

1. **默认算法:令牌桶**。`TokenBucketRateLimiter`(`ConcurrentHashMap<String, TokenBucket>`,
   每 key 一个独立 `TokenBucket`、`synchronized` 方法级锁)——纯 JDK,零第三方依赖,
   单机场景够用。
2. **可替换 Seam:`RateLimiter` 接口**。装配层
   `@ConditionalOnMissingBean(RateLimiter.class)`,消费方注册自定义 bean(如委托 Redis
   `INCR`+`EXPIRE`,或 Redisson `RRateLimiter`)即可整体替换默认实现;跨节点一致性由
   消费方自选的实现负责,facility 本身不内置该依赖。
3. **降级不阻断业务**:编程式门面 `RateLimiterUtil`(`SpringContextHolder.getBean(
   RateLimiter.class).map(...).orElse(放行值)`)在容器不存在 `RateLimiter` bean 时
   一律放行(`tryAcquire` 返回 `true`;`acquire` 返回
   `new RateLimitResult(true, Long.MAX_VALUE, 0)`)。限流是保护性附加能力而非核心
   业务逻辑,其基础设施(bean 未装配等)缺席不应连带拖垮受保护的业务接口。
4. **无界 key 防护**:`TokenBucketRateLimiter` 按 key(常来自用户 ID、IP 等外部输入)
   建桶,key 基数不可控。当桶数达到 `maxBuckets`(`FacilityRateLimitProperties`,
   默认 `100_000`)且待建 key 不在集合中时,整体 `clear()` 并记录 WARN 日志——以短暂的
   限流状态重置换取内存安全。
5. **web 集成分离到 `cn.code91.facility.web.ratelimit`**:通用限流类型(`RateLimiter`
   SPI / `RateLimitResult` / `TokenBucket` / `TokenBucketRateLimiter` / `RateLimiterUtil`)
   留在 `cn.code91.facility.ratelimit`,零 web 依赖,可在非 web 场景(批处理、定时任务)
   直接复用;`@RateLimit` 注解 / `RateLimitInterceptor` / `RateLimitExceededException`
   三个 web 专属类型移至 `cn.code91.facility.web.ratelimit`。原因:ArchUnit
   `packages_are_cycle_free` 规则按顶层子包聚合 slice,`web.*` 全部归入同一个 `web`
   slice。若限流全放在顶层 `ratelimit` 包,拦截器需要 `ratelimit→web.util`
   (`RequestUtil.getClientIp` 取默认 key)而全局异常处理器需要
   `web.exception→ratelimit`(捕获 `RateLimitExceededException`)——两条边方向相反,
   在 `ratelimit`/`web` 这对顶层 slice 之间成环。拆分后两条边分别改写为
   `web.ratelimit→web.util` 与 `web.exception→web.ratelimit`,都落在 `web` slice
   **内部**,不再跨顶层 slice,`packages_are_cycle_free` 保持绿。
6. **默认 IP key 的受信代理假设(安全边界)**:`@RateLimit` 空 `key()` 时按 `类#方法#clientIp`
   限流,`clientIp` 取自 `RequestUtil.getClientIp`——它信任 `X-Forwarded-For` 头,而该头**可被
   客户端伪造**。因此默认 IP 维度限流**仅在前置受信反向代理覆写 XFF 的部署下可靠**。公网直连服务
   若依赖默认 IP key,存在两个后果:①**绕过**——攻击者轮换伪造 IP,每个伪造 IP 获得独立满桶;
   ②**放大**——伪造海量唯一 IP 顶到 `maxBuckets` 触发决策 4 的 `clear()`,抹掉所有合法用户的限流
   状态,且可重复。缓解:公网服务设显式 `key()`(如已认证用户 ID),或经 SPI 注入受信代理感知的 key
   策略。此边界已在 `@RateLimit.key()` / `RateLimitInterceptor` / `web.ratelimit` package-info /
   USAGE 限流节标注。

## 备选(否决)

- **默认算法用滑动窗口**:精度更高,但内存/CPU 成本对"通用组件库默认值"这个场景不划算
  ——多数限流场景(接口保护、防刷)能接受令牌桶允许的短时突发,精度不是主要诉求;
- **默认算法用固定窗口**:实现最简,但边界突变允许 2 倍突发不符合限流的直觉语义,否决;
- **默认内置 Redis 支持**(如新增 spring-data-redis 强制依赖):会为所有消费方(含单机
  部署)强塞一个可能用不到的依赖,与 optional 依赖范式冲突(ADR-0001);SPI 化后消费方
  按需自行接入分布式限流实现,facility 保持零强制新依赖;
- **无界 key 防护改用 LRU 驱逐**:更精细,但需要额外的访问顺序簿记(如
  `LinkedHashMap` 访问序 + 同步包装,或依赖 Caffeine 等第三方实现),与"纯 JDK 默认
  实现"的定位冲突;整体清空是更简单的降级路径,代价是短暂的全局限流重置,在
  防护性质的兜底场景可接受。

## 后果

- 单机场景零额外依赖即可用;分布式场景消费方按需替换 `RateLimiter` bean,facility
  不背书具体分布式方案。
- 限流基础设施缺席(容器无 `RateLimiter` bean)与业务接口可用性解耦;但若消费方
  自定义的 `RateLimiter` 实现在**存在**时抛出运行时异常,门面/拦截器不捕获转化——
  `Result.map` 直接转发 lambda 抛出的异常,降级路径只覆盖"无 bean"这一种缺席场景,
  不覆盖"有 bean 但内部抛异常"。
- `packages_are_cycle_free` ArchUnit 规则对新增的 `ratelimit`/`web.ratelimit` 两个包
  保持绿,未需要为限流引入例外或放宽规则。
