# 核心能力研究：把工具箱收敛为值得依赖的 Module

研究日期：2026-10-03。源码基线：`0ee9d547022371ad31f885605e999de17ec22777`。范围：`result`、`error`、`structure`、`common`、`copy`、`date`、`number`、`pattern`、`path`、`io`、`mime`、`json`、`http`、`csv`、`excel`、`crypto`、`hash`。本文是改造提案，不改变现行 ADR；与既有决策不同的建议必须在实施时用新 ADR 替代。读取了上述包的实现、代表测试以及 README、DESIGN、ADR 0001/0007/0009/0010/0018/0019/0021；没有运行构建或测试。下文区分源码确定的行为、官方事实与设计判断，不把已有测试名称当作测试已通过的证据。

## 1. 架构判断

这个项目有真正值得保留的资产：显式错误通道、错误本地化与底层错误数据分离、流操作中的资源释放、AES-GCM 的固定模式与内管 IV、CSV/Excel 的简单数据模型，以及用 ADR 记录行为取舍的纪律。问题不是“工具类太多”这个外观，而是很多公共 Interface 只包住一行 JDK 调用，却同时增加新的 null、时区、缓存、资源所有权和失败规则。Agent 必须先学这些局部规则，才能知道什么时候可以安全调用。

建议把目标改为：**共享那些不该由每个业务调用者重新决定的不变量；其余代码直接使用成熟框架。** 一个 Module 是否 deep，取决于它消除了多少调用方知识，不取决于实现行数或公共类数量。`CopyUtil.autoCopy` 隐藏反射，却要求调用方理解八种深浅策略；它的 Implementation 很复杂，Interface 仍然很宽。相反，把“文件先受限落暂存、识别、校验、提交；任何失败不得留下可见半成品”放入一次操作，才有明确 Depth。

建议按以下实际场景决定去留，而非按包是否已有 90% 覆盖率决定：

| 场景 | 调用者真正需要知道的事实 | 新 Module 应承担的知识 |
|---|---|---|
| 调用库存/支付等第三方系统 | 请求/响应模型、业务可恢复失败 | 超时、身份凭证、错误分类、观测、幂等重试资格；用 Spring 类型化客户端建立 Adapter |
| 接收业务日期/金额 | 允许的格式、币种、业务时区 | 严格校验、禁止静默修正；展示格式留在展示层 |
| 用户上传文件 | 用途、大小预算、允许类型 | 文件名与存储名分离、流所有权、受限暂存、识别局限、原子提交与清理 |
| 导入十万行 CSV/XLSX | 行模型、行级校验/提交政策 | 流式处理、资源预算、错误位置、取消、公式处理、临时文件生命周期 |
| 加密长期持久化字段 | 加密目的、租户/记录上下文 | nonce、AAD、密钥标识、密文格式版本、轮换与旧数据读取 |

后三类是有机会继续加深的 Module。`Objects.equals`、`LocalDate.plusDays`、`Matcher.results` 一类调用不值得建立新 Interface。

## 2. `result`、`error`：保留共同语言，停止用统一类型抹平语义

### 2.1 Result

