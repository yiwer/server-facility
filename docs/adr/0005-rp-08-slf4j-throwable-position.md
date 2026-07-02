> **inherited-from**: beacon ADR-0005(原仓库 docs/adr/0005-rp-08-slf4j-throwable-position.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0005: RP-08 LogUtil Throwable 参数末位（SLF4J 对齐）

## Status

Accepted

日期：2026-05-21

## Context

- 评审来源：`docs/facility/REVIEW.md` §7 RP-08 + §3.4 评审发现（`log\LogUtil.java:165, 208`）+ §6.B.5 `log/`
- beacon 现状：`LogUtil.warn(Throwable t, String msg, Object... args)` 与 `LogUtil.error(Throwable t, String msg, Object... args)` 把 Throwable 置于第一参数位。
- 问题：与 SLF4J 标准用法相反——SLF4J `Logger.warn(String msg, Throwable t)` 把 Throwable 置于末位。当类型恰好兼容时编译器不报错，调用方若不注意顺序将产生静默 bug（参数颠倒但无编译错误）。例：`LogUtil.warn("用户 {} 未找到", userException)` 在 SLF4J 中 `userException` 会被识别为 stack trace（正确）；在旧 LogUtil 中 `userException` 作为占位符 args[0]，stack trace 丢失。
- 行业现状：
  - **SLF4J User Manual** *Logging Exceptions* 段明确：`logger.error("Failed to ...", t)` —— Throwable 置于末位
  - **Logback `Logger` API**：`error(String format, Object... args)`（SLF4J 1.6+ 风格，若 args 最后一个是 Throwable 则单独处理为 stack trace）+ `error(String msg, Throwable t)` 双重载
  - **Log4j 2 API**：同上风格（msg 在前，Throwable 在末）

## Decision

`LogUtil` 新增 SLF4J 风格签名（Throwable 在 msg 之后），旧签名标 `@Deprecated(forRemoval = true)`：

**新增签名**：
```java
public static void warn(String msg, Throwable t);
public static void warn(String msgPattern, Throwable t, Object... args);
public static void error(String msg, Throwable t);
public static void error(String msgPattern, Throwable t, Object... args);
```

**旧签名**（标弃用，方法体保留 delegate）：
```java
@Deprecated(since = "phase-3", forRemoval = true)
public static void warn(Throwable t, String msg, Object... args);
@Deprecated(since = "phase-3", forRemoval = true)
public static void error(Throwable t, String msg, Object... args);
```

**关键设计选择**：用 `(msg, t, args...)` 而非 SLF4J 1.6+ 的 `(msg, args..., t-last)` 风格。理由：
- 显式声明 Throwable 类型参数（编译期约束），避免调用方误传 Throwable 到 args[N-1]
- 与 Logback 双重载第二种 `error(String msg, Throwable t)` 一致
- 对调用方而言：固定 msg 在前、Throwable 在中（显式）、args 在末（占位符填充）

**facility 内部调用方更新**：扫 facility 内部所有旧风格 `LogUtil.warn(t, ...)` / `LogUtil.error(t, ...)` 调用，改用新签名（phase-3 同 commit 完成）。

## Consequences

**Positive**：
- 对齐 SLF4J 用户预期，减少静默 bug 风险
- 显式 Throwable 参数（中间位）使 IDE 自动补全更明确
- 与 Logback / Log4j 2 业界惯例一致

**Negative**：
- LogUtil 公开 API 重载数增加（4 个新签名 + 4 个旧签名同共存）；deprecation 窗口期间存在两套 API
- 旧签名 @Deprecated 在 phase-4+ 删除前不能完全消除"调用方误用"风险

**Carry-forward**：
- phase-4+ 删除旧 4 个 @Deprecated 签名（when forRemoval=true 窗口结束）
- 非 facility 内部调用方（未来 beacon-* 模块）应直接用新签名

## References

1. SLF4J User Manual: *Logging Exceptions* 段. https://www.slf4j.org/faq.html#paramException
2. SLF4J `Logger.error(String, Throwable)` JavaDoc. https://www.slf4j.org/apidocs/org/slf4j/Logger.html#error-java.lang.String-java.lang.Throwable-
3. Logback Manual `Logger` API. https://logback.qos.ch/manual/architecture.html#LoggerClass
4. *Effective Java* (3rd ed., Joshua Bloch, 2018) Item 56 (Write doc comments for all exposed API elements)

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
