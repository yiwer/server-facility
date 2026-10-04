# ADR-0043: 显式时间、容量与有限模式政策

## Status

Accepted，2026-10-04。设计已落实；票20本地完整门已通过、跨平台验收待CI，实际完成范围见[验证报告](../verification/ticket-20-value-policies.md)。

## Context

DateUtil把动态pattern无限缓存并捕获首次默认Locale，SMART日期归一化不等于严格业务输入。NumberFormat.parseSize用double会丢字节并把溢出转成长整型极值；Patterns保存任意模式且不能由缓存政策保证匹配时间。继续扩张静态透传方法无法让应用表达时钟、时区、授权输入和资源预算。

## Decision

应用自有ExportRequests Module使用注入Clock/ZoneId/Locale、固定严格uuuu-MM-dd HH:mm、0001–9999年、严格未来且最多30×24小时窗口。DST gap拒绝，overlap要求有效显式offset；容量字段固定MiB、32UTF16、ASCII十进制最多6小数、正值且最多64MiB，字节向下取整。错误不包含原输入cause。独立JDK-only消费者真正执行该入口；根库没有生产日期/容量请求链可假称已迁移，不新增规则引擎或浅工具facade。

DateUtil保留SMART与调用时默认FORMAT Locale的legacy语义，移除动态formatter map，旧默认时区/当前时间/错误cause范围明示。Numbers通用解析与预算政策不混同。NumberFormat.parseSize改为ASCII精确十进制、有符号long范围检查和向零截断，128UTF16及scale[-128,128]先于算术；零/负仍是通用数值，不是无限预算。NumberUnits只弃用并提供直接BigDecimal迁移，保留double/Math.round旧金样，不凭空构造打印模块。

Patterns只保留最多256个regex/flags，键长度超过4096UTF16的宿主可信模式编译后不入全局缓存；缓存操作同步、编译在锁外，identity不跨逐出/clear保证。错误/组号/集合/替换与预定义形状谓词沿旧契约明确。此容量不保证编译/匹配CPU或所有输入/输出内存；请求模式必须由应用限制为有限预定义集合，禁止宣称任意Java正则安全超时。

库路径不新增线程/队列/网络/文件生命周期。32/64MiB独立进程、固定seed200043、实际旧普通jar金样及新流程是本票证据；完整最终来源和跨平台状态按报告分别登记。

## Consequences

**Positive**：业务输入可从调用点看出环境和失败规则；容量不再无声丢失整数精度。

**Negative**：严格输入、拒绝溢出及有限缓存可能拒绝旧入口曾接受的值；迁移说明与兼容样本必须逐项区分。Java正则仍仅接受宿主信任的开发者模式，不承诺任意不可信模式的执行时限。

**Carry-forward**：本票Q01–Q10/J14/J16的Windows完整门通过，Linux待同源CI；33负责最后候选组合，不反向替代本票证据。

## References

1. [正式票20](../../.scratch/server-facility-next/issues/20-bounded-value-policies.md)。
2. [核心模块研究§4](../research/2026-10-03-core-modules.md)。
3. [JDK25 BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html)。
4. [JDK25 DateTimeFormatter](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/DateTimeFormatter.html) 与 [ZoneRules](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/zone/ZoneRules.html)。
