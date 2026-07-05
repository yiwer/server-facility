# 日志脱敏 masking 设计(§10 roadmap 收尾组件一)

日期:2026-07-05 | 分支:feat/masking | 状态:已批准(4 决策用户在线敲定)

## 1. 背景与目标

§10 roadmap 最后两个组件之一。目标:敏感数据(密码/token/身份证/银行卡/手机号/邮箱)**不落日志**——既不写盘,也不流向 LogPostHandler 旁路(监控上报/告警/入库)。

安全基线沿用 crypto 轮确立的红线:**门面的安全性不得依赖「调用方记得做 X」**。脱敏若可被绕过、可被配错即失效,则属伪不可误用。

## 2. 关键发现(决定架构,推翻任务原始前提)

原设想「实现 log 簇现有 LogPostHandler SPI」被代码证伪,三个铁的事实:

1. `LogPostHandler.handle(LogContext)` 返回 **void**(LogPostHandler.java:47);
2. `LogContext` **不可变**(Builder 构造、仅 getter,无 setter);
3. `LogUtil` 各级别方法是**先** `logger.xxx(msg)` 写盘、**后**才 `invokePostHandler(msg, ...)`(如 LogUtil.java:117-118)。

结论:masking LogPostHandler 改不了已写盘的日志,也改不了传给其他 handler 的同一不可变 context——做出来是安全剧场。该 SPI 的定位是写盘后的上报/告警旁路,不是变换钩子。本组件**不实现** LogPostHandler。

## 3. 已敲定决策(2026-07-05,用户在线选定)

| # | 决策点 | 取值 |
|---|--------|------|
| D1 | 集成点 | **LogUtil 写前脱敏**:消息最终化后、`logger.xxx(msg)` 与 `invokePostHandler` 之前统一脱敏,写盘与旁路双覆盖 |
| D2 | 规则集 | **6 类全部内置**(见 §5),标识符部分保留、秘密全遮蔽不保长 |
| D3 | 可配性 | **固定内置 + 总开关**:`LogUtil.setMaskingEnabled(boolean)`,默认 `true`(安全默认);不加 properties/autoconfig,per-type 开关与自定义规则留 roadmap |
| D4 | 命名 | **`MaskUtil`**,包 `cn.code91.facility.masking` |

## 4. 组件结构

### 4.1 `cn.code91.facility.masking.MaskUtil`(纯静态门面)

- `public final class`,私有构造 `throw UnsupportedOperationException`(house 范式)。
- **纯变换助手例外条款**:never-throw、无 `Result`、无错误码、零依赖(仅 JDK)。
- **null 契约(须文档化)**:`null → null`、空串 → 空串、无命中 → 返回**原实例**(引用相等,零分配)。

API:

```java
public static String mask(String text)          // 全部内置规则单遍扫描
public static String maskSecrets(String text)   // 仅键值秘密 + JWT
public static String maskIdCard(String text)    // 仅身份证(含 mod11-2 校验)
public static String maskBankCard(String text)  // 仅银行卡(含 Luhn 校验)
public static String maskPhone(String text)     // 仅手机号
public static String maskEmail(String text)     // 仅邮箱
```

单类 helper 采用**扫描语义**(在任意文本中查找并遮蔽所有命中),单值输入是其特例。

### 4.2 LogUtil 集成(唯一架构改动)

- log → masking 单向依赖(masking 零依赖),ArchUnit `packages_are_cycle_free` 保持绿。
- `LogUtil` 增加:
  - `private static volatile boolean maskingEnabled = true;`
  - `public static void setMaskingEnabled(boolean enabled)` / `public static boolean isMaskingEnabled()`
  - `private static String maskIfEnabled(String msg)` → 开启时委托 `MaskUtil.mask(msg)`
- **全部 9 个公共发射点**(trace/debug/info/warn×3/error×3)在消息最终化后(`formatMessage` 之后;`(String, Throwable)` 重载的裸 msg 亦同)调用 `maskIfEnabled`,再传给 `logger.xxx` 与 `invokePostHandler`——两个下游收到同一脱敏后字符串。
- 脱敏在级别 gate(`isXxxEnabled`)之后执行:被禁级别零成本。
- 零新 autoconfig、零新 bean、零新依赖、零错误码;自动装配保持 **11**。

## 5. 内置规则(v1 固定)

实现为**单个预编译合并 Pattern**(命名组 alternation)+ 单遍 `Matcher` 循环 + 按命中组分派遮蔽函数。alternation 顺序(Java 正则同起点先列者胜):

