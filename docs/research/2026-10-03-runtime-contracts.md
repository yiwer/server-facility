# 运行时能力：从工具接口转向可兑现的保证

研究日期：2026-10-03。源码基线：`0ee9d547022371ad31f885605e999de17ec22777`。本文是改造提案，未修改生产源码、POM 或已接受的 ADR。

覆盖 `async`、`id`、`cache`、`ratelimit`、`lock`、`idempotency` 六个顶层包。相关 Web 与装配问题见[Web 研究](2026-10-03-web-and-agent-design.md)，总体取舍见[架构方案](2026-10-03-architecture-proposal.md)。源码路径均相对仓库根，行号基于上述提交。

## 1. 最重要的架构判断

这六个 Module 的共同问题是：Java 方法很少，但调用者必须了解的隐藏条件很多。默认节点号、JVM 范围、租约与等待、过期与在途、执行器与 ThreadLocal、classpath 与 TTL，都是 Interface 的组成部分。把方法藏在静态 `Util` 后面，并没有隐藏这些复杂性。

应把这些保证分成三类：

| 类别 | 能力 | 合理失败方式 | 不可接受的默认行为 |
|---|---|---|---|
| 业务正确性 | ID 唯一性、幂等、互斥 | 拒绝执行、配置期失败、明确冲突 | 返回成功但取消保证 |
| 资源保护 | 并发预算、限流、任务截止时间 | 按用途明确拒绝/降级策略 | 默认所有场景都放行 |
| 性能优化 | 普通可丢失缓存 | 有界缓存或显式禁用 | 配置 TTL 后悄悄换成永不过期缓存 |

这是本研究的设计建议，不声称所有行业都采用一种策略。短信防刷与计费配额中的限流也参与业务正确性，不能沿用 ADR-0014“限流永远只是保护性附加能力”的分类。

## 2. Async：避免维护半套并发运行时

**已有价值。** 惰性组合、错误聚合、首个成功结果、显式执行器和拦截器，确实比散落的 Future 拼接集中。现有测试覆盖组合与错误分支，不能简单按“重复 JDK”删除。

**实际场景。** 一个 HTTP 请求并发查询库存、价格和运费。调用方真正需要的是整体截止时间、最大并发数、失败后的剩余任务处置、trace 传播，以及任务是否仍可能产生副作用。

**源码证据。**

- `async/DefaultAsync.java:52–63`：未指定 executor 时，每次提交创建 `newVirtualThreadPerTaskExecutor()`，没有把生命周期交给 Spring；`all`/`any` 的子任务各自 `submit()`（95–97、137–138），外层 executor 不是整棵任务树的预算。
- `async/DefaultAsync.java:262–276`：超时只改变观察结果，底层计算继续；`exceptionally` 又把非超时异常完成转换为 `TimeoutException`，改变了错误类别。
- `async/DefaultAsync.java:317–332`：拦截器在提交线程构建并执行，任务随后异步执行。`AsyncInterceptor` 的动态数据源示例先设置调用线程 ThreadLocal，再在 future 回调里清理；这不能保证同一线程上的成对设置与清除。
- `autoconfigure/FacilityAsyncAutoConfiguration.java:35–39`：装配了 executor，但默认 `Async.supply` 不查找也不注入它。不能把“有一个 bean”当作运行路径已经受它管理。

