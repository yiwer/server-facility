# §10 Roadmap 遗留技术债处理

- **状态**:待执行
- **来源**:docs/superpowers/P7-verdict-reconciliation.md §5 + spec §10 遗留债表
- **分支**:feat/roadmap-tech-debt(自 master,P0-P7 收官后 775 绿)
- **性质**:收口后技术债清理,非迁移。沿用 TDD + PowerShell 提交(信息不含 ASCII 双引号)+ 迁移/rework 分离纪律。

## 0. 逐项审视结论(计划作者已 grep/实测)

| 债 | 审视结论 | 处置 |
|---|---|---|
| **债1** FacilityErrorType {0} 占位 | args 三路可用:`getFormattedMessage()` 不含(面向用户稳定、不泄漏路径)、`toString()` L315 **已含** `args=[...]`(服务端日志)、`getArgs()` getter。无差别让 args 进 message 会泄漏 `FILE_NOT_FOUND`/`FILE_DELETE_ERROR` 的服务器路径(`file.getPath()`/`dir.toString()`)。P7 文档收窄**完备无缺口**。 | **决议关闭,不改代码**;roadmap 标记 closed |
| **债2** Jsons deserialize null 不对称 | `deserialize(String/byte[])` null→返 err;`deserialize(InputStream)` L141/L151 `requireNonNull(input)`→抛 NPE。数据参数应统一返 err,类型参数(target/typeRef)保持 requireNonNull(编程契约)。JsonUtil L114/L118 委托 Jsons,改 Jsons 即生效。 | **修**:InputStream null→返 err(TDD) |
| **债3** formatSize 边界 | 浮点**实测无偏差**(1024^n 全正确显示 1.00 X);`<1024` 已有 B 单位。真问题:超 PB(EB 级)显示 `1024.00 PB` 不进位(long 可达 9.2 EB);负数返 `-N B`。 | **修**:加 EB 单位进位;负数文档化(TDD) |
| **债4** 覆盖率提门 0.80→0.85 | 全局 line 81.9%(~516 missed);低覆盖:copy 51%(128 missed 大头)、web.interceptor 48%(11)、web.util 55%(49)、web.filter 61%(39)。补 ~88 行即达 85%。 | **修**:补 copy+web 盲区,gate 提 0.85 |

## 1. 任务列表

### T1 债2:Jsons InputStream null 对称(控制器;TDD;1 提交)
- **Files**:Modify `src/main/java/cn/code91/facility/json/Jsons.java`(deserialize(InputStream,Class) L140、deserialize(InputStream,TypeReference) L150);Test `src/test/java/cn/code91/facility/json/JsonUtilTest.java`(追加)
- RED:JsonUtilTest 追加 `deserializeInputStreamNull_returnsErr`(2 断言:`JsonUtil.deserialize((InputStream)null, String.class).isErr()` 与 TypeReference 重载同)。当前抛 NPE → 测试 error(RED)。
- GREEN:两个 InputStream 重载 `Objects.requireNonNull(input, ...)` → `if (input == null) { return handleDeserializeError(...) 或 Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR)); }`(与 String/byte[] 分支一致的 err 构造,读那两个分支照做);target/typeReference 的 requireNonNull **保留**。
- 摘要:`fix: Jsons deserialize(InputStream) null 入参返 err(与 String/byte[] 对称,债2)`

### T2 债3:formatSize EB 进位(控制器;TDD;1 提交)
- **Files**:Modify `src/main/java/cn/code91/facility/number/NumberFormat.java`(formatSize);Test `src/test/java/cn/code91/facility/number/NumberFormatTest.java`(既有或新建,先读)
- RED:测 `formatSize(1152921504606846976L)`(1 EB)== `"1.00 EB"`(当前 "1024.00 PB" → RED);再固化 `formatSize(1024)=="1.00 KB"`、`formatSize(1073741824)=="1.00 GB"`、`formatSize(1023)=="1023 B"`、`formatSize(0)=="0 B"` 防退化。
- GREEN:units 加 `"EB"`(6 个);clamp 边界同步(`exp > units.length` 语义仍成立,units.length=6);字段 javadoc 补一句负数/上限说明(负数走 `bytes < 1024` 返 `-N B`,不进位)。
- 摘要:`fix: formatSize 加 EB 单位,超 PB 进位(债3;浮点边界实测无偏差)`

### T3 债4:copy + web 盲区补测 + gate 提门(sonnet;补测非红绿;1-2 提交)
- 补 `copy`(CopyUtil,128 missed)、`web.interceptor`(AccessLogInterceptor/SessionUserClearInterceptor)、`web.util`(RequestUtil/CookieUtil 等)、`web.filter`(TraceIdFilter/RepeatableRequestWrapper)盲区行为测试。断言探针实证,不改主源(发现 bug 记观察)。
- 目标:全局 line ≥85%(awk 实测报增量);随后 pom 提 gate `jacoco.line.min`/`jacoco.instruction.min` 0.80→0.85(branch 视实测,现 69.8% 保 0.65 或按实测提)。
- **顺序**:先补测到 ≥85% 绿,**再**改 pom 阈值(否则 gate 先红)。改阈值后 `mvn clean verify` 全绿实证。
- 摘要:`test: copy/web 盲区补测(line XX%→YY%)` + `build: JaCoCo gate 提门 0.85(债4)`

### T4 债1 决议关闭 + roadmap 更新(控制器;1 提交)
- **Files**:Modify `docs/superpowers/P7-verdict-reconciliation.md`(§5 债1 标记 closed + 理由)、spec §10 遗留债表(债1 划闭、债2/3/4 标完成)
- 债1 记:args 三路可用(getFormattedMessage 面向用户不含/toString 服务端日志含/getArgs getter),无差别进 message 泄漏服务器路径;决议维持文档收窄,不改行为。
- 摘要:`docs: 债1 决议关闭(args 三路可用无缺口)+ roadmap 债务对账`

### 收尾(控制器)
- opus 全分支终审(重点:债2/3 行为改动正确性、补测真实性、gate 阈值达标、债1 决议记录诚实)。
- ledger + memory 更新;finishing-a-development-branch(本地合并回 master)。

## 2. 验收总标准
- `mvn clean verify` 全绿(JaCoCo gate 提至 0.85 后仍绿);dependency:analyze 零 warning。
- 债2:deserialize(InputStream) null 三参数族对称(数据 null 返 err、类型 null 抛 NPE);债3:formatSize EB 进位 + 边界防退化测试;债4:全局 line ≥85%;债1:决议记录闭合。
- `@Test` 实数较 775 基线有增量(T1+T2+T3 补测)。
