# 批次 5 一致性宪法 实施计划(spec: 2026-07-06-consistency-constitution-design.md)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地一致性宪法:三个 breaking 修正(B1 formatFallback 降 private/B2 删 severity/B3 allNotNull 空数组→true)+ 条款落地(C1 类级契约汇总/C2 「≤0 不限制」拼法统一/C5 error/result 簇 @Nullable)+ DESIGN 宪法章节(五条款+C3 审计清单,控制器亲自)。

**Architecture:** 见 spec(九项拍板全取推荐,2026-07-06)。除 B1/B2/B3 与 C2 构造器等价改写外行为零改动。

## Global Constraints

- 一切 Maven 命令带 `-o`;基线 **1173 绿**(master 02f3faa);gate 0.88/0.75;ArchUnit 5/5;doc-truth 红线;计数用实数。
- **授权修改/删除的既有测试仅限**:`ErrorTypeInterfaceTest.getSeverity_defaultIsInfo`(B2 随删)、NullSafe 的 allNotNull 空数组锁定断言(B3 翻转)——报告引旧断言原文;其余零改动。
- 只许改各 Task 点名文件;提交 `git commit -F`(UTF-8);零新依赖(jakarta.annotation 已在 classpath,以既有 @Nullable import 为证)。

---

### Task 1: B1+B2 — ErrorTypeInterface 契约面收缩

**Files:**
- Modify: `src/main/java/cn/code91/facility/error/ErrorTypeInterface.java`
- Test: `src/test/java/cn/code91/facility/error/ErrorTypeInterfaceTest.java`(删 1 测,改 getDetailedDescription 断言如有 severity)

**Steps:**
- [ ] **1. RED 前置**:`grep -rn "formatFallback\|getSeverity\|ErrorSeverity" src/ --include=*.java` 确认引用面 = ErrorTypeInterface.java + ErrorTypeInterfaceTest L54-56(唯一外部引用);把 grep 输出贴报告。
- [ ] **2. 实现 B1**:`default String formatFallback(...)` → `private String formatFallback(...)`(javadoc 保留,删「子类可覆盖」类措辞如有;方法体不动)。
- [ ] **3. 实现 B2**:删除 `getSeverity()` 方法与 `ErrorSeverity` 枚举整段;`getDetailedDescription()` 格式串去 `severity='%s'` 段与对应参数(输出变为 fullCode/messageKey/defaultMessage 三段);其 javadoc 同步。
- [ ] **4. 测试**:删除 `getSeverity_defaultIsInfo`(引旧文);grep 检查 getDetailedDescription 既有断言若含 severity 字样→按新三段格式更新(授权,引旧文);若无则补一个三段格式锁定测试。
- [ ] **5. GREEN**:`mvn -o test -Dtest=ErrorTypeInterfaceTest` 全绿;再 `grep -rn "getSeverity\|ErrorSeverity" src/` **零命中**贴报告(B2 零残留证据)。
- [ ] **6. 全量 `mvn -o test`**(计数变化以实数为准,预期 -1~+0)→ 提交:

```
refactor: ErrorTypeInterface 契约面收缩——formatFallback 降 private + 删 severity(B1/B2,宪法批)

formatFallback 是 format() 的内部降级细节(全库唯一调用点),从契约面降回
实现细节;getSeverity/ErrorSeverity 全库零外部消费(仅自消费+1 个默认值
测试),YAGNI 删除,getDetailedDescription 去 severity 段。0.1.0 窗口内
breaking,用户拍板(2026-07-06)。
```

---

### Task 2: B3 — allNotNull 空数组对齐惯例

**Files:**
- Modify: `src/main/java/cn/code91/facility/common/NullSafe.java`
- Test: `src/test/java/cn/code91/facility/common/NullSafeTest.java`(翻转空数组锁定断言,授权)

**Steps:**
- [ ] **1. 改锁定测试先行(RED)**:找到 allNotNull 空数组断言(引旧文),翻转为 `assertThat(NullSafe.allNotNull()).isTrue()`;补 null 入参仍 false 断言(如无);`mvn -o test -Dtest=NullSafeTest` → 翻转断言红(旧代码空数组返 false),贴原始输出。
- [ ] **2. 实现**:`if (elements == null || elements.length == 0) return false;` → 拆分:`if (elements == null) return false;`(null-safe 保持)+ 空数组自然落入 for 循环零迭代返 true(vacuous truth,无需显式分支)。javadoc:「空数组返回 true(空集上的全称命题为真,对齐业界惯例;B3/宪法批);null 入参返回 false(null-safe)」。
- [ ] **3. 同族审计**:grep NullSafe 全类,确认无其他「空数组/空集合上的全称/存在命题」方法(已预核:无 anyNull/allNull;isEmpty 族语义无歧义)——结论写报告。
- [ ] **4. GREEN** → 全量 → 提交:

