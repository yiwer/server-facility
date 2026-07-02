# ADR-0010: 错误消息边界本地化(C1 断环)

- **状态**:Accepted(2026-07-02)
- **源起**:spec §4.4 C1——包级循环 `error → locale → context → error`

## 背景

源项目 `ErrorTypeInterface.format()` 内联调用 `LocaleUtil.translateMessageWithFallback(...)`,
使核心错误类型编译期依赖 locale,进而经 `SpringContextHolder` 与 Spring 运行时纠缠,
构成三包循环(证据:ErrorTypeInterface.java:3 / LocaleUtil.java:4 / SpringContextHolder.java:16-18)。
循环导致 error/locale/context 三簇无法独立理解、测试与迁移。

## 决策

1. **error 包纯数据化**:`ErrorTypeInterface` 只承载 `code/messageKey/defaultMessage`;
   `format()` 只渲染默认模板,语义精确镜像旧 `LocaleUtil.translateMessageWithFallback` 的 MessageSource 未命中路径:
   - 无参 → 返回 `getDefaultMessage()` 原文(不经 MessageFormat,单引号不被吞);
   - 有参 → `MessageFormat.format(defaultMessage, args)`;
   - 模板 null → 返回 `getMessageKey()`;
   - `IllegalArgumentException` → `formatFallback`(原降级逻辑不变)。
2. **i18n 解析移交展示边界**:locale 包保留 `translateMessageWithFallback` 能力;
   P5 提供针对 `ErrorTypeInterface` 的便捷解析入口并在 P6 异常处理器接线,
   等价旧行为:`LocaleUtil.translateMessageWithFallback(et.getMessageKey(), args, et.getDefaultMessage(), locale)`。
3. 依赖方向变为锥形单向:`error(纯) ← context ← locale`;ArchUnit 规则
   `error_package_depends_only_on_jdk` 常驻守护。

## 行为影响

- **无 Spring / MessageSource 未命中场景**:行为完全不变(旧实现本就落入 renderFallback 路径),
  37 个迁移测试断言零改动通过是直接证据。
- **Spring + i18n 命中场景**:`format()` 不再隐式返回本地化消息——需要本地化的调用点
  (源项目中实际只有 web 异常出口)改为在边界显式解析(P6 落地)。

## 备选(否决)

- 保留循环:三簇永久绑定,分簇迁移拓扑不可行。
- error 内嵌 ResourceBundle 解析:重新发明 MessageSource,且仍需 Locale 上下文,复杂度高于边界解析。
