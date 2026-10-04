# ADR-0055: Explicit cookie scope and HTML fragment policy

## Status

Accepted

日期：2026-10-04。票32；保留ADR-0001中jsoup为optional依赖的决定。

## Context

旧CookieUtil写入没有明确SameSite政策，删除无法描述完整domain/path/flags作用域，重复请求cookie会在首值与末值之间产生歧义。Servlet Cookie不足以为宿主提供不可变的完整响应政策，Spring ResponseCookie已经提供相应标准描述与基本语法检查。HTML清洗仍需由业务显式选择；jsoup升级必须验证实际输出变化，不能把HTML片段清洗扩张为任意输出上下文的安全保证。

## Decision

CookieUtil新增接收Spring ResponseCookie的写入和删除入口。删除使用原policy的mutate构造空值、Max-Age=0，保留scope与flags。旧签名保留，默认host-only、path=/、SameSite=Lax、HttpOnly和Secure；本机HTTP须显式关闭Secure。写入前检查SameSite、Secure/Partitioned/prefix组合、path、整秒有效期和4096字节ASCII头预算；基本name/value/domain/path语法继续由Spring处理。响应追加Set-Cookie，不覆盖已有头，不自动编码输入；重复目标名或全量读取中的重复名拒绝。

XssUtil维持显式HTML body fragment清洗，jsoup升级为1.23.2并保持optional。所有解析入口在解析前限制262144个UTF-16单元，指定policy即使输入为空也必须有效。自定义Safelist由调用者拥有，不得并发变动。16项固定样本对比旧新版，hostless HTTP href与iframe fallback文本的两项改变公开登记；安全片段及其余14项保持预期输出。

不新增全局输入改写filter、认证提供者、HTML沙箱或自造cookie/parser。容器总请求预算、cookie域名信任、浏览器存储与当前partition选择、最终输出上下文encoding属于宿主/浏览器边界。

## Consequences

**Positive**：完整Cookie作用域可以复用与删除，非法政策在响应变动前拒绝；固定样本和普通jar资源消费者提供可重复的升级证据。HTML原业务值保留，是否清洗由业务明确决定。

**Negative**：Cookie写入消费者现在需要可选Spring Web依赖；旧代码中同名cookie歧义、任意SameSite、超限输入和无效policy将明确失败。删除协议值由null变为空字符串，旧调用者应核对迁移表。400天是库发送政策，不保证浏览器保存时长；Partitioned删除仅作用于当前partition。

**Carry-forward**：最终双平台集成候选及独立规格/标准审查由票33完成。有限资源测试是特定64MiB进程的观测，不承诺任意输入或宿主配置的CPU/GC最坏上界。

## References

1. [Spring Framework 7.0.9 ResponseCookie](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-web/src/main/java/org/springframework/http/ResponseCookie.java).
2. [HTTP State Management Mechanism draft-ietf-httpbis-rfc6265bis-22](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-22)，Internet-Draft，非已发布RFC。
3. [Partitioned Cookies draft](https://datatracker.ietf.org/doc/html/draft-cutler-httpbis-partitioned-cookies-01#section-2.4)，删除按当前partition生效。
4. [jsoup 1.23.2 release](https://jsoup.org/news/release-1.23.2)及[Safelist sanitizer](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer).
5. [OWASP XSS Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html).
6. [迁移政策](../building/cookie-html-policy.md)、[票32验证记录](../verification/ticket-32-cookie-html.md)。
