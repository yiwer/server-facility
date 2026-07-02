# ADR 索引

> 0001-0008 为 inherited(源:beacon 仓库,facility 专属决策);0009 起为 server-facility 本工程决策。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](0001-rp-02-jsoup-tika-optional.md) | inherited | jsoup / tika-core 声明为 Maven optional |
| [0002](0002-rp-04-async-bean-type-matching.md) | inherited | 异步线程池注入按类型匹配,不按 bean 名 |
| [0003](0003-rp-06-rfc-7807-problem-details.md) | inherited | RFC 7807 ProblemDetail 双轨(use-problem-detail 开关) |
| [0004](0004-rp-07-facility-exception-interface.md) | inherited | FacilityException 接口解耦异常层次 |
| [0005](0005-rp-08-slf4j-throwable-position.md) | inherited | LogUtil Throwable 参数对齐 SLF4J 末位 |
| [0006](0006-rp-13-cas-compare-and-exchange.md) | inherited | LogUtil 内部状态 compareAndExchange 消除 ABA |
| [0007](0007-rp-10-result-empty-factory.md) | inherited | Result.empty() 表达"成功但无值" |
| [0008](0008-rp-15-snowid-parsetimestamp-instance.md) | inherited | SnowId parseTimestamp/parseInfo 改 instance |
