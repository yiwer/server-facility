# 02: 隔离应用上下文的注册、刷新与关闭

**What to build:** 一个应用上下文失败或关闭时，其他仍在运行的应用能继续使用自己拥有的服务；兼容门面清理具有正确归属。

**Blocked by:** None (can start immediately)

**Status:** closed

**Traceability:** FR-05、FR-08；AC-07、AC-11

## Acceptance criteria

- [x] 继续支持的静态门面只能由拥有它的实例清理，重复关闭与初始化失败有明确定义。
- [x] 新注入入口不依赖全局 holder；兼容入口的行为与弃用方向有说明。
- [x] 消费者测试不靠手工全局 reset 掩盖污染；跨测试、父子上下文与重新启动保持隔离。
- [x] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [x] 正常：注册、查询、刷新、关闭、重新创建，已有应用的服务可观察。
- [x] 边界/并发：A/B 两种关闭顺序、父子关系、并行初始化、重复关闭、刷新中查询。
- [x] 故障/接合：B 被拒绝注册后 destroy、启动失败后清理、关闭期间请求，不破坏 A；最终资源归属可核验。

## Scope boundary

修复生命周期与默认注入路径，不在本票重构所有 JSON/日志/locale 政策。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际实施、测试与验收结果见下文。


## 实施与决策（2026-10-03）

分支 `codex/ticket-02`，起点 `a6648bb`。ADR-0025 替代 ADR-0006 的进程级 handler cache 策略，保留每次日志至多分发一次的语义；INDEX 由集成者统一登记。

- holder 以实例作为静态注册令牌，成功刷新才发布，关闭事件/bean 销毁按令牌撤销；重复销毁、被拒绝者、父子事件不清理他人，退役实例不能被迟到事件复活。未发布与关闭竞态返回既有 Result 错误。
- 新消费者用 Spring 构造器注入自己的 Resource/Consumer；未新增通用 locator。旧 holder 明确弃用、保持单 owner，不自动接管曾被拒绝的应用。
- 手工兼容注册仅支持已完成刷新、未开始关闭且使用 Spring singleton registry 的 AbstractApplicationContext；不与 refresh 并发调用。其 listener 和 bean 销毁回调随自己的 context 撤销，原地刷新后重新手工登记；不覆盖现存 owner。
- ID/日志门面不缓存 Spring bean，保留显式 ID override；所有 holder 消费者测试移除全局 reset/反射，fixture 只关闭本测试创建的容器。

## Q01–Q10 与真实验证

