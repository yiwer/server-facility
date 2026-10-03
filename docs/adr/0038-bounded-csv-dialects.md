# ADR-0038: 有界 CSV 行消费、明确方言与独立电子表格导出

## Status

Proposed（实现中的已批准票15；测试完成后登记 Accepted）

日期：2026-10-04。

## Context

ADR0021在当时离线环境选择纯JDK手写CSV。当前依赖可实际获取，旧解析器全量累积行、没有字节/字段预算，且宽松语法与机器交换入口没有区分。保留该ADR的裸列表形态、无表头ORM与Excel optional理由；本决定替代手写CSV和无界读的假设，不变更Excel实现。

## Decision

- Commons CSV 1.14.1负责语法，required依赖保留CSV引入即用；不新增optional探针、bean、properties或可配置方言DSL。
- `CsvDialect.STRICT`使用该版本RFC4180 reader，关闭`trailingData`与`lenientEof`；`LEGACY`只开启`trailingData`，保留历史`"ab"x → abx`。两者均拒绝未闭合引号，保留空记录、ragged rows、CR/LF/CRLF与字段内部换行。STRICT是项目定义的机器方言，不宣称完整RFC语法验证：库允许裸字段内的引号，并忽略闭合引号后的空白。该限制必须有独立样本和文档，不能把枚举名当作额外安全保证。
- `forEach`同步逐行消费，`readAll`及旧`read`共享有界路径。`CsvLimits`声明每操作字节、行、列、字段UTF-16长度；全部预算为正数，旧便利入口采用小文件默认。发现N+1行报告失败，不使用会静默截断的库`maxRows`。错误累积固定为1，首次失败立即停止；已有consumer副作用不回滚。
- 实现中的资源设计：库没有字段/列解析前预算，因此在其Reader前设置推导的每记录源字符上限，记录由成熟解析器产出后才复位；语法识别仍只有一套。精确列/字段检查在交付consumer前执行，解析峰值由源记录预算先约束。拒绝、consumer故障与取消不drain，不承诺恢复借用流位置。
- 流重载借用输入/输出，Path重载拥有自己打开的流。UTF-8坏编码确定失败。程序错误与consumer异常传播；可预期格式/预算/IO错误经Result，安全位置不包括单元格正文。
- 机器输出保留字段值；电子表格导出政策另设明确入口，不能把普通CSV引号当作公式防护。不新增字段映射、必填验证或表头模型。

## Consequences

**Positive**：成熟实现独占语法，公共调用集中资源政策与所有权。严格与历史消费有可执行差异，机器数据与电子表格安全政策分离。

**Negative**：新增required依赖及传递图；旧无界便利读取收紧。Reader记录预算和读前探测有固定开销，需实际受限堆场景验证。STRICT的确切范围见上，不能用于声称任意CSV规范验证。

**Carry-forward**：票31负责上传至CSV的业务交接；票33对最终候选扩大组合和故障稳态，不反向作为本票实现前置。具体最终测试、默认值和导出政策在本票完成时同步此记录。

## References

1. [Apache Commons CSV 1.14.1 Lexer source](https://github.com/apache/commons-csv/blob/rel/commons-csv-1.14.1/src/main/java/org/apache/commons/csv/Lexer.java).
2. [CSVFormat Builder API](https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVFormat.Builder.html)：站点标题为开发版，行为以实际1.14.1制品与测试为准。
3. [正式票15](../../.scratch/server-facility-next/issues/15-bounded-csv.md)、ADR0021、PRD v0.2与测试策略Q01–Q10/J12–J16。
