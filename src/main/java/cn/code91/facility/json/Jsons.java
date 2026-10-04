package cn.code91.facility.json;

import jakarta.annotation.Nullable;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    public Result<String, WrappedError> serialize(@Nullable Object value) {
        try {
            return Result.ok(objectMapper.writeValueAsString(value));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_SERIALIZE_ERROR, e);
        }
    }

    public String serializeUnsafe(@Nullable Object value) {
        return serialize(value).orElseThrow(err ->
                new JsonUtil.JsonSerializationException("Serialize failed", err.getException()));
    }

    public Result<byte[], WrappedError> serializeToBytes(@Nullable Object value) {
        try {
            return Result.ok(objectMapper.writeValueAsBytes(value));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_SERIALIZE_ERROR, e);
        }
    }

    public Result<Void, WrappedError> serializeTo(@Nullable Object value, OutputStream output) {
        Objects.requireNonNull(output, "output cannot be null");
        try {
            objectMapper.writeValue(output, value);
            return Result.ok();
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_SERIALIZE_ERROR, e);
        }
    }

    // ==================== 反序列化 ====================

    public <T> Result<T, WrappedError> deserialize(@Nullable String json, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        if (json == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(json, target));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public <T> Result<T, WrappedError> deserialize(@Nullable String json, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        if (json == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(json, typeReference));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public <T> Result<T, WrappedError> deserialize(@Nullable byte[] bytes, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        if (bytes == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(bytes, target));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public <T> Result<T, WrappedError> deserialize(@Nullable byte[] bytes, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        if (bytes == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(bytes, typeReference));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public <T> Result<T, WrappedError> deserialize(@Nullable InputStream input, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        if (input == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(input, target));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public <T> Result<T, WrappedError> deserialize(@Nullable InputStream input, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        if (input == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(input, typeReference));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public <E> Result<List<E>, WrappedError> deserializeToList(@Nullable String json, Class<E> elementClass) {
        Objects.requireNonNull(elementClass, "elementClass cannot be null");
        JavaType type = objectMapper.getTypeFactory()
                .constructCollectionType(ArrayList.class, elementClass);
        return deserializeWithJavaType(json, type);
    }

    public <E> Result<Set<E>, WrappedError> deserializeToSet(@Nullable String json, Class<E> elementClass) {
        Objects.requireNonNull(elementClass, "elementClass cannot be null");
        JavaType type = objectMapper.getTypeFactory()
                .constructCollectionType(LinkedHashSet.class, elementClass);
        return deserializeWithJavaType(json, type);
    }

    public <K, V> Result<Map<K, V>, WrappedError> deserializeToMap(@Nullable String json, Class<K> keyClass, Class<V> valueClass) {
        Objects.requireNonNull(keyClass, "keyClass cannot be null");
        Objects.requireNonNull(valueClass, "valueClass cannot be null");
        JavaType type = objectMapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, keyClass, valueClass);
        return deserializeWithJavaType(json, type);
    }

    private <T> Result<T, WrappedError> deserializeWithJavaType(String json, JavaType javaType) {
        if (json == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readValue(json, javaType));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    // ==================== JsonNode ====================

    public Result<JsonNode, WrappedError> parseTree(@Nullable String json) {
        if (json == null) {
            return Result.err(WrappedError.of(FacilityErrorType.JSON_DESERIALIZE_ERROR));
        }
        try {
            return Result.ok(objectMapper.readTree(json));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_DESERIALIZE_ERROR, e);
        }
    }

    public Result<JsonNode, WrappedError> valueToTree(@Nullable Object value) {
        try {
            return Result.ok(objectMapper.valueToTree(value));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_NODE_TRANSFER_ERROR, e);
        }
    }

    public <T> Result<T, WrappedError> treeToValue(JsonNode node, Class<T> target) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        try {
            return Result.ok(objectMapper.treeToValue(node, target));
        } catch (JacksonException e) {
            return failure(FacilityErrorType.JSON_NODE_TRANSFER_ERROR, e);
        }
    }

    /** Expected failures deliberately retain no payload, original exception, cause or diagnostic log. */
    private static <T> Result<T, WrappedError> failure(FacilityErrorType type, JacksonException failure) {
        if (failure instanceof tools.jackson.databind.exc.InvalidDefinitionException) throw failure;
        // Jackson wraps user getter/creator/codec bugs when the host enables WRAP_EXCEPTIONS.
        // MismatchedInputException is Jackson's explicit input-error channel (including bad dates/numbers).
        if (!(failure instanceof tools.jackson.databind.exc.MismatchedInputException)) {
            if (failure.getCause() instanceof Error bug) throw bug;
            if (failure.getCause() instanceof RuntimeException bug && !(bug instanceof JacksonException)) throw bug;
        }
        return Result.err(WrappedError.of(type));
    }
}
