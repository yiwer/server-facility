# ADR-0011: 删除 LogUtil.setLevel,消除主源码 logback 依赖

- **状态**:Accepted(2026-07-02)
- **源起**:spec §5 第 18 行 rework ②(logback 耦合隔离)+ P2 迁移窗口

## 背景

源项目 `LogUtil.setLevel(Class, Level)` 直接引用 `ch.qos.logback.classic.LoggerContext`,
是主源码唯一的 logback 实现类触点,迫使 logback-classic 以 optional-compile 存在,
并使日志门面与具体实现绑定(换 log4j2 即断)。迁移前全仓扫描证实:
**setLevel 在 beacon 全仓库零调用**(仅定义自身)。

## 决策

1. 迁移时删除 `setLevel` 方法(YAGNI:零调用能力不背;动态改级别属日志实现的运维关注点,
   应用可用 Spring Boot actuator loggers 端点或 logback 自身配置实现);
2. logback-classic 降为 **test** scope(测试经 ListAppender 断言真实日志输出,并充当
   SLF4J provider 保证测试输出纯净);
3. ArchUnit 常驻规则 `main_code_does_not_depend_on_logback` 锁定该成果。

## 后果

- 主 classpath 零日志实现依赖,消费方自由选择 SLF4J provider——spec §6"logback 目标降 test"达成;
- 若未来确需动态改级别能力,以独立组件按 §4.3 范式接入(SPI + optional 依赖),不回填门面。
