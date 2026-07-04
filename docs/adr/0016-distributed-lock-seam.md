# ADR-0016: 分布式锁 DistributedLock SPI seam + 单机 InMemoryDistributedLock 默认实现

- **状态**:Accepted(2026-07-04)
- **源起**:幂等+分布式锁+HTTP client 实现计划(docs/superpowers/plans/2026-07-04-idempotency-lock-http.md),簇 C

## 背景

server-facility 此前无锁组件。计划 §簇C 为服务端组件库新增互斥锁能力,用于保护"同一维度
(如订单号、用户 ID)在同一时刻只应有一个执行路径"的临界区场景。与限流(ADR-0014)、缓存
(ADR-0015)两簇面对的取舍类似:server-facility 定位为通用组件库,消费方既有单机部署也有
多实例部署——单机场景 JDK 自带的 `ReentrantLock` 零依赖、实现成本最低,足以满足"进程内
互斥"需求;多实例场景需要跨节点共享锁状态(如 Redis/Redisson、ZooKeeper、数据库行锁),
但该依赖不应成为库的默认强制项,与既有 optional 依赖范式一致(ADR-0001)。

锁与限流/缓存在语义严格度上有一个关键差异:限流"漏放几个请求"、缓存"缓存未命中多算一次"
后果通常可接受,但锁的核心承诺是**互斥**——如果默认实现或降级路径悄悄放弃了互斥保证,
消费方可能在生产环境遭遇难以复现的数据竞争。因此本 ADR 需要比 ADR-0014/0015 更明确地
标注"什么场景下默认实现/降级路径不提供真正的互斥保证",而不是简单复制"缺席不阻断业务"
的降级哲学。

## 决策

1. **SPI:`DistributedLock` 接口**。三个方法:`tryLock(String key, Duration leaseTime)`、
   `unlock(String key)`,以及两个 `default executeWithLock` 重载(`Supplier`/`Runnable`)
   ——获取锁、执行动作、无论正常返回还是抛出异常均在 `finally` 释放锁,获取失败抛
   `LockAcquisitionException`。装配层 `@ConditionalOnMissingBean(DistributedLock.class)`,
   消费方注册自定义 bean(如基于 Redisson)即可整体替换默认实现,不影响调用方代码
   (`LockUtil`/直接注入 `DistributedLock` 均不变)。

2. **默认实现:单机 `InMemoryDistributedLock`**。`ConcurrentHashMap<String, ReentrantLock>`,
   每个 key 对应一个独立锁。**语义边界必须显式声明**(已落 `InMemoryDistributedLock`
   javadoc):
   - `leaseTime` 仅是 `tryLock` 的**等待获取超时**,不是"持锁后自动过期释放"的真租约——
     锁一旦获取,只有显式 `unlock` 才会释放,不会像 Redisson 等分布式实现那样在租约到期
     后自动释放;
   - **可重入**:同一线程可对同一 key 多次 `tryLock` 而不阻塞自己(`ReentrantLock` 语义
     直接继承);
   - **进程内**:仅在当前 JVM 进程内互斥,无法跨进程/跨实例协调,进程崩溃也不会自动
     释放锁(不像 Redisson 有看门狗或租约到期机制兜底)。

3. **单机 vs 分布式的三个关键差异**(消费方决定是否需要升级到真正分布式实现的判断依据):

   | 维度 | `InMemoryDistributedLock`(单机) | 分布式实现(如 Redisson) |
   |---|---|---|
   | 互斥范围 | 仅当前 JVM 进程内 | 跨进程/跨实例 |
   | 持锁方崩溃 | 锁永久占用(直到 JVM 重启清空 map) | 租约到期自动释放(看门狗续期健康进程的锁) |
   | `leaseTime` 语义 | 仅等待获取的超时 | 真实租约(到期自动释放)+ 可选等待超时 |

   多实例部署下需要跨进程互斥、故障自动释放(真租约)时,必须替换为真正的分布式实现。

4. **无界 key 防护**:`InMemoryDistributedLock` 按 key(常来自订单号、用户 ID 等外部输入)
   建锁,key 基数不可控。当锁数达到 `maxLocks`(`FacilityLockProperties`,默认
   `100_000`)且待建 key 不在集合中时,整体 `clear()` 并记录 WARN 日志——以短暂的失锁
   风险换取内存安全,与 ADR-0014 令牌桶、ADR-0015(间接,经 Caffeine `maximumSize`)一致
   的防护策略。

5. **编程式门面 `LockUtil` + 降级权衡**。`SpringContextHolder.getBean(DistributedLock.class)`
   查找容器 bean:
   - `tryLock`/`unlock` 用 `.map(...).orElse(...)` 委托,无 bean 时 `tryLock` 降级放行
     (返回 `true`)、`unlock` 降级为 no-op;
   - `executeWithLock` 用 `isErr()` 显式判空(而非 `.map(...).orElse(...)`)——降级路径
     需要在直接执行 `action` **之前**记录一条 WARN 日志(`"无 DistributedLock bean,退化
     无锁执行: key={}"`),`.map` 链式写法无法在"无值"分支插入日志副作用,故此处退回显式
     分支判断,与 `tryLock`/`unlock` 的写法风格刻意不同、职责不同。
   - **风险披露(不同于限流/缓存的降级哲学)**:限流退化为"放行"、缓存退化为"不缓存",
     后果是性能/保护性下降,业务正确性不受影响;但锁的核心承诺是互斥,`executeWithLock`
     无 bean 时"直接执行 action"意味着**完全放弃互斥保证**——单实例部署下这与"没有配置锁"
     等价,可以接受;但**多实例部署下如果 `DistributedLock` bean 因配置疏漏未装配,所有
     实例都会各自无锁执行,跨实例的互斥期望被静默破坏,且只有一条 WARN 日志作为唯一线索**。
     消费方必须确保生产环境(尤其多实例)该 bean 确实在场——这是本 ADR 对"降级不阻断业务"
     范式的必要限定:降级在这里换来的是可用性,不是正确性上的免费午餐。

