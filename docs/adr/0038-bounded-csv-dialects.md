# ADR-0038: 有界 CSV 行消费、明确方言与独立电子表格导出

## Status

Accepted

日期：2026-10-04。

## Context

ADR0021在当时离线环境选择纯JDK手写CSV。当前依赖可实际获取，旧解析器全量累积行、没有字节/字段预算，且宽松语法与机器交换入口没有区分。保留该ADR的裸列表形态、无表头ORM与Excel optional理由；本决定替代手写CSV和无界读的假设，不变更Excel实现。

## Decision

- Commons CSV 1.14.1负责语法，required依赖保留CSV引入即用；不新增optional探针、bean、properties或可配置方言DSL。
- `CsvDialect.STRICT`使用该版本RFC4180 reader，关闭`trailingData`与`lenientEof`；`LEGACY`只开启`trailingData`，保留历史`"ab"x → abx`。两者均拒绝未闭合引号，保留空记录、ragged rows、CR/LF/CRLF与字段内部换行。STRICT是项目定义的机器方言，不宣称完整RFC语法验证：库允许裸字段内的引号，并忽略闭合引号后的空白。该限制必须有独立样本和文档，不能把枚举名当作额外安全保证。
- `forEach`同步逐行消费，`readAll`及旧`read`共享有界路径。`CsvLimits`声明每操作字节、行、列、字段UTF-16长度；全部预算为正数，旧便利入口采用小文件默认 **1 MiB、10,000行、128列、每字段1,024个UTF-16单元**。发现N+1行报告失败，不使用会静默截断的库`maxRows`。错误累积固定为1，首次失败立即停止；已有consumer副作用不回滚。
- 资源设计：库没有字段/列解析前预算，因此在其Reader前设置推导的每记录源字符上限，记录由成熟解析器产出后才复位；语法识别仍只有一套。精确列/字段检查在交付consumer前执行，解析峰值由源记录预算先约束。推导上限为 `(2F+3)*C+3`（默认262,531），超过Integer.MAX_VALUE的配置拒绝，冗余语法也受限制。Commons在CR后最多窥看下一记录1字符；UTF-8解码器可预读8KiB。字节实际探测不超过N+1。拒绝、consumer故障与取消不drain，不承诺恢复借用流位置。
- 流重载借用输入/输出，Path重载拥有自己打开的流。UTF-8坏编码确定失败。程序错误与consumer异常传播；可预期格式/预算/IO错误经Result，安全外层位置不包括单元格正文，保留的外部I/O cause仅供受信任诊断。Path直接覆盖写，不承诺原子发布；打开前中断不能截断已有文件。协作取消不替代宿主阻塞I/O超时。
- 机器输出 `writeMachine` 保留字段值且不加BOM，旧 `write` 保留BOM与原值。`writeSpreadsheet` 带BOM，跳过前导Unicode空白/控制/FORMAT字符后拒绝 `= + - @` 及全角对应字符，同时拒绝前导区域Tab/CR/LF。不加转义前缀、不偷偷改数据，保守拒绝负数字符串；不承诺所有电子表格的通用安全。CSV引用本身不是公式防护。不新增字段映射、必填验证或表头模型。

## Consequences

**Positive**：成熟实现独占语法，公共调用集中资源政策与所有权。严格与历史消费有可执行差异，机器数据与电子表格安全政策分离。

**Negative**：新增required依赖及传递图；旧无界便利读取收紧。Reader记录预算和读前探测有固定开销，已在登记规模的64MiB受限堆场景验证，但不承诺任意配置/业务callback的固定内存。STRICT的确切范围见上，不能用于声称任意CSV规范验证。

**Carry-forward**：票31负责上传至CSV的业务交接；票33对最终候选扩大组合和故障稳态，不反向作为本票实现前置。实测与边界见 [票15证据](../verification/ticket-15-bounded-csv.md)。本模块无共享可变状态、后台线程、队列或临时文件；预算每调用有效，宿主负责全局并发准入。

## References

1. [Apache Commons CSV 1.14.1 Lexer source](https://github.com/apache/commons-csv/blob/rel/commons-csv-1.14.1/src/main/java/org/apache/commons/csv/Lexer.java).
2. [CSVFormat Builder API](https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVFormat.Builder.html)：站点标题为开发版，行为以实际1.14.1制品与测试为准。
3. [正式票15](../../.scratch/server-facility-next/issues/15-bounded-csv.md)、ADR0021、PRD v0.2与测试策略Q01–Q10/J12–J16。
4. [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection)：公式前缀、控制字符及非通用转义边界；本项目选用显式拒绝。
