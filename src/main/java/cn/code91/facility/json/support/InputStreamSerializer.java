package cn.code91.facility.json.support;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * <b>InputStream序列化器</b>
 * <p>
 * 将InputStream转换为Base64编码的字符串进行序列化。
 * 用于在JSON中传输二进制数据。
 * </p>
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * public class FileData {
 *     @JsonSerialize(using = InputStreamSerializer.class)
 *     private InputStream content;
 * }
 * }</pre>
 *
 * <p><b>注意：</b></p>
 * <ul>
 *     <li>序列化过程中会完全消费输入流，调用后流不可复用</li>
 *     <li>默认注解入口限制 1 MiB；通过 Module 注册正数预算实例可改变原始字节上限</li>
 * </ul>
 *
 * @author yvvb
 * @since 2025/5/4
 * @see InputStreamDeserializer
 */
public class InputStreamSerializer extends ValueSerializer<InputStream> {

    private final int maxBytes;

    /** 注解入口固定限制 1 MiB，源流由本序列化器关闭。 */
    public InputStreamSerializer() {
        this(1024 * 1024);
    }

    /**
     * @param maxBytes 原始字节上限；必须为正数。最多读取上限加一个探测字节。
     *                 成功、超限、读写失败均关闭源流；Base64/JSON 输出还需约 4/3 的编码空间。
     */
    public InputStreamSerializer(int maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        this.maxBytes = maxBytes;
    }

    /**
     * <b>序列化InputStream为Base64字符串</b>
     *
     * @param value       要序列化的InputStream
     * @param gen         JSON生成器
     * @param serializers 序列化器提供者
     * @throws tools.jackson.core.exc.JacksonIOException IO异常
     */
    @Override
    public void serialize(InputStream value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        // 读完即关闭源流，避免 fd 泄漏（RV2-10）
        try (InputStream in = value) {
            byte[] bytes = in.readNBytes(maxBytes);
            if (in.read() != -1) {
                throw new IOException("InputStream field exceeds byte limit: " + maxBytes);
            }
            gen.writeString(Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            throw tools.jackson.core.exc.JacksonIOException.construct(e);
        }
    }
}
