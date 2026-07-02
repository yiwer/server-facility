package cn.code91.facility.json.support;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InputStreamSerializer - 关闭源流 + null 守卫 (RV2-10)")
class InputStreamSerializerTest {

    @Test @DisplayName("序列化后关闭源 InputStream")
    void closesSourceStream() throws IOException {
        AtomicBoolean closed = new AtomicBoolean(false);
        ByteArrayInputStream in = new ByteArrayInputStream("hi".getBytes()) {
            @Override public void close() throws IOException { closed.set(true); super.close(); }
        };
        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        new InputStreamSerializer().serialize(in, gen, null);
        gen.flush();
        assertThat(closed).isTrue();
        assertThat(sw.toString()).contains("aGk="); // base64("hi")
    }

    @Test @DisplayName("null 输入写 JSON null，不 NPE")
    void nullWritesNull() throws IOException {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        new InputStreamSerializer().serialize(null, gen, null);
        gen.flush();
        assertThat(sw.toString()).isEqualTo("null");
    }
}
