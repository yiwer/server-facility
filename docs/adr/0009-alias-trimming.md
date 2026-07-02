# ADR-0009: Result/Tuple/Triple 纯别名精简

- **状态**:Accepted(2026-07-02)
- **源起**:REVIEW-2 RV2-18(wontfix-for-now)+ spec A6 重估授权

## 背景

源项目 REVIEW-2 认定 `filter`≡`ensure`、`unwrap`≡`get` 等纯别名为表面冗余(RV2-18),
当年 wontfix 理由是"无生产消费方,删除仅 churn facility 自身测试"。迁移到零消费方的新仓库后,
该理由消失,而"每个语义一个名字"的 API 收敛收益永久化。
迁移前全仓 import 扫描证实:所有别名在源码内部零使用(仅 3 处测试用例覆盖别名自身)。

## 决策

迁移时删除以下纯别名(委托实现、无语义差异):

| 类 | 删除 | 保留(语义主名) |
|---|---|---|
| `Result` | `filter(Predicate,Supplier)`、`unwrap()`、`unwrapErr()` | `ensure`、`get`、`getErr` |
| `Tuple` | `first()`、`second()`、`key()`、`value()` | `left()`、`right()` |
| `Triple` | `first()`、`second()`、`third()`、`toLeftMiddle()`、`toMiddleRight()`、`toLeftRight()` | `left()`、`middle()`、`right()`、`dropRight()`、`dropLeft()`、`dropMiddle()` |

**非别名不删**:`and`(急切)/`andThen`(惰性)、`or`/`orElseSupplier`、`match`(消费)/`fold`(映射)
语义各自独立,保留。

## 后果

- API 面收敛 13 个方法;新消费方不再面临"两个名字选哪个"。
- 自 beacon 迁移代码的消费方按上表映射改名即可(编译期报错,机械替换)。
