# server-facility 下一代架构：让 agent 用标准框架构建有保证的应用

日期：2026-10-03（Asia/Shanghai）  
源码基线：`0ee9d547022371ad31f885605e999de17ec22777`  
性质：研究结论与待实施设计；不是已完成的版本升级，也不是已接受的新 ADR。

## 1. 我的架构判断

**推荐将项目定位为“可运行 Spring Boot 应用模板 + 小型、契约明确的运行时模块”。默认技术基线是 JDK 25、Spring Boot 4 的受支持稳定线；本次可核实的候选为 Boot 4.1.1。** 现有设施库是迁移资产，后续演进应围绕“让 agent 完成一个正确的业务用例”，而非继续增加静态工具入口。

最值得保留的是显式错误、错误与本地化分离、装配与实现分离、架构测试和记录设计理由的习惯。最值得改变的是：通过全局静态状态隐藏依赖，用降级成功掩盖保证消失，以及把线程、HTTP 响应和业务一致性都包进通用设施接口。

我的 code taste 可以概括为四条判断标准：

1. **接口要诚实。** 名叫 DistributedLock 的默认实现不应该只提供进程内互斥；名叫 leaseTime 的参数不应该随实现改成等待时间；配置了 TTL，就应兑现 TTL 或明确失败。
2. **小接口必须隐藏复杂性。** public 方法少，只是表象。如果调用者还得记住上下文初始化、特殊 classpath、线程切换、过滤器顺序、隐式降级，它仍然是浅 Module。
3. **标准能力采用标准入口。** DI、HTTP、JSON、日志、trace、认证、缓存不各自复制一套名字。保留包装层需要给出它额外兑现的保证。
4. **用业务行为检验框架。** 新增一项业务是否更容易写对，比工具类数量、注释长度和单测数量更有价值。测试应守住真实契约，允许实现被替换。

本文不是以代码“有问题”为理由推倒重写。原仓库在本轮 `mvn verify` 中 1196 项测试通过，说明它已经投入了大量工程约束；同时，消费者探针仍复现了不适合作为下一代默认值的行为。改造应改变这些行为背后的产品契约，再有顺序地收缩实现。

## 2. 事实基线与证据边界

| 项目 | 本轮观察 |
|---|---|
| 产品形态 | `cn.code91:server-facility:0.1.0-SNAPSHOT`，单 Maven jar；当前没有可运行应用入口/完整认证/数据库迁移/部署模板 |
| 规模 | 29 个顶层包，158 个 main Java 文件（含 package-info），101 个 test Java 文件；约 14,765 / 12,415 非空行（含注释） |
| 工程基础 | 11 个 AutoConfiguration、23 份编号 ADR、5 条 ArchUnit 规则、JaCoCo 和依赖账目门 |
| POM | Java release 21 写了两处；Boot BOM 3.5.10；若干依赖及插件独立 pin |
| 实际构建 | Oracle JDK 25.0.4.1 + Maven 3.9.16；原 POM `verify` 成功，1196 tests，0 failure/error/skipped |
| 覆盖率 | instruction 93.60%，line 93.34%，branch 86.95%；原门槛全部通过 |
| 消费者探针 | runtime 12 项观测、Web 9 项观测全部复现；其中部分是正常前置条件或同一问题的两次观测，不能当作 21 个独立漏洞 |
| 尚未做的验证 | release 25 编译后的全量质量门、Boot 4/Jackson 3 迁移、双进程后端测试、压力/性能测试、真实部署升级 |

系统 PATH 没有 Maven，本轮在临时目录下载 Maven 3.9.16 并核对 SHA-512，未改系统 PATH。构建日志与临时探针位于 `target/research/`，该目录被 Git 忽略；只有本文及四篇研究附录是交付的仓库改动。

阅读路径：

- [JDK 25 / 框架与所有显式依赖升级矩阵](2026-10-03-java25-baseline.md)：精确版本、来源、断点与方案 B。
- [核心值类型、序列化、文件、表格、HTTP 与加密](2026-10-03-core-modules.md)：17 个功能包与最小基础库方案 A。
- [运行时契约](2026-10-03-runtime-contracts.md)：异步、ID、缓存、限流、锁、幂等六包与实证。
- [Web / 自动装配 / 上下文 / 日志 / agent 体验](2026-10-03-web-and-agent-design.md)：其余六包、全部 Web 子包、实证与模板方案 C。

