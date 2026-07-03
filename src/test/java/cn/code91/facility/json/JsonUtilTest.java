package cn.code91.facility.json;

import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JsonUtil JSON 工具类测试")
class JsonUtilTest {

    // ==================== serialize ====================

    @Nested
    @DisplayName("serialize 序列化")
    class SerializeTests {

        @Test
        @DisplayName("普通对象序列化为 JSON 字符串")
        void serializeObject_ok() {
            Map<String, Object> obj = Map.of("name", "test", "age", 25);
            Result<String, WrappedError> result = JsonUtil.serialize(obj);

            assertThat(result.isOk()).isTrue();
            String json = result.get();
            assertThat(json).contains("\"name\"").contains("\"test\"").contains("\"age\"").contains("25");
        }

        @Test
        @DisplayName("null 对象序列化为 'null'")
        void serializeNull_ok() {
            Result<String, WrappedError> result = JsonUtil.serialize(null);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("null");
        }

        @Test
        @DisplayName("空列表序列化为 '[]'")
        void serializeEmptyList_ok() {
            Result<String, WrappedError> result = JsonUtil.serialize(List.of());
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("[]");
        }
    }

    // ==================== deserialize (Class) ====================

    @Nested
    @DisplayName("deserialize(json, Class) 反序列化")
    class DeserializeClassTests {

        @Test
        @DisplayName("正常反序列化为 Map")
        @SuppressWarnings("unchecked")
        void deserialize_ok() {
            String json = "{\"name\":\"test\",\"age\":25}";
            Result<Map, WrappedError> result = JsonUtil.deserialize(json, Map.class);

            assertThat(result.isOk()).isTrue();
            Map<String, Object> map = result.get();
            assertThat(map).containsEntry("name", "test").containsEntry("age", 25);
        }

