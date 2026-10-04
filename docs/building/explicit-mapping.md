# 显式DTO映射与旧复制入口迁移

新路径把转换放在拥有业务字段的Module，用具名record/值对象和显式构造。可执行例子是[订单到发运DTO](../../examples/order-mapping/README.md)：`id→orderId`、`buyer→recipient`、重复商品行保序、属性按名字排序、备注null保持缺失。独立消费者使用完整字面业务结果，新增目标字段/改名来源字段会编译失败，同类型字段对调由业务结果测试发现。没有新增MapStruct、反射映射框架或无人消费的库接口。

旧 `CopyUtil.autoCopy` 保留签名、标记弃用且不立即删除；`CopyTrait`及集合helper仍可迁移使用。不要把它当作任意图的深快照。

| 旧入口/形状 | 明确行为 |
|---|---|
| null源 | autoCopy返回null；集合helper返回新的空容器；必需回调/options仍先检查 |
| autoCopy构造/字段 | 要求可访问无参构造和可访问字段；拒绝未忽略的final字段。static/transient/synthetic和`@CopyField(ignore=true)`跳过，忽略的final保留构造值 |
| null字段/预存默认值 | source字段为null时不覆盖新目标的构造默认值；显式DTO映射可选择真正传null |
| 普通引用、普通/嵌套泛型集合、仅key为CopyTrait的Map | DIRECT，共享同一引用，不能宣称独立快照；DIRECT中的循环不被遍历 |
| 普通数组 | 只复制数组槽；对象/多维数组内层仍共享 |
| CopyTrait字段/数组元素 | 调用用户copy；多个深别名分别复制，不保留图身份；回调自己承担具体深浅语义 |
| autoCopy深集合/Map | 规范化为ArrayList或LinkedHashSet、LinkedHashMap；遍历顺序保留。无法赋给字段声明类型的具体容器（如LinkedList）及深SortedSet/SortedMap拒绝；不会猜比较器/构造方式。声明List而实际LinkedList可规范化 |
| 集合helper | 历史ArrayList/HashSet/HashMap输出政策保留，Set/Map比较器与顺序不保留。需要排序/不可变语义时在业务层显式构造 |
| 复制后的相等key冲突 | 按源遇见顺序最后一项覆盖；不是去重业务规则 |
| CopyOptions | 既有null政策保留：严格copy返回null抛CopyException；宽容List/Set可留null，map-values跳过违约null结果；map-all原null-key跳过并警告，宽容复制key/value为null的历史行为保留 |

每次同步调用及其嵌套库调用共用10,000工作单元：每个实际遍历的可复制反射字段（包括null字段）、数组槽、集合/Map条目各计一项。数组在分配前预留；声明size超预算前置拒绝，实际迭代也计数。最多32个活动公共调用；活动路径的同源递归拒绝，独立深别名不做去重。越界/不支持反射或容器/循环/协作中断使用CopyException，finally移除每次调用作用域，线程间互不共享。

这些边界只限制库拥有的遍历、结果容器和作用域。输入对象、运行时Class定义、ClassValue元数据的Class生命周期，以及用户构造器/CopyTrait/Function/iterator的时间与分配由宿主拥有。库不会抢占任意用户代码；中断在库工作前和回调之间观察且不清除标记。已完成回调的外部副作用不回滚。集合helper透传回调RuntimeException/Error；autoCopy保留其反射调用的历史异常包装，字段回调Error传播，清理不替换首因。

`autoCopy`不是安全沙箱，也不是序列化或Bean字段名转换API。新record天然的final字段由显式构造形成；不要为兼容反射增加无参构造、打开JDK模块或改可变字段。标准SLF4J警告仅含有限固定消息，无对象/key/cause原文，后端RuntimeException不改变复制结果。默认路径不经过LogUtil的第二次分发。

原普通jar签名、构造默认值及浅引用子集以对旧库编译的真实class fixture在新jar运行。新有界拒绝属于有意收窄，需要依赖旧无界/final/比较器丢失行为的调用方显式迁移。精确RED/GREEN、受限堆、seed与平台状态见[19验证报告](../verification/ticket-19-explicit-mapping.md)和[ADR0042](../adr/0042-explicit-dto-mapping.md)。
