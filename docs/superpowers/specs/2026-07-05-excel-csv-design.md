# Excel/CSV 组件设计(§10 roadmap 收官组件)

日期:2026-07-05 | 分支:feat/excel-csv | 状态:已批准(4 决策用户在线敲定)

## 1. 背景与目标

§10 roadmap 最后一个组件。目标:表格文件(xlsx/xls/CSV)的读写静态门面,读→内存行集、写←内存行集,失败返 `Result` 从不抛异常;Excel 能力在 POI 缺失时**优雅降级为 err**(而非 `NoClassDefFoundError` 崩溃)。

## 2. 硬约束(决定选型)

本机构建全程 `mvn -o` 离线。本地 .m2 盘点(2026-07-05 实测):**POI 5.3.0 全栈齐备**(poi、poi-ooxml、poi-ooxml-lite + 传递依赖 xmlbeans 5.2.1 / commons-compress / commons-collections4 4.4 / curvesapi 1.08 / log4j-api / SparseBitSet 1.3 / commons-io / commons-math3 3.6.1);easyexcel、cn.idev.excel(fastexcel)、opencsv、commons-csv **全部缺失**。

## 3. 已敲定决策(2026-07-05,用户在线选定)

| # | 决策点 | 取值 |
|---|--------|------|
| D1 | 库选型 | **Excel = POI 5.3.0(poi + poi-ooxml 成对 optional);CSV = RFC 4180 纯 JDK 手写**(零依赖恒可用,无需门控) |
| D2 | 包结构 | **分两包**:`cn.code91.facility.excel`(`ExcelUtil`,POI optional + 运行时探测降级)与 `cn.code91.facility.csv`(`CsvUtil`,零依赖);两包均仅依赖 error/result,无环风险 |
| D3 | API 形状 | **裸 `List<List<String>>` 仅**(用户显式选定,否掉 Map 表头驱动与 POJO 绑定):读 → `Result<List<List<String>>, WrappedError>`,写 ← `List<List<String>>`;表头由调用方自行作为首行处理 |
| D4 | 大文件与格式边界 | **写 = SXSSF**(恒定内存,仅 xlsx);**读 = usermodel + WorkbookFactory**(自动兼容 xls/xlsx,文档诚实记内存界限);SAX 流式读留 roadmap;CSV 写默认 UTF-8+BOM(Excel 打开不乱码),读兼容剥 BOM |

命名沿 masking 轮用户既定的 Util 家族:`ExcelUtil` / `CsvUtil`。

## 4. 组件结构

### 4.1 `cn.code91.facility.excel.ExcelUtil`(静态门面,POI optional)

- `public final class` + 私有构造 `throw UnsupportedOperationException`(house 范式);可失败方法一律返 `Result<T, WrappedError>`,从不抛异常;null 入参 → err(非 NPE)。
- **运行时探测降级(本组件的范式新点,ADR 记录)**:静态门面无装配、无 bean,`@ConditionalOnClass` 不适用;改为**入口类探测**——缓存的 `Class.forName("org.apache.poi.ss.usermodel.Workbook")` 探针(`volatile Boolean` 惰性单次),缺失 → `err(EXCEL_LIB_MISSING)`。
- **POI 类型隔离**:所有 POI import 收进包私有实现类 `ExcelSupport`(同包),门面 `ExcelUtil` 自身零 POI 引用——保证门面类加载/校验永不触发 `NoClassDefFoundError`,探测为真后才委托 `ExcelSupport`。
- 缺库分支可测性:探测结果经包私有 setter 覆盖(测试强制 false 断言 err 且不触碰 POI),`@AfterEach` 复原(毒化纪律)。

API(读第一个 sheet;写单 sheet,名 `Sheet1`):

```java
public static Result<List<List<String>>, WrappedError> read(Path file)
public static Result<List<List<String>>, WrappedError> read(InputStream in)
public static Result<Void, WrappedError> write(Path file, List<List<String>> rows)
public static Result<Void, WrappedError> write(OutputStream out, List<List<String>> rows)
```

### 4.2 `cn.code91.facility.csv.CsvUtil`(静态门面,纯 JDK)

- 同 house 范式;RFC 4180 解析/生成手写实现(引号字段、内嵌逗号/引号/换行、CRLF),UTF-8。
- API 与 ExcelUtil 同形对称:

```java
public static Result<List<List<String>>, WrappedError> read(Path file)
public static Result<List<List<String>>, WrappedError> read(InputStream in)
public static Result<Void, WrappedError> write(Path file, List<List<String>> rows)
public static Result<Void, WrappedError> write(OutputStream out, List<List<String>> rows)
```

## 5. 行为语义(v1 固定)

### 5.1 读(Excel)

- `WorkbookFactory.create` 自动识别 xls/xlsx;仅读**第一个** sheet。
- 单元格**全字符串化**:经 POI `DataFormatter`(避免数字科学计数法/保留显示格式);公式单元格取**计算值**(`FormulaEvaluator`)再格式化;空单元格 → `""`。
- **行宽按行自身末列**(不跨行补齐,ragged rows 如实返回;文档记录)。空行 → 空 `List`。
- 畸形/非表格文件 → `err(EXCEL_READ_ERROR)`(含 cause)。

### 5.2 写(Excel)