| 标准 | 证据/不适用范围 |
|---|---|
| Q01 | FR-05/AC-07 对应 SpringContextOwnershipTest 的17个生命周期测试；FR-08/AC-11 对应 constructorInjectedConsumerKeepsItsOwnServiceWhenTheOwnerClosesFirst 与真实 FacilityCoreAutoConfiguration 装配；ADR-0025/USAGE/CHANGELOG 记录公开迁移。 |
| Q02 | SpringContextHolderTest 的14个查询/缺席测试；null name、required Class null、无效手工登记、重复登记/关闭及重新创建有明确断言。字符编码、数值溢出、Locale/时间边界不改变该注册状态机，不适用。 |
| Q03 | 真实 GenericApplicationContext、父子上下文、AbstractRefreshableApplicationContext 和自动装配；ID/日志重启回归及 cache/lock/http/ratelimit/locale/web 既有消费者走实际 Spring fixture。未 mock 自有模块。 |
| Q04 | CountDownLatch 有界屏障控制并行 refresh、查询与 close 竞争、关闭事件发出后销毁阶段手工登记；失败 bean 的 refresh 回滚关闭真实 Resource。所有屏障5秒，future5秒，finally释放，不用 sleep。 |
| Q05 | 进程静态注册最大1；固定种子120步、最多4个同时运行容器，所有 Resource 最终关闭；手工12代原地刷新及重复登记时 listener数恒为1，关闭后无owner。没有本票新增线程池、队列或外部连接；测试执行器用try-with-resources关闭。 |
| Q06 | 保留原静态方法签名、Result错误种类与手工ID override；既有消费者测试迁移至真实生命周期。无独立二进制/线协议样本需求；普通jar跨平台消费归票01/24/33。 |
| Q07 | fixedSeedLifecycleSequenceRetainsOnlyTheCurrentOwnerAndClosesAllServices，seed=20261003，120步；可用 `-Dtest=SpringContextOwnershipTest#fixedSeedLifecycleSequenceRetainsOnlyTheCurrentOwnerAndClosesAllServices test` 重放。 |
| Q08 | Windows NT10.0.26100；Oracle JDK25.0.4.1；Maven3.9.16；China Standard Time/Asia-Shanghai。该次产物release21、Boot3.5.10；本票不宣称Boot4/完整跨平台验证。无DB/网络服务依赖。日志在本worktree target/evidence/ticket-02。 |
| Q09 | `mvn -B -ntp -Djacoco.version=0.8.15 verify`：1215 tests，0 failure/error/skipped；5条ArchUnit、覆盖率与依赖检查均通过。instruction93.798%、line93.391%、branch87.382%。原1196→1215：新增ownership17、log重启1、ID关闭1；未删行为用例或跳过。早期TDD旧JaCoCo0.8.12对JDK25内部locale类报警，最终用票01选定0.8.15消除，未改变质量阈值/POM。 |
| Q10 | 11轮RED→GREEN原始记录、本票映射、代码/ADR/迁移说明同交付；原始日志留在ignored target，不纳入源码。最终集成基线与复验结果在后续记录补充。 |

11轮回归依次验证：B关闭误清A、刷新提前发布、关闭中查询、同context重复holder、手工覆盖/关闭、ID缓存跨关闭、日志缓存跨重启、getBean关闭竞争、null契约、关闭事件后的手工登记、手工原地刷新和listener稳态。最终完整运行记录：`target/evidence/ticket-02/verify-final.log`（2026-10-03 22:51 +08:00）。静态兼容入口仍只代表单owner；各应用的完整JSON/locale/日志政策隔离由票23/26/33闭合，不在本票冒称已验证。

反序消费者验证首次发现旧 IdUtilTest 在每个测试无条件 setGenerator，却仅少数 nested 测试撤销，遗留显式全局 override。移除通用 setup，UUID/默认SnowId测试直接走默认路径，只有验证自定义epoch的用例显式设置并成对撤销。没有在新生命周期测试中增加reset来掩盖污染。

反序复验命令：`mvn -B -ntp -Djacoco.version=0.8.15 -Dsurefire.runOrder=reversealphabetical -Dtest=SpringContext*Test,LogUtil*Test,IdUtil*Test,CacheUtilTest,LockUtilTest,HttpClientsTest,RateLimiterUtilTest,LocaleUtilTest,GlobalExceptionHandlerTest,FacilityCoreAutoConfigurationTest test`。196 tests，0 failure/error/skipped（2026-10-03 22:54 +08:00，reverse-order-green.log）。


## 集成基线复验与提交

- 实现提交：`49d18d3`；同步集成 `fbdcd45`（含ticket01）后的本分支merge提交：`0e4f3ca`。仅在ticket-02 worktree合入上游，未修改主checkout/未发布。
- 2026-10-03 22:56 +08:00：`./mvnw.cmd -B -ntp verify` 成功，Maven Wrapper3.10.0、release25、Boot3.5.16、JaCoCo0.8.15；1215 tests，0 failure/error/skipped；ArchUnit、覆盖率门与dependency analyze均成功。最终覆盖率instruction93.805%、line93.367%、branch87.382%。
- 最终日志：`target/evidence/ticket-02/integrated-verify.log`。该结果覆盖本票与新的Java25集成基线；Boot4/Jackson3、Linux原生产物及最终跨票J05/J15组合仍按票24/23/26/33各自范围闭合，不在本票宣称完成。
