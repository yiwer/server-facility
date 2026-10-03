# ADR-0021: Excel/CSV——POI optional 运行时探测降级与纯 JDK CSV

- **状态**:Accepted(2026-07-05)
- **CSV部分替代（2026-10-04）**：[ADR-0038](0038-bounded-csv-dialects.md) 替代纯JDK手写解析器、无界便利读取和混合导出用途的旧假设，明确方言/预算/异常归属；保留裸列表、无表头ORM与Excel optional理由。历史正文保留。
- **Excel部分替代（2026-10-04）**：[ADR-0039](0039-bounded-excel-formats.md) 替代无界整簿/稀疏补齐、隐式公式计算、best-effort临时清理及仅靠类探针的保证；保留裸列表、无表头ORM、POI optional和成对引擎理由。
- **源起**:Excel/CSV 组件设计(docs/superpowers/specs/2026-07-05-excel-csv-design.md)

## 背景

§10 roadmap 最后一个收官组件:表格文件(xlsx/xls/CSV)的读写静态门面。本机构建全程
`mvn -o` 离线,库选型第一约束是本地 `.m2` 盘点结果(2026-07-05 实测):**POI 5.3.0
全栈齐备**(`poi`、`poi-ooxml`、`poi-ooxml-lite` 及 xmlbeans/commons-compress/
commons-collections4/curvesapi/log4j-api/SparseBitSet/commons-io/commons-math3 等传递
依赖均在);而 `easyexcel`、`cn.idev.excel`(fastexcel)、`opencsv`、`commons-csv` **全部
缺失**。离线环境下,"缺失" 直接否决候选,不进入功能/API 层面的权衡。

## 决策

### 1. Excel = POI 5.3.0 成对 optional;CSV = RFC 4180 纯 JDK 手写(选型)

Excel 走 `poi` + `poi-ooxml` 成对 Maven optional 引入(版本 5.3.0 自 pin,Boot BOM 不管
POI 版本;对齐 ADR-0015 缓存簇 `caffeine` + `spring-context-support` 的成对 optional
范式——门面实现同时字节码引用 `poi` 核心 `ss.usermodel` 与 `poi-ooxml` 的
`xssf.streaming`,两者都是 used-declared,不能只声明一个)。

CSV 不引入任何第三方库,手写 RFC 4180 状态机(引号字段、内嵌逗号/换行、成对引号转义、
CR/LF/CRLF 容忍)——几十行状态机换来零依赖恒可用,不必为此走 optional 门控。

**否决候选与理由(如实记录)**:

- **easyexcel / cn.idev.excel(fastexcel)**:本地 `.m2` 缺失(离线构建下直接出局,未进入
  功能评估);即便可离线获取,easyexcel 体量大且对 POI 做了较深的二次封装/魔改
  (自有内存模型与注解体系),与本组件"薄门面 + 恒定内存流式" 的诉求不完全对齐,POI 已
  原生具备 SXSSF 恒定内存写与 usermodel 读,无需额外抽象层。
- **opencsv / commons-csv**:本地 `.m2` 缺失。即便可获取,为几十行 RFC 4180 状态机逻辑
  引入一个 optional 门控的第三方库,门控本身(探测/降级文档/测试覆盖)带来的复杂度已经
  超过手写实现——不偿失。CSV 走纯 JDK 反而更简单、维护面更小、且恒可用(无需消费方关心
  是否引入)。

### 2. 静态门面的 optional 门控范式:运行时探测降级(vs `@ConditionalOnClass`)

`@ConditionalOnClass` 是 Spring **装配层**的条件注解,作用于 `@Bean`/`@Configuration` ——
本组件与 `crypto`/`masking`/`hash` 同属"无状态静态门面,无 bean、无 properties"一类
(ADR-0019、ADR-0020),没有装配点可挂载 `@ConditionalOnClass`。因此 optional 依赖缺失时
的降级不能走装配层机制,改为**入口运行时探测**:

- **缓存的双类 `Class.forName` 探针**:`volatile Boolean poiPresent` 惰性单次探测,分别对
  `org.apache.poi.ss.usermodel.Workbook`(poi 核心)与
  `org.apache.poi.xssf.streaming.SXSSFWorkbook`(poi-ooxml)各探一次
  (`Class.forName(name, initialize=false, classLoader)`,不触发类初始化),**两者都在场
  才判定可用**,任一缺失即降级为 `err(EXCEL_LIB_MISSING)`。
