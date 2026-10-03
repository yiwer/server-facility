# ADR-0032: 本地配额的成本、主体与有界准入契约

## Status

Accepted

日期：2026-10-04

部分替代 ADR-0014 决策1的数值/并发实现、决策3的无条件放行、决策4的整体清空，以及决策6的操作与身份组装；保留纯JDK本地令牌桶、可替换SPI、Web分包避环的理由。转发信任仍由 ADR-0029 负责。旧理由保留供历史评审，中央索引由集成者登记。

## Context

正式票09对应 FR-03/FR-05、AC-04/08/09。预研普通jar反例证明负cost恢复余额，新增key达到上限会恢复已耗尽主体额度。原double余额在Long.MAX_VALUE容量下连减1都可能丢失；满桶毫秒等待也不能代表部分补充后的真实等待。旧控制器简单类名/方法名发生跨包及重载碰撞，缺Adapter与Adapter故障政策不一致，幂等重放可先于入口扣费短路。

登录/短信/付费配额与普通过载保护的失败代价不同，因此没有一个对所有调用都正确的隐式放行政策。继续采用小型RateLimiter接口，不增加配置DSL或分布式后端。

## Decision

### 核心输入和数值

保留 `RateLimiter.acquire(key, int cost, long capacity, double rate)`、`tryAcquire` 和原构造器。key为1..512个UTF-16代码单元、非blank、无ISO控制字符，区分大小写且不规范化Unicode。容量和maxBuckets必须为正；cost必须为正且不大于容量；rate必须有限且大于0，允许Double.MIN_VALUE。非法输入在建桶和扣费前抛IllegalArgumentException。相同驻留key固定capacity和rate，冲突不修改余额。回收后该key不再持有政策记录；应用应使用稳定政策，发布时主动迁移policy别名。

余额用有限double速率的规范十进制表示（BigDecimal.valueOf）与纳秒差值精确计算，整数扣减不会被浮点精度吞掉。remaining是同次原子判定后的向下取整余额。拒绝等待为实际缺额除速率向上取整到毫秒，超过long范围饱和到Long.MAX_VALUE；允许等待0。HTTP沿用04的向上取整秒与最少1秒Retry-After，不溢出。

新增四参数构造器接受宿主 `LongSupplier nanoTime`；默认System.nanoTime，不创建定时器或线程。负/零差值不补充也不后移锚点；signed wrap按差值处理。外部时间源必须单调、连续两次可观察间隔小于2^63纳秒；不提供墙钟修正或292年以上间隔的保证。

### 状态和资源

一个限流器实例维护严格maxBuckets槽位，key长度有界；准入、扣减和显式clear共用一把锁。没有按请求创建的后台任务。新key满额时最多检查16个轮转候选，只回收已经完全补满的桶；无法安全腾出槽位抛RateLimiterUnavailableException。轮转避免永久只扫描冷头部，但单次可能保守拒绝，即使未扫描位置存在可回收桶。新主体不能驱逐带欠额的桶。`clear()`保留为宿主显式重置全部额度的管理操作，任何自动路径都不调用它。

默认100000槽位是key数量上界，绝非100000字节；需要结合最长key、数值状态和并发压测选择较小部署预算。BigDecimal状态的位数受long容量、有限double速率和long时间差约束，没有任意精度用户输入。全局锁降低多主体峰值吞吐，用来换取严格准入和一致判定；不承诺吞吐SLA或等待线程数，调用并发由宿主预算限制。

### 故障与装配

构造器注入RateLimiter为新服务首选。兼容静态RateLimiterUtil普通入口要求设施存在且正常；缺席、准入不可用、Adapter运行故障抛RateLimiterUnavailableException并保留服务端cause。新增名字明确的tryAcquireOptional/acquireOptional只在设施不可用时放行（remaining=-1表示未知），非法输入/政策冲突及Error仍传播。

