# Cookie 与显式 HTML 片段清洗

票 32 实施记录，当前契约仍在验证。Cookie 不承载本模板的 JWT 认证；模板继续使用原生 Bearer token。应用若另行采用 Cookie 认证，需自行设计 CSRF、登录状态和服务端失效策略。

## 写入和删除

默认简化入口面向 HTTPS：host-only、Path=/、HttpOnly、Secure、SameSite=Lax。本机 HTTP 示例明确传 `secure=false`。跨站业务显式采用 SameSite=None 且 Secure；SameSite 不是完整的 CSRF 防护。

完整政策直接使用 Spring 的不可变 `ResponseCookie`，不再增加同义的自定义 builder。Cookie写入现在需要Servlet API和Spring Web（Boot MVC消费者已有）；仅用Servlet API的旧消费者需显式补充`spring-web`，此optional运行依赖变化不能只由方法签名兼容推断：

```java
ResponseCookie preference = ResponseCookie.from("theme", "dark")
    .path("/portal").domain("example.com").maxAge(Duration.ofDays(30))
    .secure(true).httpOnly(true).sameSite("Lax").build();
CookieUtil.addCookie(response, preference);
CookieUtil.removeCookie(response, preference);
```

删除发送同名、同 domain/path 的空值及 Max-Age=0，保留安全 flags 和 SameSite。它只请求浏览器删除匹配作用域的 Cookie，不证明远端浏览器已经执行；服务端状态撤销必须另行完成。旧 remove(name,path) 只删除 host-only 的 HTTPS/Lax Cookie，带 Domain 或不同 flags 的宿主使用完整入口。

Partitioned 删除只作用于当前顶层站点的浏览器分区上下文；不会清除其他分区。Secure 是发送政策，不根据不可信转发头自动降级。删除没有办法自动发现以前部署过的所有 path/domain，迁移需由应用明确枚举。

发送前要求显式以 `/` 开头的 path、SameSite 缺省或 Lax/Strict/None（值大小写不敏感），None/Partitioned/`__Secure-`/`__Host-` 必须 Secure。`__Host-` 额外要求 Path=/ 且无 Domain，前缀匹配大小写不敏感，普通名字匹配仍区分大小写。Domain 字符与值的基础语法由 Spring 验证；设施不掌握请求的可信域名或公共后缀列表，不能替宿主批准任意 Domain。

maxAge 为整数秒：-1 表示不发送持久有效期，0 为立即过期，正数最大 400 天。这是本入口的明确有限政策，浏览器仍可更早删除。旧整数 -2 不再静默转成会话 Cookie；ResponseCookie 的 long builder 本身可能已经归一负数，本入口只能检验成品的 Duration。小数 Duration 和溢出/超限均拒绝，禁止静默截断。

Set-Cookie 的**字段值**最大 4096 ASCII 字节（不含 `Set-Cookie: `）；先检查各组件长度和有限有效期，再生成一次标准 header 并核对最终长度。超限拒绝不截断，不替换已有响应头。设施验证异常不回显原始字段或保留cause；宿主在调用前构造ResponseCookie时发生的builder异常仍由宿主处理，输出容器自身的I/O/状态异常不被吞掉。此单头预算不保证所有浏览器都接受，也不限制宿主主动发出的总 Cookie 数；请求解析总字节/数量预算由 Servlet 容器管理。

Cookie 值是 ASCII 协议值，不对 Unicode 自动 URL 编码或解码，也不裁剪、替换控制字符。需要中文业务值时由宿主选择并记录编码协议。请求中同名 Cookie 的原始头不包含 domain/path，无法据此认定哪一个是可信身份；读取入口拒绝同名歧义。应用应先消除多作用域同名策略。

`getCookie(name)` 仅拒绝目标名字重复，无关名字重复不会阻断指定读取；`getAllCookies` 拒绝任意重复。不重写 raw header，不自行实现第二套 Cookie parser，仅使用容器暴露的 Cookie。null value 返回 Optional.empty，空值返回 Optional.of("")；全量 Map 保留 null/空值且不可变。null request/name 必须拒绝。旧 getAll 最后值覆盖、getCookie 首值胜出的不一致政策明确退出。

## HTML 是一个明确的内容类型

仅对应用明确支持的富文本字段调用 `XssUtil.clean`。订单号、密码、SQL 文本、普通备注等原始字符串不经过自动全局清洗；如要保留原始编辑内容，应用应将原文与清洗后 HTML 分开存储/命名。BASIC/BASIC_WITH_IMAGES/RELAXED/NONE 沿用 jsoup Safelist 预设，宿主自定义 Safelist 时自行负责放行协议和属性。

输出是 HTML body 片段，不是 JavaScript、CSS、JSON、URL 或 HTML 属性上下文中的安全字符串。嵌入这些位置必须使用相应编码/序列化 API；清洗之后再拼接未处理片段会失去原有保证。`isSafe` 仅表示输入满足所选 Safelist 校验，不证明授权、安全 URL 目标、可信内容或任意渲染上下文安全。清洗不请求外部图片或链接。

所有 clean 重载和 isSafe 都在解析前限定输入为最多 262144 个 UTF-16 单元；超过上限抛 IllegalArgumentException，不返回截断内容。这个预算是已形成 String 的解析成本边界，HTTP 解码前字节限制仍归宿主。parser 使用所选 jsoup 版本的默认有限栈深度；不再自行解析 HTML。null HTML 的 clean 仍返回 null，空输入仍返回空；对应 isSafe 返回 true 仅表示没有需要校验的内容。level/Safelist 参数始终必须存在。调用期间不要并发修改自定义 Safelist。

旧公共方法名和签名保留；不再使用“对编码绕过免疫”之类无边界承诺。升级的固定输入/输出、两个已发现差异及重放说明见 [HTML消费者](../../verification/html-consumer/README.md)。jsoup 仍为 optional，显式调用者必须声明1.23.2；缺依赖不会回退到原文放行。

## 核对的一手依据

- [Spring Framework 7.0.9 ResponseCookie 源码](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-web/src/main/java/org/springframework/http/ResponseCookie.java)：标准 builder、scope 与 mutate；基础字符校验不等于 SameSite/前缀组合政策，设施在发送前补足自己的边界。
- [RFC6265bis draft-22](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-22)：Cookie scope、同名 Cookie、SameSite 和前缀处理。此来源仍是 Internet-Draft，不把它写成已发布 RFC；400 天是浏览器建议保留上限，不是服务端持续存储承诺。
- [jsoup 1.23.2 发行说明](https://jsoup.org/news/release-1.23.2)：2026-08-26 稳定版本；解析器修复包含深嵌套、畸形 SVG/MathML 和无 host 的 HTTP URL。旧 1.18.3 的比较样本随验证记录保存。
- [jsoup Safelist 清洗指南](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer) 与 [OWASP XSS prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)：显式 HTML 清洗与按最终输出上下文编码承担不同责任。
