> **inherited-from**: beacon ADR-0004(原仓库 docs/adr/0004-rp-07-facility-exception-interface.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0004: RP-07 FacilityException interface

## Status

Accepted

日期：2026-05-21

## Context

- 评审来源：`docs/facility/REVIEW.md` §7 RP-07 + §3.3 评审发现 + §4.2 评审发现 + §6.D.4 `web/exception/`
- beacon 现状：
  - `BusinessException` 与 `SystemException` 字段、方法签名、实现逻辑一一镜像对应（phase-1 评审 C12 候选已确认），但无公共父类或接口
  - `AbstractGlobalExceptionHandler` 只有 `handleBusinessException`（line 98，phase-2 末状态），**无专属 SystemException handler**——SystemException 走 `handleException(Exception)` 兜底（per phase-3 plan R1 实测发现）
  - 扩展时若想统一处理两类异常（如均映射到 RFC 7807 ProblemDetail），需重复维护两条 handler 路径
- 行业现状：
  - Effective Java 第 72 条：Favor the use of standard exceptions — 用统一抽象（interface 或抽象父类）减少重复
  - Effective Java 第 70 条：For recoverable conditions use checked exceptions, for programming errors use runtime exceptions — `BusinessException` (recoverable / 业务) vs `SystemException` (programming-related / 系统) 语义区别有价值
  - Robert C. Martin SOLID 接口隔离原则：用 interface 抽取公共契约，不强加继承关系

## Decision

抽象 `FacilityException` interface 统一公共契约：

```java
public interface FacilityException {
    ErrorTypeInterface getErrorType();
    Object[] getArgs();
    int getCode();
    String getFormattedMessage();
    WrappedError toWrappedError();
}
```

`BusinessException` / `SystemException` 各 `implements FacilityException`（不抽抽象父类，避免单继承约束 —— consumer 可继续按 RuntimeException 子类型 catch / 重抛）。

`AbstractGlobalExceptionHandler` 新增统一 handler：
```java
@ExceptionHandler({BusinessException.class, SystemException.class})
public Object handleFacilityException(FacilityException e, WebRequest request) {
    // 统一逻辑：translate + log + buildResponse
}
```

旧 `handleBusinessException`（无专属 SystemException handler 故无需处理后者）：去掉 `@ExceptionHandler` 注解 + 标 `@Deprecated(since = "phase-3", forRemoval = true)`，delegate 到 `handleFacilityException`。phase-4+ 删除方法。

**关键设计选择**：
- 用 interface 而非抽象父类：保留 BusinessException / SystemException 各自的单继承空间（已 extends RuntimeException）
- 统一 handler 返回类型用 `Object` 而非 `BaseResponse<Void>`：为 T5 RP-06 ProblemDetail 双轨返回类型预留（useProblemDetail=true 时返回 `ProblemDetail` 类型）

## Consequences

**Positive**：
- 消除 BusinessException / SystemException handler 路径重复
- 提供 stable interface 供 phase-4+ 在新异常类型加入时复用（只需 `implements FacilityException`）
- 与 Spring Web ExceptionHandler 注解的"多类型注册" `@ExceptionHandler({A.class, B.class})` 用法对齐
- 不破坏现有 catch / 重抛代码（两类异常仍 extends RuntimeException）

**Negative**：
- 旧 `handleBusinessException` 标 @Deprecated 但保留方法体（delegate），增加一行间接调用（性能可忽略）
- 引入新 interface 增加一个 facility 内部专有名词（`FacilityException`），但与 phase-1 收录纪律一致：interface 是 facade 接触面，使用方应只接触 `FacilityException` 而不应直接 import BusinessException / SystemException 实现类

**Carry-forward**：
- phase-4+ 删除 `handleBusinessException` 方法（when forRemoval=true deprecation 窗口结束）
- phase-N 引入新 facility 异常时（如 `ConfigurationException` 或 `IntegrationException`），直接 implements FacilityException，无需改 handler

## References

1. *Effective Java* (3rd ed., Joshua Bloch, 2018) Item 70 (Use checked exceptions for recoverable conditions and runtime exceptions for programming errors)
2. *Effective Java* (3rd ed., Joshua Bloch, 2018) Item 72 (Favor the use of standard exceptions)
3. *Clean Architecture: A Craftsman's Guide to Software Structure and Design* (Robert C. Martin, 2017) Chapter 10 (Interface Segregation Principle)
4. Spring Framework 6 `@ExceptionHandler` JavaDoc (multi-type registration). https://docs.spring.io/spring-framework/docs/6.x/javadoc-api/org/springframework/web/bind/annotation/ExceptionHandler.html

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
