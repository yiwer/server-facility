# ADR-0022: LogUtil 级别门控基于调用方 logger + StackWalker 惰性解析

> 2026-10-04：部分由 [ADR0049](0049-application-owned-observability.md) 替代。新路径直接使用标准SLF4J，不经过LogUtil二次分发；历史兼容LogUtil的门控决定保留。

- **状态**:Accepted(2026-07-05)
- **源起**:全库评审 F1(P0);P2 轮以来的「DEFAULT_LOGGER 门控 caveat」升格为修复

## 背景

LogUtil 全部 9 个公共方法首行以 `DEFAULT_LOGGER.isXxxEnabled()` 早退预检。
`DEFAULT_LOGGER` 绑定 `cn.code91.facility.log.LogUtil` 自身 logger(继承 root):
root=INFO 而业务配 `logging.level.com.myapp=DEBUG` 时,业务包经 LogUtil 的 DEBUG
调用在第一道门被短路——per-package 日志级别配置对 LogUtil 通道完全无效。
预检的本意是省去禁用级别下的调用方解析成本(当时用
`Thread.currentThread().getStackTrace()` 全栈快照,成本高)。

## 决策

1. **删除 DEFAULT_LOGGER 预检**,级别门控完全基于调用方 logger——per-package 配置生效,
   语义与直接持有 `LoggerFactory.getLogger(自身类)` 一致。DEFAULT_LOGGER 保留,仅作
   post handler 失败时的兜底错误日志。
2. **getCallerClassName 换 StackWalker**(`getInstance(RETAIN_CLASS_REFERENCE)` 静态共享
   实例):惰性遍历只实体化前几帧(LogUtil 帧 + 首个外部帧),以 Class 引用比较跳过自身帧,
   抵消"每次调用都解析 caller"的热路径成本;反射帧默认隐藏,反射调用方解析更准。
3. 性能不做测试断言(微基准不进单测);选型理由记于此与方法 javadoc。

## 后果

- 每次调用(含禁用级别)都解析调用方——成本从"全栈快照"降为"惰性 2~3 帧",
  换来 per-package 级别语义正确;
- 锁定测试:root=WARN + 调用方 DEBUG/TRACE → 事件必须产出(LogUtilTest 两向);
- 类 javadoc「先检查日志级别再获取调用者信息」的旧声称随之删除(doc-truth)。
