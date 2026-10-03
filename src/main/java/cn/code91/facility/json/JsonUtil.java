package cn.code91.facility.json;

import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 兼容的 standalone JSON 静态门面。
 * 不读取 Spring 应用 mapper，应用启停也不改写本 registry；应用代码应注入 Jsons。
 * 该入口保留原 namespace 预设，用于明确不依赖应用 HTTP 政策的调用方。
 */
public final class JsonUtil {

    public static final String DEFAULT = JsonsRegistry.DEFAULT;
    public static final String GENERIC = JsonsRegistry.GENERIC;
    public static final String CANONICAL = JsonsRegistry.CANONICAL;
    public static final String PRETTY = JsonsRegistry.PRETTY;

    private static final JsonsRegistry REGISTRY = new JsonsRegistry();

    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 进程级 {@link JsonsRegistry} 单例。{@link cn.code91.facility.autoconfigure.FacilityJsonAutoConfiguration}
     * 通过它把默认 namespace 替换为复用 Spring {@code ObjectMapper} 的 {@link Jsons}。
     */
    public static JsonsRegistry registry() {
        return REGISTRY;
    }

    /**
     * 取指定 namespace 的 {@link Jsons}。
     */
    public static Jsons use(String namespace) {
        Jsons jsons = REGISTRY.use(namespace);
        if (jsons == null) {
            throw new IllegalArgumentException("Unknown JSON namespace: " + namespace);
        }
        return jsons;
    }

    private static Jsons getDefault() {
        return REGISTRY.getDefault();
    }

    // ==================== 序列化 ====================

    public static Result<String, WrappedError> serialize(Object value) {
        return getDefault().serialize(value);
    }

    public static Result<byte[], WrappedError> serializeToBytes(Object value) {
        return getDefault().serializeToBytes(value);
    }

    public static Result<Void, WrappedError> serializeTo(Object value, OutputStream output) {
        Objects.requireNonNull(output, "output cannot be null");
        return getDefault().serializeTo(value, output);
    }

    public static String serializeUnsafe(Object value) {
        return serialize(value).orElseThrow(err ->
                new JsonSerializationException("Serialize failed", err.getException()));
    }

    // ==================== 反序列化 ====================

    public static <T> Result<T, WrappedError> deserialize(String json, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        return getDefault().deserialize(json, target);
    }

    public static <T> Result<T, WrappedError> deserialize(String json, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        return getDefault().deserialize(json, typeReference);
    }

    public static <T> Result<T, WrappedError> deserialize(byte[] bytes, Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        return getDefault().deserialize(bytes, target);
    }

    public static <T> Result<T, WrappedError> deserialize(byte[] bytes, TypeReference<T> typeReference) {
        Objects.requireNonNull(typeReference, "typeReference cannot be null");
        return getDefault().deserialize(bytes, typeReference);
    }

    public static <T> Result<T, WrappedError> deserialize(InputStream input, Class<T> target) {
        return getDefault().deserialize(input, target);
    }

    public static <T> Result<T, WrappedError> deserialize(InputStream input, TypeReference<T> typeReference) {
        return getDefault().deserialize(input, typeReference);
    }

    public static <E> Result<List<E>, WrappedError> deserializeToList(String json, Class<E> elementClass) {
        return getDefault().deserializeToList(json, elementClass);
    }

    public static <E> Result<Set<E>, WrappedError> deserializeToSet(String json, Class<E> elementClass) {
        return getDefault().deserializeToSet(json, elementClass);
    }

    public static <K, V> Result<Map<K, V>, WrappedError> deserializeToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        return getDefault().deserializeToMap(json, keyClass, valueClass);
    }

    // ==================== JsonNode ====================

    public static Result<JsonNode, WrappedError> parseTree(String json) {
        return getDefault().parseTree(json);
    }

    public static Result<JsonNode, WrappedError> valueToTree(Object value) {
        return getDefault().valueToTree(value);
    }

    public static <T> Result<T, WrappedError> treeToValue(JsonNode node, Class<T> target) {
        return getDefault().treeToValue(node, target);
    }

    // ==================== 异常 ====================

    /**
     * JSON 序列化异常（unsafe 路径抛出）。保留为内嵌类型供 {@link Jsons#serializeUnsafe(Object)} 引用。
     */
    public static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
