# Identifier policy and migration

新应用直接使用`UUID.randomUUID()`，以UUID或其标准字符串作为新业务标识。它不需要分配SnowId节点；唯一性来自JDK UUID v4的随机算法及业务数据库约束，不是有限测试可证明的数学保证。需要替换或可控测试时，应用可提供具名`Supplier<UUID>`，注入所属业务操作；不要让设施维护另一套通用ID接口。独立消费者会沿真实应用bean及业务操作验证默认值、用户覆盖与多context关闭。

## 显式SnowId

继续使用既有数字协议时，直接注入`SnowIdGenerator`或以明确节点构造它。默认不注册SnowId bean。选用自动装配必须同时声明：

```properties
facility.id.enabled=true
facility.id.data-center-id=2
facility.id.worker-id=3
facility.id.start-timestamp=1735660800000
facility.id.clock-backwards-threshold-millis=5
facility.id.throw-on-clock-backwards-exceed-threshold=true
facility.id.wait-timeout=1s
```

节点各占2位、范围0–3；缺省-1表示尚未配置，不能生成。节点0仍可显式分配，禁止的是缺配置时暗中复用。用户自有SnowId bean优先，不会被默认实现替换。`enabled=false`只关闭设施装配，不销毁用户bean，也不影响JDK UUID。

本实现保留41位时间差、2位数据中心、2位worker和10位sequence，共55个有效位。每个节点在同一毫秒使用sequence0–1023；耗尽后必须等待严格后一个毫秒，不虚构未来时间。生成前检查`wallTime - epoch`在0–2199023255551之间，溢出或范围外失败，不推进状态。epoch构造值仍可为任意long，以便使用旧实例解析历史协议；这不意味着当前时钟能在该epoch下生成有效ID。

跨实例唯一性要求整个部署使用一致的epoch/布局，并为每个活动生成器分配不同节点对。节点对不能同时复用；重启/迁移后的新owner必须在**严格晚于旧owner曾使用的最大时间戳**后开始，且旧owner已不可能继续发号。仅fencing或“没有同时运行两个进程”不能解决同毫秒重启后sequence归零的问题。本库不保存跨进程高水位、不分配节点、不检测其他JVM；无法兑现这些条件时使用UUID。两个同节点同时间的生成器会确定地产生相同ID，测试公开证明这一边界，不把部署责任包装成库保证。

## 时限与失败

`wait-timeout`为正Duration，至多1分钟，缺省1秒。一次调用从锁准入到时钟恢复/序列耗尽等待共用`System.nanoTime()`期限，取得锁后不会重置期限；状态提交前再次检查。不同调用各有自己的预算，不保证公平性。超时返回`IllegalStateException`，cause为标准`TimeoutException`；中断返回`IllegalStateException`，cause为`InterruptedException`，保留中断标志。失败不消耗sequence或改变最近已发时间。

`throw-on-clock-backwards-exceed-threshold=true`在回拨超过非负阈值时立即抛旧`ClockBackwardsException`；阈值内等待恢复。`false`允许等待更大回拨，但同样服从总预算和中断，**不再承诺无限等待/绝不抛出**，由ADR0033替代ADR0023。无论哪种模式，冻结时钟不能延长期限。

注入的时钟callback必须及时返回、不得重入同一生成器；程序异常直接传播且锁释放。等待政策不能抢占不合作的callback，也不是OS调度、GC或整个请求的硬实时保证。调用方的整体请求预算与失败后重试政策仍由应用管理。

## 旧入口与存量协议

`IdUtil`全部公开签名保留，作为迁移入口。`snowId`、`parseTimestamp`、`parseInfo`需要显式设置的generator或当前应用provider；缺席抛出明确异常，不再使用静态节点0。`getGeneratorType`报告`EXPLICIT`、`SPRING_BEAN`或`MISSING`；`isUsingSpringGenerator`只表示当前实际来自Spring，手工设置不冒充Spring。`setGenerator/resetGenerator`仍是进程级显式兼容状态，新应用使用构造注入；关闭应用后不会缓存已关闭应用的bean。

旧数字ID不改写。`parseTimestamp/parseInfo`继续按实例epoch读取，worker/dataCenter/sequence解析保留；旧解析是位字段读取，**不是有效性或来源验证**，也保留负ID的历史算术。新发号的严格范围检查不倒灌到旧reader。

| 已持久化样本 | 旧/新约定 |
|---|---|
| `163851281`，epoch1735660800000 | timestamp1735660810000，dataCenter2，worker3，sequence17 |
| `0`，同epoch | 可表示epoch瞬间、节点0/0、sequence0 |
| `36028797018963967` | 55位最大值；timestamp3934684055551，节点3/3、sequence1023 |
| `16384000`，epoch-1000 | timestamp0；负epoch构造/解析保留 |

默认JSON long仍是数字，例如`{"id":36028797018963967}`，不会暗中把现有wire类型改成字符串。该最大值超出JavaScript精确整数范围；面向此类消费者的新DTO应显式选择字符串字段，得到`{"id":"36028797018963967"}`，或对版本化API配置宿主serializer。不要先转double再转字符串。UUID本身按标准字符串表示；旧数字字段改成UUID属于业务数据/API迁移，不能由设施自动替换。

旧原始jar、字面样本、当前消费者和资源结果见[票10验证记录](../verification/ticket-10-id-policy.md)。参考JDK25 [UUID](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/UUID.html)、[ReentrantLock](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)及[System.nanoTime](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#nanoTime())。