- **勘误(设计中途发现的缺口)**:v1 曾只探测单个 poi 核心类。但半拉子 classpath(消费方
  只引 `poi` 漏引 `poi-ooxml`,违反成对约定)下,单探针会误判"可用",委托进
  `ExcelSupport` 后在真正使用 `SXSSFWorkbook` 时触发 `NoClassDefFoundError`——`Error` 不
  被 `catch (Exception e)` 捕获,会逃逸门面"从不抛异常"的契约。改为双类探测堵住该缺口,
  对齐 ADR-0015 缓存簇 Caffeine + spring-context-support 的双类探测范式(同样的"成对
  optional,缺一不可"结构)。
- **POI 类型隔离**:所有 POI import 收进包私有实现类 `ExcelSupport`(同包);门面
  `ExcelUtil` 自身零 POI 类型引用——保证门面类的加载与校验(JVM 类加载/链接阶段)永不
  触发 `NoClassDefFoundError`,只有探测通过之后才会真正委托进 `ExcelSupport`,由该类
  首次被引用时才触发 POI 类的解析。`ArchitectureTest` 的
  `excel_facade_does_not_depend_on_poi` 规则永久锁定这一结构(`noClasses().that()
  .haveFullyQualifiedName("cn.code91.facility.excel.ExcelUtil").should()
  .dependOnClassesThat().resideInAnyPackage("org.apache.poi..")`),防止未来重构不慎把
  POI 引用带回门面类。
- **缺库分支的可测性边界(如实记录)**:测试经包私有 `overridePoiPresent` 强制探测结果为
  `false`,断言四个 API 全返 `EXCEL_LIB_MISSING` 且不触碰真实 POI 调用路径,`@AfterEach`
  复原探测缓存(毒化纪律)。这只验证了"探测判定为假时门面的分支行为",**不是**真实
  classpath 缺失 POI 场景的端到端验证(test scope 下 POI 恒在场,无法在同一测试进程内
  卸载已加载的类)。真实缺库场景的正确性由结构性保证:门面零 POI 引用 + 探测通过才委托,
  两者叠加使得"探测为真才可能触发 POI 类加载"在结构上成立,而非靠这条测试覆盖。

### 3. API 形状:裸 `List<List<String>>`(用户显式选定)

读 → `Result<List<List<String>>, WrappedError>`,写 ← `List<List<String>>`,表头由调用方
自行当作首行处理。用户在设计讨论中显式否掉了两个更"高级"的候选:

- **`Map<String, String>` 表头驱动**:需要门面对首行做表头语义假设(是否有表头、表头是否
  唯一、列数不一致时如何对齐),这些假设在通用门面层面没有唯一正确答案,留给调用方按自己
  的表结构决定更诚实。
- **POJO 绑定**(类似 `@ExcelProperty` 注解映射):引入反射/注解扫描的复杂度,且与
  "薄门面"定位冲突——门面应该做少量确定的事,而非成为一个小型 ORM。

两者均列入 roadmap,留待有真实需求时再加,不预先设计。

字符串化是全字符串化语义:Excel 读经 POI `DataFormatter` 取"忠实 Excel 显示语义"的字符串
——数字按单元格的显示格式渲染,**`General` 格式下的大整数会按 Excel 自身的显示行为呈现为
科学计数法**(例如足够长的数字串显示为 `1.23457E+15`)。这不是本组件引入的 bug,而是
`DataFormatter` 忠实复刻 Excel 桌面版对 `General` 格式单元格的显示规则;需要保留精确大数值
的调用方应在源文件里把目标单元格设为文本格式,而不是依赖门面做额外的数值探测/特殊处理
(如实记录,不掩饰)。Ragged rows(行宽不一)按行自身实际列数如实返回,不做补齐。

### 4. 大文件与格式边界:写 SXSSF / 读 usermodel

写:`SXSSFWorkbook` 恒定内存(默认滚动窗口),仅产出 xlsx,单 sheet(`Sheet1`)。写完调用
`close()` 清理临时文件——POI 5.3.0 的 `SXSSFWorkbook.close()` 字节码内部对每个 sheet 关闭
`SheetDataWriter` 后即调用 `dispose()`,再关闭底层 `XSSFWorkbook`,即 **close 已经内含
dispose 语义**;显式再调用 `dispose()` 是冗余的(注:5.3.0 字节码中 `dispose()` 并无
ACC_DEPRECATED 标志,冗余性是唯一依据),故门面统一只调用 `close()`。

读:`WorkbookFactory.create` 自动识别 xls/xlsx,走 usermodel 整簿载入内存——行数上限受堆
约束(万行级常规堆可用,十万行级需等待 SAX 流式读,留 roadmap)。仅读第一个 sheet;写仅
单 sheet;不支持样式/合并单元格/多 sheet(均留 roadmap,如实记录为局限而非缺陷)。

### 5. CSV 细节:UTF-8+BOM 写出取舍

写默认 **UTF-8 + BOM**——面向"业务导出场景"选择:Excel 双击打开 CSV 时依赖 BOM 判断
UTF-8 编码,缺 BOM 会被误判本地编码导致中文乱码;这与"纯 Unix 工具链"偏好无 BOM(部分
`*nix` 工具把 BOM 当作数据的一部分处理)存在天然冲突。本组件的目标消费场景是"业务系统
生成报表给业务用户用 Excel 打开",故选择前置 BOM,并如实记录该取舍并非普适最优——纯粹
面向 Unix 管道消费的场景需要自行去 BOM。

其余细节:最小引号策略(字段含逗号/引号/换行/首尾空格才加引号,内嵌引号翻倍转义);行尾
CRLF(RFC 4180 建议);裸 CR/LF/CRLF 三种行分隔在读侧均容忍;未闭合引号到 EOF 视为格式
错误,fail-fast 返回 `err`(不做"尽力恢复"式的静默容错)。空行的写读不对称如实记录:写出
一个空 `List`(无字段的空行)产出一个空行(仅 CRLF,无字段内容);该空行回读时因为字段
分隔逻辑的必然结果,会解析为**一个空字符串字段**的行(`List.of("")`),而不是原样的空
`List`。这是 CSV 文本格式本身"行 = 至少一个字段"的表达能力边界,不是实现缺陷。

## 备选(否决)

- **`@ConditionalOnClass` 挂在虚拟/占位 bean 上,强行套用装配层机制**(为了套用现成模式
  而制造一个本不需要的 bean,增加消费方心智负担且与"无状态静态门面"的既定家族范式
  (crypto/masking/hash)不一致,否决);
- **仅探测单个 POI 核心类**(半拉子 classpath 下会让 `NoClassDefFoundError` 逃逸
  never-throw 契约,已由勘误堵住,否决保留单探针方案);
- **`Map<String, String>` 表头驱动 API**(表头语义假设无通用正确答案,用户显式否决,
  留 roadmap);
- **POJO 绑定 API**(反射/注解扫描复杂度与薄门面定位冲突,用户显式否决,留 roadmap);
- **CSV 引入 opencsv/commons-csv**(本地缺失且为几十行逻辑引入 optional 门控不偿失,
  否决,详见决策 1);
- **CSV 写默认不带 BOM**(更符合 Unix 工具链偏好,但与本组件面向的业务导出场景——Excel
  直接打开——冲突,否决,详见决策 5);
- **SXSSF 写完显式调用 `dispose()`**(POI 5.x `close()` 已内含 dispose 语义,显式调用
  冗余,否决)。

## 后果

- Excel 能力在 POI 缺失时优雅降级为 `err(EXCEL_LIB_MISSING)`,不崩溃、不抛
  `NoClassDefFoundError`;引入 `poi` + `poi-ooxml` 成对依赖即自动启用,无需任何额外配置
  或装配开关。
- CSV 能力零依赖恒可用,与 `hash`/`crypto`/`masking` 同属"引入即用,无 bean 无
  properties"家族;`ArchitectureTest` 的 `excel_facade_does_not_depend_on_poi` 规则
  (第 5 条)永久锁定"门面零 POI"结构,防止未来重构悄悄破坏该边界。
- 消费方需要在文档层面理解三处诚实局限:(1)Excel 读为整簿内存模型,超大文件需等待未来
  SAX 流式读;(2)全字符串化语义下 `General` 格式大整数会按 Excel 显示规则呈现科学计数法,
  精确数值应使用源文件文本格式单元格;(3)CSV 空行写读不对称(`[]` → 空行 → 回读
  `[""]`)。这些不是缺陷,USAGE 需要如实披露而非只字未提。
- **Carry-forward**:README/USAGE/DESIGN 三件套 csv/excel 特性矩阵、optional 依赖矩阵、
  ADR 索引更新于本计划 Task 5 统一处理。

## Roadmap

- SAX 流式读(突破整簿内存模型的行数上限);
- `Map<String, String>` 表头驱动 / POJO 绑定 API 形态(当前仅裸 `List<List<String>>`);
- CSV 分隔符可配置(当前固定逗号,分号/Tab 变体留待真实需求);
- 多 sheet 读写(当前仅第一个 sheet 读 / 单 sheet 写)。
