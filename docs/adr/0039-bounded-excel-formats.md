# ADR-0039: Excel按格式消费、有界行处理与自有临时空间

## Status

Accepted，2026-10-04。Windows同源完整门通过（1600项及独立消费者/资源/格式证据）；CI12同源Windows/Ubuntu完整门与归档均通过，票16closed，见[跨平台报告](../verification/ticket-11-16-27-ci.md)。详见[验证报告](../verification/ticket-16-bounded-excel.md)。

## Context

0021保留裸列表、无表头ORM与POI按需使用的理由。旧WorkbookFactory整簿/补齐稀疏行没有预算，公式自动计算的代价未声明；双类探针不能代替实际格式依赖图。POI5.5.1源码还证明SXSSF.write创建sheet和template两类临时文件，而close对部分writer/dispose失败只记录日志，不能据此声称成功总意味着清理完成。

## Decision

- POI升级5.5.1并继续optional；POI类型留在实现侧，门面无POI引用。实际XLS/HSSF及XLSX引擎具备/缺席通过独立普通jar图验证，不把测试classpath当依赖保证。
- 保留有界小文件read/readAll，增加同步逐行forEach；XLSX选用成熟XSSF/SAX，XLS保留明确小输入上限的HSSF。行号/列号在补齐前检查，拒绝稀疏极末行绕过预算。
- 正预算覆盖实际输入/输出字节、展开字节、行、列、单元格、字符及临时空间；首次错误停止，先前consumer副作用不回滚。默认4 MiB输入/输出、16 MiB展开、10,000行、128列、100,000单元格、32,767 UTF-16单元和32 MiB临时写入内容；错误固定首次停止，禁止0/负数成为无限制。
- Locale由调用明确选择，便利入口采用确定默认；读公式缓存值或显式拒绝公式，不自动执行任意公式。缺缓存/未知函数的实际格式差异有独立样本，旧自动evaluate的变更有迁移说明。写入字符串始终为文本单元格，包括=1+2。
- 流为借用，Path为自有；程序/consumer异常传播，预期格式/IO/预算通过Result且外层安全定位。清理/关闭保留首因，失败可以有输出前缀，不承诺Path写的原子发布。
- 每操作自有临时目录；读取有界暂存，写通过POI现有扩展口让sheet/template均在预算内，结束显式确认清理。不修改全局TempFile策略、ZipSecureFile限额或JVM Locale，不构建新的通用存储/报表框架。

## XML与实际POI边界

XLSX预检用Commons Compress1.28.0的Zip64流遍历，固定512个local条目/名称512单元、4 MiB非worksheet元数据、每part2 MiB及样式256 KiB。实际关系指向的metadata再次有界预检，XML深64/属性64/格式码256、共享字符串32,767、公式源8,192；只读首sheet。额外DOCTYPE/external entities/DTD加载均显式禁用，无法执行必要feature的宿主parser失败关闭。

真实反例：12 MiB伪随机二元属性被压成不到2 MiB XLSX，默认16 MiB展开预算仍允许其进入Xerces；64 MiB进程在SAX回调前OOM。因此sheet输入增加**任意相邻start/end/characters事件之间最多新读取64 KiB**，包含parser预取；无进度0-read拒绝，skip也走同一计数，mark/reset不开放。它是输入进度预算，不解析XML语法；巨型属性/注释在回调前不能继续增长，正常大sheet的连续事件允许流式处理。cell长度另由Guard检查。Woodstox7.3.0仅作探索，未新增生产依赖或自写事件桥接。

SAX仍保留有限metadata，64 MiB资源样本不推广到任意调用者显式上调预算。BIFF的极大SST计数可能被POI容错忽略；资源探针断言实际行保持而无巨大分配，不声称严格验证全部BIFF语义。POI5.5.1的SXSSF protected sheet writer/injectData扩展被本实现固定使用；升级POI必须复验实际临时文件/首因契约，不能直接借用其best-effort close保证。

## Consequences

**Positive**：旧XLS能力继续保留，真正大的XLSX使用逐行路径，资源与公式政策从隐藏假设变成可测试约定。

**Negative**：旧无界便利方法与自动公式计算收紧；SAX仍须保留有界样式/共享字符串元数据，不能承诺任意工作簿恒定内存。

**Carry-forward**：本票先提供明确支持范围和实际资源证据；31/33负责最终业务/候选接合，不反向作为实现前置。本文部分替代0021中Excel内存/公式/清理/探针保证，历史理由保留。

## References

1. [POI Spreadsheet How-to](https://poi.apache.org/components/spreadsheet/how-to.html)：HSSF/XSSF event API与SXSSF；站点为开发版，行为以实际5.5.1为准。
2. [POI5.5.1源码制品](https://repo.maven.apache.org/maven2/org/apache/poi/poi-ooxml/5.5.1/poi-ooxml-5.5.1-sources.jar)：已实际获取并核读SXSSFWorkbook/SheetDataWriter/ReadOnlySharedStringsTable/XSSFSheetXMLHandler。
3. [正式票16](../../.scratch/server-facility-next/issues/16-bounded-excel.md)、PRDv0.2 FR06、共同Q01–Q10/J13/J16及0021。
