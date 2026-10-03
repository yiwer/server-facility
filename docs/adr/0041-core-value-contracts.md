# ADR-0041: 保留核心结果语义，明确回调与值所有权

## Status

Accepted，2026-10-04，票18。补充 ADR-0007 与 ADR-0010，不替代无值成功或错误边界本地化。没有公共删除，也不引入 Unit 或非空 Result 新模型。

## Context

核心值已可在没有 Spring 的进程运行。问题是文档把保存可变引用的容器写成深不可变/天然线程安全；部分集合 mapper 在空输入时接受 null、非空时才失败；容量计算可溢出成负数。新的框架模型不能改善这些契约。

## Decision

1. 推荐 `ok/empty/err`、`map/flatMap/mapErr`、`fold`，查询缺席在消费者使用 `Optional`，预期失败使用领域自有 E。既有组合、Swap、收集和兼容入口全部保留。`empty()` 与 `ok(null)` 等价为成功，不代表查询不存在；转换 Optional/Stream 会丢失此区别。
2. 必需回调在分支判断前检查非空，但不调用未选中的回调。Result、Tuple、Triple、NullSafe 已有该规则；Collects 的 mapper/keyExtractor/valueExtractor 统一遵守。此为显式行为收紧：原来依赖空输入容忍 null 回调的调用方，应提供合法回调。
3. 普通组合回调的程序异常和 Error 直接传播。`of/ofRunnable` 保留显式捕获 Exception（包括 RuntimeException）的兼容语义；InterruptedException 转 Err 同时恢复中断位，Error 不捕获。不要用这两个适配入口包裹全部业务逻辑。
4. Result、Tuple、Triple 只固定引用，不复制载荷。WrappedError 克隆参数数组，返回数组也克隆；数组元素、错误类型与 Exception 仍共享。使用可变载荷须由消费者管理同步、复制与 hash key 稳定性。诊断字符串可能包含业务参数/异常，不是安全 HTTP 消息。
5. 集合输出保留可修改、浅复制、null/重复值的既有语义；`listDiff` 保留多重集差，不承诺顺序。容量计算在 int 上限饱和，合并长度溢出在分配前明确失败；这些操作仍是 O(n) 内存，调用方拥有输入规模预算。
6. `WrappedError.getArg(index, type)` 的 type 必须非空，即使对应参数本身为 null。数据 null 仍合法。此行为收紧登记于迁移说明。
7. 跨业务模块的协议优先使用有名 record。Tuple/Triple 保留用于局部组合及旧消费者；提供实际消费者转换示例，不因库内没有引用就删除公共类型。

## Consequences

没有新增状态、后台资源或抽象层。现有业务协议及 Result serialVersionUID 保持不变；两个回调/类型参数校验收紧及容量溢出修复须有独立消费者回归。error 仍只依赖 JDK，本地化继续在宿主展示边界完成。
