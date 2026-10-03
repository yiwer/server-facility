# server-facility 下一代：已发布任务清单与依赖

日期：2026-10-03。状态：用户已确认任务粒度与依赖；33 张票已发布为本地 tickets，状态均为 ready-for-agent；尚未开始实现。

来源：[PRD v0.2](../specs/2026-10-03-server-facility-next-prd.md)；配套：[测试策略与接合矩阵](2026-10-03-server-facility-next-test-strategy.md)。本轮已将用户“底层脚手架测试全面覆盖”的要求写回 PRD，并分配到每张任务。

## 拆分原则

- 每张票包含一个可观察行为、实现、必要迁移/设计说明和测试，不按 Controller/Service/DAO 横向拆分。没有 UI 的基础库不人为添加 UI 层。
- 修复现有能力与新模板建设并行；旧独立 claim 协议和新同库事务幂等分开，不能互相冒充保证。
- 每张票引用共同测试完成标准 Q01–Q10，同时列出具体正常、边界、接合、故障、并发及资源场景。覆盖率保留但不替代场景证据。
- 保留能力先修契约；公共 API 先盘点、扩展替代、迁移消费者，再按明确窗口收缩。尚不建设完整 starter 体系或 documents 平台。
- 唯一宽迁移例外为 21→22→23→24：21 是旧平台全绿的前置整理，22/23 在专用集成线上分批迁移，24 全量验证后才允许合入主线。22/23 不能单独发布或宣称完整绿色。
- 阻塞边只表达实现的真实前置。联合验证所需的另一能力可以稍后接合，不因此让所有任务串行；33 收敛同一候选版本的全部证据。

## 可开始的 frontier

当前无其他任务阻塞的任务：01、02、03、04、05、06、07、08、10、11、13、14、15、16、17、18、19、20、32。可按风险与人员选择这些任务开始实施；有 Blocked by 的任务须先完成其前置。ready-for-agent 表示内容可领取，不表示依赖已经解除或验收完成。

02–20 的现有能力大多可在当前平台开展，不必等待完整 Boot 4 迁移。它们随后必须在目标平台复跑；当多个实现修改同一装配或构建入口时，在工作安排中协调变更，不额外捏造产品依赖。

## 编号任务清单

