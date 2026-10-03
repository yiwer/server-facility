package cn.code91.facility.json;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.json.support.JsonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFailureBoundaryTest {
    private static final String SECRET = "PASSWORD-23-private";
    record Value(String text) {}
    record Envelope(Value value) {}
    static class NoProperties {}

    @Test
    void malformedInputDoesNotExposePayloadOrOriginalCause() {
        var jsons = new Jsons(JsonConfig.standard().build());
        WrappedError failure = jsons.deserialize("{\"password\":\"" + SECRET + "\",", Object.class).getErr();
        assertThat(failure.getErrorType()).isEqualTo(FacilityErrorType.JSON_DESERIALIZE_ERROR);
        assertThat(failure.getArgs()).isEmpty();
        assertThat(failure.getException()).isNull();
        assertThat(failure.toString()).doesNotContain(SECRET, "password");
    }

    @Test
    void userSerializerAndDeserializerBugsPropagateWithEitherHostWrappingPolicy() {
        for (boolean wrap : new boolean[]{true, false}) {
            var bug = new IllegalStateException("user implementation failed");
            var module = new SimpleModule()
                    .addSerializer(Value.class, new ValueSerializer<Value>() {
                        @Override public void serialize(Value value, JsonGenerator generator, SerializationContext context) {
                            throw bug;
                        }
                    }).addDeserializer(Value.class, new ValueDeserializer<Value>() {
                        @Override public Value deserialize(JsonParser parser, DeserializationContext context) {
                            throw bug;
                        }
                    });
            var mapper = JsonConfig.standard().addModule(module).customizeBuilder(builder -> builder
                    .configure(SerializationFeature.WRAP_EXCEPTIONS, wrap)
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap)).build();
            var jsons = new Jsons(mapper);
            assertThatThrownBy(() -> jsons.serialize(new Envelope(new Value("x")))).isSameAs(bug);
            assertThatThrownBy(() -> jsons.deserialize("{\"value\":{\"text\":\"x\"}}", Envelope.class)).isSameAs(bug);
            assertThat(mapper.isEnabled(SerializationFeature.WRAP_EXCEPTIONS)).isEqualTo(wrap);
            assertThat(mapper.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)).isEqualTo(wrap);
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void expectedInputAndIoFailuresStaySafeAcrossEveryPublicRepresentation(CapturedOutput output) {
        var jsons = new Jsons(JsonConfig.strict().customizeBuilder(builder -> builder
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).build());
        String malformed = "{\"password\":\"" + SECRET + "\", ";
        byte[] bytes = malformed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var type = new tools.jackson.core.type.TypeReference<java.util.List<Integer>>() {};
        var failures = java.util.List.of(
                jsons.deserialize(malformed, Object.class), jsons.deserialize(malformed, type),
                jsons.deserialize(bytes, Object.class), jsons.deserialize(bytes, type),
                jsons.deserialize(new java.io.ByteArrayInputStream(bytes), Object.class),
                jsons.deserialize(new java.io.ByteArrayInputStream(bytes), type),
                jsons.deserializeToList("[\"" + SECRET + "\"]", Integer.class),
                jsons.deserializeToSet("[1,", Integer.class),
                jsons.deserializeToMap("{\"x\":\"" + SECRET + "\"}", String.class, Integer.class),
                jsons.deserialize("[\"" + SECRET + "\"]", type),
                jsons.deserialize("\"not-a-date\"", java.time.LocalDate.class),
                jsons.deserialize("1 2", Integer.class), jsons.parseTree(malformed),
                jsons.treeToValue(jsons.parseTree("{\"password\":\"" + SECRET + "\"}").get(), Integer.class),
                jsons.deserialize(failingInput(), Object.class), jsons.deserialize(failingInput(), type),
                jsons.serializeTo(java.util.Map.of("password", SECRET), new java.io.OutputStream() {
                    @Override public void write(int value) throws java.io.IOException { throw new java.io.IOException(SECRET); }
                }));
        failures.forEach(result -> {
            assertThat(result.isErr()).isTrue();
            assertSafe(result.getErr());
        });
        assertThat(output.getAll()).doesNotContain(SECRET, malformed);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void userCodecIoFailuresAndUnsafeWrapperDoNotExposeCauses(CapturedOutput output) {
        var module = new SimpleModule().addSerializer(Value.class, new ValueSerializer<Value>() {
            @Override public void serialize(Value value, JsonGenerator generator, SerializationContext context) {
                throw tools.jackson.core.exc.JacksonIOException.construct(new java.io.IOException(SECRET));
            }
        });
        var jsons = new Jsons(JsonConfig.standard().addModule(module).build());
        var value = new Value(SECRET);
        assertSafe(jsons.serialize(value).getErr());
        assertSafe(jsons.serializeToBytes(value).getErr());
        assertSafe(jsons.valueToTree(value).getErr());
        assertThatThrownBy(() -> jsons.serializeUnsafe(value)).isInstanceOf(JsonUtil.JsonSerializationException.class)
                .hasMessage("Serialize failed").hasNoCause();
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test
    void configurationFailuresAndVmErrorsAreNeverResultInputErrors() {
        var bug = new IllegalStateException("builder failed");
        assertThatThrownBy(() -> JsonConfig.standard().customizeBuilder(builder -> { throw bug; }).build()).isSameAs(bug);
        assertThatThrownBy(() -> new Jsons(JsonConfig.strict().customizeBuilder(builder -> builder.enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)).build()).serialize(new NoProperties()))
                .isInstanceOf(tools.jackson.databind.exc.InvalidDefinitionException.class);
        var error = new AssertionError("user codec invariant");
        var module = new SimpleModule().addSerializer(Value.class, new ValueSerializer<Value>() {
            @Override public void serialize(Value value, JsonGenerator generator, SerializationContext context) { throw error; }
        });
        assertThatThrownBy(() -> new Jsons(JsonConfig.standard().addModule(module).build()).serialize(new Envelope(new Value("x"))))
                .isSameAs(error);
    }

    private static java.io.InputStream failingInput() {
        return new java.io.InputStream() {
            @Override public int read() throws java.io.IOException { throw new java.io.IOException(SECRET); }
        };
    }

    private static void assertSafe(WrappedError failure) {
        assertThat(failure.getArgs()).isEmpty();
        assertThat(failure.getException()).isNull();
        assertThat(failure.toString()).doesNotContain(SECRET);
    }
}
