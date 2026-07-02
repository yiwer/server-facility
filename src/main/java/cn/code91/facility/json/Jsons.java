package cn.code91.facility.json;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.log.LogUtil;
import cn.code91.facility.result.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <b>JSON 序列化器一等公民</b>
 * <p>把过去藏在 {@code JsonUtil.Serializer} 里的能力提升为顶层类型。所有方法返回 {@link Result}。
 * 通过 {@link JsonsRegistry} 注册多 namespace；Spring 应用中默认 {@code Jsons} 复用 Spring 自动配置的
 * {@link ObjectMapper}，避免与 controller 出口序列化结果不一致。</p>
 *
 * @see JsonsRegistry
 * @see JsonUtil JsonUtil 静态门面（保留作为零配置入口）
 */
public final class Jsons {

    private final ObjectMapper objectMapper;

    public Jsons(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }

    // ==================== 序列化 ====================

    public Result<String, WrappedError> serialize(Object value) {
        try {
            return Result.ok(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            return handleSerializeError(e, value);
        }
    }

    public String serializeUnsafe(Object value) {
        return serialize(value).orElseThrow(err ->
                new JsonUtil.JsonSerializationException("Serialize failed", err.getException()));
    }

    public Result<byte[], WrappedError> serializeToBytes(Object value) {
        try {
            return Result.ok(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            return handleSerializeError(e, value);
        }
    }

    public Result<Void, WrappedError> serializeTo(Object value, OutputStream output) {
        Objects.requireNonNull(output, "output cannot be null");
        try {
            objectMapper.writeValue(output, value);
            return Result.ok();
        } catch (IOException e) {
            return handleSerializeError(e, value);
        }
    }

    // ==================== 反序列化 ====================

    public <T> Result<T, WrappedError> deserialize(String json, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        if (json == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_DESERIALIZE_ERROR,
                    new IllegalArgumentException("json cannot be null"),
                    target.getName()));
        }
        try {
            return Result.ok(objectMapper.readValue(json, target));
        } catch (JsonProcessingException e) {
            return handleDeserializeError(e, json, target.getName());
        }
    }

    public <T> Result<T, WrappedError> deserialize(String json, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        if (json == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_DESERIALIZE_ERROR,
                    new IllegalArgumentException("json cannot be null"),
                    typeReference.getType().getTypeName()));
        }
        try {
            return Result.ok(objectMapper.readValue(json, typeReference));
        } catch (JsonProcessingException e) {
            return handleDeserializeError(e, json, typeReference.getType().getTypeName());
        }
    }

    public <T> Result<T, WrappedError> deserialize(byte[] bytes, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        if (bytes == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_DESERIALIZE_ERROR,
                    new IllegalArgumentException("bytes cannot be null"),
                    target.getName()));
        }
        try {
            return Result.ok(objectMapper.readValue(bytes, target));
        } catch (IOException e) {
            return handleDeserializeError(e, "[bytes]", target.getName());
        }
    }

    public <T> Result<T, WrappedError> deserialize(byte[] bytes, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        if (bytes == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_DESERIALIZE_ERROR,
                    new IllegalArgumentException("bytes cannot be null"),
                    typeReference.getType().getTypeName()));
        }
        try {
            return Result.ok(objectMapper.readValue(bytes, typeReference));
        } catch (IOException e) {
            return handleDeserializeError(e, "[bytes]", typeReference.getType().getTypeName());
        }
    }

    public <T> Result<T, WrappedError> deserialize(InputStream input, Class<T> target) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        try {
            return Result.ok(objectMapper.readValue(input, target));
        } catch (IOException e) {
            return handleDeserializeError(e, "[stream]", target.getName());
        }
    }

    public <T> Result<T, WrappedError> deserialize(InputStream input, TypeReference<T> typeReference) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        try {
            return Result.ok(objectMapper.readValue(input, typeReference));
        } catch (IOException e) {
            return handleDeserializeError(e, "[stream]", typeReference.getType().getTypeName());
        }
    }

    public <E> Result<List<E>, WrappedError> deserializeToList(String json, Class<E> elementClass) {
        Objects.requireNonNull(elementClass, "elementClass cannot be null");
        JavaType type = objectMapper.getTypeFactory()
                .constructCollectionType(ArrayList.class, elementClass);
        return deserializeWithJavaType(json, type);
    }

    public <E> Result<Set<E>, WrappedError> deserializeToSet(String json, Class<E> elementClass) {
        Objects.requireNonNull(elementClass, "elementClass cannot be null");
        JavaType type = objectMapper.getTypeFactory()
                .constructCollectionType(LinkedHashSet.class, elementClass);
        return deserializeWithJavaType(json, type);
    }

    public <K, V> Result<Map<K, V>, WrappedError> deserializeToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        Objects.requireNonNull(keyClass, "keyClass cannot be null");
        Objects.requireNonNull(valueClass, "valueClass cannot be null");
        JavaType type = objectMapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, keyClass, valueClass);
        return deserializeWithJavaType(json, type);
    }

    private <T> Result<T, WrappedError> deserializeWithJavaType(String json, JavaType javaType) {
        if (json == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_DESERIALIZE_ERROR,
                    new IllegalArgumentException("json cannot be null"),
                    javaType.getTypeName()));
        }
        try {
            return Result.ok(objectMapper.readValue(json, javaType));
        } catch (JsonProcessingException e) {
            return handleDeserializeError(e, json, javaType.getTypeName());
        }
    }

    // ==================== JsonNode ====================

    public Result<JsonNode, WrappedError> parseTree(String json) {
        if (json == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_DESERIALIZE_ERROR,
                    new IllegalArgumentException("json cannot be null"),
                    "JsonNode"));
        }
        try {
            return Result.ok(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            return handleDeserializeError(e, json, "JsonNode");
        }
    }

    public Result<JsonNode, WrappedError> valueToTree(Object value) {
        try {
            return Result.ok(objectMapper.valueToTree(value));
        } catch (IllegalArgumentException e) {
            LogUtil.warn("Convert to JsonNode failed, value: {}", e, safeToString(value));
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_NODE_TRANSFER_ERROR,
                    new RuntimeException(e)));
        }
    }

    public <T> Result<T, WrappedError> treeToValue(JsonNode node, Class<T> target) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        try {
            return Result.ok(objectMapper.treeToValue(node, target));
        } catch (JsonProcessingException e) {
            LogUtil.warn("Convert JsonNode to value failed, target: {}", e, target.getName());
            return Result.err(WrappedError.of(
                    FacilityErrorType.JSON_NODE_TRANSFER_ERROR, e));
        }
    }

    // ==================== 错误处理 ====================

    private <T> Result<T, WrappedError> handleSerializeError(Exception e, Object value) {
        String valueStr = safeToString(value);
        LogUtil.warn("JSON serialize failed, value: {}", e, valueStr);
        return Result.err(WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR, e, valueStr));
    }

    private <T> Result<T, WrappedError> handleDeserializeError(Exception e, String source, String targetType) {
        String truncatedSource = truncate(source, 500);
        LogUtil.warn("JSON deserialize failed, target: {}, source: {}", e, targetType, truncatedSource);
        return Result.err(WrappedError.of(
                FacilityErrorType.JSON_DESERIALIZE_ERROR, e, targetType, truncatedSource));
    }

    private String safeToString(Object obj) {
        if (obj == null) return "null";
        try {
            return truncate(obj.toString(), 200);
        } catch (Exception e) {
            return obj.getClass().getName() + "@" + System.identityHashCode(obj);
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...(truncated, total: " + str.length() + ")";
    }
}