**成熟实现与取舍。** JDK `CompletableFuture.cancel` 不保证中断底层计算；`orTimeout` 是完成状态机制，不能据此承诺真实停止。Spring Boot 有受容器管理的任务执行器，虚拟线程可通过标准配置启用。JDK 25 的 Structured Concurrency 仍为预览，不应成为发布库的强制公开依赖。[JDK CompletableFuture](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html)、[Boot 任务执行](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)、[JDK 25 预览列表](https://docs.oracle.com/en/java/javase/25/docs/api/preview-list.html)

**建议。** 默认应用用 MVC + 普通同步应用服务；执行器由 Boot 管理，阻塞 I/O 场景验证后启用虚拟线程，不因升级 JDK 就强制全局开启。先停止扩展自制 Async DSL。真正有多任务组合场景时，集中到应用侧一个小 Module，注入 executor、Clock/截止时间和并发预算，返回真实错误；中断只承诺协作式取消，不声称所有 I/O 都能立即停止。事务在实际执行业务的线程内建立，不传播现有 JDBC 事务到另一线程。旧 Async 暂时兼容，生命周期/上下文/错误类别修复后再评估是否保留为独立能力。

**验收。** 任务在指定 executor 运行；父级截止时间不延长；trace 与安全身份按策略传播且清理；首成功后其余任务有明确处置；超时不改写 AssertionError/RejectedExecutionException；应用关闭不遗留无主任务。容量测试同时观察下游连接池，而非只统计虚拟线程数。

## 3. ID：默认唯一性应不依赖人工分配节点

**已有价值。** SnowId 有可注入时钟、回拨处理、实例 epoch 解析；这是有行为的 Module。问题集中在默认部署假设。

`id/FacilityIdProperties.java:29–45` 默认节点 `(0,0)`；`id/IdUtil.java:50` 的静态 fallback 同样如此；`id/support/SnowIdGenerator.java:45–55` 两组各 2 bit，只有 16 个节点组合。两个配置相同的实例在相同毫秒、相同序列位置会产生同 ID。ID 的“分布式”保证来自外部节点分配协议，不来自这个实现本身。

`SnowIdGenerator.java:171–201` 的有界 spin 用同一个可回拨的 wall clock 计算超时；若时钟冻结在目标之前，elapsed 不增加，所谓有界不成立。false 模式不可中断等待是 ADR-0023 明确接受的行为，应作为需要重新决策的可用性取舍，而不是把它误报成未实现承诺。JDK `parkNanos` 在中断状态下可立即返回，保留中断标志而一直 park 也不等于每轮真正休眠。[LockSupport](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/LockSupport.html)

**建议。** 模板默认使用 JDK UUID v4；关系库内部主键也可以直接由数据库生成。只有明确需要时序索引局部性时引入成熟 UUIDv7 实现；RFC 9562 定义了 v7 时间布局，但没有替应用决定排序、可枚举性或数据库主键策略。旧 SnowId 移为显式选择：要求节点来源、拒绝未配置、时钟等待采用单调计时和可中断 deadline。已有数据库 ID 不原地重写，新增 ID 类型通过版本化接口迁移。[RFC 9562](https://datatracker.ietf.org/doc/html/rfc9562)

**验收。** 默认模板多实例不需协调 workerId；选择 SnowId 时重复节点配置有部署约束及测试；回拨、冻结、序列耗尽有有界行为；大整数的 JSON 表示契约明确。不要为“JavaScript 精度”声称当前日期下所有 SnowId 都已经溢出安全整数；这是生命周期内必须设计的协议约束。

## 4. Cache：复用标准是正确方向，静默更换语义不是

**已有价值。** ADR-0015 选择 Spring CacheManager/Caffeine，避免自建缓存协议，这一决定应保留。

`cache/CacheUtil.java:114–121` 是 get → loader → put，不能合并同 key 的并发加载；`Optional` 还把缓存中的 null 与未命中合并。`FacilityCacheAutoConfiguration` 缺 Caffeine 时使用无 TTL/无上限的 ConcurrentMap。两个可选依赖仅缺其一的情况下，条件分支和注释/ADR 有互相矛盾的描述，详见 Web/装配研究。

**场景。** 商品详情缓存可以在失效时直查数据库；权限快照若约定最多缓存 30 秒，就不能在部署漏依赖后永久缓存。这两个用途不能由同一个“缓存不可用不影响业务”的口号统领。

**成熟实现。** Spring `Cache.get(key, Callable)` 暴露加载操作，并要求实现尽可能协调并发加载；Caffeine 的 `get(key, mappingFunction)` 提供原子的计算与插入。应核实选定 Adapter 的保证，不能把 Spring SPI 的建议等同于所有实现都保证只加载一次。[Spring Cache](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/cache/Cache.html)、[Caffeine Population](https://github.com/ben-manes/caffeine/wiki/Population)

**建议。** 默认不自动给所有应用安装 CacheManager。应用选择本地缓存能力后，starter/模板显式引入 Caffeine 并使用 Boot 配置；没有后端时，显式禁用或启动失败。普通业务直接注入 Spring Cache/使用 `@Cacheable`，只有需要封装跨请求合并、stale-while-revalidate 等真实策略时才创建新的 Module。删除静态 bean 查找门面，避免又造一层通用 Cache API。

**验收。** 用并发集成测试检查同 key 加载策略、null、异常不缓存、TTL、最大容量；授权信息另测失效语义；每种选定依赖组合均启动或给出确定配置错误。

## 5. Rate limit：算法、身份和预算范围是一份契约

**已有价值。** 每 key 的同步令牌更新与 `nanoTime` 补充，避免普通并发超发；Web 适配分离也有价值。

**证据。** `ratelimit/TokenBucketRateLimiter.java:62–72` 不校验每次调用的 permits/capacity/rate；负 permits 会加回余额。满集合时 `clear()` 重置所有已耗尽桶；61 以后同 key 的第一次配置固定，但 retryAfter 又用本次传入 rate 计算。测试 `maxBuckets` 用例主动锁定重置满桶行为。这是既有产品决定需要改，而不是靠再提高覆盖率解决。

**方案比较。** Bucket4j 已实现令牌桶、ConsumptionProbe 等结果及多种分布式集成；适合作为配额型限流的候选。Resilience4j 的隔离/熔断适合下游容错；入口用户配额和下游并发预算不是一个概念。不为采用成熟库而把两者都塞入默认依赖。[Bucket4j 文档](https://bucket4j.com/8.10.1/toc.html)、[Resilience4j CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker)

**建议 Interface。** `policyId + authenticatedSubject + positiveCost → Allowed / Rejected(retryAfter) / Unavailable`。策略配置在启动时解析，避免每次 acquire 传一组实际不生效的容量/速率。进程内与共享配额分别声明，不能伪装成透明替换；IP 从受信代理策略获取。登录/短信/付费接口对 unavailable 采取保守拒绝，普通过载保护可显式允许降级。

**验收。** key churn 不清空其他人的余额；非法参数早失败；两实例共享配额的 Adapter 有真实后端测试；429/Retry-After 与实际等待一致；客户端伪造 XFF 不能改变受保护身份。

## 6. Lock：互斥协议不能以 boolean 成功掩盖缺席

`lock/LockUtil.java:49–50` 在无 bean 时返回 true，`executeWithLock` 在无 bean 时执行 action。单 JVM 内同样存在并发竞争，“单实例可以无锁”不成立。`DistributedLock.java:27–33` 把 `leaseTime` 解释为等待时间；ADR 的 Redisson 示例又把同一值同时用于等待与租约，替换 Adapter 后业务可能在持锁期被自动放开。

`InMemoryDistributedLock.java:60–83` 使用稳定锁对象，满时拒绝而不 clear 是正确的安全修订。但解锁从不移除 key，所以处理不同订单达到容量后，所有新订单持续失败。不要简单“unlock 后 remove”：另一个线程可能已持有该锁对象引用，移除会产生两个同 key 锁。成熟的 Guava Striped 用固定数量的条带换内存上界，代价是不同 key 可能串行，适合进程内方案比较。[Guava Striped](https://github.com/google/guava/wiki/StripedExplained)

**建议。** 默认模板的数据正确性由数据库条件写、唯一约束或事务保护；按需提供 `LocalKeyedMutex`，名字准确声明进程范围。确需跨节点协调时直接使用成熟实现或封装一个场景明确的 Module：等待时间、租约/续租、owner handle、释放条件和不可用结果彼此独立。对外部资源的旧持锁者写入，只有资源实际校验 fencing token 才能获得相应保护；不能说“上了 Redisson 就安全”。Redisson 官方明确区分 waitTime/leaseTime，并解释 fencing 需要资源端参与。[Redisson Locks](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/)

**验收。** 未配置所需实现时不执行 action；同 JVM 双线程互斥；新 key 长时间流转不会永久耗尽；owner 错误不能释放其他执行者的锁；租约过期后旧 owner 不覆盖新状态；不要以线程 ID 作为跨异步执行的隐式 owner。

## 7. Idempotency：最值得深挖的 Module，但当前 seam 不够表达协议

**已有价值。** 用 Filter 捕获最终响应、Interceptor 识别 endpoint、Store 管原子占位，并能替换存储，是六者中最接近深 Module 的方向。错误在于当前 `boolean tryBegin / find / void complete` 不足以表达需要保证的状态转换。

**确定问题。** `idempotency/InMemoryIdempotencyStore.java:75–89` 对 PROCESSING 也按 TTL 过期，`complete` 无 owner/version 比较直接 put。顺序 A 获取 → A 超过 TTL 仍在运行 → B 获取并完成 → A 晚到完成，会覆盖 B 的结果。换 Redis 只会把相同协议错误搬到网络另一端。原子占位解决同一时刻的竞争，不解决过期 owner 和业务提交后的崩溃窗口。

**成熟案例。** Stripe 会保存首次执行的状态和响应（包括 500），比较重复 key 的参数；未进入执行的校验失败或并发冲突不保存结果。它是成熟产品案例，不是所有应用必须照抄的行业标准。应该明示本项目的失败缓存政策，而不是说“和 Stripe 一样”却采取另一套语义。[Stripe 幂等请求](https://docs.stripe.com/api/idempotent_requests)

**采用独立 claim/租约协议时的内部持久化 Seam（设计草图，不是已实现 API）。** 应用的公开 Interface 仍是 `orders.create(actor, requestKey, command)`；以下协议协调留在 Module 内部，不要求 controller 计算 fingerprint、传 policy 或管理 lease。

```java
BeginOutcome begin(RequestIdentity identity, Fingerprint fingerprint, Policy policy);
CompletionOutcome complete(Lease lease, StoredResponse response);
// BeginOutcome: Acquired(Lease) | Replay(Response) | InProgress | Conflict | Unavailable
// Lease: 不透明 owner token + generation；只能完成自己取得的那次执行。
```

`RequestIdentity` 包括 tenant/actor、稳定操作名、客户端 key；fingerprint 是规范化的业务请求，不把授权凭据、trace 或临时传输头纳入；不同 fingerprint 复用 key 返回明确冲突。执行 lease 与重放保留期分离，过期不自动证明旧执行者已停止。`StoredResponse` 仅适用于兼容旧 HTTP 重放的 Adapter；推荐的应用方案保存有版本的业务 Receipt，由 HTTP Adapter 重新生成表示。Store 的内部 Adapter 可用内存实现协议测试，但生产持久幂等必须测试真实数据库/Redis，不把内存测试当成分布式证据。

**默认业务事务方案。** 对创建订单这样的数据库业务，优先让唯一命令键、业务记录和幂等完成结果在同一数据库事务内提交；崩溃后查询已提交结果。这种方案以数据库事务/约束处理竞争，不必再叠一套独立 owner lease。同 key 的并发插入只能等待至既定事务/锁等待预算；另一事务提交后读回 receipt，回滚后可重试，等待超时返回明确的处理中/重试结果，不能无限挂住。响应保存期与业务键保留期分别定义；清掉响应不能自动使旧业务命令再次执行，过期重试选择查询业务凭据或拒绝过期键。

若需要先提交 PROCESSING claim、再在事务外执行，则采用上一段的 owner/generation 协议；CAS 只防止旧 owner 覆盖状态，**不保证旧任务停止，也不保护它已经发出的副作用**，需额外依赖业务条件写、外部幂等键或恢复机制。外部支付调用还需向支付方传递其幂等键及恢复/对账流程；不宣称 exactly-once。Filter 只负责 HTTP 适配，不能单靠 response cache 证明业务只执行一次。数据库唯一性约束有明确并发语义，但跨系统副作用不在其原子范围内。[PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)

**验收。** 双用户/双路由隔离；同 key 异内容冲突；长请求超过 lease；迟到完成被拒；提交后响应丢失与进程重启可恢复；超大响应、Location 等重放头、异常响应和异步 Servlet 生命周期都有明确政策。SSE/下载不进入全量响应缓存。

## 8. 实证与证据强度

在未修改源码和 POM 的情况下，本机 Oracle JDK `25.0.4.1`、临时 Maven `3.9.16` 执行 `mvn -B -ntp verify` 成功：1196 tests，0 failures/errors/skipped；覆盖率门和依赖检查通过。**POM 仍使用 release 21；这不是 release 25 或 Boot 4 的验证。**

随后在忽略目录 `target/research/ContractProbes.java` 编译运行一个短探针（使用现有 target/classes 与 dependency:build-classpath），退出码 0，共 12 个 observation；分组如下：

| 探针 | 观测结果 | 证据含义 |
|---|---|---|
| 耗尽容量 1 的桶，再 acquire(-5)，随后 acquire(1) | 两次均成功 | 非法 cost 可恢复已耗尽余额；不声称可保留超过桶容量的余额，补充逻辑会截断到 capacity |
| 最大 2 个桶，依次 a/b/c，再请求 a | a 再次成功 | clear-all 重置已有配额 |
| 最大 1 个本地锁，a 解锁后申请 b | b 失败 | 容量按历史 key 永久累积 |
| 无 SpringContext 调用 LockUtil.tryLock | true | 没有锁也报告成功 |
| A 获取 1ms TTL，等待过期，B 获取并完成，再完成 A | 最终 body 为 A | 缺 owner CAS 的迟到覆盖 |
| 两个默认 SnowId 实例，相同时钟 | 首个 ID 相等 | 配置相同节点时唯一性不成立，不是随机碰撞测试 |
| Async before 写 ThreadLocal，独立 executor 读 | worker 为 null、caller 值仍在 | before/after 不能直接包裹线程局部上下文 |
| supplier 抛 AssertionError，设置 5 秒 timeout | Result.err(TimeoutException) | 非超时异常被改类 |

临时探针与日志在 `target/research/`，不作为新增生产代码或正式回归测试提交。其余源码推断均在各节按路径列出，尚未进行双进程、故障注入、压力或迁移后测试。将这些反例变成目标版本的契约测试，才是下一阶段的工作。

## 9. 需要重新决策的既有 ADR

| 现有决定 | 建议修订方向 |
|---|---|
| 0002 异步按类型让位 | 保留让位原则，统一实际执行路径与生命周期，去掉仅存在但不被使用的兜底 bean |
| 0014 限流无 bean 放行/clear-all | 按业务用途确定故障政策；有界且不重置其他 key；成本参数严格校验 |
| 0015 可选缓存回退 | 选择能力即完整依赖；保证 TTL/容量；默认不替应用无条件注册缓存 |
| 0016 同 SPI 支持本地/分布式、无锁执行 | 区分保证，等待与租约分离，缺必要能力拒绝执行 |
| 0017 完整幂等 | 增加身份/fingerprint/owner/持久化事务模型，收窄“完整”的承诺 |
| 0023 false 永不抛 | 升级到有界、可取消策略；保持老契约仅用于明确兼容入口 |

这里没有覆盖原 Accepted 记录。后续实施应新增 superseding 决策，说明原场景、变更理由、消费者迁移与验收；不能把历史记录偷偷改写成新设计一直如此。
