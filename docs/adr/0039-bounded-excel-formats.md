# ADR-0039: Excel按格式消费、有界行处理与自有临时空间

## Status

Proposed（已批准票16实施中），2026-10-04。验证完成后登记Accepted，不把未执行环境当通过。

## Context

0021保留裸列表、无表头ORM与POI按需使用的理由。旧WorkbookFactory整簿/补齐稀疏行没有预算，公式自动计算的代价未声明；双类探针不能代替实际格式依赖图。POI5.5.1源码还证明SXSSF.write创建sheet和template两类临时文件，而close对部分writer/dispose失败只记录日志，不能据此声称成功总意味着清理完成。

## Decision

- POI升级5.5.1并继续optional；POI类型留在实现侧，门面无POI引用。实际XLS/HSSF及XLSX引擎具备/缺席通过独立普通jar图验证，不把测试classpath当依赖保证。
- 保留有界小文件read/readAll，增加同步逐行forEach；XLSX选用成熟XSSF/SAX，XLS保留明确小输入上限的HSSF。行号/列号在补齐前检查，拒绝稀疏极末行绕过预算。
- 正预算覆盖实际输入/输出字节、展开字节、行、列、单元格、字符及临时空间；首次错误停止，先前consumer副作用不回滚。精确默认值随资源实测确定，禁止0/负数成为无限制。
- Locale由调用明确选择，便利入口采用确定默认；读公式缓存值或显式拒绝公式，不自动执行任意公式。缺缓存/未知函数的实际格式差异有独立样本，旧自动evaluate的变更有迁移说明。写入字符串始终为文本单元格，包括=1+2。
- 流为借用，Path为自有；程序/consumer异常传播，预期格式/IO/预算通过Result且外层安全定位。清理/关闭保留首因，失败可以有输出前缀，不承诺Path写的原子发布。
- 每操作自有临时目录；读取有界暂存，写通过POI现有扩展口让sheet/template均在预算内，结束显式确认清理。不修改全局TempFile策略、ZipSecureFile限额或JVM Locale，不构建新的通用存储/报表框架。

## Consequences

**Positive**：旧XLS能力继续保留，真正大的XLSX使用逐行路径，资源与公式政策从隐藏假设变成可测试约定。

**Negative**：旧无界便利方法与自动公式计算收紧；SAX仍须保留有界样式/共享字符串元数据，不能承诺任意工作簿恒定内存。

**Carry-forward**：本票先提供明确支持范围和实际资源证据；31/33负责最终业务/候选接合，不反向作为实现前置。本文部分替代0021中Excel内存/公式/清理/探针保证，历史理由保留。

## References

1. [POI Spreadsheet How-to](https://poi.apache.org/components/spreadsheet/how-to.html)：HSSF/XSSF event API与SXSSF；站点为开发版，行为以实际5.5.1为准。
2. [POI5.5.1源码制品](https://repo.maven.apache.org/maven2/org/apache/poi/poi-ooxml/5.5.1/poi-ooxml-5.5.1-sources.jar)：已实际获取并核读SXSSFWorkbook/SheetDataWriter/ReadOnlySharedStringsTable/XSSFSheetXMLHandler。
3. [正式票16](../../.scratch/server-facility-next/issues/16-bounded-excel.md)、PRDv0.2 FR06、共同Q01–Q10/J13/J16及0021。