6. **real seam 升级示范:接入 Redisson**。消费方只需声明一个 `DistributedLock` bean 即可
   整体覆盖默认实现(`@ConditionalOnMissingBean` 自动让位),骨架示例:

   ```java
   public class RedissonDistributedLock implements DistributedLock {
       private final RedissonClient redisson;

       public RedissonDistributedLock(RedissonClient redisson) {
           this.redisson = redisson;
       }

       @Override
       public boolean tryLock(String key, Duration leaseTime) {
           try {
               // 三参 tryLock(waitTime, leaseTime, unit):waitTime 复用同一 Duration 作为
               // 等待获取的超时,leaseTime 是 Redisson 的真租约——到期后即便持锁进程崩溃、
               // 未调用 unlock,Redis 端也会自动释放,这是相对 InMemoryDistributedLock
               // 的关键差异(见决策 2)。生产场景通常会为两者拆分独立可配置的 Duration,
               // 此处为骨架示例复用同一个入参。
               return redisson.getLock(key)
                       .tryLock(leaseTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
           } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return false;
           }
       }

       @Override
       public void unlock(String key) {
           RLock lock = redisson.getLock(key);
           if (lock.isLocked() && lock.isHeldByCurrentThread()) {
               lock.unlock();
           }
       }
   }

   @Configuration
   public class RedissonLockConfiguration {
       @Bean
       public DistributedLock distributedLock(RedissonClient redisson) {
           return new RedissonDistributedLock(redisson);
       }
   }
   ```

   消费方引入 `redisson-spring-boot-starter`(或直接构造 `RedissonClient`)、声明上述
   `DistributedLock` bean 后,`FacilityLockAutoConfiguration` 的 `@ConditionalOnMissingBean`
   条件不再满足,默认的 `InMemoryDistributedLock` 不再装配;`LockUtil`/直接注入
   `DistributedLock` 的调用方代码不需要任何改动——这正是"seam"设计的目的:替换实现而不
   改变调用契约。

7. **装配无 web 条件**:`FacilityLockAutoConfiguration` 的 `facilityDistributedLock` bean
   不带 `@ConditionalOnWebApplication`——纯通用能力,非 web 场景(批处理、定时任务)可
   直接注入 `DistributedLock` 或经 `LockUtil` 编程式使用,与限流簇的 SPI bean(无 web
   条件)一致,不同于限流簇需要额外拆分 `web.ratelimit` 拦截器(锁本身没有 HTTP 请求粒度
   的默认拦截需求,不存在 ADR-0014 决策 5 那种 web↔ratelimit 环风险)。

## 备选(否决)

- **`executeWithLock` 无 bean 时抛异常而非降级执行**:与限流/缓存"基础设施缺席不阻断
  业务"的既定范式不一致,且单实例部署下这类异常没有实际防护价值(单实例本就无需跨进程
  互斥),否决——但决策 5 已明确记录多实例场景的风险,不是没有权衡地照搬限流哲学;
- **默认内置 Redisson(非 optional)**:为所有消费方(含单机部署)强塞一个可能用不到的
  依赖,与 optional 依赖范式(ADR-0001)冲突,否决;
- **`leaseTime` 参数拆分为 `waitTime`+`leaseTime` 两个独立参数**:更贴近 Redisson 等
  分布式实现的真实语义,但会让 SPI 签名从一开始就假设"真租约"存在,与
  `InMemoryDistributedLock` 的实际能力(无真租约)不符;保留单一 `Duration` 参数、
  在 javadoc/本 ADR 显式声明其含义随实现而变,是更诚实的接口设计——真正需要区分两者的
  消费方,可以在自定义 `DistributedLock` 实现内部另行读取专属配置(如 ADR 决策 6 骨架
  示例的注释所述);
- **无界 key 防护改用 LRU 驱逐**:与 ADR-0014 决策否决项理由一致(需要额外的访问顺序
  簿记,与"纯 JDK 默认实现"定位冲突),否决,沿用整体清空策略。

## 后果

- 单机场景零额外依赖即可用(`InMemoryDistributedLock` 纯 JDK);分布式场景消费方按需
  替换 `DistributedLock` bean,facility 不背书具体分布式方案,`LockUtil` 调用点不变。
- `executeWithLock` 的降级路径(无 bean → 直接执行 + WARN)是本 ADR 重点披露的风险点:
  多实例部署必须确保 `DistributedLock` bean 在场,否则跨实例互斥会被静默破坏——这一点
  已在 `LockUtil` javadoc、本 ADR 决策 5 双重记录,后续文档收尾阶段(USAGE)需要再次
  强调,避免消费方只读到"降级不阻断业务"这半句就忽略互斥语义的特殊性。
- `packages_are_cycle_free` ArchUnit 规则对新增的 `lock` 包保持绿,`lock` 无 web 依赖、
  未拆分 `web.lock`,未引入新的包间边。
- **Carry-forward**:README/USAGE/DESIGN 文档三件套的锁特性矩阵、装配开关表、ADR 索引
  更新留给计划收尾阶段统一处理(与 ADR-0015 的 Carry-forward 处理方式一致),本任务
  (C2)未涉及。