1. **[用固定 JDK 25 工具链构建并消费普通库产物](../../../.scratch/server-facility-next/issues/01-java25-consumer.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 全新环境通过 Wrapper 完成 release 25 构建，并让一个独立消费应用使用发布 jar 中的公共能力；形成可复用的消费者验证入口。

2. **[隔离应用上下文的注册、刷新与关闭](../../../.scratch/server-facility-next/issues/02-context-ownership.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 一个应用上下文失败或关闭时，其他仍在运行的应用能继续使用自己拥有的服务；兼容门面清理具有正确归属。

3. **[让异步组合遵守执行器、截止时间和清理契约](../../../.scratch/server-facility-next/issues/03-async-contract.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 一组异步工作确实在声明的执行器上运行，保留原始失败类别，遵守整体截止时间，并在完成、取消和关闭后释放自身资源。

4. **[在真实 HTTP 链路统一安全错误与显式兼容协议](../../../.scratch/server-facility-next/issues/04-http-errors.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** API 调用者在 MVC、Filter 和异常派发失败时得到准确状态与安全 ProblemDetail；旧客户端仅通过显式兼容路径获得原 envelope。

5. **[让普通下载与请求体处理保持流式和有界](../../../.scratch/server-facility-next/issues/05-bounded-web-streams.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 普通下载、SSE 和非目标请求不再经过全量响应捕获；需要重复读取的请求体及选定响应捕获都有明确预算和关闭责任。

6. **[固定请求身份、代理信任与上下文清理](../../../.scratch/server-facility-next/issues/06-request-boundaries.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 消费应用明确知道请求 IP、trace 和身份上下文来自何处，不因伪造头、派发切换或线程复用改变主体。

7. **[让互斥操作获得真实保护或明确拒绝](../../../.scratch/server-facility-next/issues/07-honest-local-lock.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 需要进程内互斥的操作在正确的锁下执行；所需实现缺席时不执行 action；长期键轮转不会永久耗尽锁能力。

8. **[兑现本地缓存的 TTL、容量和装配承诺](../../../.scratch/server-facility-next/issues/08-cache-guarantees.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 显式选用缓存的应用获得声明的到期与容量政策；后端或依赖不满足配置时清楚失败，不退化为永久 Map。

9. **[让 API 配额抵抗非法成本、伪身份和键洪泛](../../../.scratch/server-facility-next/issues/09-rate-limit-contract.md)**
   - **Blocked by：** 04、06。
   - **What it delivers：** 一个受保护 API 的本地限流能按可信主体和合法成本计费，拒绝结果可解释，新增键不能恢复其他主体额度。

10. **[以 UUID 默认值和显式 SnowId 协议生成标识](../../../.scratch/server-facility-next/issues/10-id-policy.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 新应用多实例启动不需要隐藏的节点协调；继续支持的 SnowId 只在节点与时钟条件明确时产生结果。

11. **[扩展旧幂等协议以拒绝迟到 owner 覆盖](../../../.scratch/server-facility-next/issues/11-legacy-claim-owner.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 继续支持的独立 claim 协议可辨别拥有者、内容冲突与不可用；旧执行者迟到完成时不能覆盖新执行结果。

12. **[只对已授权目标操作执行有界 HTTP 重放](../../../.scratch/server-facility-next/issues/12-legacy-http-replay.md)**
   - **Blocked by：** 04、05、11。
   - **What it delivers：** 旧 HTTP 幂等消费者迁至安全 claim 入口，以可信身份、操作和内容隔离结果；普通响应保持流式输出。

13. **[上传在探测、保存和失败时保持完整与有界](../../../.scratch/server-facility-next/issues/13-upload-integrity.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 现有上传入口同时兑现大小、类型、可信存储名和清理政策，MIME 探测后保存的正文与原输入一致。

14. **[让 ZIP 与目录操作报告真实完整性](../../../.scratch/server-facility-next/issues/14-zip-result-contract.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 打包和目录统计能区分完整成功、声明的部分结果与失败；损坏或未完成的产物不会被当作成功文件消费。

15. **[让 CSV 方言、流消费和电子表格导出策略可验证](../../../.scratch/server-facility-next/issues/15-bounded-csv.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 消费者明确选择严格机器交换或 legacy 方言，有界处理行数据，并按导出用途处理公式风险。

16. **[让 Excel 读取与导出拥有预算和临时资源保证](../../../.scratch/server-facility-next/issues/16-bounded-excel.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 选用 Excel 能力的独立消费者获得完整依赖、稳定格式政策和受控资源使用，稀疏或畸形输入不会引发无界分配。

17. **[升级加密政策时保持历史密文可读](../../../.scratch/server-facility-next/issues/17-crypto-legacy.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 旧密文具有明确读取路径，新增策略不会因改变全局 KDF 参数而使历史数据失效；异常输入不泄露秘密或触发无界计算。

18. **[让核心值与错误可脱离 Spring 稳定组合](../../../.scratch/server-facility-next/issues/18-core-value-contracts.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 独立纯 Java 消费者能表达成功、无值成功、查询缺席、预期失败和程序错误；核心错误不会强制依赖框架或全局枚举。

19. **[用显式 DTO 映射迁移一个真实消费流程](../../../.scratch/server-facility-next/issues/19-explicit-mapping.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 一个真实 DTO 转换流程通过具名值与显式映射工作，字段、别名和容器语义清楚；旧反射复制入口有有限的支持范围与替代。

20. **[让时间、数值和模式输入遵守显式政策](../../../.scratch/server-facility-next/issues/20-bounded-value-policies.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 代表性的日期与容量输入流程使用明确 Clock、时区、舍入和解析规则；继续支持的格式/模式工具不持有无界缓存。

21. **[为平台替换预先隔离配置入口与消费者金样](../../../.scratch/server-facility-next/issues/21-platform-expand.md)**
   - **Blocked by：** 01。
   - **What it delivers：** 在旧平台仍能验证的状态下，扩展应用拥有的配置入口并保存消费者协议样本，使 Boot/Jackson 替换有明确接缝和回退依据。

22. **[迁移 Boot 4、Spring 技术模块与测试工具链](../../../.scratch/server-facility-next/issues/22-boot-platform-batch.md)**
   - **Blocked by：** 21；宽迁移集成线批次。
   - **What it delivers：** 目标平台依赖、Boot 技术模块、自动装配归属和 JUnit/ArchUnit 工具链在同一迁移线上切换，形成 Jackson 后续批次可消费的明确基线。

23. **[迁移 Jackson 3 并让 HTTP 与应用 mapper 政策一致](../../../.scratch/server-facility-next/issues/23-jackson-application-scope.md)**
   - **Blocked by：** 22；宽迁移集成线批次。
   - **What it delivers：** 目标平台应用通过不可变 builder 和应用作用域 mapper 处理 JSON，HTTP 输出与注入入口一致，两个应用的政策互不污染。

24. **[把目标平台集成为可发布的真实消费者组合](../../../.scratch/server-facility-next/issues/24-boot4-integrate.md)**
   - **Blocked by：** 23。
   - **What it delivers：** Boot 4/Jackson 3 普通库产物在独立应用中完成启动和 HTTP 往返，各种启用/缺席/覆盖组合有确定行为，迁移线恢复完整绿色。

25. **[隔离两个外部服务的配置、凭据和失败](../../../.scratch/server-facility-next/issues/25-outbound-http.md)**
   - **Blocked by：** 24。
   - **What it delivers：** 类型化 HTTP Adapter 继承宿主 Boot builder、JSON 和观测配置，同时为不同第三方保持独立凭据、时限和错误语义。

26. **[让应用拥有本地化、日志与观测政策](../../../.scratch/server-facility-next/issues/26-host-owned-observability.md)**
   - **Blocked by：** 24。
   - **What it delivers：** 应用自定义消息、日志与观测时，设施库贡献必要能力而不抢占配置或重复处理；错误可以关联内部原因而不泄露秘密。

27. **[交付能独立启动且默认受保护的 API 模板](../../../.scratch/server-facility-next/issues/27-secured-template.md)**
   - **Blocked by：** 04、24。
   - **What it delivers：** 从模板实例化的独立应用启动真实 MVC 端点，用 JWT resource server 验证身份；生产缺配置不自动放行，本地开发身份显式选择。

28. **[用一个受保护业务 Module 完成持久化 CRUD 与分页](../../../.scratch/server-facility-next/issues/28-persistent-business-slice.md)**
   - **Blocked by：** 27。
   - **What it delivers：** 调用者通过认证 API 创建并读取业务数据，数据跨重启保留；分页、授权、事务和数据库约束在同一垂直用例中可观察。

29. **[在业务事务中提交命令身份、结果与业务写入](../../../.scratch/server-facility-next/issues/29-transactional-idempotency.md)**
   - **Blocked by：** 28。
   - **What it delivers：** 持久化创建命令通过可信身份、请求键和命令调用业务 Module，重复请求恢复同一已提交业务结果，回滚后可以安全重试。

30. **[在断连、进程丢失和结果清理后恢复同一命令](../../../.scratch/server-facility-next/issues/30-command-recovery.md)**
   - **Blocked by：** 29。
   - **What it delivers：** 客户端不知道提交结果、服务进程重启或两个实例同时收到重试时，系统恢复同一业务效果；receipt 清理不会重新授权重复执行。

31. **[验证模板的版本升级与五类 agent 任务](../../../.scratch/server-facility-next/issues/31-template-upgrade-experience.md)**
   - **Blocked by：** 13、15、25、26、30。
   - **What it delivers：** 模板应用能够按文档独立交付，并在保留业务改动的情况下升级；固定任务展示新增业务、错误、外部调用和已有文件能力的真实使用路径。

32. **[明确 Cookie 与 HTML 清洗的兼容和安全边界](../../../.scratch/server-facility-next/issues/32-web-content-cookies.md)**
   - **Blocked by：** 无，可立即开始。
   - **What it delivers：** 使用现有 Cookie 和显式 HTML 清洗入口的消费者获得稳定可说明的结果，升级 jsoup 不悄悄改变策略，也不把清洗视为通用 XSS 保证。

33. **[在同一候选产物上闭合组合、极端场景与发布证据](../../../.scratch/server-facility-next/issues/33-release-evidence.md)**
   - **Blocked by：** 02、03、07、08、09、10、12、14、16、17、18、19、20、31、32。
   - **What it delivers：** 维护者能用同一提交和候选制品的证据判断首版是否兑现全部承诺，关键接合与持续故障不会被各票独立通过掩盖。

## 关键依赖链

- 平台：01 → 21 → 22 → 23 → 24；24 开启 25、26，并与 04 一起开启 27。
- 新业务路径：27 → 28 → 29 → 30；29/30 不依赖旧响应重放票 11/12。
- 旧重放：04 + 05 + 11 → 12；先补安全接口，再迁移消费者。
- 限流：04 + 06 → 09；身份来源与外部失败协议有真实前置关系。
- 模板使用/升级：13 + 15 + 25 + 26 + 30 → 31；31 只验证所选上传/CSV 场景，不等待无关 Excel/ZIP 才能开展。
- Cookie/HTML：32 独立交付，不阻塞可信代理/配额或原生认证模板。
- 发布证据：33 依赖所有尚未被其他链条包含的末端票，以及 31；依赖清单已省略传递边。文件各票相互独立，没有人为的上传→ZIP→CSV→Excel 顺序。

## 测试要求如何落到任务

用例/契约、场景、接合、极端情况四类测试均有具体责任。高风险的身份×错误×幂等×配额、事务×receipt×断连、流式×捕获×取消、JSON×HTTP×多 context、上传×解析×清理等组合在接合矩阵 J01–J17 中列明。

33 不是“最后补所有测试”的任务。每张前置票必须已经交付自己的测试和结果，33 只在同一候选产物上复验组合、长稳与发布条件，并阻断未闭合缺口。

FR-01–FR-10、AC-01–AC-15 和全部 29 个功能包均有责任归属。FR-11/AC-16 是第二真实消费者出现后的条件扩展，没有伪装成当前 ready-for-agent 实现票。

## 发布记录与执行规则

用户已于 2026-10-03 确认本拆分并要求完成 to-tickets，采用本地文件作为本批发布目标。33 张正式票位于 [.scratch/server-facility-next/issues](../../../.scratch/server-facility-next/issues)，一票一文件，编号 01–33 按依赖顺序排列；状态为 ready-for-agent，Blocked by 保留编号与标题。

- 正式执行以本清单链接的 issues 文件为准；drafts 目录保留评审快照，不作为另一套待办队列。
- 所有验收与场景复选框保持未勾选。领取后先核对 Blocked by，再按本票及 Q01–Q10 交付实现、测试和证据。
- 22/23 仍只进入指定集成线，24 是平台主线合入门；33 复验同一候选产物，不能用不同版本的局部绿色结果替代。
- 本次发布的是本地任务文档，不是软件制品发布，也没有执行票内代码改造、目标版本测试或实际部署。
- 本次不创建远端 issue，不修改或关闭 parent issue。若日后切换 tracker，保留本地编号与映射，并迁移既有阻塞关系。