每项建议区分源码事实、官方文档支持、实验观察与本研究的价值判断。源码证据基于上述 commit；生态来源采用发布方文档、源码、规范和 Maven Central，未把社区博客当兼容性依据。“成熟产品这样实现”也不自动等于全行业只有这一种正确答案。

## 3. 从需求重新定义项目职责

目标用户是需要迅速增加业务能力的 coding agent，以及审阅、部署、维护其输出的人。常见任务至少有四种：

| 场景 | agent 需要的默认路径 | 框架必须说明的困难 | 不应默认携带的复杂性 |
|---|---|---|---|
| 内部管理/CRUD API | 路由、DTO、校验、应用 Module、持久化、错误契约 | 事务、授权、分页/排序白名单、数据库约束 | 分布式锁、通用规则引擎、自制 ORM |
| 对外创建/变更命令 | 已验证身份、请求键、业务结果、明确状态码 | 重试、并发、提交后断连、跨实例恢复 | 对全站响应做无界缓存 |
| 外部系统聚合 | 类型化 HTTP Adapter、超时、预算、trace | 远程失败、限速、非幂等重试、未知结果 | 对所有任务再造一套 Async/Result/Interceptor 语言 |
| 文件导入导出 | 流、限额、临时资源、类型策略、行级错误 | 巨大文件、路径/格式、部分失败、公式、流所有权 | 给 JSON/MIME/Excel 各做互不一致的资源规则 |

新项目先服务第一类；第二、四类是值得形成深 Module 的候选；第三类以 Spring 标准客户端与应用 Adapter 为主。当前没有数据库或身份实现，因此不能把某种 ORM/认证模式写成“原项目需要修复的遗漏”。它们是新模板的显式产品选择。

## 4. 三种候选架构，和我的选择

本轮并行探索了不同约束的三个方案；接口草图和依赖策略在对应附录末尾。

| 方案 | Interface 与所有者 | Depth / Locality | 适合情形 | 核心代价 |
|---|---|---|---|---|
| A 精简基础库 | 少数值类型、错误/受控资源入口；应用直接用 JDK/Spring | 纯函数易测；少量真正深的处理流程集中维护 | 已有成熟应用平台，只缺基础类型或文件能力 | 不能独自解决 agent 首次建站、依赖、运行与部署 |
| B 多能力 starters | 应用选 artifact 和明确配置；每能力有完整运行时契约 | 策略统一升级，Adapter 可替换 | 多团队、多应用稳定复用同一套能力 | 支持矩阵、装配组合和公共兼容承诺迅速增大 |
| C 可运行模板 + 极小 runtime | 应用拥有业务 Module；Spring 管标准基础；runtime 只提供少数有价值策略 | 业务变化留在业务包；标准知识直接复用 | 本项目最初的目标：agent 快速构建可靠 Web server | 模板生成后的副本要有升级策略；允许少量普通配置重复 |

**选择 C，吸收 A 的精简原则，在有真实第二个消费者时采用 B 的发布方式。** 不立即建立一个拥有几十个开关的生成平台，也不把 29 个 Java 包拆成 29 个 Maven artifact。用两个独立消费样板来证明共性，再提取可发布运行时，是更低风险的演进顺序。

为什么不以 B 为终点：这个仓库已经显示“一个依赖，很多能力默认生效”的成本。纯工具调用需要理解静态容器；关闭锁可能变无锁执行；新增幂等默认改变普通下载；optional 依赖使同样配置含义不同。若只把这些代码拆成 starters，而保留相同保证，复杂性只会换位置。

## 5. 目标结构与责任归属

第一步先在现仓库增加一个独立消费者 fixture；模块拆分是后续动作。最终候选结构如下，名字为提案：

```text
server-facility/
  facility-core/                     小值类型/错误；无 Spring、无服务定位器
  facility-spring-boot-starter-webmvc/ 经验证的 Web 策略与 Boot 装配
  facility-documents/                真有消费者时提取的文件/表格流程
  templates/web-server/              唯一推荐应用路径，含 wrapper 和运行说明
  examples/consumer-contracts/       发布依赖方式消费，不偷用内部实现
  docs/                             用法、设计理由、迁移与研究
```

初期 starter 可以把少量装配放在同一个 artifact；只有复用需要才拆独立 autoconfigure。不存在可复用 Web 策略时，也允许只使用模板配置而暂不发布 starter。`facility-documents` 不应被默认 Web starter 传递引入。旧坐标保留短期兼容发布线，默认新模板不进入旧的全局 static 路径。

应用内部按业务组织：

