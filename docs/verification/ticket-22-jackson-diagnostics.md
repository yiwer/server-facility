# 票 22 → 23 的实际 Jackson 编译诊断

日期 2026-10-04；被测源 5428982 + 票22工作区修改（初次诊断）。执行根 Wrapper clean verify，失败发生于主 compile，javac 报告 64 个错误（未达到默认 100 条截断）。本表逐条记录首次诊断区段，去掉 Maven 最后重复汇总。

所有下列项由 **23** 拥有：迁移 Jackson 公开类型、mapper/builder 与 serializer 签名后，根 compile/testCompile 及金样必须关闭这些错误。未加入 Jackson 2 fallback、未跳测试。当前记录没有非 Jackson 编译诊断；主编译失败会阻止 testCompile，不能据此声称尚未执行的测试源码/运行时也没有其他迁移缺口。

## 按文件计数

| 文件（相对 src/main/java/cn/code91/facility） | 条数 |
|---|---:|
| autoconfigure/FacilityJsonAutoConfiguration.java | 5 |
| json/Jsons.java | 15 |
| json/JsonUtil.java | 8 |
| json/support/InputStreamDeserializer.java | 6 |
| json/support/InputStreamSerializer.java | 6 |
| json/support/JsonConfig.java | 21 |
| json/support/TypeRef.java | 2 |
| web/filter/RepeatableRequestFilter.java | 1 |

## 精确诊断

