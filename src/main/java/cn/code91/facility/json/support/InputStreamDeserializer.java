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
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return new ByteArrayInputStream(bytes);
        } catch (IllegalArgumentException e) {
            throw new IOException("无效的Base64编码字符串", e);
        }
    }
}