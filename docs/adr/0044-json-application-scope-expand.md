# ADR-0044: 先扩展应用 JSON 配置入口，再替换平台

## Status

Accepted，2026-10-03。票 21；补充 ADR-0024 的 Java 25 中间基线和 ADR-0025 的构造器注入方向。旧 ADR 中没有单独定义 JSON registry；本决策替代 USAGE 中“多上下文共享 JSON 注册表是推荐设计”的表述，保留其旧 API 行为。

## Context

Boot 3 的 MVC 使用应用 ObjectMapper，但 FacilityJsonAutoConfiguration 只把它发布到进程级 JsonUtil registry。应用 B 会覆盖 A 的默认值，关闭也不恢复。JsonConfig 在 build 后突变 mapper；Jackson 3 的不可变 builder 生命周期不能机械迁移这条路径。公开 Jackson 类型、独立协议样本和真实 HTTP 接合都必须在旧平台仍能验证时登记。

## Decision

1. 在原 Boot 3 / Jackson 2 线上发布可注入的应用 `Jsons` bean，复用本应用 ObjectMapper；用户自有 Jsons bean 优先。构造器 `Jsons(ObjectMapper)`、静态 JsonUtil、namespace registry 和旧 mapper 回调保留。兼容 registry 继续原来的进程级行为，不把它包装成多应用路由，也不承诺关闭后恢复。
2. `JsonConfig.Builder.customizeBuilder(Consumer<JsonMapper.Builder>)` 增加构建期入口。预设、模块、时区和 feature 先配置 builder；构建期回调按注册顺序执行，随后 build；旧 `customize(Consumer<ObjectMapper>)` 仍最后执行。两种回调均由调用方保证不保留并发突变引用。新代码优先使用 Boot 的标准 builder customizer 或此入口，无新增通用配置 DSL。
3. InputStream 字段的序列化/反序列化器增加显式字节预算构造参数。正数限制解码后的字节数；默认及 ≤0 保持无上限兼容行为。字段 serializer 在成功、超限和 I/O 失败时均关闭其拥有的输入；最多额外读取一个探测字节。反序列化结果由调用方关闭。根 JSON 输入/输出流仍遵从 mapper 的 AUTO_CLOSE_SOURCE / AUTO_CLOSE_TARGET，与字段所有权分别记录。
4. 独立 `verification/json-consumer` 从隔离 repository 的普通 jar 启动真实 Web 应用。字面金样先在 `731598b` 验证，再用同一消费者切换应用注入路径；新增两应用交错请求、关闭重建。金样来源与边界见该目录 README，依赖/执行证据见票 21 验证报告。
5. `codex/server-facility-next` 是 22/23 唯一非发布集成线。22 更换 BOM、技术模块、测试引擎，只能明确登记归 23 的 Jackson 编译缺口；23 迁移 Jackson 公共类型和不可变构建并清空这些缺口；24 必须恢复同一产物的全质量门、Windows/Linux 和独立消费者证据，才允许进入 master/候选发布。中间状态不发布，不宣称同一 jar 同时二进制兼容两个 Boot/Jackson 主版本。

## Consequences

应用代码可以先迁移到注入入口，协议差异与平台迁移可以分别验证。已有静态消费者不会先失去入口，但其全局政策限制仍然存在。对 `mapper()`、Jackson 类型签名和旧可变回调的删除/替换属于票 23 的显式破坏性迁移，不能在本票悄悄改变。

票 23 还必须移除敏感 payload 错误详情、登记 canonical 并非签名标准、迁移三种已合并 Java 8 模块。固定 413 mapper 与 HTTP 错误语义由 04/05 协调，出站 RestClient 的宿主 builder 政策属于 25。完整影响登记见 `docs/building/platform-migration-inventory.md`。

## References

- [Spring Boot 4 官方迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)：技术模块与测试 starter 按技术拆分，Jackson 3 为默认。
- [Jackson 3 官方发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)：builder 生命周期、合并模块与 annotations 保留原组。
- [Boot JsonMapperBuilderCustomizer API](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/JsonMapperBuilderCustomizer.html)。