```
fix: NullSafe.allNotNull 空数组改返 true(B3,宪法批)

空集上的全称命题为真(vacuous truth,对齐 lang3 惯例);null 入参仍返
false(null-safe 不变)。锁定测试随语义翻转(旧断言引于报告)。0.1.0
窗口内 breaking,用户拍板(2026-07-06)。同族审计:无其他空数组全称/
存在命题方法。
```

---

### Task 3: C1+C2+C5 — 契约汇总 + 拼法统一 + @Nullable 补标

**Files:**
- Modify: `src/main/java/cn/code91/facility/number/Numbers.java`(仅类 javadoc,C1)
- Modify: `src/main/java/cn/code91/facility/number/NumberFormat.java`(仅类 javadoc,C1;若类名不同以 format(null)→"" 所在类为准,grep 定位并披露)
- Modify: `src/main/java/cn/code91/facility/mime/MimeTyping.java`(仅类 javadoc,C1;路径以 grep 为准)
- Modify: `src/main/java/cn/code91/facility/pattern/Patterns.java`(仅类 javadoc 补 null-safe 汇总一句,C1)
- Modify: `src/main/java/cn/code91/facility/web/filter/RepeatableRequestWrapper.java`(C2:便利构造器 `Long.MAX_VALUE`→`0` + javadoc「≤0 = 不限制」)
- Modify: 全库「无限制」拼法点(C2:grep `MAX_VALUE.*不限制|0.*不限制|不限制` 于 src/main + docs/USAGE.md,统一为「≤0 = 不限制」;逐点列报告)
- Modify: `src/main/java/cn/code91/facility/result/Result.java`、`src/main/java/cn/code91/facility/error/WrappedError.java`、`src/main/java/cn/code91/facility/structure/Tuple.java`、`src/main/java/cn/code91/facility/structure/Triple.java`(C5:可空参数/返回补 `jakarta.annotation.Nullable`——仅注解与 import,零行为;以各类实际可空点为准,逐点列报告)

**Steps:**
- [ ] **1. C1 四处类级 javadoc**:Numbers「setScale(null)→null(数据参数 null-safe)」;NumberFormat「format(null)→""(空串回退)」;MimeTyping「detect(File) 走 Result;detect(byte[]) 返 String,失败回退 FALLBACK 常量(吞异常,行为已测试锁定)」;Patterns 类级补「全部方法 null-safe:null 输入返回空集合/空 map/原样」。每句先对照代码实况(doc-truth),不符则如实改写并披露。
- [ ] **2. C2**:便利构造器改 `this(request, 0);`(行为等价:`max > 0` 才限制)+ 全库拼法 grep 统一。跑 `mvn -o test -Dtest=RepeatableRequestWrapperLimitTest,RepeatableRequestFilter413Test` 证零回归。
- [ ] **3. C5**:四类补 @Nullable(工厂方法可空参、可空访问器返回);`mvn -o clean test-compile` 零警告。
- [ ] **4. 全量 `mvn -o test`**(计数不变预期)→ 提交:

```
docs: 宪法条款落地——C1 契约汇总×4 + C2 ≤0 拼法统一 + C5 error/result 簇 @Nullable(宪法批)

C1:Numbers/NumberFormat/MimeTyping/Patterns 类级 javadoc 如实汇总各自
null 契约(行为零改动,均有测试锁定);C2:「≤0 = 不限制」全库统一拼法,
便利构造器 MAX_VALUE→0(行为等价);C5:Result/WrappedError/Tuple/Triple
可空点补 jakarta @Nullable(仅注解,触碰即补政策的首批)。
```

---

### Task 4: DESIGN 宪法章节 + 收口(控制器亲自)

- [ ] DESIGN 新章节「一致性宪法」:五条款(C1-C5,措辞照 spec §1)+ C3 降级日志政策审计清单(WARN 点/静默点各列,与现状逐点对照,预期全符合;发现违背→停下呈报)+ B1/B2/B3 记录(含 HttpClients 历史例外条目)
- [ ] `mvn -o clean verify` → 实数回填 README/DESIGN 计数;grep 复查 severity/拼法零残留
- [ ] 提交:`docs: 一致性宪法章节入 DESIGN(五条款+C3 审计清单)+ 计数同步(宪法批收官)`

---

## 验收(整分支)

1. verify 全绿、gate met、ArchUnit 5/5;severity 全库零残留;拼法统一 grep 零歧义;
2. B3 行为翻转有 RED→GREEN;B1/B2 编译面+grep 证据;C5 注解零行为(diff 仅注解/import/javadoc);
3. DESIGN 宪法章节与 spec §1 逐条对应;C3 审计清单与实况相符;
4. opus 终审 → merge --no-ff → 复验 → 删分支 → 台账/记忆(findings 文档五批次全部收官)。
