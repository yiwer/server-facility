# ADR-0029: Explicit request origin and scoped compatibility context

## Status

Accepted.

日期：2026-10-04。部分替代 ADR-0014 将所有代理头视为可用 IP 的部署假设，保留其限流 SPI 和算法理由。

## Context

请求工具无条件读取 XFF，trace 覆盖宿主 MDC 后删除，SessionUser 只在 MVC 完成时清除。短路和异步派发可能绕过这些边界。PRD v0.2 的 D06 与票06批准公共 Servlet/HTTP、IP policy 和兼容 holder seam。

## Decision

- 默认请求 IP 只取数字形式的连接 peer。只有显式可信 CIDR 才采用单个 X-Forwarded-For 头，从右向左经过可信代理，遇到首个不可信地址停止。IP 是网络来源，不是认证身份；旧 X-Real-IP/厂商头不参与。无 DNS 解析。
- 使用最高优先级请求边界统一拥有来源快照、兼容 SessionUser 清理和 trace 作用域，内部复用 TraceIdFilter；默认独立 trace 注册禁用，避免重复调用。错误 +1、repeatable +2、capture +3 顺序保持。
- 身份只适配宿主 Servlet Principal 或宿主显式设置的兼容用户。兼容 holder 的存在不证明登录；新应用使用 Spring Security 原生上下文，JWT 留票27。
- trace 是有界相关性标识，不是安全主体。有效宿主观测优先；嵌套调用恢复原 MDC。每次 REQUEST/ASYNC/ERROR 作用域结束都清除兼容身份，异步请求快照与线程状态分离。
- Spring MVC Callable 在实际执行线程安装/清理快照。DeferredResult 的任意外部生产者不隐式获得 ThreadLocal；再次派发才安装请求快照。异步完成/超时监听器不得跨线程清除其他线程状态。

### Concrete budgets and ownership

`ClientIpPolicy(List<String>).resolve(HttpServletRequest)` 是可替代的具体bean；配置 `facility.web.proxy.trusted-proxies` 默认空。最多128个数字CIDR，单XFF最多2048字符/32跳；整条非法、重复、超限回peer。IPv4完整十进制四段无前导零；IPv6无zone/括号/端口；mapped IPv6规范成IPv4并只用IPv4 CIDR。缺失/非法peer为unknown，API不做DNS。配置在构造期解析并保存，不持有可变列表。

过滤链冻结一次IP；Tomcat `org.apache.tomcat.request.forwarded` 标记防止二次解析。已由Tomcat/Spring代理组件拥有转发政策时，facility列表应为空，读取该组件给出的remoteAddr。宿主IP策略抛异常时，顶层finally仍清理holder，ERROR派发以unknown来源继续统一500，避免重入同一失败解析器。

默认 `trace.accept-inbound=true` 仅维持有界correlation兼容，不表示可信身份；false关闭入站接受，仍尊重有效宿主MDC。值白名单ASCII 1–64字符，header-name合法HTTP token且≤128，mdc-key为1–128字母数字点横线下划线。有效宿主MDC优先，finally只恢复所拥有的键；配置构造时冻结。Callable/DeferredResult初始交接会捕获在内层宿主Filter建立的观测。已有worker观测优先、恢复原值后交回所有者。Callable安装阶段自身负责部分失败回滚，不依赖Spring在preProcess失败后调用postProcess。恢复状态归实际worker的ThreadLocal作用域；身份先清理，MDC恢复失败保留原异常并附加suppressed。若MDC适配器拒绝恢复，库不声称能修复宿主适配器内部数据。Servlet trace作用域遵循相同首因政策。

MVC兼容拦截器使用宿主Principal覆盖旧兼容值；无需认证的自定义域对象适配可由宿主后续MVC拦截器完成。`isLoggedIn`仅有值检查并弃用。用户对象引用不深拷贝；宿主负责不可变性。任意后台生产者/SecurityContext传播与JWT验证不属于此组件。

## Consequences

**Positive**：默认公网请求不能通过转发头伪造来源，线程复用与派发有明确作用域。

**Negative**：旧无条件 XFF 行为改变，部署必须配置可信 CIDR 或让唯一上游框架负责代理解析；任意异步生产者仍须宿主 Security/观测的原生传播支持。

**Carry-forward**：票27提供真实认证，票24复验 Boot4/Servlet6.1，票33组合候选跨票场景。

## References

1. [运行时研究](../research/2026-10-03-runtime-contracts.md)
2. [Web研究](../research/2026-10-03-web-and-agent-design.md)
3. [Java25 InetAddress.ofLiteral](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/InetAddress.html#ofLiteral(java.lang.String))
4. [Spring CallableProcessingInterceptor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/async/CallableProcessingInterceptor.html)
5. [Tomcat RemoteIpValve](https://tomcat.apache.org/tomcat-10.1-doc/api/org/apache/catalina/valves/RemoteIpValve.html)

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