- `SXSSFWorkbook`(恒定内存,默认窗口),仅产出 xlsx;`rows` 内 null 单元格写为 `""`,null 行 → err;写完 `dispose()` 清理临时文件。
- 目标 I/O 失败 → `err(EXCEL_WRITE_ERROR)`。

### 5.3 CSV 读写

- 读:兼容剥 UTF-8 BOM;引号字段内的逗号/换行/成对引号转义(`""`→`"`)按 RFC 4180;裸 CR/LF/CRLF 行分隔均容忍;ragged rows 如实返回;I/O/未闭合引号 → `err(CSV_READ_ERROR)`。
- 写:默认 **UTF-8 + BOM**(Excel 直接打开不乱码——ADR 记取舍);行尾 CRLF(RFC 4180);**最小引号策略**(字段含逗号/引号/换行/前后空白才加引号,内嵌引号翻倍);null 单元格 → `""`;I/O 失败 → `err(CSV_WRITE_ERROR)`。

### 5.4 null 契约

门面方法 null 入参(file/in/out/rows)→ `err`(对应 READ/WRITE 错误码),从不抛;`rows` 含 null 行 → err;行内 null 单元格 → `""`(文档化)。

## 6. 错误码与 i18n

`FacilityErrorType` 新段 **500700-500799(表格文件错误)**,5 键,i18n 4 bundle(base/en/zh_CN/zh_TW)各补:

| 码 | 名 | 语义 |
|----|----|------|
| 500700 | EXCEL_LIB_MISSING | POI 不在 classpath,Excel 能力不可用 |
| 500701 | EXCEL_READ_ERROR | Excel 读取失败(畸形文件/IO) |
| 500702 | EXCEL_WRITE_ERROR | Excel 写出失败 |
| 500703 | CSV_READ_ERROR | CSV 读取失败(IO/未闭合引号) |
| 500704 | CSV_WRITE_ERROR | CSV 写出失败 |

## 7. 依赖与工程

- pom:`poi` + `poi-ooxml` **成对显式 optional**(版本 5.3.0 自 pin,Boot BOM 不管 POI;对齐 cache 轮 caffeine+context-support 成对范式——门面实现同时字节码引用 poi 核心与 xssf,两者都是 used-declared);test scope 下 POI 可用(真实 xlsx 往返测试)。
- `maven-dependency-plugin` failOnWarning:ignore 列表按实际 `javap`/analyze 输出实测,勿凭旧输出堆(house 教训)。
- 自动装配保持 **11**(两包均零装配、零 properties;对标 crypto/masking 的零装配决策)。
- ArchUnit 4/4 保持绿(两包仅依赖 error/result,单向)。

## 8. 测试策略(TDD,由简到繁)

1. **CsvUtilTest**(先,零依赖):RFC 4180 正反例(引号/内嵌逗号/内嵌换行/成对引号/CRLF-LF-CR/BOM 剥离/ragged/空行/空文件)、写读往返、最小引号策略断言、BOM 写出断言(字节级)、null 契约、私有构造。
2. **ExcelUtilTest**:写读往返(Path 与流)、DataFormatter 字符串化(数字不科学计数)、公式取值、空单元格/空行、ragged、xls 读兼容(POI 现造 HSSF 样本)、畸形文件 err、null 契约、私有构造;**缺库降级**:探测覆盖为 false → 四 API 全返 EXCEL_LIB_MISSING 且不触碰 POI,`@AfterEach` 复原。
3. 覆盖 gate 0.88/0.75 保持;新类目标 100%(探测缓存的竞态分支等按构造不可达者循 dispatch 先例记录)。

## 9. 文档交付

- 两包 `package-info.java`(英文 h2 house 模板);
- **ADR-0021**:选型(离线 .m2 盘点驱动 + easyexcel/opencsv 否决理由)+ 静态门面 optional 运行时探测降级范式(vs @ConditionalOnClass 的适用边界)+ 裸 List API 取舍(用户选定,Map/POJO 留 roadmap)+ SXSSF/usermodel 边界 + CSV BOM 取舍 + 诚实局限;
- README(特性矩阵 + optional 矩阵)/ USAGE(用法 + 缺库降级说明 + 内存界限)/ DESIGN(计数)增补,INDEX 加行;计数以真实 verify 回填。

## 10. 诚实局限(v1,文档记录)

- Excel 读为内存模型:整簿载入,行数上限受堆约束(万行级常规堆可用;十万行级建议等 SAX 流式读 roadmap);
- 只读第一个 sheet、写单 sheet;不支持样式/合并单元格/多 sheet(roadmap);
- 全字符串化语义:消费方需自行做类型转换;日期显示格式随源文件单元格格式;
- CSV 分隔符固定逗号(分号/Tab 变体留 roadmap);
- 探测降级的「缺库」分支以探测开关模拟(真实 NoClassDefFoundError 场景由「POI 类型隔离进 ExcelSupport + 先探测后委托」结构性保证)。

## 11. 验收

- 全量 `mvn -o clean verify` 绿(基线 1099 + 新增);coverage met;analyze 零问题(ignore 实测);ArchTest 4/4;
- 终审锚点:doc-truth(USAGE 示例逐一核签名)、缺库降级不崩、CSV RFC 4180 边界(内嵌换行往返)、SXSSF 临时文件清理(dispose)。
