package cn.code91.facility.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoResourceTest {
    @Test
    void impossibleKeyLengthsAreRejectedWithoutDecodingTheirBody() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var child = new ProcessBuilder(java, "-Xmx32m", "-cp", System.getProperty("java.class.path"),
                CryptoResourceProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertThat(child.waitFor(20, TimeUnit.SECONDS)).as("owned resource probe deadline").isTrue();
            String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(child.exitValue()).withFailMessage("Resource probe failed: %s", output).isZero();
            assertThat(output).contains("CRYPTO_KEY_RESOURCE_BOUND_PASSED");
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
