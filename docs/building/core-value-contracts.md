# 纯 Java 核心值与兼容入口

票18 / ADR-0041。推荐先把业务成功值、查询缺席与预期失败分别命名，再选择返回类型。比如 `Optional<Product>` 表达查询缺席，`Result<OrderLine, OrderFailure>` 表达创建明细的预期失败；算术溢出等程序错误直接传播。完整可执行示例见 `verification/core-consumer/CoreConsumer.java`，运行时只加载普通 facility jar。

## 推荐入口与不变语义

- 用 `ok(value)`、`empty()`、`err(domainError)` 构造，`map/flatMap/mapErr` 组合，`fold` 在边界选择业务分支。E 可以是领域自己的 enum、record 或其他类型，不需要注册到 FacilityErrorType。
- `ok(null)`、`ok()`、`empty()` 都是无值成功；`orElse` 不会因成功值为 null 而选择默认值。`map` 接收真实的 null。`Optional`/`Stream` 转换会把无值成功与失败都变为空，调用方须接受这种信息丢失。
- Err 必须非空。非空 Ok 可以 swap 成 Err；无值 Ok 的 swap 抛 IllegalStateException。没有 Unit 替代，没有新增 non-null Result 协议。
- 必需回调总是先检查非空；短路仅避免调用有效的回调。mapper 可返回 null 成功值；flatMap/recoverWith/andThen 等返回 Result 的回调不能返回 null 容器。
- 普通组合不捕获回调的程序异常或 Error。`of/ofRunnable` 是选择性的 Exception 转换适配器，保留捕获 RuntimeException 的旧语义；捕获 InterruptedException 同时恢复中断位，Error 传播。不要把所有业务函数套进这两个入口。

## 所有权与资源

Result、Tuple、Triple 固定的是引用，不是任意对象图。集合输出与 WrappedError 的参数数组复制容器结构，元素仍共享；WrappedError 的 Exception 也未复制。可变元素会影响 equals/hashCode；需要并发访问或长期作为 hash key 时，消费者应选择不可变领域值或自己完成同步/复制。

`getFullMessage`、`toString`、默认错误格式化是诊断能力，可能包含业务参数/异常细节，不是可直接公开的 HTTP detail。本地化仍由宿主展示边界负责；error 包无 Spring 注册、容器或 MessageSource 依赖。

集合工具保留 O(n) 处理与内存需求，不接受无限输入承诺，不启线程或创建静态业务缓存。`calculateCapacity` 对超大正值饱和到 int 上限；这不保证可以分配该规模。`safelyJoin` 的总长度溢出在分配、遍历前抛 ArithmeticException。调用方拥有正常输入的规模预算。

## 公共 API 处置台账

本票没有删除公共类型、方法或构造入口；现有序列化版本号不变。表格按同语义入口分组，保留的低频组合不作为首选教程扩张。

| 入口 | 处置 / 契约 |
|---|---|
| Result `ok/empty/err`，公开 Ok/Err record | 保留；成功可空，错误非空，浅引用 |
| `map/flatMap/mapErr/fold` | 推荐；领域错误自主，程序错误传播 |
| `fromOptional/fromNullable/of/ofRunnable`，ThrowableSupplier/ThrowableRunnable | 保留的显式转换；absence/Exception 各自有可见政策 |
| `bimap/ensure/recover/recoverWith/swap/and/andThen/or/orElseSupplier` | 保留；短路、惰性和 null 返回规则不变 |
| `isOk/isErr/isOkAnd/isErrAnd/get/getErr/orElse/orElseGet/orElseMap/orElseThrow` 两重载 | 保留；默认按状态选择，必需回调非空 |
| `peek/peekErr/ifOk/ifErr/match` | 保留；副作用回调只在所选分支执行 |
| `toOptional/toOptionalErr/stream` | 保留；文档明确成功 null 的信息丢失 |
| `collectShortCircuit/collectAll` | 保留；前者不读取首个失败之后输入，后者保留顺序/重复/null 成功值，失败返回错误列表 |
| ErrorTypeInterface / FacilityErrorType / WrappedError 各工厂与查询、格式化入口 | 保留；领域无需依赖全局枚举；默认格式化与本地化分开，诊断不承诺脱敏 |
| WrappedError `getArg(index,type)` | 收紧：type 必须非空，包括参数值为空时；`getArgs/getArgsList` 只保证数组结构保护 |
| Collects `toMap` 两重载，`safelyMappingAndJoin/mapNonNull` | 收紧：必需 extractor/mapper 在空输入时也不能为 null；first-wins、null 过滤、顺序/重复语义保持 |
| `safeExtractFromMap/extractCompareTuple` | 保留；前者 null type 仍按其历史 nullable 查询返回 empty，与 getArg 必需类型参数有明确区别 |
| `safelyJoin/listDiff/calculateCapacity/longListToLongArray` | 保留；join 检查算术溢出，listDiff 不保证顺序，capacity 饱和，null Long list 仍返回 null 数组 |
| NullSafe 全部检查/default/asList 入口 | 保留；数据 null 容忍，computeOrElse supplier 必需；asList 返回可修改浅复制 |
| Tuple/Triple 工厂、映射、swap/rotate/reverse、merge、投影及 TriFunction | 保留用于局部组合与旧消费者；公开业务协议优先有名 record |
| Tuple `toEntry/toNullableEntry/fromEntry` | 保留；前者非空且不可修改，nullable Entry 保持可修改，新旧值不暗改 |

显式迁移：空输入时曾传 null 回调的调用点应提供有效回调；typed getArg 应提供实际 Class。容量溢出不再导致负容量或回绕后尝试读取巨大输入。没有基于“库内部无引用”删除 public API。

`CoreConsumer` 的订单场景和 PriceChange 转换是实际运行的纯 Java 消费者：旧 Tuple/Triple 在内部组合，输出为有名 OrderLine/PriceChange；没有另建映射平台。

## 验证入口

正常完整验证运行 `java verification/Verify.java integration` 或 `all`：先构建/安装普通 jar，再用 JDK javac 编译独立消费者，只把该 jar 与消费者 class 放入 64MiB/45秒子进程。它主动检查 Spring、Jackson、SLF4J、Lombok 与 annotation runtime 不在 classpath，运行实际业务、别名/复制、错误/中断和固定种子180041的512组组合性质。

本票保留原有 Result/Swap/WrappedError/集合与 Tuple/Triple 测试；四项行为修复另有 `CoreValueBoundaryTest` 参与覆盖率门。迁移红态的局部源码 TDD 不替代完整普通 jar、架构或 CI 验证，实际结果见票18验证报告。