| # | 文件与行:列 | 编译器诊断 | Owner |
|---:|---|---|---|
| 1 | `autoconfigure/FacilityJsonAutoConfiguration.java:6:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 2 | `autoconfigure/FacilityJsonAutoConfiguration.java:38:24` | `cannot find symbol; class ObjectMapper` | 23 |
| 3 | `json/Jsons.java:7:34` | `package com.fasterxml.jackson.core does not exist` | 23 |
| 4 | `json/Jsons.java:8:39` | `package com.fasterxml.jackson.core.type does not exist` | 23 |
| 5 | `json/Jsons.java:9:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 6 | `json/Jsons.java:10:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 7 | `json/Jsons.java:11:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 8 | `autoconfigure/FacilityJsonAutoConfiguration.java:48:40` | `cannot find symbol; class ObjectMapper` | 23 |
| 9 | `json/Jsons.java:35:19` | `cannot find symbol; class ObjectMapper` | 23 |
| 10 | `json/Jsons.java:37:18` | `cannot find symbol; class ObjectMapper` | 23 |
| 11 | `json/Jsons.java:41:12` | `cannot find symbol; class ObjectMapper` | 23 |
| 12 | `json/Jsons.java:95:65` | `cannot find symbol; class TypeReference` | 23 |
| 13 | `json/Jsons.java:125:66` | `cannot find symbol; class TypeReference` | 23 |
| 14 | `json/Jsons.java:155:71` | `cannot find symbol; class TypeReference` | 23 |
| 15 | `json/Jsons.java:192:78` | `cannot find symbol; class JavaType` | 23 |
| 16 | `json/Jsons.java:208:19` | `cannot find symbol; class JsonNode` | 23 |
| 17 | `json/Jsons.java:222:19` | `cannot find symbol; class JsonNode` | 23 |
| 18 | `json/Jsons.java:233:52` | `cannot find symbol; class JsonNode` | 23 |
| 19 | `web/filter/RepeatableRequestFilter.java:30:56` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 20 | `json/JsonUtil.java:5:39` | `package com.fasterxml.jackson.core.type does not exist` | 23 |
| 21 | `json/JsonUtil.java:6:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 22 | `json/JsonUtil.java:99:72` | `cannot find symbol; class TypeReference` | 23 |
| 23 | `json/JsonUtil.java:109:73` | `cannot find symbol; class TypeReference` | 23 |
| 24 | `json/JsonUtil.java:118:78` | `cannot find symbol; class TypeReference` | 23 |
| 25 | `json/JsonUtil.java:136:26` | `cannot find symbol; class JsonNode` | 23 |
| 26 | `json/JsonUtil.java:140:26` | `cannot find symbol; class JsonNode` | 23 |
| 27 | `json/JsonUtil.java:144:59` | `cannot find symbol; class JsonNode` | 23 |
| 28 | `json/support/InputStreamDeserializer.java:3:34` | `package com.fasterxml.jackson.core does not exist` | 23 |
| 29 | `json/support/InputStreamDeserializer.java:4:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 30 | `json/support/InputStreamDeserializer.java:5:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 31 | `json/support/InputStreamDeserializer.java:33:46` | `cannot find symbol; class JsonDeserializer` | 23 |
| 32 | `json/support/InputStreamDeserializer.java:59:36` | `cannot find symbol; class JsonParser` | 23 |
| 33 | `json/support/InputStreamDeserializer.java:59:50` | `cannot find symbol; class DeserializationContext` | 23 |
| 34 | `json/support/InputStreamSerializer.java:3:34` | `package com.fasterxml.jackson.core does not exist` | 23 |
| 35 | `json/support/InputStreamSerializer.java:4:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 36 | `json/support/InputStreamSerializer.java:5:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 37 | `json/support/InputStreamSerializer.java:36:44` | `cannot find symbol; class JsonSerializer` | 23 |
| 38 | `json/support/InputStreamSerializer.java:62:46` | `cannot find symbol; class JsonGenerator` | 23 |
| 39 | `json/support/InputStreamSerializer.java:62:65` | `cannot find symbol; class SerializerProvider` | 23 |
| 40 | `json/support/JsonConfig.java:5:34` | `package com.fasterxml.jackson.core does not exist` | 23 |
| 41 | `json/support/JsonConfig.java:7:38` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 42 | `json/support/JsonConfig.java:8:43` | `package com.fasterxml.jackson.databind.json does not exist` | 23 |
| 43 | `json/support/JsonConfig.java:9:45` | `package com.fasterxml.jackson.databind.module does not exist` | 23 |
| 44 | `json/support/JsonConfig.java:10:46` | `package com.fasterxml.jackson.databind.ser.std does not exist` | 23 |
| 45 | `json/support/JsonConfig.java:11:43` | `package com.fasterxml.jackson.datatype.jdk8 does not exist` | 23 |
| 46 | `json/support/JsonConfig.java:12:45` | `package com.fasterxml.jackson.datatype.jsr310 does not exist` | 23 |
| 47 | `json/support/JsonConfig.java:13:51` | `package com.fasterxml.jackson.datatype.jsr310.deser does not exist` | 23 |
| 48 | `json/support/JsonConfig.java:14:51` | `package com.fasterxml.jackson.datatype.jsr310.deser does not exist` | 23 |
| 49 | `json/support/JsonConfig.java:15:51` | `package com.fasterxml.jackson.datatype.jsr310.deser does not exist` | 23 |
| 50 | `json/support/JsonConfig.java:16:49` | `package com.fasterxml.jackson.datatype.jsr310.ser does not exist` | 23 |
| 51 | `json/support/JsonConfig.java:17:49` | `package com.fasterxml.jackson.datatype.jsr310.ser does not exist` | 23 |
| 52 | `json/support/JsonConfig.java:18:49` | `package com.fasterxml.jackson.datatype.jsr310.ser does not exist` | 23 |
| 53 | `json/support/JsonConfig.java:19:47` | `package com.fasterxml.jackson.module.paramnames does not exist` | 23 |
| 54 | `json/support/JsonConfig.java:196:37` | `cannot find symbol; class ObjectMapper` | 23 |
| 55 | `json/support/JsonConfig.java:197:47` | `package JsonMapper does not exist` | 23 |
| 56 | `json/support/JsonConfig.java:593:43` | `cannot find symbol; class ObjectMapper` | 23 |
| 57 | `json/support/JsonConfig.java:606:60` | `package JsonMapper does not exist` | 23 |
| 58 | `json/support/JsonConfig.java:631:16` | `cannot find symbol; class ObjectMapper` | 23 |
| 59 | `json/support/JsonConfig.java:703:50` | `package JsonMapper does not exist` | 23 |
| 60 | `json/support/JsonConfig.java:6:29` | `package com.fasterxml.jackson.databind does not exist` | 23 |
| 61 | `json/support/TypeRef.java:3:39` | `package com.fasterxml.jackson.core.type does not exist` | 23 |
| 62 | `json/support/TypeRef.java:34:42` | `cannot find symbol; class TypeReference` | 23 |
| 63 | `autoconfigure/FacilityJsonAutoConfiguration.java:28:21` | `cannot find symbol; class ObjectMapper` | 23 |
| 64 | `autoconfigure/FacilityJsonAutoConfiguration.java:29:20` | `cannot find symbol; class ObjectMapper` | 23 |

## 尚未执行的编译/运行接合

- 主库原测试保留；尚未执行 testCompile/Surefire，不能把独立探针 4 项当作全库已发现。23 须迁移 JSON、自动装配、ResponseUtil 和旧 413 测试的 Jackson 引用，随后恢复测试编译。
- 独立 JSON consumer 仍有 Jackson 2 ObjectMapper/TypeReference/serializer 和 Jackson2ObjectMapperBuilderCustomizer；其 MVC starter、Servlet context 归属已由 22 更新，其余实际签名与策略由 23 处理。尚未得到目标普通库 jar，因此没有声称该消费者已通过目标编译。
- 24 负责完整质量门、五条主库 ArchUnit、主库 metadata/普通 jar、Windows/Linux 两类消费者和缺类/覆盖/Servlet 6.1 包装器接合。依赖/工具链子集成功不解除这些责任。

原始日志：`.verification-results/ticket-22/root-verify-initial.log`。执行命令、环境、输入哈希与完整依赖图见 [票 22 验证报告](ticket-22-platform.md)。
