# ADR-0012: LogUtil.formatMessage 委托 SLF4J MessageFormatter(RV2-17 翻案)

- **状态**:Accepted(2026-07-02)
- **源起**:REVIEW-2 RV2-17(wontfix-for-now)+ spec A6 重估授权

## 背景

源项目 `formatMessage` 手写 `{}` 顺序替换:不支持 `\\{}` 转义、数组参数输出 JVM 默认
toString(如 `[I@1a2b3c`)、null 模板遇参数抛 NPE——与"SLF4J 风格"名实不符(RV2-17)。
当年 wontfix 理由是"改日志输出格式,风险>收益"——针对已有消费方的日志观感;
新项目零消费方,理由消失。

## 决策

`formatMessage` 委托 `org.slf4j.helpers.MessageFormatter.arrayFormat(template, args, null).getMessage()`
(slf4j-api 自带,零新依赖)。**三参变体传 null throwable**:禁用双参变体的"尾参 Throwable 自动剥离",
所有参数(含 Throwable,经 toString)按占位符填充,与旧手写语义一致;stack trace 输出走显式重载位(ADR-0005)。
行为差异(均为修正而非破坏):

| 场景 | 旧(手写) | 新(MessageFormatter) |
|---|---|---|
| 基础顺序替换 / null 参数 / 参数多于占位符 / 占位符多于参数 | 一致 | 一致(8 个迁移期用例零改动通过) |
| `\\{}` 转义 | 当普通占位符消耗参数 | 输出字面 `{}` |
| 数组参数 | `[I@hash` | 深度格式化 `[1, 2, 3]` |
| null 模板 + 参数 | NPE | 返回 null,不抛 |

## 后果

- LogUtil 占位符渲染语义与 SLF4J 生态一致,"SLF4J 风格"名实相符;
- 守卫用例 `info_trailingThrowableArg_formattedIntoPlaceholder` 钉住"尾参 Throwable 填入占位符
  而非被静默剥离"的语义,防止未来误改回双参变体。
