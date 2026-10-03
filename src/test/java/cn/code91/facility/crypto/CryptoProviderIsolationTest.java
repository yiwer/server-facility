package cn.code91.facility.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoProviderIsolationTest {
    @Test
    void failingRealJcaProviderCannotExposeSecretsThroughPublicErrorsOrLogs() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var child = new ProcessBuilder(java, "-Xmx64m", "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), CryptoProviderFailureProbe.class.getName())
                .redirectErrorStream(true).start();
        try {
            assertThat(child.waitFor(20, TimeUnit.SECONDS)).as("owned JCA provider probe deadline").isTrue();
            String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(output).doesNotContain(CryptoProviderFailureProbe.SECRET);
            assertThat(child.exitValue()).withFailMessage("Provider probe failed: %s", output).isZero();
            assertThat(output).contains("CRYPTO_PROVIDER_FAILURES_SAFE");
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
