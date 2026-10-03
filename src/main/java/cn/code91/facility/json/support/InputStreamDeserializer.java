package cn.code91.facility.json.support;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

/**
 * <b>InputStream反序列化器</b>
 * <p>
 * 将Base64编码的字符串反序列化为InputStream。
 * 与{@link InputStreamSerializer}配对使用。
 * </p>
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * public class FileData {
 *     @JsonDeserialize(using = InputStreamDeserializer.class)
 *     private InputStream content;
 * }
 * }</pre>
 *
 * <p><b>注意：</b>如果输入的Base64字符串无效，将抛出 Jackson 输入异常。</p>
 *
 * @author yvvb
 * @since 2025/5/4
 * @see InputStreamSerializer
 */
public class InputStreamDeserializer extends ValueDeserializer<InputStream> {

    private final int maxBytes;

    /** 注解入口固定限制 1 MiB。 */
    public InputStreamDeserializer() {
        this(1024 * 1024);
    }

    /**
     * @param maxBytes 解码后的字节上限；必须为正数。JSON 文档/字符串预算另由应用 JsonFactory 的 StreamReadConstraints 管理。
     *                 返回流由调用方拥有；Base64 超限在分配大解码数组前拒绝。
     */
    public InputStreamDeserializer(int maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        this.maxBytes = maxBytes;
    }

    /**
     * <b>将Base64字符串反序列化为InputStream</b>
     *
     * @param p    JSON解析器
     * @param ctx 反序列化上下文
     * @return 解码后的InputStream
     */
    @Override
    public InputStream deserialize(JsonParser p, DeserializationContext ctx) {
        if (!p.hasToken(tools.jackson.core.JsonToken.VALUE_STRING)) {
            return ctx.reportInputMismatch(InputStream.class, "Expected a Base64 JSON string");
        }
        // 从Base64字符串重建InputStream
        String base64 = p.getString();
        if (base64 == null || base64.isEmpty()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        if (base64.length() > ((long) maxBytes + 2) / 3 * 4) {
            return ctx.reportInputMismatch(InputStream.class, "InputStream field exceeds byte limit: %s", maxBytes);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length > maxBytes) {
                return ctx.reportInputMismatch(InputStream.class, "InputStream field exceeds byte limit: %s", maxBytes);
            }
            return new ByteArrayInputStream(bytes);
        } catch (IllegalArgumentException e) {
            return ctx.reportInputMismatch(InputStream.class, "Invalid Base64 input");
        }
    }
}
