package cn.code91.facility.json.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
 * <p><b>注意：</b>如果输入的Base64字符串无效，将抛出{@link IOException}。</p>
 *
 * @author yvvb
 * @since 2025/5/4
 * @see InputStreamSerializer
 */
public class InputStreamDeserializer extends JsonDeserializer<InputStream> {

    private final int maxBytes;

    /** 保留注解方式使用的无上限兼容入口。 */
    public InputStreamDeserializer() {
        this(0);
    }

    /**
     * @param maxBytes 解码后的字节上限；≤0 保持无上限。JSON 文本读取预算另由 mapper/input 管理。
     *                 返回流由调用方拥有；Base64 超限在分配大解码数组前拒绝。
     */
    public InputStreamDeserializer(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * <b>将Base64字符串反序列化为InputStream</b>
     *
     * @param p    JSON解析器
     * @param ctxt 反序列化上下文
     * @return 解码后的InputStream
     * @throws IOException 当Base64字符串无效时抛出
     */
    @Override
    public InputStream deserialize(JsonParser p, DeserializationContext ctx)
            throws IOException {
        // 从Base64字符串重建InputStream
        String base64 = p.getText();
        if (base64 == null || base64.isEmpty()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        if (maxBytes > 0 && base64.length() > ((long) maxBytes + 2) / 3 * 4) {
            throw new IOException("InputStream field exceeds byte limit: " + maxBytes);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (maxBytes > 0 && bytes.length > maxBytes) {
                throw new IOException("InputStream field exceeds byte limit: " + maxBytes);
            }
            return new ByteArrayInputStream(bytes);
        } catch (IllegalArgumentException e) {
            throw new IOException("无效的Base64编码字符串", e);
        }
    }
}
