# CONTEXT — server-facility 域术语权威

> 本文件是本仓库文档与 javadoc 的用语基准。与代码不一致时,以代码 + ADR 为准并回头修订此文件。
> 源头:beacon 仓库 CONTEXT.md 的 facility 相关子集,随迁移精简。

## 架构术语

- **deep module**:接口面窄(静态门面 + Spring bean + 值类型)、承载面宽(20+ 子包)的模块形态
  (John Ousterhout)。判据:删掉它,横切职责会以重复、各自为政的形式散落到各消费方。
- **接口面 / 承载面**:消费方可见的入口集合 / 支撑入口的内部实现集合。接口面 ≪ 承载面 ⇒ deep module。
- **Seam**:`@ConditionalOnMissingBean` 落地的可替换点——应用声明同类型 bean 即可替换默认实现,
  无需改 facility 代码。
- **hypothetical seam / real seam**:只有一个默认实现的 Seam(声明可替换性,不预先抽象 Strategy)/
  出现第二实现后升格的 Seam。不为单一实现预先抽象接口。
- **静态门面**:`XxxUtil` 形态的零配置入口(IdUtil/JsonUtil/LogUtil/DateUtil/LocaleUtil),
  Spring 就绪后与容器装配的实例共享状态。
- **Result-style**:`sealed Result<T,E>`(`Ok`/`Err` record)替代受检异常;switch 编译期穷尽两条路径;
  `Result.empty()` 表达"成功但无值"(ADR-0007)。
- **RFC 7807 双轨**:同一异常处理器支持 `BaseResponse`(默认)与 `ProblemDetail`(opt-in),
  配置切换不换类(ADR-0003)。
- **错误消息边界本地化**:错误类型(`ErrorTypeInterface`)只承载 code/messageKey/defaultMessage 纯数据,
  `format()` 仅渲染默认模板;i18n 解析发生在展示边界(locale 包),error 包不依赖 Spring(ADR-0010,C1)。

## 工作流术语

- **SDD**:spec(docs/superpowers/specs)→ plan(docs/superpowers/plans)→ TDD 实施 → retrospective。
- **ADR**:docs/adr/,0000 为模板;0001-0008 为 inherited(源:beacon);0009 起为本工程决策。
- **迁移三判定**:keep(原样迁移)/ keep+rework(迁移时重构,TDD)/ drop(不迁移)。
- **断环 C1/C2/C3**:spec §4.4 的三组包级循环依赖及其断法;ArchUnit 规则守护。

## 命名与配置

- 包根 `cn.code91.facility.*`;类前缀 `Facility*`;配置前缀 `facility.*`;
  i18n bundle `i18n/facility-messages_*`。

## 独立 claim 术语（ADR0034）

- **命令绑定**：可信scope与client key首次绑定canonical fingerprint；新协议在store生命周期内保留，不随回执正文过期删除。
- **执行资格（ClaimToken）**：当前scope/key、owner和generation的记录更新资格；只有当前活跃PROCESSING可完成；ADR0035允许过期但仍为当前PROCESSING的owner终止，被替换owner不可释放新generation。不代表身份认证或外部副作用锁。
- **lease / retention**：PROCESSING允许同内容新owner的租约，与从完成时刻起保留receipt正文的时长；正文到期不重新授权执行业务。
- **终态墓碑**：RESULT_EXPIRED、RELEASED或UNKNOWN保留的命令绑定；默认内存满额拒新，不通过驱逐墓碑恢复执行许可。

## HTTP 重放术语（ADR0035）

- **当前授权 Adapter**：宿主 `IdempotencyAuthorization` 在每次 claim/replay 前检查当前资源/方法权限并规范化输入，仅做授权与规范化，不执行业务副作用。
- **有限同步目标**：明确 `@Idempotent` 的可有界捕获请求/响应；普通下载/SSE直通，已知异步、流式、form/multipart目标拒绝。
- **HTTP receipt**：qualified Store中的有界FHR1状态、允许头与正文；重放跳过业务方法，不能依赖被跳过的方法权限检查，也不代表业务事务已和receipt原子提交。

## 本地缓存术语（ADR0031）

- **显式本地缓存**：应用选用的 Spring CacheManager/Cache，Caffeine + context-support 成对存在，名称有限且 TTL/每缓存条目容量为正；默认不注册，宿主管理器优先。
- **缓存条目容量**：Caffeine maintenance 后兑现的条目政策，不是瞬时准入限制或任意 key/value 的字节预算。
- **缓存生命周期**：管理器关闭先解除其 provider 引用并尝试清理各缓存；借用 Cache 和业务 loader 必须由宿主在关闭前 quiesce，不代表任意副作用取消或幂等执行资格。