| 序 | 规则 | 匹配 | 遮蔽 | 校验 |
|----|------|------|------|------|
| 1 | SECRET(键值秘密) | key ∈ {password, passwd, pwd, token, access[-_]?token, secret, api[-_]?key, authorization}(大小写不敏感,可带引号),分隔符 `=` 或 `:`,值为带/不带引号的 token;`Authorization` 值支持 `Bearer/Basic <token>` 整体 | 值内容 → 固定 `******`(**不保长**——长度本身是秘密信息);保留 key、分隔符、引号结构(JSON 形态 `"password":"******"`) | — |
| 2 | JWT(裸 token) | `eyJ` 开头的三段 base64url(`eyJx.y.z` 形) | 整体 → `******` | — |
| 3 | IDCARD(身份证 18 位) | `(?<!\d)\d{17}[\dXx](?!\d)`(前后非数字边界) | 前 6 + `********` + 后 4(保长) | **ISO 7064 mod 11-2** 校验位通过才遮 |
| 4 | BANKCARD(银行卡) | `(?<!\d)\d{15,19}(?!\d)` | 仅留后 4,前段逐位 `*`(保长) | **Luhn** 通过才遮 |
| 5 | EMAIL | 常规形态 `local@domain.tld` | local 首字符 + `***` + `@` + 完整域名 | — |
| 6 | PHONE(大陆手机号) | `(?<!\d)1[3-9]\d{9}(?!\d)` | 前 3 + `****` + 后 4(`138****5678`) | — |

### 5.1 设计理由(ADR-0020 展开)

- **校验位驱动的误伤抑制**:后端日志充斥雪花 ID(18-19 位)、epoch 毫秒(13 位)等长数字。真实证/卡号**定义上必过** mod11-2/Luhn(校验加不出漏遮);随机数字串仅 ~1/11、~1/10 概率误过——误伤压到十分之一量级。BANKCARD 下限取 15(排除 13 位 epoch 毫秒这一最普遍形态;13/14 位卡号在国内场景近绝迹)。
- **IDCARD 先于 BANKCARD**:18 位纯数字两者皆可命中,先证后卡;18 位过不了 mod11-2 时回落 BANKCARD 试 Luhn,双不过则不遮(雪花 ID 大概率完整保留)。
- **EMAIL 先于 PHONE**:local 恰为手机号形态的邮箱(`13800138000@qq.com`)按邮箱语义整体遮蔽为 `1***@qq.com`,而非割裂成手机号 + 残缺域名;纯手机号不含 `@`,EMAIL 不命中自然回落 PHONE。
- **秘密类不保长、标识符类保长**:秘密的长度是信息(口令位数);标识符部分保留供排障对账(尾 4 位核对)。
- **幂等**:遮蔽产物含 `*`,不再命中任何规则 → `mask(mask(x)) == mask(x)`(锁定测试)。
- **误遮优于漏遮**:残余误伤(如恰过 Luhn 的 16 位数)只损可读性不泄数据;总开关是逃生舱。

### 5.2 诚实局限(v1 不覆盖,文档 + ADR 记录)

- `Throwable` message / stack trace 不脱敏(`logger.warn(msg, t)` 的 `t` 部分原样输出——重写异常对象不可行);
- 绕过 LogUtil 直接用 slf4j 的日志不覆盖(含本工程内部少量直连 slf4j 的类);
- 15 位老身份证无专属规则(纯数字 15 位若恰过 Luhn 会被 BANKCARD 附带遮蔽,非专属样式);带分隔符卡号(`6222 0202 ...`)、`+86` 前缀手机号不识别;
- 姓名/地址/IP 不做(正则不可靠 / 运维排障需要 IP)。

## 6. 性能

- Pattern 类加载时预编译一次;每条日志单遍扫描;无命中返回原串实例(除 Matcher 外零分配);
- 校验函数仅对正则已命中的候选执行(每候选 ≤ 数十次整数运算);
- 结论:对日志热路径可接受;禁用级别零成本;极端场景可 `setMaskingEnabled(false)`。

## 7. 测试策略(TDD,由简到繁)

1. **MaskUtilTest**:
   - 每规则正例(真实校验位通过的构造样本)/ 负例(校验位不过的雪花 ID/epoch 毫秒不遮、12 位订单号不遮、长数字串内部不误配);
   - 键值秘密的 k=v / k: v / JSON 引号 / Bearer 形态;JWT;邮箱短 local;
   - null → null、空串、无命中引用相等(`assertSame`)、幂等 `mask(mask(x))`;
   - 组合消息(一条含多类敏感项)全遮蔽。
2. **LogUtil 集成测试**(logback `ListAppender` 捕获真实输出):
   - 断言写盘消息已脱敏;注册 LogPostHandler 断言 `LogContext.getMessage()` 亦已脱敏;
   - `setMaskingEnabled(false)` 时原样输出;
   - **毒化纪律**:凡动开关的测试 `@AfterEach` 复位 `true`(对齐 SpringContextHolderTestSupport.reset 惯例)。
3. ArchUnit 4/4 保持绿;覆盖 gate line/instr ≥0.88、branch ≥0.75,新类目标 100%。

## 8. 文档交付

- `masking/package-info.java`(新包);
- **ADR-0020**:集成点选型(LogPostHandler 证伪证据链)+ 默认开启(安全默认)+ 规则清单与遮蔽策略 + 校验位误伤抑制 + 性能 + 诚实局限;
- README(特性表 + 开关)/ USAGE(用法 + 局限须知)/ DESIGN(组件计数)增补;自动装配计数 11 不变;
- 全部计数以真实 `mvn -o clean verify` 输出回填。

## 9. 验收

- 全量 `mvn -o clean verify` 绿(基线 1048 + 新增);coverage gate met;`dependency:analyze` 零问题;ArchTest 4/4;
- 终审锚点:doc-truth(USAGE 示例逐一核 API 签名)、默认开启不可绕过(除显式总开关)、`mask` 幂等锁定测试在。
