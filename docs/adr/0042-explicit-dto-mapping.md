# ADR-0042: 显式DTO映射与有限旧复制契约

## Status

Proposed，2026-10-04，票19实施中；验证状态见[报告](../verification/ticket-19-explicit-mapping.md)。

## Context

原CopyUtil/AutoCopyEngine按字段泛型选择深浅策略，普通集合与嵌套泛型共享源引用、源null保留新构造目标默认值。这些已是测试固定的历史行为，不能统称缺陷。真实缺口是反射写final字段、递归没有有界拒绝、具体集合赋值失败和比较器丢失；通用“深拷贝”名称无法说明这些不同语义。研究建议把DTO映射放回业务模块，不建设新的对象图框架。

## Decision

新路径用具名record和显式构造表达订单转换，业务字段完整性由真实消费者结果与编译控制检查。旧autoCopy保留签名供迁移，退出默认示例；禁止反射写final字段，递归CopyTrait→autoCopy拒绝活动路径循环，并限制32层活动源。浅引用的历史范围继续如实说明，不能把未遍历的对象图声称独立快照。旧集合helper仍按原契约规范化为ArrayList/HashSet/HashMap；autoCopy的深容器拒绝排序或不兼容具体类型，普通DIRECT字段仍共享。同步嵌套公共调用合计10,000工作单元（非null反射字段、数组槽、集合/Map条目），最多32层活动公共调用；实际遍历计数，越界/中断拒绝并finally清理作用域。固定安全SLF4J日志吞自身backend RuntimeException，不改变复制结果。独立业务消费与资源进程仍待后续验证，不把本Proposed记录当作整票完成。

## Consequences

公共签名保持可迁移，复杂对象图由业务显式选择快照语义；不新增MapStruct运行时依赖、通用反射映射器或配置DSL。用户CopyTrait/Function中的任意代码仍由宿主管理时间与资源，库不会声称能抢占其工作。

## References

- [正式票19](../../.scratch/server-facility-next/issues/19-explicit-mapping.md)、PRDv0.2 FR08/09、AC11/12。
- [核心模块研究§3.3](../research/2026-10-03-core-modules.md)。
