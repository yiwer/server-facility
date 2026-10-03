package cn.code91.facility.autoconfigure;

import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.support.InputStreamDeserializer;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;

class JsonParserBudgetTest {
    public record Attachment(InputStream content) {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, FacilityJsonAutoConfiguration.class))
            .withBean(JsonMapperBuilderCustomizer.class, () -> builder -> builder.addModule(new SimpleModule()
                    .addDeserializer(InputStream.class, new InputStreamDeserializer(100_000))));

    @Test
    void applicationStringBudgetStopsReadingBeforeACompleteFieldCanBeAllocated() {
        String document = "{\"content\":\"" + "A".repeat(100_000) + "\"}";
        runner.run(unbounded -> {
            try (var content = unbounded.getBean(Jsons.class).deserialize(document, Attachment.class).get().content()) {
                assertThat(content.readAllBytes()).hasSize(75_000);
            }
        });
        runner.withBean(JsonFactoryBuilderCustomizer.class, () -> builder -> builder.streamReadConstraints(
                StreamReadConstraints.builder().maxDocumentLength(200_000).maxStringLength(32).build()))
                .run(context -> {
                    var source = new ChunkedInput(document);
                    var jsons = context.getBean(Jsons.class);
                    assertThat(jsons.deserialize(source, Attachment.class).isErr()).isTrue();
                    assertThat(source.consumed()).isLessThan(4_000);
                    assertThat(source.closed).isTrue();
                    try (var content = jsons.deserialize("{\"content\":\"aGk=\"}", Attachment.class).get().content()) {
                        assertThat(content.readAllBytes()).containsExactly(104, 105);
                    }
                });
    }

    @Test
    void applicationDocumentBudgetStopsManySmallValuesBeforeTheWholeDocumentIsRead() {
        String document = "[" + "0,".repeat(50_000) + "0]";
        runner.withBean(JsonFactoryBuilderCustomizer.class, () -> builder -> builder.streamReadConstraints(
                StreamReadConstraints.builder().maxDocumentLength(256).maxStringLength(1_000).build()))
                .run(context -> {
                    var source = new ChunkedInput(document);
                    var jsons = context.getBean(Jsons.class);
                    assertThat(jsons.deserialize(source, int[].class).isErr()).isTrue();
                    assertThat(source.consumed()).isLessThan(4_000);
                    assertThat(source.closed).isTrue();
                    assertThat(jsons.deserialize("[1,2]", int[].class).get()).containsExactly(1, 2);
                });
    }

    static final class ChunkedInput extends ByteArrayInputStream {
        boolean closed;
        ChunkedInput(String document) { super(document.getBytes(StandardCharsets.UTF_8)); }
        @Override public synchronized int read(byte[] target, int offset, int length) {
            return super.read(target, offset, Math.min(length, 64));
        }
        int consumed() { return pos; }
        @Override public void close() { closed = true; }
    }
}