        @Test
        @DisplayName("无效 JSON 返回 Err")
        void deserialize_invalidJson_err() {
            Result<Map, WrappedError> result = JsonUtil.deserialize("{invalid", Map.class);
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("null JSON 字符串反序列化返回 Err")
        void deserialize_nullJson_err() {
            Result<Map, WrappedError> result = JsonUtil.deserialize((String) null, Map.class);
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("超长非法 JSON 反序列化失败,来源串按 500 字符截断(不抛异常)")
        void deserialize_longInvalidJson_truncatesSourceInError() {
            String longInvalidJson = "{" + "x".repeat(600);
            Result<Map, WrappedError> result = JsonUtil.deserialize(longInvalidJson, Map.class);
            assertThat(result.isErr()).isTrue();
        }
    }

    // ==================== deserialize (TypeReference) ====================

    @Nested
    @DisplayName("deserialize(json, TypeReference) 泛型反序列化")
    class DeserializeTypeReferenceTests {

        @Test
        @DisplayName("反序列化为 List<Map<String, Object>>")
        void deserializeGeneric_ok() {
            String json = "[{\"id\":1},{\"id\":2}]";
            Result<List<Map<String, Object>>, WrappedError> result = JsonUtil.deserialize(
                    json,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            assertThat(result.isOk()).isTrue();
            List<Map<String, Object>> list = result.get();
            assertThat(list).hasSize(2);
            assertThat(list.get(0)).containsEntry("id", 1);
        }
    }

    // ==================== LocalDate / LocalDateTime roundtrip ====================

    @Nested
    @DisplayName("Java 时间类型序列化/反序列化往返")
    class DateTimeRoundtripTests {

        @Test
        @DisplayName("LocalDate 序列化/反序列化往返一致")
        void localDate_roundtrip() {
            LocalDate original = LocalDate.of(2025, 6, 15);

            Result<String, WrappedError> serialized = JsonUtil.serialize(original);
            assertThat(serialized.isOk()).isTrue();

            String json = serialized.get();
            assertThat(json).contains("2025");

            Result<LocalDate, WrappedError> deserialized = JsonUtil.deserialize(json, LocalDate.class);
            assertThat(deserialized.isOk()).isTrue();
            assertThat(deserialized.get()).isEqualTo(original);
        }

        @Test
        @DisplayName("LocalDateTime 序列化/反序列化往返一致")
        void localDateTime_roundtrip() {
            LocalDateTime original = LocalDateTime.of(2025, 6, 15, 10, 30, 45);

            Result<String, WrappedError> serialized = JsonUtil.serialize(original);
            assertThat(serialized.isOk()).isTrue();

            String json = serialized.get();

            Result<LocalDateTime, WrappedError> deserialized = JsonUtil.deserialize(json, LocalDateTime.class);
            assertThat(deserialized.isOk()).isTrue();
            assertThat(deserialized.get()).isEqualTo(original);
        }
    }

    // ==================== deserializeToList ====================

    @Nested
    @DisplayName("deserializeToList 便捷反序列化")
    class DeserializeToListTests {

        @Test
        @DisplayName("反序列化为 List<String>")
        void deserializeToList_ok() {
            String json = "[\"a\",\"b\",\"c\"]";
            Result<List<String>, WrappedError> result = JsonUtil.deserializeToList(json, String.class);

            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsExactly("a", "b", "c");
        }
    }

    // ==================== deserializeToSet / deserializeToMap (P7-T3 补测) ====================

    @Nested
    @DisplayName("deserializeToSet / deserializeToMap 便捷反序列化")
    class DeserializeToSetMapTests {

        @Test
        @DisplayName("deserializeToSet 正常反序列化并去重")
        void deserializeToSet_ok() {
            Result<Set<Integer>, WrappedError> result = JsonUtil.deserializeToSet("[1,2,2,3]", Integer.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsExactlyInAnyOrder(1, 2, 3);
        }

        @Test
        @DisplayName("deserializeToSet null JSON → err")
        void deserializeToSet_nullJson_err() {
            assertThat(JsonUtil.deserializeToSet(null, Integer.class).isErr()).isTrue();
        }

        @Test
        @DisplayName("deserializeToMap 正常反序列化")
        void deserializeToMap_ok() {
            Result<Map<String, Integer>, WrappedError> result =
                    JsonUtil.deserializeToMap("{\"a\":1,\"b\":2}", String.class, Integer.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsEntry("a", 1).containsEntry("b", 2);
        }

        @Test
        @DisplayName("deserializeToMap null JSON → err")
        void deserializeToMap_nullJson_err() {
            assertThat(JsonUtil.deserializeToMap(null, String.class, Integer.class).isErr()).isTrue();
        }

        @Test
        @DisplayName("deserializeToList 非法 JSON → err(命中共享 deserializeWithJavaType 的 catch 分支)")
        void deserializeToList_invalidJson_err() {
            assertThat(JsonUtil.deserializeToList("[1,2,", String.class).isErr()).isTrue();
        }
    }

    // ==================== serializeToBytes / serializeTo / serializeUnsafe ====================

    @Nested
    @DisplayName("serializeToBytes / serializeTo / serializeUnsafe")
    class SerializeVariantsTests {

        @Test
        @DisplayName("serializeToBytes 正常对象序列化为字节数组")
        void serializeToBytes_ok() {
            Result<byte[], WrappedError> result = JsonUtil.serializeToBytes(Map.of("k", "v"));
            assertThat(result.isOk()).isTrue();
            assertThat(new String(result.get(), StandardCharsets.UTF_8)).contains("\"k\"", "\"v\"");
        }

        @Test
        @DisplayName("serializeTo 写入 OutputStream 成功")
        void serializeTo_ok() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Result<Void, WrappedError> result = JsonUtil.serializeTo(Map.of("k", "v"), out);
            assertThat(result.isOk()).isTrue();
            assertThat(out.toString(StandardCharsets.UTF_8)).contains("\"k\"", "\"v\"");
        }

        @Test
        @DisplayName("serializeTo 目标流损坏(IOException) → err")
        void serializeTo_brokenStream_err() {
            OutputStream broken = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    throw new IOException("boom");
                }
            };
            Result<Void, WrappedError> result = JsonUtil.serializeTo(Map.of("k", "v"), broken);
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("serializeTo null 输出流抛 NPE")
        void serializeTo_nullOutput_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> JsonUtil.serializeTo(Map.of("k", "v"), null));
        }

        @Test
        @DisplayName("serializeUnsafe 正常返回 JSON 字符串")
        void serializeUnsafe_ok() {
            assertThat(JsonUtil.serializeUnsafe(Map.of("k", "v"))).contains("\"k\"", "\"v\"");
        }

        @Test
        @DisplayName("serializeUnsafe 序列化失败(JsonUtil 门面)抛 JsonSerializationException")
        void serializeUnsafe_error_throwsJsonSerializationException() {
            assertThatThrownBy(() -> JsonUtil.serializeUnsafe(new Broken()))
                    .isInstanceOf(JsonUtil.JsonSerializationException.class);
        }

        @Test
        @DisplayName("Jsons 实例级 serializeUnsafe 序列化失败同样抛 JsonSerializationException")
        void jsonsInstanceSerializeUnsafe_error_throwsJsonSerializationException() {
            assertThatThrownBy(() -> JsonUtil.use(JsonUtil.DEFAULT).serializeUnsafe(new Broken()))
                    .isInstanceOf(JsonUtil.JsonSerializationException.class);
        }

        @Test
        @DisplayName("serialize 序列化失败(getter 抛异常) → err")
        void serialize_brokenBean_err() {
            Result<String, WrappedError> result = JsonUtil.serialize(new Broken());
            assertThat(result.isErr()).isTrue();
        }
    }

