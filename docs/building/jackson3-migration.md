# Jackson 3 应用迁移（票 23）

目标为 Boot 4.1.1 / Jackson 3.1.5；本批仍是 `codex/server-facility-next` 非发布集成，完整平台组合和两 OS 门由票 24 关闭。决策见 [ADR-0046](../adr/0046-jackson3-application-ownership.md)。

| 旧入口 | 目标入口 / 行为 |
|---|---|
| `com.fasterxml.jackson.core/databind` | `tools.jackson.core/databind`；`JsonProcessingException` → unchecked `JacksonException`；旧签名不能二进制兼容 |
| `com.fasterxml.jackson.annotation` | 保留原包、原 annotations 坐标，不混装 Jackson 2 databind/core |
| JavaTimeModule/Jdk8Module/ParameterNamesModule | databind 已内置；JsonConfig.enableJava8Support 保留为无额外模块的兼容表达，裸 builder 也支持这些类型 |
| `JsonSerializer` / `JsonDeserializer` / `SerializerProvider` | `ValueSerializer` / `ValueDeserializer` / `SerializationContext`；用户 I/O 通过 JacksonIOException 传播，程序 RuntimeException 不转换成输入错误 |
| `Module` | `JacksonModule`；自定义时间格式使用 databind 的 ext.javatime 类型 |
| `JsonConfig.customize(mapper -> ...)` | 删除；改 `customizeBuilder(builder -> ...)`，build 返回不可变 JsonMapper |
| `Jackson2ObjectMapperBuilderCustomizer` | Boot `JsonMapperBuilderCustomizer`；自有 bean 声明为 JsonMapper；parser 配置用 `JsonFactoryBuilderCustomizer` |
| Spring 启动后静态 JsonUtil 取得宿主 mapper | 改为注入 Jsons；应用 JsonsRegistry 独立，默认引用同一个应用 Jsons；静态入口是 standalone 预设，不自动取得 Spring 政策 |
| 原始异常/payload 出现在 WrappedError 和日志 | 预期失败只含稳定 error type；args 为空、exception 为 null，不记录输入、输出对象、原异常或 cause |
| getter/用户 codec bug 被吞成 Result Err | RuntimeException 和 Error 传播；Jackson 包装用户 RuntimeException 时恢复原故障，保留宿主 WRAP_EXCEPTIONS 配置。Jackson MismatchedInputException 是数据错误通道，InvalidDefinitionException 是配置错误 |
| InputStream 无参/≤0 无界 | 默认不注册；显式预算必须正数，无参注解入口固定 1 MiB；根流所有权由 mapper 控制，字段输入由 serializer 关闭，返回字段流由调用方关闭 |
| canonical 用于“签名” | 仅属性/Map 排序及既有空值预设，不承诺 RFC 8785 或密码签名规范化 |
| `server.error.*` | Boot 4 的 `spring.web.error.*`；Facility fallback 和真实 ERROR 派发夹具同步迁移 |

Jackson 3 默认拒绝尾随第二个 JSON 值，空 bean 的默认序列化行为也有变化；应用需要旧政策时使用标准 builder 明确选择对应 feature，库不使用整包 Jackson 2 defaults 开关。`JsonConfig.strict()` 显式保留未知属性拒绝，Java 时间默认是 databind 内置能力。

字段 Base64 在解码前按编码长度检查，将临时 byte[] 限制为 N+2 固定分组舍入开销，成功结果严格 ≤N。serializer 最多读 N+1 并关闭源；无自动无界注册。JSON 文本的 document/string budget 由应用的 JsonFactory 在 parser 形成完整字符串前生效；示例见 [USAGE](../USAGE.md#jsonjsonutil)，真实流截断证据来自 JsonParserBudgetTest。Jackson 以输入块和缓冲增长为检查点，这些限制不是逐字节网络配额。

Spring 7 标准 `JacksonJsonHttpMessageConverter` 配置 ProblemDetail mixin；直接裸 JsonMapper 不包含这个 Web 协议配置。无宿主 mapper 的兼容 fallback 使用标准 converter 的 mapper，宿主提供 mapper 时保持原实例。HTTP 错误策略显式设置 `type=about:blank`，保留票 04 字面协议，不放宽状态、扩展属性和秘密检查。

票 21 的全部金样文件保留不变。独立消费者只对等价 Unicode escape 和对象属性顺序采用解析后比较；数值/字符串类型、日期/时区、null/Optional、未知字段、尾随策略和真实 HTTP 状态仍分别断言。消费者将历史宽松尾随政策改成显式宿主配置，custom 应用仍拒绝；两应用同时运行、交错请求、关闭重建后继续比较同一组字面样本。[来源及执行](../../verification/json-consumer/README.md)。

官方依据：[Jackson 3 发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)、[Boot factory customizer](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/JsonFactoryBuilderCustomizer.html)、[Spring 7 JSON converter](https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/http/converter/json/JacksonJsonHttpMessageConverter.html)、[Boot 4 属性迁移](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Configuration-Changelog)。
