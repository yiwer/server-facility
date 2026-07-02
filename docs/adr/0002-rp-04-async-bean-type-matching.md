> **inherited-from**: beacon ADR-0002(原仓库 docs/adr/0002-rp-04-async-bean-type-matching.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0002: RP-04 Async bean 名匹配 → 类型匹配

## Status

Accepted

日期：2026-05-21

## Context

- 评审来源：`docs/facility/REVIEW.md` §7 RP-04 + §2.6 评审发现（`autoconfigure\FacilityAsyncAutoConfiguration.java:11`）+ §6.E.1 `autoconfigure/`
- beacon 现状：`FacilityAsyncAutoConfiguration` 对 `facilityAsyncExecutor` bean 使用 `@ConditionalOnMissingBean(name = "facilityAsyncExecutor")` —— String 名匹配。
- 问题：若 consumer 以不同 bean 名（例如 Spring Boot 自动装配的 `applicationTaskExecutor`）注册 `TaskExecutor` / `Executor` 类型的 bean，facility 的 condition 不会触发，将导致**额外 executor 共存**（两个 Executor bean），在 `@Async` 调度场景产生预期外行为（Spring 选用哪个执行 @Async 取决于 bean 排序）。
- 行业现状：
  - Spring Framework 6 Reference §1.9.4 (Conditional Bean) 推荐"类型匹配优先"
  - Spring Boot `TaskExecutionAutoConfiguration`（Spring Boot 3.x）实际使用 `@ConditionalOnMissingBean(Executor.class)` 类型匹配（更具体地 ConditionalOnMissingBean(TaskExecutor.class)）
  - 类型匹配的语义：context 中已有任何"该类型或其子类型" bean 时跳过自身装配

## Decision

把 `FacilityAsyncAutoConfiguration` 的 condition 改为类型匹配：

```java
// Before
@ConditionalOnMissingBean(name = "facilityAsyncExecutor")
public Executor facilityAsyncExecutor() { ... }

// After
@ConditionalOnMissingBean(TaskExecutor.class)
public Executor facilityAsyncExecutor() { ... }
```

**关键选择**：选 `TaskExecutor.class`（Spring 抽象）而非 `Executor.class`（JDK 抽象）。理由：
- Spring `@Async` 调度路径优先寻找 `TaskExecutor` 类型 bean（见 `AsyncConfigurationSelector` / `AsyncAnnotationBeanPostProcessor`）
- Spring Boot `TaskExecutionAutoConfiguration` 默认装配 `ThreadPoolTaskExecutor`（implements TaskExecutor），condition 应对齐此类型
- 若用 `Executor.class`，consumer 的非 Spring TaskExecutor（如纯 JDK `ExecutorService`）也会触发条件失败，过于宽泛

**bean 返回类型保持 `Executor` 不动**（不改为 `TaskExecutor`）。理由：
- 改返回类型是 API-break（旧调用方按 Executor 类型 `@Autowired` 注入会工作，按 ThreadPoolTaskExecutor 类型注入会失败）
- phase-3 范围限于 condition 改动；返回类型升级留 carry-forward
- facility 默认装配的是 `Executors.newVirtualThreadPerTaskExecutor()`（Java 21 虚拟线程），它返回 `ExecutorService`（不实现 Spring `TaskExecutor`）。若强行把返回类型声明为 TaskExecutor 需额外包装，超出本 phase 范围

**预期行为**：
- consumer 有 `applicationTaskExecutor`（Spring Boot 自动装配的 TaskExecutor）→ condition fail → facility 不装配 `facilityAsyncExecutor`
- consumer 无任何 TaskExecutor → condition pass → facility 装配 `facilityAsyncExecutor`（Executor 类型）；旧调用方按 `@Autowired Executor` 仍可注入

## Consequences

**Positive**：
- 消除"facility executor 与 consumer executor 共存"风险
- 与 Spring Boot `TaskExecutionAutoConfiguration` 业界惯例对齐
- consumer 若有自己的 TaskExecutor 配置（如自定义线程池大小），facility 不再悄悄注册另一个

**Negative**：
- 若 consumer 旧代码依赖"facility 总是装配 facilityAsyncExecutor"（即使已有 TaskExecutor），行为变更（潜在 break）；本 phase 无真实 consumer，影响为 0
- bean 返回类型保持 Executor，新装配的 bean 不被 `@ConditionalOnMissingBean(TaskExecutor.class)` 自身覆盖（即 facility 装配后再次评估 condition 仍 pass）—— 这是 Spring 单次装配语义，不引发问题

**Carry-forward**：
- 若 phase-4+ 有 consumer 需 facility executor 与 `@Async` 集成且需 TaskExecutor 类型，新建 facility-managed TaskExecutor wrapper bean（保留 Executor bean 兼容旧调用方）
- bean 返回类型 Executor → TaskExecutor 升级留待"facility executor 公开 API 大重构" phase

## References

1. Spring Framework 6 Reference §1.9.4 (Conditional Bean). https://docs.spring.io/spring-framework/docs/6.x/reference/htmlsingle/#beans-java-conditional
2. Spring Boot 3.x `TaskExecutionAutoConfiguration` 源码. https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/task/TaskExecutionAutoConfiguration.java
3. Spring Boot Reference Documentation §10.5 (Task Execution and Scheduling). https://docs.spring.io/spring-boot/docs/3.5.x/reference/htmlsingle/#features.task-execution-and-scheduling
4. `org.springframework.core.task.TaskExecutor` JavaDoc

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