    // ==================== use(namespace) ====================

    @Nested
    @DisplayName("use(namespace) — namespace 门面")
    class UseNamespaceTests {

        @Test
        @DisplayName("use 已知 namespace 返回非空 Jsons")
        void use_knownNamespace_returnsJsons() {
            assertThat(JsonUtil.use(JsonUtil.PRETTY)).isNotNull();
            assertThat(JsonUtil.use(JsonUtil.GENERIC)).isNotNull();
            assertThat(JsonUtil.use(JsonUtil.CANONICAL)).isNotNull();
        }

        @Test
        @DisplayName("use 未知 namespace 抛 IllegalArgumentException")
        void use_unknownNamespace_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> JsonUtil.use("no-such-namespace"));
        }
    }

    // ==================== deserialize(byte[], ...) ====================

    @Nested
    @DisplayName("deserialize(byte[], ...) 反序列化")
    class DeserializeByteArrayTests {

        @Test
        @DisplayName("deserialize(byte[], Class) 正常反序列化")
        void deserializeBytesClass_ok() {
            byte[] bytes = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            Result<Map, WrappedError> result = JsonUtil.deserialize(bytes, Map.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsEntry("name", "test");
        }

        @Test
        @DisplayName("deserialize(byte[], Class) null 字节数组 → err")
        void deserializeBytesClass_nullBytes_err() {
            Result<Map, WrappedError> result = JsonUtil.deserialize((byte[]) null, Map.class);
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("deserialize(byte[], Class) 非法字节内容 → err")
        void deserializeBytesClass_invalidBytes_err() {
            byte[] bytes = "{invalid".getBytes(StandardCharsets.UTF_8);
            Result<Map, WrappedError> result = JsonUtil.deserialize(bytes, Map.class);
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("deserialize(byte[], TypeReference) 正常反序列化")
        void deserializeBytesTypeReference_ok() {
            byte[] bytes = "[1,2,3]".getBytes(StandardCharsets.UTF_8);
            Result<List<Integer>, WrappedError> result =
                    JsonUtil.deserialize(bytes, new TypeReference<List<Integer>>() {});
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("deserialize(byte[], TypeReference) null 字节数组 → err")
        void deserializeBytesTypeReference_nullBytes_err() {
            Result<List<Integer>, WrappedError> result =
                    JsonUtil.deserialize((byte[]) null, new TypeReference<List<Integer>>() {});
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("deserialize(byte[], TypeReference) 非法字节内容 → err")
        void deserializeBytesTypeReference_invalidBytes_err() {
            byte[] bytes = "[1,2,".getBytes(StandardCharsets.UTF_8);
            Result<List<Integer>, WrappedError> result =
                    JsonUtil.deserialize(bytes, new TypeReference<List<Integer>>() {});
            assertThat(result.isErr()).isTrue();
        }
    }

    // ==================== deserialize(InputStream, ...) ====================

    @Nested
    @DisplayName("deserialize(InputStream, ...) 反序列化")
    class DeserializeInputStreamTests {

        @Test
        @DisplayName("deserialize(InputStream, Class) 正常反序列化")
        void deserializeStreamClass_ok() {
            InputStream in = new ByteArrayInputStream("{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8));
            Result<Map, WrappedError> result = JsonUtil.deserialize(in, Map.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsEntry("name", "test");
        }

        @Test
        @DisplayName("deserialize(InputStream, Class) 流读取异常(IOException) → err")
        void deserializeStreamClass_brokenStream_err() {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("boom");
                }
            };
            Result<Map, WrappedError> result = JsonUtil.deserialize(broken, Map.class);
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("deserialize(InputStream, TypeReference) 正常反序列化")
        void deserializeStreamTypeReference_ok() {
            InputStream in = new ByteArrayInputStream("[1,2,3]".getBytes(StandardCharsets.UTF_8));
            Result<List<Integer>, WrappedError> result =
                    JsonUtil.deserialize(in, new TypeReference<List<Integer>>() {});
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("deserialize(InputStream, TypeReference) 流读取异常(IOException) → err")
        void deserializeStreamTypeReference_brokenStream_err() {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("boom");
                }
            };
            Result<List<Integer>, WrappedError> result =
                    JsonUtil.deserialize(broken, new TypeReference<List<Integer>>() {});
            assertThat(result.isErr()).isTrue();
        }
    }

    // ==================== JsonNode: parseTree / valueToTree / treeToValue ====================

    @Nested
    @DisplayName("JsonNode 操作:parseTree / valueToTree / treeToValue")
    class JsonNodeTests {

        @Test
        @DisplayName("parseTree 正常解析为 JsonNode")
        void parseTree_ok() {
            Result<JsonNode, WrappedError> result = JsonUtil.parseTree("{\"a\":1}");
            assertThat(result.isOk()).isTrue();
            assertThat(result.get().get("a").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("parseTree null JSON → err")
        void parseTree_nullJson_err() {
            assertThat(JsonUtil.parseTree(null).isErr()).isTrue();
        }

        @Test
        @DisplayName("parseTree 非法 JSON → err")
        void parseTree_invalidJson_err() {
            assertThat(JsonUtil.parseTree("{invalid").isErr()).isTrue();
        }

        @Test
        @DisplayName("valueToTree 正常对象转 JsonNode")
        void valueToTree_ok() {
            Result<JsonNode, WrappedError> result = JsonUtil.valueToTree(Map.of("a", 1));
            assertThat(result.isOk()).isTrue();
            assertThat(result.get().get("a").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("valueToTree 转换失败(getter 抛异常) → err")
        void valueToTree_brokenBean_err() {
            assertThat(JsonUtil.valueToTree(new Broken()).isErr()).isTrue();
        }

        @Test
        @DisplayName("treeToValue 正常转换")
        void treeToValue_ok() {
            JsonNode node = JsonUtil.parseTree("{\"name\":\"test\"}").get();
            Result<Map, WrappedError> result = JsonUtil.treeToValue(node, Map.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsEntry("name", "test");
        }

        @Test
        @DisplayName("treeToValue 类型不匹配(对象节点转 Integer) → err")
        void treeToValue_mismatchedType_err() {
            JsonNode node = JsonUtil.parseTree("{\"name\":\"test\"}").get();
            Result<Integer, WrappedError> result = JsonUtil.treeToValue(node, Integer.class);
            assertThat(result.isErr()).isTrue();
        }
    }

    // ==================== 私有构造函数 ====================

    @Test
    @DisplayName("私有构造函数反射调用抛 UnsupportedOperationException")
    void privateConstructor_throwsUnsupportedOperationException() throws NoSuchMethodException {
        Constructor<JsonUtil> constructor = JsonUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * getter 抛异常的探测对象,用于触发 Jackson 序列化失败分支
     * (handleSerializeError / safeToString / valueToTree 的 catch 分支)。非合法业务场景,仅供测试。
     */
    private static class Broken {
        public String getValue() {
            throw new RuntimeException("boom");
        }
    }
}
