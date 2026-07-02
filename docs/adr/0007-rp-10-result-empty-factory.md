> **inherited-from**: beacon ADR-0007(原仓库 docs/adr/0007-rp-10-result-empty-factory.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0007: RP-10 Result.empty() 工厂方法（无值成功语义）

## Status

Accepted (phase-4, 2026-05-21)

## Context

`Result.ok(T value)` 现在允许 null（`beacon-support/beacon-facility/src/main/java/cn/hbads/beacon/facility/result/Result.java:66`），导致调用方面对一个语义歧义场景：`isOk() == true` 且 `get() == null` 时，到底是"成功且值为 null"还是"成功但无值"？两种意图无法区分。

调用方 `isOk()` 为 true 但 `get()` 返回 null，违反"成功时有值"的直觉契约。与 Rust `Option<T>` 的 `Some(value)` / `None` 区分、Java `Optional<T>` 的 `Optional.of(value)` / `Optional.empty()` 区分相比，beacon `Result<T, E>` 缺少显式表达"无值成功"的 API。

问题来源：
- phase-1 REVIEW.md C-§3.2-2（`docs/facility/REVIEW.md:354-357`）
- phase-1 worksheet §H C3 + §B/result/ F-result-2
- REVIEW.md §7 RP-10（`docs/facility/REVIEW.md:1630`）

REVIEW.md §C-§3.2-2 建议两个方向：(a) 文档化 `ok(null)` 的明确含义；(b) 新增 `Result.empty()` 工厂方法。phase-4 选 (b)。

## Decision

新增 `static <T, E> Result<T, E> empty()` 静态工厂方法，返回 `new Ok<>(null)`，表达"无值成功"语义。

实现选择：
- **简单 `new Ok<>(null)`，不引入 sentinel 单例**。理由：简单清晰；`Ok` 是 record，每次 new 开销小；GC 友好；若未来 profiling 发现热点再切单例 + `@SuppressWarnings("unchecked")` 强转。
- **不 deprecate 现 `Result.ok(null)`**。理由：现有生产 1 处调用（`web/util/ResponseUtil.java:68`）+ 测试 3 处验证 `ok(null)` 行为本身（`ResultTest.java:34, 313, 338`），全保留；调用方 opt-in 用 `empty()` 表达新语义；不引入新 deprecation lifecycle（与 phase-4 "收口" 性质契合）。
- **签名**：`static <T, E> Result<T, E> empty()`，T 为 phantom（实际 value=null）；与现 `static <E> Result<Void, E> ok()` 不冲突（`ok()` 受类型约束返回 `Result<Void, E>`，`empty()` 返回任意 `Result<T, E>`）。

测试覆盖：
- `empty()` 返回 Ok，`isOk()=true`，`get()=null`
- `empty().toOptional()` 为 `Optional.empty()`
- `empty().stream()` 为空 stream
- `empty()` 与 `ok(null)` 语义等价（toOptional / stream 行为一致）
- 支持任意 T / E 类型参数

## Consequences

- API 增量，调用方有显式 API 表达"无值成功"。
- 与 `Optional.empty()` 形状对齐，认知负担低（迁移 Optional 习惯的调用方零学习成本）。
- `ok(null)` 仍合法；新代码鼓励用 `empty()`；旧调用方按需渐进迁移。
- 不引入新业务术语（"无值成功"是 Result-style 错误处理的内部语义概念，不进 CONTEXT.md）。
- 未来 phase 可评估是否 deprecate `ok(null)` —— 但需先 grep 生产调用方变化趋势再决定。

## References

1. *Rust Programming Language — `Option<T>`* (Rust Standard Library, 2024). <https://doc.rust-lang.org/std/option/>
2. *JDK 21 `java.util.Optional`* (Oracle, 2023). <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html>
3. *Effective Java (3rd ed.) §55 Return Optionals Judiciously* (Bloch, 2018).
4. beacon `docs/facility/REVIEW.md` §3.2 + §6.A.1 + §7 RP-10 + worksheet §H C3 / §B/result/ F-result-2 (phase-1)

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
