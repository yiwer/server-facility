package cn.code91.facility.json;

import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
