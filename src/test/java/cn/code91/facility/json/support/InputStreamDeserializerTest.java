package cn.code91.facility.json.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

@DisplayName("InputStreamDeserializer - Base64 → InputStream")
class InputStreamDeserializerTest {

    private static ObjectMapper mapperWithStreamModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(InputStream.class, new InputStreamSerializer());
        module.addDeserializer(InputStream.class, new InputStreamDeserializer());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    void validBase64_decodesToOriginalBytes() throws Exception {
        String json = "\"" + Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)) + "\"";
        InputStream in = mapperWithStreamModule().readValue(json, InputStream.class);
        assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void emptyString_yieldsEmptyStream() throws Exception {
        InputStream in = mapperWithStreamModule().readValue("\"\"", InputStream.class);
        assertThat(in.readAllBytes()).isEmpty();
    }

    @Test
    void invalidBase64_throwsIOException() {
        assertThatIOException().isThrownBy(() ->
                mapperWithStreamModule().readValue("\"@@not-base64@@\"", InputStream.class));
    }

    @Test
    void roundTrip_serializerThenDeserializer() throws Exception {
        ObjectMapper mapper = mapperWithStreamModule();
        byte[] payload = {1, 2, 3, 4, 5};
        String json = mapper.writeValueAsString(new ByteArrayInputStream(payload));
        InputStream back = mapper.readValue(json, InputStream.class);
        assertThat(back.readAllBytes()).isEqualTo(payload);
    }
}
