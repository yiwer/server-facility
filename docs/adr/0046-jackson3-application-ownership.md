# ADR-0046: Jackson 3 的应用所有权、不可变配置和安全错误边界

## Status

Accepted，2026-10-04。票 23；收缩 ADR-0044 决策 1–3 的旧平台兼容阶段，落实 ADR-0045 交接的 Jackson 编译迁移。INDEX 由集成者登记。

## Context

Jackson 3 的 mapper 在 builder 完成后不可变，Java 时间、Optional 和参数名称支持已合入 databind；annotations 仍使用原坐标。原有 Spring 自动配置会改写进程级 registry，原 Jsons 在预期失败时记录 payload 和含源内容的异常，并可能把用户 getter/serializer 的程序故障当作输入失败。旧字段流的无参和非正数预算允许无界读取。

## Decision

1. 公开 core/databind 类型迁移到 `tools.jackson`，annotations 保留 `com.fasterxml.jackson.annotation`。`JsonConfig.build()` 发布 `JsonMapper`；删除 build 后的 `customize(Consumer<ObjectMapper>)`，使用已在票 21 引入的 `customizeBuilder(Consumer<JsonMapper.Builder>)`。Java 8 支持方法保留为兼容表达，基础能力由 databind 提供，不再装入旧模块。宿主使用 Boot `JsonMapperBuilderCustomizer` 和 `JsonFactoryBuilderCustomizer`，不增加 JSON DSL，也不突变已发布 mapper。
2. 自动配置只复用本应用 mapper，`JsonsRegistry` bean 是应用自己的实例，默认入口复用应用 `Jsons`（含用户替换的 bean）。旧 `JsonUtil` 静态入口保留独立、显式的 standalone registry；Spring 启停不会修改它。新应用注入 `Jsons`；静态入口和其余内置 namespace 的预设不代表宿主 HTTP 政策。
3. Jsons 只把预期 JSON 输入、约束和 I/O 故障转成 Result 错误；不在该边界记录输入、输出对象、原始异常或 cause，也不把它们放进 WrappedError。程序错误和无效 mapper 定义传播给调用方；用真实用户 serializer/deserializer 验证 Jackson 包装异常时的行为。不会为方便 catch 改写宿主的 WRAP_EXCEPTIONS 政策。
4. 字段流能力默认不注册。显式构造参数必须大于零；无参注解入口使用固定 1 MiB 上限，不能再通过 0/负数请求无限制。serializer 拥有并关闭字段源流，读取至多 N+1 个字节；deserializer 返回流由调用方关闭。Base64 在解码前检查编码长度，临时数组最多 N+2，成功结果严格不超过 N。根 JSON 流所有权仍遵从宿主 AUTO_CLOSE_SOURCE/TARGET。
5. 字段字节预算不能代替 parser 文档/字符串预算。应用通过 Boot 的 JsonFactoryBuilderCustomizer 在解析器创建前配置正数 maxDocumentLength/maxStringLength；独立使用时向 `JsonMapper.builder(JsonFactory)` 提供受限 factory。文档限制按输入块检查，字符串限制按增长缓冲区检查，允许 Jackson 文档记载的固定缓冲探测开销，不承诺逐字节硬截断。库不修改进程级 StreamReadConstraints 默认值。
6. canonical 仅表示排序和既有空值预设，不是 RFC 8785 或密码签名算法。票 21 字面金样继续保留，JSON 值、Unicode、数值类型及日期政策是协议依据；只允许明确说明的等价 escape 差异，不能用本实现写后读替代独立样本。

## Consequences

**Positive**：两个应用的 mapper、registry 和 HTTP 政策可独立关闭重建；预期 JSON 错误不传播秘密；配置和流所有权可通过公共入口验证。

**Negative**：Jackson 公共签名、可变回调和旧无界字段预算具有源/二进制迁移成本。静态 JsonUtil 不再自动取得 Spring 政策；需要迁移到注入入口。预期错误的 WrappedError 不再携带原始 cause，排错应使用不含 payload 的调用方业务上下文。

**Carry-forward**：票 24 负责目标平台完整消费者、缺类装配、Servlet 6.1 接合和跨平台全门；票 25/26 继续出站 HTTP 和其他静态门面迁移。本票不发布中间制品。

## References

- [Jackson 3 发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)
- [Boot JsonMapperBuilderCustomizer](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/JsonMapperBuilderCustomizer.html)
- [Boot JsonFactoryBuilderCustomizer](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/JsonFactoryBuilderCustomizer.html)
- [Jackson StreamReadConstraints：文档和字符串的缓冲检查语义](https://github.com/FasterXML/jackson-core/blob/3.1/src/main/java/tools/jackson/core/StreamReadConstraints.java)
- [票 21 字面金样和真实消费者](../../verification/json-consumer/README.md)
