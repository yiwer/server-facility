package probe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import probe.model.ProbeProperties;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTest {
    @Test
    void targetPlatformAndJupiterAreActuallyLoaded() {
        assertFalse(Boolean.getBoolean("probe.fail.jupiter"), "intentional Jupiter discovery control");
        assertEquals("4.1.1", SpringBootVersion.getVersion());
        assertEquals("6.0.3", Test.class.getPackage().getImplementationVersion());
    }

    @Test
    void processorsAndClassfileWorkOnJava25WithoutPreview() throws Exception {
        var model = ProbeProperties.builder().limit(7).label("seven").build();
        assertEquals(7, model.limit());
        assertEquals("seven", model.label());
        try (var stream = new DataInputStream(ProbeProperties.class.getResourceAsStream("ProbeProperties.class"))) {
            assertEquals(0xcafebabe, stream.readInt());
            assertEquals(0, stream.readUnsignedShort(), "preview must remain disabled");
            assertEquals(69, stream.readUnsignedShort(), "Java 25 classfile");
        }
        var metadata = Files.readString(Path.of("target/classes/META-INF/spring-configuration-metadata.json"));
        assertTrue(metadata.contains("\"name\": \"probe.limit\""), metadata);
        assertTrue(metadata.contains("\"name\": \"probe.label\""), metadata);
    }
}
