# ADR0049: 应用拥有本地化、日志与观测

- 状态：Proposed（2026-10-04；票26）
- 部分替代：0010的静态本地化推荐、0015的默认聚合装配、0027/0029的自造trace默认协议；兼容公开入口保留。

宿主MessageSource和Boot spring.messages必须拥有最高配置权。设施bundle按应用明确的basename顺序接合，不扫描并委托所有MessageSource。缺宿主时允许仅设施bundle兜底。新代码构造注入MessageSource；核心error仍纯数据。

新路径走标准SLF4J/Micrometer；不通过静态LogUtil二次分发，不把通用日志当审计。默认诊断只记录有限白名单元数据，不输出Throwable消息/SQL/请求体/token；错误日志backend RuntimeException不能掩盖原业务失败。脱敏工具只作纯函数，不能识别所有秘密。

标准观测上下文由宿主Tracing/ObservationRegistry与标准scope/task decorator管理。旧TraceIdFilter与LogUtil等保持公开兼容入口和明确迁移。逐项行为和具体迁移在TDD确认后补充。

依据：Spring Boot 4.1.1 internationalization / actuator tracing 官方文档；本票不建设通用秘密检测器、审计平台或第二套追踪协议。
