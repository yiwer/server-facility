# ADR-0048: 宿主拥有外部 HTTP 配置与业务失败政策

## Status

Proposed，2026-10-04，票25实施中。保留ADR0018的同步RestClient选型，替代其私有builder作为推荐默认、静态服务定位器作为新业务入口及笼统网络/解析错误策略。旧公共签名先弃用、提供实际迁移消费者，不直接删除。

## Context

旧自动装配直接RestClient.builder并替换factory，绕过Boot的JSON/customizer/观测配置；静态HttpClients把不同第三方塞进同一个全局入口，失败无法表达提交后断连的未知结果。标准框架已提供builder克隆、转换器、观测和HTTP传输，不需要新远程任务DSL。

## Proposed decision

1. 注入宿主管理的RestClient.Builder，每个业务Adapter在配置前克隆；宿主builder存在时设施兼容bean也从它克隆并保留factory。缺builder的历史独立装配路径单独标注兼容政策，不进入新推荐示例。
2. 两个真实类型化Adapter由一个外部聚合Module消费；业务DTO、第三方协议、凭据、已知失败/未知结果与显式重试由该应用拥有。认证头/base URL/超时为各服务独立配置，不通过全局静态变更。
3. 运行时仅提供可复用的响应字节预算（标准ClientHttpRequestInterceptor），在消息转换器物化前限制实际body并关闭被拒绝响应；状态与必要安全头由Adapter解释，不复制远端错误body、秘密或原始URL到公共错误。
4. JDK HTTP transport及其生命周期由示例应用明确配置，默认副作用操作一次发送。显式读取重试采用协议允许的GET、有限次数和单一deadline；不得以任意Idempotency-Key头推断副作用可重做。返回流不越过拥有它的exchange作用域。
5. 用两个受控真实HTTP服务验证customizer、JSON、trace、凭据/超时隔离、泛型/204、坏JSON/截断/超限/压缩响应、取消、实际发送次数及资源回收。公开适用限制，不能把mock调用次数当wire证据。

## Primary sources

2026-10-04核读：[Boot4.1.1 RestClient](https://docs.spring.io/spring-boot/reference/io/rest-client.html)的prototype builder、clone与customizer；[Framework REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)的exchange所有权；[Framework7.0.9 JDK request源码](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-web/src/main/java/org/springframework/http/client/JdkClientHttpRequest.java)的TimeoutHandler与gzip/deflate处理。配置与budget结论还须真实消费者验证，不能仅从文档推断完成。