```text
app/
  orders/           HTTP Adapter、命令、结果、应用服务、持久化实现、测试
  customers/        同上；仅按需建立内部子包
  configuration/    明确的 Security/序列化/观测配置
```

不强制每个业务复制四层空目录，不给每个类配一个 `XxxServiceImpl`。业务 Module 对外只暴露调用者需要的操作，内部 Seam 根据真实变化点形成。Spring Modulith/ArchUnit 可以验证业务包关系，但 Modulith 不是采用业务包结构的前置条件。[Spring Modulith 验证](https://docs.spring.io/spring-modulith/reference/verification.html)、[ArchUnit](https://www.archunit.org/userguide/html/000_Index.html)

| 问题 | 默认责任所有者 | facility 何时有资格封装 |
|---|---|---|
| 注入、配置、生命周期 | Boot/Framework | 确有独立策略；仍通过构造器和 bean 生命周期 |
| JSON / HTTP 客户端 / 缓存 | Boot 管理的 Jackson、RestClient.Builder、CacheManager | 增加一致且被测试的领域无关策略，不复制标准 API |
| 身份认证与请求安全 | Spring Security；应用选择 Session/OIDC/资源服务器模式 | 不定义第二个 SessionUserHolder 作为认证体系 |
| 可观测性 | SLF4J、Micrometer、宿主日志与采集设施 | 保留纯脱敏策略；不默认再做 caller 查栈、日志二次分发和自制 trace 协议 |
| 业务授权、事务、幂等语义 | 应用 Module | 可以复用持久化 Adapter，不能靠注解猜业务提交语义 |
| 文件/表格处理 | 有资源预算和生命周期的独立 Module | 将 MIME、路径、CSV/Excel、临时文件、错误报告收在同一流程 |

Boot 文档说明观测依赖 Micrometer，异步传播有明确配置；应优先沿用原生机制，而不是默认再维护第二条链。[Boot Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

## 6. 重新设计最有价值的三个 Interface

### 6.1 业务命令：把幂等放在真正知道业务成功的位置

```java
// 设计草图；以下订单域不属于现有源码。
CreateOrderOutcome outcome = orders.create(actor, requestKey, command);
```

这个 Interface 要说明：actor 已被认证，授权仍由业务校验；同 actor/operation/key 只代表同一规范化命令；冲突、处理中、正常结果与设施不可用是不同结果；超时不表示业务没发生。Controller 不需要知道 Redis key、锁名、TTL、Filter 顺序或响应 wrapper。

默认先实现同库事务方案：唯一命令键、订单与可恢复的幂等结果一起提交。请求断开后，重试读回已提交 receipt，再生成 HTTP 表示。同 key 竞争受事务/锁等待预算约束；等待另一事务结束后读取结果或重试，超时返回明确的处理中状态。此方案不必叠加独立 owner lease。响应保存期与业务键保留期分别定义，响应过期不能自动授权再次执行旧命令。

确需先独立提交 claim、再在事务外执行时，才引入 owner/generation 与恢复状态机，防止过期执行者覆盖新状态；owner CAS 仍不能取消它或阻止其业务副作用，后者必须由业务条件写或外部幂等协议保护。跨外部系统不承诺通用 exactly-once。原 `IdempotencyStore` 不足以表达这些不同保证，仅增加一个 Redis 实现不够。这些内部协议不泄漏到 `orders.create` 的调用者。

Stripe 提供了“请求参数一致性 + 首次执行结果保存”的成熟参照，但失败结果是否保存仍应按本项目业务明确决定。[Stripe 幂等](https://docs.stripe.com/api/idempotent_requests)

### 6.2 文件摄入与表格处理：封装一段流程，而不是一袋工具

```java
// 同样只是草图，不承诺立即增加一套通用文件平台。
try (AcceptedUpload upload = uploads.accept(input, uploadPolicy)) {
    importReport = customerImport.read(upload, rowPolicy);
}
```

`accept` 集中兑现文件大小、真实类型探测、可信存储名、临时资源清理与流所有权；`read` 按行处理，给出行号与字段错误，不默认把全部数据装入 List。CSV 与 Excel 是已经存在的两种 Adapter，因而这一 Seam 有真实变化依据。业务映射与必填字段留在应用，文件 Module 不推断用户领域模型。

成熟解析器如 Commons CSV、Apache POI 应负责格式解析；本库负责资源预算和调用契约。对于小而稳定的数据导出可以直接调用 POI，不为五行调用强加新层。若暂时没有真实导入业务，先修正既有方法的资源与错误契约，不抢先实现此完整草图。附录列出当前宽松 CSV、ZIP 部分失败、Excel 全量缓冲与 Tika 流位置的具体证据和来源。

### 6.3 Web 错误：一个明确的失败协议

新模板使用真实 HTTP 状态和 RFC 9457 ProblemDetail；成功响应直接是业务 DTO。错误提供稳定 `code`、安全 `detail`、必要 field errors 和 traceId；日志保存内部 cause。禁止生产错误消息直接取任意 `ex.getMessage()`。`Result` 是应用内可预期失败的选择，不是所有 HTTP 响应的包装壳。[RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457)

旧的 `HTTP 200 + BaseResponse` 放在明确兼容路径；迁移接口需同步客户端和契约测试，不对既有应用一夜切换。过滤器/Security 的 401/403、MVC 的参数错误和业务错误需要一致策略，但不用一个巨大异常继承树囊括所有失败。

## 7. 29 个现有功能包的去向

这是决策索引；各包的设计细节、源码证据、成熟替代与验收在附录中，不用这一张表取代阅读实现。

| 包 | 已有价值 | 目标处置 |
|---|---|---|
| result | 显式可预期失败、组合操作 | 保留核心语义，评估收缩重载；不强制纯计算都 Result 化 |
| error | 错误码/消息键与本地化分开 | 保留，缩小公共错误表；domain error 不全部挤进 FacilityErrorType |
| structure | Tuple/Triple 简化临时组合 | 逐步让公开协议使用有名 record；内部低成本组合可保留 |
| common | null/collection 便利方法 | 不再扩充；JDK 直接表达得更清楚的 API 弃用 |
| copy | 字段策略和深浅复制 | DTO 默认显式映射；复杂静态映射评估 MapStruct；通用反射深拷贝收缩 |
| date | 格式/时区/解析约定 | 保留少量确有业务含义的策略；优先 java.time/Clock，移除无界格式缓存 |
| number | 舍入/格式/单位语义 | 只保留有清晰协议的策略；不再包装所有 BigDecimal 操作 |
| pattern | 常用模式、匹配工具 | 领域校验回应用；有限预编译，避免任意 pattern 的永久缓存 |
| path | 文件名清理 | 并入受控文件流程；展示名与存储名区分 |
| io | Path/ZIP 错误转换 | 保留有界资源流程；明确部分失败、输出覆盖/自身遍历和文件所有权 |
| mime | Tika 探测与 fallback | 并入文件策略；探测不等于内容安全；明确 detect 的流位置 |
| json | 类型引用、统一序列化策略 | 接入 Boot/Jackson 3 builder；去掉共享可变 mapper 和静态注册表默认路径 |
| http | RestClient 的便捷 Result 化 | 首选注入 Boot builder + 类型化外部 Adapter；保留状态/头/限时等必要信息 |
| csv | 简单读写和错误通道 | 成熟解析器 + 严格/宽松方言显式化 + 行流/公式策略 |
| excel | POI 隔离、读写入口 | 独立按需能力；格式引擎依赖完整；补行流/预算/清理，不逐请求猜缺库 |
| crypto | AES-GCM、内部管理随机 IV | 保留经过约束的原语；版本化密文/KDF、密钥来源；不造认证和 KMS 平台 |
| hash | 摘要封装 | 只保留文件完整性等真实调用；不把普通 hash 宣传为密码存储 |
| async | 惰性组合、错误聚合 | 冻结 DSL 扩张；接标准 executor/生命周期，逐步迁出默认路径 |
| id | 时序 ID、可注入时钟 | 默认 UUID；SnowId 显式节点协议、时钟预算，取消节点 0 静默生产 fallback |
| cache | 复用 Spring CacheManager | 保留标准生态，移除 service locator 与静默 TTL 降级 |
| ratelimit | 令牌桶与 Web 分离 | 成熟算法、可信身份、明确预算范围/故障策略，禁止 clear-all 重置 |
| lock | 可替换实现、finally 释放 | 默认不启用；本地/分布式分名，缺必需实现不能无锁成功 |
| idempotency | 原子占位与响应重放 | 重设 owner/fingerprint/范围/持久化一致性；从全站拦截迁向明确命令 |
| autoconfigure | Boot 原生装配、条件让位 | 保留机制；显式依赖与所有权、完整消费者组合验证 |
| context | 静态调用获得 Spring 能力 | 短期修生命周期归属，长期从新 API 移除 SpringContextHolder |
| locale | 多模块消息聚合 | 保留 bundle 贡献；应用拥有 MessageSource，避免默认抢占其配置 |
| log | 格式、post-handler、统一脱敏 | SLF4J/宿主负责日志，逐步取消静态第二管线；审计事件独立建模 |
| masking | 无依赖纯函数脱敏 | 保留为辅助手段，优先字段白名单；不得宣传能覆盖所有日志与秘密 |
| web | 异常/过滤/会话/上传下载适配 | 以标准 HTTP、资源预算与原生身份/trace 收敛；逐子包目标见 Web 附录 |

公共接口收缩先盘点实际消费者。没有调用点证据时，标记弃用并提供迁移示例，不因仓库内部“零引用”就推断外部一定没人使用。

## 8. JDK 25 与依赖升级应分层实施

**长期目标：JDK 25 + Boot 4 稳定线 + BOM 协调的 Framework/Jackson/测试体系。** Java 25 不强制 Boot 4；Boot 3.5 可作为缩小升级变量的过渡。具体 patch 的来源、发布可验证性与所有手动 pin 在[版本矩阵](2026-10-03-java25-baseline.md)列明；实施前再次解析锁定同一组版本。[Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)

1. 先固定 Wrapper、JDK toolchain/Enforcer 和唯一 release 属性。升级 JaCoCo/ArchUnit/编译与测试插件，再实际编译为 release 25。现有 Lombok 1.18.42 已支持 Java 25，不把它误报为必然阻断。
2. Boot 3.5 的已验证维护版可作为短暂中间提交，保留 Jackson 2，使 JDK/字节码工具失败与框架迁移失败可区分。它不是新的长期平台目标。
3. Boot 4 升级单独一批：Jackson 3 不只是 import 改名，当前可变 mapper 配置需要重设；Boot 的拆包、测试 starter/JUnit、Servlet 和自动装配要按实际源码处理。不能全局替换 `com.fasterxml` 或所有 auto-configuration 包名。
4. 对非 BOM 依赖逐项迁移。例：Tika 4 改变流探测后的流位置语义，即使能编译也会影响上传流程；POI/jsoup 等按实际用到的行为验收。不要一次把所有大版本设为“最新”后仅跑单测宣称完成。

JDK 25 默认不开启 preview。虚拟线程可用于阻塞 I/O 工作负载，但连接池、外部服务、内存和 CPU 仍有限；用场景数据决定开关，不承诺“换 JDK 就高并发”。[Boot 任务执行](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)

## 9. 可交付的迁移批次

每批独立可审阅、有明确退出条件。规模估计以变更面表示，不虚构日程。

| 批次 | 具体交付 | 完成条件 | 回退/兼容 |
|---|---|---|---|
| M0 建立可复现基线 | Maven Wrapper、唯一 Java25 release、更新字节码工具、当前版本依赖清单；原生消费者 fixture | release25 全 verify；新机器不需要预装 Maven；依赖与产物可追溯 | 尚不改变公共运行语义；构建批次可独立回退 |
| M1 先封住危险契约 | 幂等作用域/owner，非目标响应不缓冲，安全 ProblemDetail，holder/异步清理，必要锁缺席拒绝 | 本次反例成为回归；真实容器覆盖 sync/async/error；缺少必需配置有确定失败 | 旧响应格式可保留显式兼容；数据正确性缺陷不靠兼容开关继续默认启用 |
| M2 升级框架主线 | Boot4/Jackson3、auto-configuration/测试迁移、非BOM依赖逐项升级 | Boot4消费者 fixture 全绿；依赖缺席/用户覆盖矩阵；JSON/文件流契约不漂移 | 旧 Boot3 发布线短期维护，不在一个二进制里强行兼容两套主版本 |
| M3 收缩框架与封装 | 构造器注入、原生 builder、单日志/观测路径，工具退出/文件处理候选 Module | 新推荐路径零 service locator、零全局无界响应缓存；旧 API 迁移指南；模块无环 | 根据外部消费者盘点设弃用窗口，先有替代再删除 |
| M4 交付 agent 模板 | 一个基础 Web 模板、一个带持久化业务示例、启动/验证/部署说明、最小运行配置 | 从模板实例化后独立构建/启动/测试；新增端点无需改 runtime | 模板版本与runtime版本分别记录，可比较升级差异 |
| M5 按证据扩大能力 | 第二消费应用、按需持久幂等/表格/分布式 Adapter；性能测量 | 两个真实用例证明共享接口；每个 Adapter 有故障与契约测试 | 缺场景则不提取、不新建 starter |

模板应有一条默认数据库方案：对关系型 CRUD 推荐先选择 PostgreSQL + Flyway + 一种 Spring 数据访问方式；具体 JDBC/JPA/MyBatis 由示例复杂度和已有业务约束决定，不并列提供全部。可先用 Spring JDBC/JdbcClient 做显式 SQL 示例，遇到有价值的聚合/映射需求再选 JPA。这是设计推荐，不是宣称存在所有行业一致的 ORM 答案。数据库测试使用同类型数据库；Schema 初始化只由一种迁移机制负责。[Boot 数据初始化](https://docs.spring.io/spring-boot/how-to/data-initialization.html)

认证也要选定场景：API 模板采用明确的 Spring Security 配置与 JWT 资源服务器示例；生产必须配置可信 issuer/audience。开发身份只在命名清楚的本地示例配置中提供，禁止无配置自动降级为匿名业务用户。简单无认证示例只能暴露无敏感状态的演示端点，不能作为生产默认授权策略。[Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

## 10. 验收目标：让成功可测量

以下是建议目标，尚非测量结果：

- **第一次成功路径**：全新 checkout 只安装 JDK25，即可由 wrapper 执行验证；带数据库示例额外明确需要容器运行环境。启动命令、测试命令、环境变量不依赖 agent 推测。
- **新增业务的修改局部性**：实现一个新命令集中在一个业务 Module 与其迁移/测试；不需修改 facility-core、全局注册器和跨包大枚举。记录实际改动文件数，先建基线再优化，不定虚假的绝对文件数。
- **错误与身份**：生产错误不含内部异常消息、凭据和调试栈；状态码正确；未授权请求不能读到其他身份的幂等结果；异步后无身份残留。
- **一致性**：双实例同命令键、延迟完成、进程重启、提交后断连都产生设计所承诺的结果；明确哪些保证依赖同库事务，哪些依赖外部协议。
- **资源**：每条摄入/缓存/重放/并发路径有预算或明确禁用；不会因 key churn 全局重置，也不会为普通流式下载缓存完整响应。
- **升级**：只有一个 BOM 决定其管理的版本；非 BOM pin 有来源和理由；支持一个主要 Spring 平台线；编译/覆盖率/架构检查覆盖目标 classfile 版本。
- **agent 体验**：用固定任务集合实测冷启动、加端点、改错误、加外部调用、加导入五类任务；记录一次通过率、人工纠错点和上下文读取量。比较原版与新版结果，再谈“更适合 agent”。

保留有意义的质量门，不为了新结构把覆盖率数值压低；也不为了维持“1196”而保留依赖旧内部实现的测试。旧行为被明示替换后，把测试转移到新的 Interface 与消费者结果上。对纯文档/依赖声明不添加只镜像实现的测试。

## 11. 明确不做什么，以及何时重新考虑

不默认上微服务、响应式全栈、Redis、分布式锁、事件总线、通用 BaseService/BaseController、万能 Repository、运行时反射 DTO 映射、自己的 JWT 签发框架或一次支持所有存储后端。这些不是永远禁止，而是必须有一个能说明收益与失败模型的真实场景。

不以“消灭五行重复”为由创建公共抽象；不同业务的相似代码不一定共享同一语义。不把全部功能都塞进一个超大 Orders Module；Depth 允许内部协作者，关键是让调用者不用协调这些协作者。

新模板的更新也不能仅靠重新生成覆盖应用代码。模板版本写入生成说明，升级用小型明确差异或后续成熟迁移工具；共享稳定策略通过 runtime 版本更新，业务代码始终归应用。Initializr 的价值在于项目生成和依赖元数据，不能替代对生成物持续构建、运行和回归的责任。[Spring Initializr](https://docs.spring.io/initializr/reference/)

## 12. 实施前需要登记的设计决策

后续新增 superseding ADR，保留历史理由：①产品形态与发布边界；②缺席/降级按保证分类；③原生生命周期与静态 API 弃用；④安全 ProblemDetail 与兼容协议；⑤业务幂等/owner/事务模型；⑥本地与跨节点锁的不同契约；⑦默认 ID 策略；⑧文件/表格资源所有权；⑨Java25/Boot4 单主线支持政策。

这些决定有的会推翻旧 ADR 的已接受取舍，因此应该公开写出理由、迁移和验收。用户本轮要求的是研究和新方案；本次交付到可实施设计与证据，不将候选方案冒充已批准或已完成的应用改造。