**现状与价值。** [`Result.java:56`](../../src/main/java/cn/code91/facility/result/Result.java#L56) 使用 sealed interface 与两个 record，`map/flatMap/mapErr/fold` 形成统一可组合的错误通道；`of/ofRunnable` 在捕获 `InterruptedException` 后恢复中断位（109–136 行），不捕获 `Error`。这些是实际有 Leverage 的行为，值得保留。`ResultTest` 有短路、惰性回退、中断恢复和 collector 验证；不需要为了“行业共识”强行引入另一个函数式依赖。

**要重新决定的是“无值”。** `ok(null)`、`ok()`、`empty()` 最终都是 `Ok(null)`（68–88 行）；它们没有形成三种可辨识状态。`empty().map(String::length)` 仍会调用 mapper 并产生 NPE（592–594 行），`orElse` 不因 null 回退（265–266 行），`swap` 则必须新增特殊拒绝（394–403 行）。这是 ADR-0007 的既定行为，不是遗漏实现。`ResultSwapTest:23`、`ResultTest:697` 明确锁住此规则。JDK `Optional` 表达值缺席，其 `map` 对 null 结果为空；Result 的成功/失败是另一维度，二者不应该凭形状类比而混用。[JDK 25 Optional](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html)

**目标。** 保留 Result；新主版本建议 `Ok<T>` 要求非 null，无返回值操作用 `Result<Unit,E>`，允许查无记录的成功用 `Result<Optional<T>,E>`。如果不接受这个破坏性调整，应直接写明“成功分支允许 null，所有 map/fold 回调必须接受 null”，并停止把 `empty` 比喻成 Optional。不要加一个第三变体“Empty”来同时承担两种含义。压缩文档默认路径为 `ok/err/map/flatMap/mapErr/fold`；其他组合器只在出现真实跨业务调用后保留，不再继续扩展函数式全集。

**验收。** 以一段完整业务流程验证成功值、无返回值、查无记录、预期失败与程序错误五种行为；任意成功值 map/flatMap 的法则测试应成立；中断位不能丢失；错误分支不能偷偷变成 null；迁移器能定位所有 `ok(null)` 和 `empty()`。不以私有构造器测试或机械覆盖 getter 作为这次改造的主要质量证据。

### 2.2 Error

**现状与价值。** [`ErrorTypeInterface.java:92`](../../src/main/java/cn/code91/facility/error/ErrorTypeInterface.java#L92) 的 code/messageKey/defaultMessage 与 [`WrappedError.java:72`](../../src/main/java/cn/code91/facility/error/WrappedError.java#L72) 的异常及参数，是技术错误落到展示层的稳定接点。ADR-0010 把本地化移出 error，消除了 Spring 生命周期对错误值的影响，应该保留。数组入参和 getter 做防御性复制（76、154 行），`WrappedErrorTest:174` 验证这一点。

**问题不是少几个字段，而是错误职责过多。** `WrappedError` 同时承担错误标识、格式化参数、底层异常、诊断字符串；HTTP 状态塞入 `args[0]`（`HttpClients.java:61`），JSON 错误把原始载荷片段塞入参数（`Jsons.java:253`）。调用者必须知道参数序号及内容是否可以展示。`getFullMessage` 拼接异常消息，`toString` 输出参数（275–315 行）；这不是适合作为外部响应的默认表示。防御性复制只是数组层面的不可变性，不会冻结参数对象或异常对象。

**目标。** 保留 error 的无 Spring 依赖；将“可公开错误”和“内部诊断”分开。跨业务协作共享稳定 code 与安全参数，技术异常由发生它的 Adapter 保留并关联诊断事件。HTTP 错误用具名 `status`、`retryAfter` 等字段，不要求业务读取 `Object[]`。业务错误集合由业务 Module 拥有；不要继续向 `FacilityErrorType` 中央枚举加入所有业务含义。无错误的查询不应该制造一个技术错误，配置缺失也不应伪装成外部服务超时。

**验收。** 任意技术异常的 URL、认证信息、JSON 原文不会通过默认错误字符串外泄；错误本地化可离开 Spring 单测；HTTP 状态分类无需数组索引或异常强制转换；现有错误码兼容表可审阅。这里是改造取向，不宣称当前 Web 层已经发生泄漏。

## 3. `structure`、`common`、`copy`：删去对语言的二次翻译

### 3.1 Structure

[`Tuple.java:48`](../../src/main/java/cn/code91/facility/structure/Tuple.java#L48)、[`Triple.java:51`](../../src/main/java/cn/code91/facility/structure/Triple.java#L51) 已是 record，值语义清楚；Tuple 的 null 兼容与严格 `Map.entry` 转换分开（122–133 行），已有对应测试。它们适合局部临时结果，却不适合订单金额与币种、日期区间、租户与主体等需要名称的不变量。源码中 Tuple 被 `Collects.extractCompareTuple` 和 `DateUtil.minMaxTuple` 使用；本次 import 搜索没有看到 Triple 的包外主源码消费。这个证据仅限本仓库，不代表外部用户没有调用。

**目标：** 不再鼓励通用 tuple 作为业务公共 Interface；优先领域 record，例如 `DateRange(startInclusive,endExclusive)`。Tuple 如有兼容需要留在很小的 legacy 包；Triple、旋转、反转、三元投影不再是脚手架默认入口。验收看调用代码能否从字段名理解值，而不是对新 record 的每个 accessor 再写测试。

### 3.2 Common

[`NullSafe.java:25`](../../src/main/java/cn/code91/facility/common/NullSafe.java#L25) 的 isNull/nonNull/equals 只是语法或 `Objects` 的转发，应删除默认推荐。`getOrDefault` 允许默认值也为 null，与 `Objects.requireNonNullElse` 不等价，迁移不能盲替换。`Collects` 的“跳过 null、重复键保留第一个、多重集差”确有额外行为，但需要显式命名；`listDiff` 使用 HashMap 回组结果，没有顺序保证（139–151 行），测试特意使用 `containsExactlyInAnyOrder`，不是普通保序 list 差。

`Collects.toMap`、`mapNonNull` 在空输入时先返回，没有对函数入参 fail-fast（29–35、124–129 行），与 DESIGN C1 的函数参数原则不同；这是现有约定没有完全落地。`calculateCapacity`（159–161 行）不必公开，JDK 自 19 提供 `HashMap.newHashMap(expectedMappings)`。[JDK 25 HashMap](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/HashMap.html)

**目标：** 直接使用 JDK 集合构造、stream、Objects；只保留经过真实消费证明的 `indexFirstBy`、`multisetDifference` 之类语义操作，并在 Interface 说明顺序、null、冲突政策。不得把所有集合/字符串缺席都强制归为空值；这会丢掉业务信息。验收是迁移后调用点更少规则、更明确，而不是建立另一套名字相同的 Collections。

### 3.3 Copy

**实际实现比 README 所谓 Bean 拷贝复杂。** [`AutoCopyEngine.java:45`](../../src/main/java/cn/code91/facility/copy/AutoCopyEngine.java#L45) 用 ClassValue 缓存元数据，避免静态 Class key map 的类加载器滞留，这是正确选择；静态/transient/合成字段跳过，CopyTrait 字段与部分泛型集合递归，其他字段共享引用（91–177 行）。这不是 DTO 映射，也不是通用对象图深拷贝。`AutoCopyEngineTest:209/228/241` 明确断言普通 List、只有 key 实现 CopyTrait 的 Map、嵌套集合共享原引用；应尊重其历史契约，不能把这些都写成“深拷贝 bug”。

**但这个 Interface 的隐性条件太宽。** 类须有无参构造器（57 行），字段须反射可写（78 行），Collection 的声明泛型决定行为；复制后的 Set 固定为 LinkedHashSet，其他 Collection 固定为 ArrayList（211–221 行），Map 固定为 LinkedHashMap（228–245 行）。例如声明为 `LinkedList<Leaf>` 或 `TreeSet<Leaf>` 的字段可以被策略识别为应深拷贝，但最终赋回不兼容容器时失败（30 行）。构造器默认值也影响结果：源字段为 null 就跳过（26–29 行），源 null 不一定复制成目标 null。循环 CopyTrait 没有共享 identity map，图复制的循环/别名关系没有通用保证。这里均是源码推导，未执行运行时复现。

**目标：** 从脚手架默认能力删除反射 autoCopy。DTO/Entity 映射少量字段直接构造 record，大量结构映射选择 MapStruct，并配置 `unmappedTargetPolicy = ReportingPolicy.ERROR` 让未映射目标字段在编译时报错；真正的业务快照由业务类型实现自己的复制语义。MapStruct 是编译期生成普通 Java 调用的映射器，不是自动处理任意循环对象图的深拷贝器，也不必引进 runtime 核心依赖。[MapStruct 官方指南](https://mapstruct.org/documentation/stable/reference/html/)

**迁移验收：** 订单 DTO 新加字段若未映射，构建失败；不可变字段无反射赋值；公开文档区分“映射”“复制容器”“对象图快照”；对仍保留的显式快照测试循环、别名、null 默认值、排序比较器，而不是复制当前策略分支来凑覆盖率。

## 4. `date`、`number`、`pattern`：输入政策应显式，日常运算应回到 JDK

### 4.1 Date

[`DateUtil.java:61`](../../src/main/java/cn/code91/facility/date/DateUtil.java#L61) 将任意 pattern 缓存在无界 ConcurrentHashMap；`ofPattern` 使用默认 resolver style，解析入口没有改成 STRICT（224–253 行）。按 JDK SMART 规则，日期的月内天数超过当月最后一天、但仍在 1–31 范围时会调整为当月最后一天；因此这个解析器不适合直接作账期、生日等严格输入校验。`DateUtilTest:31` 测正常日期，非法测试主要是 `not-a-date`，没有覆盖非闰年 2 月 29 日这类有效形状、无效日期。[JDK 25 ResolverStyle](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/ResolverStyle.html)

日期转换隐式使用 `ZoneId.systemDefault()`（111、171、510、526 行），同一应用不同容器时区可以改变结果。`safePlusDays/MAX_DATE` 是业务哨兵惯例，而不是数学饱和：到 MAX_DATE 一律不动，即便 days 是负值；在 MAX_DATE 前一天加两天又会超过自定义上限（441–445 行）。`safeMinusDays` 对 MIN_DATE 对称。不能继续把它们当无领域含义的通用安全算法。

**目标：** 日常计算直接用 `java.time`；应用时间源注入 `Clock`，跨本地时间与 Instant 的位置显式传 ZoneId。保留确有用户输入场景的严格日期 codec，默认 ISO/`uuuu-MM-dd` + STRICT；格式对象在启动时构建，不为每个任意请求 pattern 建永久缓存。日期区间用具名 record 与半开区间约定，MAX/MIN 哨兵迁到实际业务。老 `Date/Calendar` 只留迁移 Adapter。[JDK 25 DateTimeFormatter](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/DateTimeFormatter.html)

**验收：** 非闰年 2 月 29 日拒绝；闰日接受；固定 Clock 可重现“今天”；UTC/上海/有 DST 时区的转换场景明确；无用户输入可造成无界 formatter cache；旧宽松解析如保留必须叫 legacy/lenient。

### 4.2 Number

`Numbers.equals(BigDecimal,BigDecimal)` 用 compareTo 忽略 scale（80–83 行），是清楚的数值语义，可留少量辅助；各种 `OrZero` 则不能用来处理支付、数量等必填输入。`parseDouble` 接受 JDK 的 NaN/Infinity，未声明有限数规则（66–72 行）。[`NumberFormat.java:94`](../../src/main/java/cn/code91/facility/number/NumberFormat.java#L94) 用 double 乘单位再强转 long，意味着 `NaN` 可得到 0，超大值可饱和为 Long.MAX_VALUE，输入精度和溢出不透明。这是 Java 浮点到整数的定义，不是异常捕获能纠正的行为。[JLS 25 5.1.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.3)

`formatMoney` 只有分组和两位小数，没有币种（63–65 行）；`formatSmart` 截到最多八位（52–57 行）；DecimalFormat/String.format 使用宿主 locale（21–24、88 行）。`formatSize` 用 1024 而标 KB/MB，`parseSize` 仅到 TB，格式化和解析不是完整可逆协议。`NumberUnits` 面向印刷 DPI，不应成为 web-server 默认心智负担，其计算还经 double 和 Math.round（19–31 行）。现有 `NumberFormatTest:36` 正是锁定八位上限，不能把改变四舍五入行为称为无损重构。

**目标：** 将输入 parsing、业务金额模型、展示 formatting 分开；预算大小配置优先用框架 DataSize 或自己的严格整数单位解析；范围/有限性/溢出失败明确。金额由业务 `Money(amount,currency)` 承担，不造通用财务框架。`NumberUnits` 移到有打印业务的模块或移除。验收包括 NaN、Infinity、溢出、负容量、多 locale、精确小数、单位往返，不再用示例中的 1KB/2MB 代替边界证明。

### 4.3 Pattern

[`Patterns.java:81`](../../src/main/java/cn/code91/facility/pattern/Patterns.java#L81) 无界缓存任意 regex + flags，并公开 clearCache/cacheSize；大量入口只是 Matcher 的重新命名。`findFirst/findAll/findDistinct` 只检查 groupIndex 上界，没有检查负数（176–181、235–240、293–298 行）。更重要的是语义误导：IPv6 常量只接受八组完整地址（43 行），DATE 形状能接受 2 月 31 日（58 行），isIdCard 仅做形状判定（`CommonPatterns.java:19`），不能说明有效身份证。

`PASSWORD_STRONG`（62 行）把固定字符构成和 8–20 字符当作强度政策。这与 NIST 现行指导所强调的长度、允许较长密码、阻止已泄漏常见密码且不强制字符构成的方向不一致；NIST 文档有自己的适用范围，不是本仓库的法律义务，但足以说明“强密码正则”不应作为普适脚手架默认值。[NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html)

**目标：** 删除全局正则 registry 与多数透传方法；固定模式用静态 final Pattern；业务验证使用 java.time、URI/IP 解析器或 Bean Validation 的相应约束，并说明语法有效不等于资源可信。若确需用户提供 regex，单独做输入大小/执行预算设计，不能用一个 cache 就称为安全。HTML 不通过正则解析。验收看压缩 IPv6、日期语义、负组号和不可信模式预算；通用 pattern 常量不再冒充安全政策。

## 5. `path`、`io`、`mime`、`hash`：从四个工具包走向一个文件生命周期

### 5.1 Path

[`Filenames.java:31`](../../src/main/java/cn/code91/facility/path/Filenames.java#L31) 对原名执行 cleanPath、拒绝残留 `..` 段、取 basename、替换控制及危险字符；`FilenamesTest:32` 特别保护 `report..final.pdf`，避免把所有连续点误当穿越，这个取舍正确。它是“显示名称规范化”，不是受控目标目录的写入授权。设备名、末尾点/空格、长度、文件名碰撞等跨平台条件不在当前规则里；黑名单后缀也不可能代表安全文件集合。

**目标：** 将存储键与用户名称分离，服务器生成存储键；按用途使用扩展名 allowlist，把真正的目录/符号链接/写入决策放进文件摄入 Module。OWASP 将文件名、类型、大小、存储位置等列为组合防护，并明确 Content-Type 和签名检测不可单独依赖。[OWASP File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)

**验收：** 上传 `CON`、尾空格、极长 Unicode 名称只影响展示 metadata 或被拒绝，不决定最终 Path；相同名称不会覆盖；拒绝路径逃逸不依赖字符串包含检测。不要增加更长黑名单作为完成标准。

### 5.2 IO

`PathIo` 复用 JDK walkFileTree，默认不跟随符号链接，是正确基础。它的 `directorySize` 却把访问失败吞掉，仍返回一个没有“部分结果”标识的成功总数（[`PathIo.java:84`](../../src/main/java/cn/code91/facility/io/PathIo.java#L84)）；`PathIoSizeVisitorTest:28` 明确锁定继续遍历。若这个值用于配额，这个 Interface 会给调用者错误信心。`deleteDirectory` 的 postVisitDirectory 未先处理 `exc`（41–43 行），错误原因可能被随后 delete 的异常遮住。

`Zipping.zipFiles` 对同名 entry、源文件消失/读取失败记录日志继续，最后返回 Ok（[`Zipping.java:43`](../../src/main/java/cn/code91/facility/io/Zipping.java#L43) 至 55 行），因此“成功”不代表全部文件打包。`zipDirectory` 在遍历前创建 output，再遍历 source，未排除 output；将输出放在源目录内会把正在生成的 ZIP 当输入读取（74–82 行）。单纯 JDK walkFileTree 不跟随目录链接，也不等于文件内容复制不跟随符号链接：`Files.copy(file,OutputStream)` 会打开文件内容，所以需要明确链接政策。现有 ZippingTest 只测外部 output、递归名称及跳过不存在文件，没有表达这些契约。[JDK 25 Files](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html)

**目标：** 少量标准目录操作直接 JDK；若项目已需要大量路径操作，可使用 Commons IO `PathUtils`，不要只为两个方法添加依赖。保留打包 Module 的理由应是它能提供完整/部分结果、受控名称、链接政策、输出排除、暂存与提交，而不是替代 try/catch。[Commons IO PathUtils](https://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/file/PathUtils.html)

**验收：** 输出位于源内必须明确拒绝或可靠排除；重复 basename 有确定政策；读取失败返回失败/带清单的部分结果，不用普通 Ok 冒充完整；失败不留下可被下载的成品；链接策略在目标平台测试；配额统计的不可访问内容不能悄悄记 0。

### 5.3 MIME

[`MimeTyping.java:34`](../../src/main/java/cn/code91/facility/mime/MimeTyping.java#L34) 直接静态初始化 Tika，缺库不是 Excel 那样返回 `LIB_MISSING`，调用会触发类加载问题；这是 ADR-0001 已接受并披露的局限。`detect(InputStream,String)` 吞 IOException 回退 octet-stream（99–104 行），但其他重载走 Result，调用者需要记住重载语义。空 allowlist 直接允许（143–147 行）；安全决策不应从通用探测方法中隐式产生。

Tika 官方将检测定义为 best guess；tika-core 主要提供 magic/name 检测，容器内格式区分依赖额外 detector 与相应流类型。不要把“包含 xlsx MIME 常量”误认为 core 始终能无提示准确识别 xlsx，也不要将 `isImage=true` 等同内容安全。[Tika Content Detection](https://tika.apache.org/3.2.3/detection.html)

**升级关键点。** Tika 4 的 `Tika#detect(InputStream,...)` 虽仍可编译，却不再保证调用方原始流回到原位置，也不替调用方关闭流；官方建议持有并 rewind 自己的 TikaInputStream，或重新打开来源。因此先升维护中的 3.x 再做 4.x 契约迁移是可审阅路线，精确版本见主报告的依赖表。[Tika 4 迁移指南](https://tika.apache.org/docs/4.1.x/migration-to-4x/migrating-to-4x.html)

**目标：** MIME 作为文件摄入内部 Implementation；对外返回检测结果/未知原因，政策另行应用。优先在受限暂存文件上检测，避免在同一个不可重放网络流上“检测后再复制”。需要该能力的 artifact 直接声明 Tika 依赖，缺失配置在启动期可见，不让业务首次请求才发现。

**验收：** MIME 检测后正文长度/摘要保持一致；可 mark/不可 mark 流均测；流关闭责任明确；Tika 缺失、识别未知、识别失败分开；xlsx/docx/zip 混淆与 SVG 等主动内容有用途级策略。

### 5.4 Hash

[`Hashing.java:35`](../../src/main/java/cn/code91/facility/hash/Hashing.java#L35) 的分块文件摘要与 try-with-resources 是合理基础。文件路径可处理空文件，byte[] 重载却拒绝长度 0（56–59 行），同一内容走不同输入形态得不同语义；`HashingTest:45` 锁住这个行为。手写每字节 String.format 生成 hex（69–73 行）可直接换 `HexFormat`，不需要新工具依赖。[JDK 25 HexFormat](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/HexFormat.html)

**目标：** 默认 SHA-256，MD5 只保留明确兼容用途；摘要是内容标识/校验，不冒充密码存储或消息认证。空字节与空文件统一；流摘要内联进受限文件复制，避免保存后再读一次全文件。验收使用官方已知向量、空输入、各输入形态等价及大文件有界内存。需要认证用 HMAC/签名，密码校验交给专门 PasswordEncoder；快速摘要不适于密码存储。[OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

## 6. `json`、`http`：拥抱框架的真实配置点，去掉进程级意外状态

### 6.1 JSON

**保留的价值。** `Jsons` 接受 ObjectMapper 的构造器（[`Jsons.java:37`](../../src/main/java/cn/code91/facility/json/Jsons.java#L37)）已经是很好用的 Seam：调用者显式提供序列化政策，测试与应用跨同一 Interface。有限 Result 适配可以保留。Web 与服务代码复用 Spring mapper 的目标也对。

**要折叠的部分。** `JsonUtil` 静态创建整个 registry（43 行），`FacilityJsonAutoConfiguration` 把应用 mapper 注册进进程级单例；多 ApplicationContext 互相影响是结构性结果。`JsonsRegistry` 用 map 和 volatile default 两处存储默认值（72–78 行），并发 register/default lookup 的一致性还需要额外规则；调用方正常不需要学习这些规则。四套 namespace、公共 `mapper()`、JsonConfig 的大量镜像 builder 方法与 TypeRef 的嵌套类型工厂，使外层没有真正隐藏 Jackson，却新造了一层配置语言。

`JsonConfig.canonical()` 实质为排序并省略空值（146–152、656–659 行），不等价 RFC 8785 JCS。后者还有数字、字符串和属性排序的规范；尤其“删除空属性”会改变 JSON 语义。要用于签名/跨语言摘要，必须明确实施 JCS；只要求方便快照，应改称 deterministic/sorted，保持与 JCS 无关。[RFC 8785](https://www.rfc-editor.org/rfc/rfc8785.html)

`InputStreamSerializer` 读完整流、Base64 化并关闭传入流（54–56 行），反序列化也一次性分配所有字节（`InputStreamDeserializer:47–53`）。这是昂贵且有所有权副作用的序列化，不适合作为通用 bean 字段默认能力。`TypeRef.ParameterizedTypeImpl` 没有 equals/hashCode 且直接返回内部类型数组（171–207 行）；JDK ParameterizedType 要求表达相同声明和等价参数的实例相等。[JDK 25 ParameterizedType](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/ParameterizedType.html)

**Jackson 3 路线。** 官方明确其主包迁移到 `tools.jackson`（annotations 例外），ObjectMapper/JsonFactory 使用不可变 builder 模型，Java time/Optional/parameter names 支持已并入 databind。当前 JsonConfig 的 `build()` 后调用 setTimeZone/setDateFormat/configure 及 `Consumer<ObjectMapper>`（662–675、688 行）必须重做，不是仅改 import。公共 ObjectMapper/JsonNode/TypeReference 也使迁移成为消费方 Interface 破坏。应删除 `enableJava8Support` 与重复基础模块注册，直接给 Spring/Jackson 原生 builder 提供有限配置；保留 Result 适配时重审 Jackson 3 的异常层次。[Jackson 3 官方发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)

**目标：** 每个 ApplicationContext 拥有自己的不可变 JsonMapper；不发布公共全局 registry；不为“将来可能换 Gson”发明 Codec SPI。默认 DTO serialization 直接走 Spring converter；有确切业务需求的持久化格式/签名格式成为具名 Adapter，并拥有版本测试。错误返回保留类型/位置/诊断标识，默认不保存原 JSON 和对象 toString。

**验收：** 两个上下文不同日期/字段配置互不污染；Web 与注入 Jsons 结果一致；升级前后金样包括 null/空字段、record、泛型、日期、BigDecimal、长整型、枚举；异常仍进入预期通道；格式变化逐项批准。若宣称 canonical，必须过 RFC 向量和跨语言对照。流序列化只有显式受限场景可用。

### 6.2 HTTP

`HttpClients` 选择同步 RestClient 符合 Servlet/虚拟线程应用；用 Result 暴露可恢复外部故障也有价值。现有测试用 MockRestServiceServer 验证 2xx/4xx/5xx/headers，这比只 mock 门面更贴近实际行为。

但 [`HttpClients.java:49`](../../src/main/java/cn/code91/facility/http/HttpClients.java#L49) 每次从静态上下文按类型找唯一 RestClient，无 bean 就 `RestClient.create()`；`FacilityHttpAutoConfiguration:49` 使用 `RestClient.builder()`，都绕过 Boot 注入的 Builder 及其 customizer。结果不是只有“少一个连接池配置”：调用者的 JSON converters、统一认证/拦截器/观测配置可能根本未进入这个实例。Boot 官方明确建议注入预配置 prototype Builder；直接 `create()` 不应用自动配置/customizer。[Spring Boot RestClient](https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html)

当前 Interface 只有 Class<T>，没有泛型 response；header 只在 GET 暴露；所有技术失败只分 HTTP_STATUS 与 SEND_AND_PARSE，调用者无法直接区分连接、响应反序列化、配置错误。继续加重载会把整个 RestClient fluent Interface 再复制一遍。RestClient 已有 ParameterizedTypeReference 和 `@HttpExchange` 类型化客户端，适合“支付提供商 Adapter”这种真实 seam。[Spring REST Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)

**目标：** 默认不提供静态 HttpClients；生成应用级、按第三方用途命名的 Client/Adapter，注入 Boot Builder 或 HTTP Service client。只有在这些 Adapter 内统一做错误翻译、明确超时和请求预算、敏感头处理与观测。第三方业务错误由 Adapter 译为业务所需错误；不要将每个 I/O 都一律 catch Exception，连空 type/坏 URI 都伪装成网络失败。默认不对有副作用请求自动重试，重试资格与第三方幂等契约放一起。

**验收：** 两个第三方不同 baseUrl/认证头/超时不会互串；Boot customizer 生效；泛型列表可反序列化；204 与“有值成功”契约协调；超时、4xx、5xx、坏响应分类；应用观测可看到请求；无配置时不能静默启动一个无预算默认 client。升级 ADR-0018 时保留它正确的 RestClient 选择，推翻的是避开 Boot 配置点的理由。

## 7. `csv`、`excel`：从读写 List 转向有预算的表格交换

### 7.1 CSV

[`CsvUtil.java:79`](../../src/main/java/cn/code91/facility/csv/CsvUtil.java#L79) 的 BOM、CRLF、引号转义和借用流不关闭政策，对“中文报表给 Excel 用户”有直接价值。无表头假设的 `List<List<String>>` 避免把列名唯一性强加给调用方，ADR-0021 的这个决策应保留在基础行模型中。

但手写 parser 用两个 boolean，没有严格区分引号刚结束的状态（172–231 行）。`CsvUtilTest:188` 明确把 `"ab"x,c` 解析为 `abx,c`；这属于 lenient dialect，不能无保留宣传 RFC 4180 合规。RFC 4180 的 escaped field 结束引号之后进入字段/记录结束，非任意文本拼接。容错是政策，不是坏事，但必须在 Interface 明确。[RFC 4180](https://datatracker.ietf.org/doc/html/rfc4180)

更大的限制是 read 全量 List、单个 field 无长度上限、全部 rows 无数量上限；当前 API 的 Interface 没有内存预算。write 面向 Excel，却原样输出 `=1+2` 这类公式前缀（106–114 行），CSV 正确引号转义不等于避免 spreadsheet 公式求值。OWASP 明确不存在对所有 spreadsheet 与所有下游同时透明的通用清洗；安全导出必须是显式用途政策，不能偷偷损坏机器交换数据。[OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection)

**目标：** 用 Commons CSV 的成熟 dialect/parser/printer 替换手写 grammar；无需把库所有开关镜像出来，只暴露已有场景的 `machineCsv` 与 `spreadsheetExport` 预设，BOM/公式文本化政策随用途明确。Commons CSV 支持逐记录迭代，避免先全量 getRecords；字节、行、列、字段长度预算仍由本项目 Module 负责，不能因为换了库就声称资源安全自动解决。[Commons CSV CSVParser](https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVParser.html) [CSVFormat](https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVFormat.html)

ADR-0021 因当时离线 `.m2` 缺失而否决成熟 CSV 库，是当时环境约束，不应成为长期语法维护理由。现在网络与依赖更新已是任务范围，应重新做选择。替换需要承认旧宽松格式的兼容影响，不应强行让老测试“仍全绿”掩盖政策变化。

**验收：** RFC 样本、旧宽松样本分开；引号/CRLF/BOM/空最后字段/异常中断；十万行读写内存受预算约束；达到预算返回行列位置，停止消费；公式输入分别验证 machine 与 spreadsheet 用途；借用流的关闭责任不变。

### 7.2 Excel

[`ExcelUtil.java:145`](../../src/main/java/cn/code91/facility/excel/ExcelUtil.java#L145) 的入口探测与 package-private ExcelSupport 隔离 POI，比随处 classloading 防护更有 Locality；但是它只证明入口两类存在，并不验证全部传递依赖或二进制兼容。测试覆盖的是 overridePoiPresent(false)，ADR 本身诚实承认不是缺库端到端。拆出独立 `facility-tabular-poi` artifact 后，用户引入这个能力就获得它的必需依赖，可以整体删除运行时探针与状态覆盖测试。

`ExcelSupport.toRows` 整簿载入、第一 sheet、DataFormatter、FormulaEvaluator（53–75 行）适合小型人工表格导入，读显示值且公式算值的行为已有测试（`ExcelUtilTest:142`）。它不保留日期/数值/文本的类型区别，formatter 还取默认 Locale（58 行）。`SXSSFWorkbook` 写限制 POI 内部行窗口，但传入者必须先提供完整 List（86–98 行），所以端到端并不是恒定内存写。读取也可能为稀疏且 lastRowNum 很大的 sheet 创建大量空行（61–64 行）。

POI 官方提供 event/SAX 读取与 SXSSF 写入，并强调临时文件、共享字符串等资源取舍；流式不等于所有内存/磁盘成本恒定。官方 how-to 的某些清理示例与当前 ADR 对具体版本 close 字节码的判断有时间差，升级时应按解析到的 POI 源码/行为测临时文件释放，不能机械地只凭教程改回 dispose。[POI Spreadsheet how-to](https://poi.apache.org/components/spreadsheet/how-to.html)

**目标：** 保留 POI，暂不引入更高层注解 ORM。定义可复用的行流 Interface：小文件便利 `readAll` 有显式限额，大文件 event reader 边读边消费，write 接受 Iterable/producer。显示值导入与类型值导入分开；Locale 与公式政策显式（读取缓存值/主动求值/拒绝公式）；先不新增表头 Map/POJO 绑定，尊重已确认的范围。CSV 和 XLSX 已有两个真实 Adapter，Tabular seam 有事实基础，但仅共享行模型、预算、位置错误，格式专有能力保留在专有配置中，不伪造完全等价。

**验收：** 指定 Locale 稳定；十万行及稀疏末行；公式缓存缺失/不支持/异常都可诊断；字符串 `=1+2` 写为文本单元格；行列和长度限制；写出失败与取消后无临时文件泄漏；目标文件不在成功前对外可见。升级 POI/Tika 必须做真实外部文件互操作，不只用同一库写再用同一库读。

## 8. `crypto`：值得继续加深，但安全默认必须可演进

[`CryptoUtil.java:141`](../../src/main/java/cn/code91/facility/crypto/CryptoUtil.java#L141) 固定 AES/GCM/NoPadding、12 字节随机 IV、128-bit tag，不允许调用方选择 mode/nonce；失败不返回底层解密异常（186–205 行），减少信息暴露。`CryptoUtilTest` 的篡改/错误 key/随机性和 RFC4231 HMAC 向量比单纯 round-trip 更有价值。JDK Cipher 官方明确 GCM 的同 key IV 唯一要求与 AAD 的调用顺序；这些复杂性值得收在 Module 内。[JDK 25 Cipher](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html)

需要更精确的承诺：随机 nonce 降低碰撞概率，不是在无限调用量下数学“根除复用”；导入 key 接受 128/192/256 位（232–236 行），所以“固定 AES-256”只准确描述默认生成/派生 key；Base64 只是密钥编码，不是安全保存。错误结果相等也不意味着实现已做到整条请求路径恒定时间。保留现有粗错误结果，但不要把它写成对所有 oracle 的完整防御。

真正长期风险是**格式没有演进位置**：输出只有 Base64(iv + ciphertext + tag)，没有版本、keyId、算法/KDF 标识，也没有 AAD（146–154 行）。`deriveKey` 把 PBKDF2-HMAC-SHA256 210000 写死（43、250 行），用户只保存 salt。OWASP 当前参考值为 SHA256 600000 次，并要求 work factor 结合目标环境衡量；其文档讨论密码存储，不能据此宣称所有加密派生场景都必须无条件取同一个常量。但 ADR 所谓“OWASP 2023 下限，参数不可调所以安全”的论证已经不适合作为永久政策。[OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

**目标：** 保留 JCA/JCE，不自行实现算法。把公开能力收成版本化 envelope encrypt/decrypt：密文含 format version/keyId/必要 KDF metadata，AAD 绑定租户/记录/用途；随机数据 key 默认生成，密码派生单独声明用途。受控 policy 可提高安全参数，旧参数只供解密，不任由调用者降低新写入基线。密钥轮换与存储是生命周期设计，不能由 `exportKey` 的 Base64 代替；只有接入第二个真实 key provider 时才建 provider SPI。[OWASP Cryptographic Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)

**迁移顺序不可反。** 先给旧密文声明 legacy v0，固定保留其 210000 派生行为；再落地新 envelope 与迁移测试；最后提高新写入政策。仅升级迭代次数会使用旧 password+salt 再派生出来的 key 改变，导致旧密文无法解密。`char[]` 入口和及时清理中间 key byte[] 可以改善局部内存卫生，但不能承诺清除所有 JVM 副本。Base64 与 Hex 直接 JDK，去掉手写 Hex；HMAC 的 verify 入口可把时间一致比较收起来，前提是确有 webhook 验签用途。

**验收：** legacy 密文持续可读；新旧 key 并存并按 keyId 选择；AAD 改变后认证失败；新写入拒绝低于政策的派生参数；错误无细分泄漏；已知向量与跨实现对照；参数基准在部署硬件留档；没有密码认证场景误用可逆加密。

## 9. 建议的实施目标与顺序

| 阶段 | 目标 | 完成证据 |
|---|---|---|
| P0 先定义契约 | 固定 Result 无值、流所有权、JSON wire 格式、CSV dialect、crypto legacy 格式 | 每项一页可审阅决策与兼容样本；不改旧 ADR 来伪造历史 |
| P1 平台迁移 | JDK 25；Boot 管理的 Jackson3/RestClient 配置点；相关依赖按主报告更新 | 消费样例能运行；两个上下文隔离；格式金样和真实 HTTP 测试；版本清单与解析结果一致 |
| P2 删除浅层 | common/date/number/pattern/copy 的重复 Interface 收缩；业务用原生 JDK/record/MapStruct | 每个保留方法有具体调用场景；默认入门文档不要求学习一套替代 JDK |
| P3 加深文件/表格 | 按需 artifact 的文件生命周期、行流、预算、精确失败、无半成品 | 真实上传/导入/导出样例；大文件、取消、临时文件、符号链接及跨平台验证 |
| P4 加密演进 | envelope、keyId、AAD、legacy reader、受控 KDF policy | 旧密文金样、新策略基准、轮换/回滚演练 |

这些验收不是要求现在为每个琐碎方法再写更多单测；应该把原来大量分支钉桩转化为跨 Interface 的可观察行为证据。更改行为之前先定义新场景，不能靠“不修改旧测试”阻止必要的主版本变化。

## 10. 独立方案 A：最小核心，原生框架，按需文件能力

这是供主方案比较的激进替代路线。它与“保留所有工具门面逐个修好”互斥：**核心只提供两个入口 `Result` 与 `Failure`；需要文件业务时增加一个 `FileWorkspace` Module。HTTP、JSON、时间、集合、复制、数字、正则全部原生使用。** 核心不承诺替换 Spring 的开发体验，它只定义团队如何表达预期失败；agent 的速度来自一组完整应用样例和明确的不变量。

### 10.1 Module 与 Interface

以下为设计草图，省略 import、泛型完整声明和实现，不是已提交的新代码。

```java
// 入口一：共享“预期失败”的最小代数，不自动捕获任意运行时异常。
sealed interface Result<T, E> {
    static <T, E> Result<T, E> ok(T value);     // value 非 null
    static <T, E> Result<T, E> err(E error);    // error 非 null
    static <E> Result<Unit, E> done();
    <U> Result<U, E> map(Function<T, U> f);
    <U> Result<U, E> flatMap(Function<T, Result<U, E>> f);
    <F> Result<T, F> mapErr(Function<E, F> f);
    <R> R fold(Function<T, R> success, Function<E, R> failure);
}

// 入口二：可公开错误；code 由发生错误的 Module 定义。
record Failure(String code, Map<String, String> safeParameters) {}

// 按需入口三：具体本地文件能力，不为未来对象存储预造接口层。
final class FileWorkspace {
    Result<StoredFile, FileFailure> put(InputStream borrowed, UploadMetadata metadata);
    Result<InputStream, FileFailure> open(FileId id); // 返回流由调用者关闭
    Result<Unit, FileFailure> delete(FileId id);
}
```

`Unit/FileId/StoredFile/FileFailure/UploadMetadata` 是支撑上述 Interface 的具名值类型，不是新工具门面。FileFailure 应给出可枚举 code 与必要安全上下文，可映射为 Failure；不使用 `args[0]`。小核心中的 `Result<T,E>` 允许应用错误使用自己的 sealed error，不强制一切都用同一个 Failure 类型。Failure 只承担对外稳定表达；内部异常诊断由实际 Adapter 处理，不能把底层 Exception 装进可公开数据。

**Interface 的完整契约：**

1. Result 只表达业务预期拒绝和可恢复外部失败；编程错误如 null mapper、违反类型不变量直接失败。成功查询的缺席用 Optional，成功命令用 Unit。不隐式 logging，不读取 ThreadLocal/SpringContextHolder，不自建线程。
2. Failure code 稳定；parameters 只接受调用方显式批准公开的具名值，值对象构造时防御性复制；localization 由 HTTP/CLI 展示 Adapter 完成。它不是异常堆栈的 JSON 编码。
3. FileWorkspace 由应用启动时用 root、最大 bytes、允许用途/类型等 policy 构造。root 是进程受控目录，不向非可信调用者开放修改符号链接的能力；配置错误启动失败。
4. put 借用输入流，读取并推进，不负责关闭；被拒绝后不保证流位置还原，不无谓 drain 剩余数据。流内容先写同一文件系统内不可公开的暂存文件，受限复制时计算摘要，随后识别/校验，再原子提交；若平台无法满足所需原子性，返回明确失败，不能静默降低承诺。失败清理自己的暂存文件，不影响已提交文件；底层故障导致清理也失败时，记录诊断并由可重试回收流程处理，半成品始终不可公开。
5. 用户名称仅是 metadata，最终存储 key 由 Module 生成；open/delete 只接受 FileId，不接受任意路径。put 成功后才返回 StoredFile；open 成功后的返回流由调用者关闭。delete 明确采用幂等删除或不存在失败中的一种，本方案选择幂等删除。FileId 不代表授权；业务 Module 负责租户/所有者鉴权和数据库关联，这个本地文件提交不承诺与业务数据库跨资源原子性。
6. policy 和探测 Implementation 在构造后固定，无进程级注册表；没有把“缺 MIME 库”“目录不可写”降级成绕过检查的成功。

### 10.2 调用示例

```java
// HTTP：应用自己的 Adapter 负责把第三方失败译成所需领域错误。
final class InventoryGateway {
    private final RestClient client;

    InventoryGateway(RestClient.Builder configuredBuilder) {
        client = configuredBuilder.baseUrl("https://inventory.internal").build();
    }

    Result<Reservation, ReserveFailure> reserve(ReserveCommand command) {
        // 使用 RestClient 原生 typed request/response；只翻译明确可预期的失败。
        // auth、timeout、observation 在应用配置及此 Adapter 的政策里。
        return /* ... */;
    }
}

// JSON：直接注入应用作用域的 Jackson 3 JsonMapper，无通用 Codec 层。
final class OrderSnapshotWriter {
    private final JsonMapper mapper;
    // 此 Adapter 拥有订单快照版本格式和升级测试。
}

// 文件：调用者不拼目录、不重新嗅探、不重复做 hash 或失败清理。
try (InputStream requestBody = upload.getInputStream()) {
    return files.put(requestBody, new UploadMetadata(upload.getOriginalFilename(), INVOICE))
        .map(stored -> new Attachment(stored.id(), stored.size(), stored.sha256()));
}
```

### 10.3 Seam、Adapter、Depth、Locality

| 概念 | 本方案落点 |
|---|---|
| Module | Result 是共享错误代数；FileWorkspace 是单次文件摄入的完整生命周期；InventoryGateway 是应用的第三方业务调用 |
| Interface | 不止方法：包含值非 null、资源所有权、失败分类、固定 policy、提交前不可见、何时有副作用 |
| Seam | Spring 的 Builder/JsonMapper 注入点、业务 Gateway 的业务行为点；不再另建全局 Http/Json 工具 seam |
| Adapter | 具体 inventory client/订单快照 writer；CSV 与 POI 行读写可作为按需业务导入 Adapter，不进入两入口核心 |
| Depth | FileWorkspace 隐藏受限复制、摘要、探测、路径决定、暂存、提交、失败清理；一次调用避免这些知识扩散 |
| Locality | 文件 policy/错误/测试在一个 Module；JSON wire 格式与对应业务 Adapter 在一起；第三方协议变更不污染所有 HTTP 调用 |

不用“一个 Adapter 也先抽接口以便 mock”作为 seam 理由。FileWorkspace 初期就是具体本地实现；真正需要 S3 与本地两种存储时，先用两个实际使用场景验证共同契约，再提取可替换 seam。它不是拿同步 Path 语义强行假装云对象存储等价。

### 10.4 依赖与制品

核心是 JDK 25 的极小 jar，Result/Failure 不依赖 Spring、Jackson、POI、Tika、Lombok。应用模板使用 Boot BOM 管理 Spring/Jackson；无需为了“抽象洁癖”把它们从业务 Adapter 隐藏到一套通用协议中。表格、文件内容检测、加密 envelope 是按需能力制品，各自声明真实必需依赖；不在单一 jar 中布满 optional 探针。为了控制制品维护面，第一阶段只拆核心与 Boot 集成/应用模板，文件/表格有消费时再拆，不立即创建十几个空 Maven module。

旧门面可在短期 `facility-legacy` 中机械转接，但新应用默认不能依赖它；迁移期结束删除。文档首先展示创建一个实际订单服务的纵向代码，再按遇到的任务链接到原生框架配置；不要求 agent 先背一个 29 包替代标准库。

### 10.5 何时这个方案更差

当团队同时维护大量同质服务，需要把统一 tracing、错误渲染、审计、幂等、鉴权、表格政策强制一次配置到位时，只有两入口核心会把整合责任留给应用模板，长期容易漂移。这时更完整的 Boot starter 或“业务操作执行” Module 可以获得更高 Leverage。又如绝大多数项目都大量处理 Excel，按需模板写 Adapter 不如共享 Tabular Module；如果多协议序列化已经真实存在，拒绝抽取 codec seam 反而降低 Locality。

本方案适合脚手架仍没有稳定外部消费、希望让 agent 凭成熟框架原生能力构建不同类型应用的阶段。其成功标准不是删了多少类，而是：一个新业务服务只学习 Result/Failure 加实际领域 Interface；平台升级的工作集中在 Boot BOM 与少数真实 Adapter；文件危险操作只有一处有完整生命周期证明。若达不到这三个标准，删工具只是把复杂性搬回消费方，并没有获得 Depth。
