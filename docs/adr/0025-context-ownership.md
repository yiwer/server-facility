# ADR-0025: Context 注册与服务的生命周期归属

## Status

Accepted。日期：2026-10-03。对应 ticket 02 / FR-05、FR-08、AC-07、AC-11。

## Context

本决定替代 ADR-0006 中 LogPostHandlerComposite 的进程级缓存策略；保留其中每次日志最多分发一次的公开保证。其他 ADR 的业务政策不在本票更改范围。

## Decision

兼容静态 holder 只发布第一个成功完成刷新并发出本容器 ContextRefreshedEvent 的实例。注册令牌归 holder 实例所有，不能只比较 context 引用：同一个 context 内也可能有多个 holder。被拒绝者关闭不清理 owner，owner 关闭后也不自动接管曾被拒绝的应用；新应用或显式再次成功刷新方可竞争空位。销毁终止该实例，重复销毁无副作用，迟到刷新事件不能复活它。

在本容器 ContextClosedEvent 的高优先级同步监听器中撤销注册，再以 DisposableBean.destroy 兜底处理启动失败与刷新时旧 bean 的销毁。父子事件传播只处理与自己绑定的 context 身份相同的事件。getBean 把关闭竞争的 Spring 生命周期异常归入既有 Result 错误；已经交给消费者的 bean/正在执行的操作不由 holder 取消，消费者资源仍由原 Spring 容器管理。

setApplicationContextManually 仅为弃用兼容入口，不再替换现存 owner；只接受已完成刷新、仍活跃且未开始关闭、使用 Spring 标准 singleton registry 的 AbstractApplicationContext，自动注册关闭监听器。手工注册的撤销回调同时绑定到 bean factory 销毁（包括原地刷新），撤销时移除手工 listener，重复登记不增长监听器。重新刷新后必须显式重新登记。调用方必须在 refresh 返回后、且不与下一次 refresh 并发调用；null 保留忽略语义。未刷新/关闭中/不支持该生命周期的对象拒绝注册。生产和测试都不再提供全局 clear 捷径。

## Consequences

新代码采用标准构造器注入所需服务（例如 CacheManager、MessageSource 或业务 Module），不新增另一套通用服务定位器。普通消费者独立拥有本应用注入的依赖，兼容 holder 不能代表多应用路由。IdUtil 和 LogUtil 不缓存 Spring bean；IdUtil.setGenerator 的显式进程级 override 仍由设置者管理，resetGenerator 只重置该显式 override/告警标志。JSON、locale、日志策略的全量迁移分别属于其负责票。

验证入口为 SpringContextOwnershipTest 的真实 Spring 生命周期与注入消费者、SpringContextHolderTest 的公共查询 API、ID/日志重启回归。测试 fixture 只关闭自己创建的上下文，不反射或重置任何全局 holder；所有关闭、失败、并发和刷新测试以资源状态及消费者结果验收。

## References

- [本票研究证据](../research/2026-10-03-web-and-agent-design.md#25-静态上下文的销毁没有所有权)
- [既有 ADR-0006](0006-rp-13-cas-compare-and-exchange.md)
- [Spring 构造器注入](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
