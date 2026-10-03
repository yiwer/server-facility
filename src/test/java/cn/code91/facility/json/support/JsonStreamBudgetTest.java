package cn.code91.facility.json.support;

import cn.code91.facility.json.Jsons;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonStreamBudgetTest {
    public record Attachment(InputStream content) {}

    @Test
    void nonPositiveLimitsKeepLegacyUnboundedSemanticsAndEmptyNullFields() throws Exception {
        for (int limit : new int[] {0, -1}) {
            var module = new SimpleModule().addSerializer(InputStream.class, new InputStreamSerializer(limit))
                    .addDeserializer(InputStream.class, new InputStreamDeserializer(limit));
            var jsons = new Jsons(JsonConfig.standard().addModule(module).build());
            var input = new TrackedInput(new byte[5]);
            assertThat(jsons.serialize(new Attachment(input)).get()).isEqualTo("{\"content\":\"AAAAAAA=\"}");
            assertThat(input.closed).isTrue();
            try (var decoded = jsons.deserialize("{\"content\":\"AAAAAAA=\"}", Attachment.class).get().content()) {
                assertThat(decoded.readAllBytes()).hasSize(5);
            }
            assertThat(jsons.serialize(new Attachment(null)).get()).isEqualTo("{\"content\":null}");
            assertThat(jsons.deserialize("{\"content\":null}", Attachment.class).get().content()).isNull();
        }
        var bounded = bounded(1);
        assertThat(bounded.serialize(new Attachment(new TrackedInput(new byte[0]))).get()).isEqualTo("{\"content\":\"\"}");
        try (var empty = bounded.deserialize("{\"content\":\"\"}", Attachment.class).get().content()) {
            assertThat(empty.read()).isEqualTo(-1);
        }
    }

    @Test
    void unknownLengthSourceIsReadOnlyThroughTheProbeByte() {
        var input = new FaultInput(-1);
        assertThat(bounded(4).serialize(new Attachment(input)).isErr()).isTrue();
        assertThat(input.reads).isEqualTo(5);
        assertThat(input.closed).isTrue();
    }

    @Test
    void fieldReadFailureClosesSourceAndReturnsErrorThenSerializerCanBeReused() {
        var jsons = bounded(4);
        var input = new FaultInput(2);
        assertThat(jsons.serialize(new Attachment(input)).isErr()).isTrue();
        assertThat(input.closed).isTrue();
        assertThat(jsons.serialize(new Attachment(new TrackedInput(new byte[] {104, 105}))).get())
                .isEqualTo("{\"content\":\"aGk=\"}");
    }

    @Test
    void rootStreamsFollowMapperOwnershipWhileFieldStreamsRemainOwned() {
        for (boolean callerOwnsRoot : new boolean[] {false, true}) {
            var config = JsonConfig.standard().addModule(new SimpleModule().addSerializer(InputStream.class, new InputStreamSerializer(4)));
            if (callerOwnsRoot) config.customizeBuilder(builder -> builder.disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                    .disable(StreamWriteFeature.AUTO_CLOSE_TARGET));
            var jsons = new Jsons(config.build());
            for (String content : new String[] {"{\"x\":1}", "{"}) {
                var root = new TrackedInput(content.getBytes(StandardCharsets.UTF_8));
                assertThat(jsons.deserialize(root, Object.class).isOk()).isEqualTo(content.length() > 1);
                assertThat(root.closed).isEqualTo(!callerOwnsRoot);
            }
            var root = new TrackedOutput(false);
            var field = new TrackedInput(new byte[1]);
            assertThat(jsons.serializeTo(new Attachment(field), root).isOk()).isTrue();
            assertThat(root.closed).isEqualTo(!callerOwnsRoot);
            assertThat(field.closed).isTrue();
            var failedOutput = new TrackedOutput(true);
            var failedField = new TrackedInput(new byte[1]);
            assertThat(jsons.serializeTo(new Attachment(failedField), failedOutput).isErr()).isTrue();
            assertThat(failedOutput.closed).isEqualTo(!callerOwnsRoot);
            assertThat(failedField.closed).isTrue();
        }
    }

    @Test
    void fixedSeedBudgetCasesAreReplayableAndDoNotPreventReuse() throws Exception {
        var random = new Random(210025L);
        for (int sample = 0; sample < 128; sample++) {
            int limit = 1 + random.nextInt(32);
            byte[] bytes = new byte[random.nextInt(limit + 4)];
            random.nextBytes(bytes);
            var jsons = bounded(limit);
            var input = new TrackedInput(bytes);
            var encoded = jsons.serialize(new Attachment(input));
            assertThat(encoded.isOk()).as("seed=210025 sample=%s limit=%s size=%s", sample, limit, bytes.length).isEqualTo(bytes.length <= limit);
            assertThat(input.closed).isTrue();
            var decoded = jsons.deserialize("{\"content\":\"" + Base64.getEncoder().encodeToString(bytes) + "\"}", Attachment.class);
            assertThat(decoded.isOk()).isEqualTo(bytes.length <= limit);
            if (decoded.isOk()) try (var content = decoded.get().content()) { assertThat(content.readAllBytes()).containsExactly(bytes); }
        }
    }

    private static Jsons bounded(int limit) {
        return new Jsons(JsonConfig.standard().addModule(new SimpleModule()
                .addSerializer(InputStream.class, new InputStreamSerializer(limit))
                .addDeserializer(InputStream.class, new InputStreamDeserializer(limit))).build());
    }

    static final class FaultInput extends InputStream {
        final int failAt;
        int reads;
        boolean closed;
        FaultInput(int failAt) { this.failAt = failAt; }
        @Override public int read() throws IOException {
            if (reads == failAt) throw new IOException("controlled source failure");
            reads++;
            return 0;
        }
        @Override public void close() { closed = true; }
    }

    static final class TrackedOutput extends java.io.OutputStream {
        final boolean fail;
        boolean closed;
        TrackedOutput(boolean fail) { this.fail = fail; }
        @Override public void write(int value) throws IOException { if (fail) throw new IOException("controlled output failure"); }
        @Override public void close() { closed = true; }
    }

    @Test
    void fieldDeserializerEnforcesDecodedByteBudgetForPaddedAndUnpaddedBase64() throws Exception {
        var module = new SimpleModule().addDeserializer(InputStream.class, new InputStreamDeserializer(4));
        var jsons = new Jsons(JsonConfig.standard().addModule(module).build());
        for (String input : new String[] {"AAAA", "AAAAAA==", "AAAAAA"}) {
            try (InputStream content = jsons.deserialize("{\"content\":\"" + input + "\"}", Attachment.class).get().content()) {
                assertThat(content.readAllBytes()).hasSize(input.equals("AAAA") ? 3 : 4).containsOnly((byte) 0);
            }
        }
        for (String input : new String[] {"AAAAAAA=", "AAAAAAA", "AAAAAAAAAAAA", "!invalid!"}) {
            assertThat(jsons.deserialize("{\"content\":\"" + input + "\"}", Attachment.class).isErr()).as(input).isTrue();
        }
    }

    @Test
    void fieldSerializerAcceptsLimitAndRejectsNextByteWhileClosingItsInput() {
        var module = new SimpleModule().addSerializer(InputStream.class, new InputStreamSerializer(4));
        var jsons = new Jsons(JsonConfig.standard().addModule(module).build());
        for (int size : new int[] {3, 4, 5}) {
            var input = new TrackedInput(new byte[size]);
            var result = jsons.serialize(new Attachment(input));
            assertThat(input.closed).as("source closed for %s bytes", size).isTrue();
            if (size == 5) assertThat(result.isErr()).isTrue();
            else assertThat(result.get()).isEqualTo(size == 3 ? "{\"content\":\"AAAA\"}" : "{\"content\":\"AAAAAA==\"}");
        }
    }

    static class TrackedInput extends ByteArrayInputStream {
        boolean closed;
        TrackedInput(byte[] bytes) { super(bytes); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