`facility.ratelimit.fail-open=false`默认。Servlet注解缺Adapter或RuntimeException故障通过04策略返回安全503，Error不被降级。显式true只适用于入口保护的可接受降级，不掩盖注解非法政策/Adapter IllegalArgumentException，不把确定的quota拒绝改为允许。宿主SPI返回null视为设施故障。`enabled=false`只关闭默认TokenBucketRateLimiter；Servlet注解守卫仍装配，避免受保护方法因缺Bean默默执行。用户RateLimiter、RateLimitInterceptor及同名WebMvcConfigurer继续让位。

### 主体与操作

`@RateLimit.scope`提供IP、PRINCIPAL、GLOBAL；DEFAULT保留旧确定语义：空key选IP，非空固定key选GLOBAL。推荐新代码显式选择scope。IP直接使用06的RequestUtil来源快照，默认连接peer，显式trusted-proxies才采用可信链；无DNS查询。PRINCIPAL只用Servlet宿主Principal的name，必须非blank、无控制字符且不超过128 UTF-16代码单元；缺失或非法返回安全403，绝不回落IP或读取任意用户头。宿主必须认证并提供跨租户唯一的稳定主体名；JWT、认证挑战和Security配置属于27。

空key的操作是完整用户类名、方法名和参数类型；非空key是显式共享操作别名，不解析表达式。别名全blank非法。scope命名空间、操作长度和主体组合为不透明key，最终仍受512单元上界；超长方法签名可用明确短别名。用户SPI不得依赖旧拼接格式，升级时迁移已有外部key。不同scope不互相挤占同一个桶；固定别名在同scope下有意共享政策。

### 入口计费与幂等接合（J07）

自动MVC注册order=Ordered.HIGHEST_PRECEDENCE+20，在默认幂等拦截器order=0之前；06请求边界和认证Filter先于MVC运行。每个独立受保护请求先扣入口额度，首次、重放、处理中、异内容冲突均计费，不因后续业务失败自动退款。这里防护的是入口工作量；业务/付费额度须由12/29的业务执行与事务政策决定，不能把该注解当作只收取成功效果一次的配额。

同一请求成功扣费后只保存一条收据（方法/key/cost/capacity/rate），相同保护方法和政策的ASYNC重派发复用它；新的请求、不同操作或主体不跳过。收据随Servlet请求结束释放，不进入全局表。当前真实HTTP验证旧幂等重放两次占满容量2、第三次429、业务只执行一次；12/29必须补齐处理中/内容冲突/事务结果的最终候选复跑，不在09虚报完成后续状态机。

## Consequences

**Positive**：负成本、浮点整数丢失、键洪泛恢复余额、重载碰撞和隐式缺设施放行被公共入口回归覆盖。默认实现只需JDK，核心与Web仍分包，HTTP使用同一安全错误策略。

**Negative**：缺Bean及容量耗尽从允许变为拒绝；enabled=false不会取消注解保护；SPI key格式改变；固定预算到顶可能保守拒绝新主体；全局锁与十进制运算有成本。依赖旧行为的应用必须明确选择Optional/fail-open或调整主体预算，不能靠关闭配置绕过必需配额。

**Carry-forward**：单实例配额不等于集群额度，进程重启/clear会重置；共享或持久配额由宿主Adapter及其真实后端测试负责，本库不背书Redis保证。11/12/29/33继续验证重试计费与业务事务的最终组合。Linux和发布候选证据由本票验证记录追踪。

## References

1. [Java 25 System.nanoTime](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#nanoTime())：差值比较及计时范围。
2. [Java 25 BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html)：valueOf(double)采用规范字符串十进制表示。
3. [运行契约研究](../research/2026-10-03-runtime-contracts.md) §5；[正式票09](../../.scratch/server-facility-next/issues/09-rate-limit-contract.md)；[共同Q/J矩阵](../superpowers/plans/2026-10-03-server-facility-next-test-strategy.md)。
4. [ADR-0014](0014-ratelimit-token-bucket-seam.md)、[ADR-0027](0027-safe-http-error-policy.md)、[ADR-0029](0029-request-boundaries.md)。

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
