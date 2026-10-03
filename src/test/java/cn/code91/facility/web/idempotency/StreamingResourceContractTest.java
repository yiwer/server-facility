package cn.code91.facility.web.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingResourceContractTest {
    @TempDir Path directory;

    @Test @Timeout(60)
    void growingResponsesRemainBoundedInAnIndependent96MiBHeap() throws Exception {
        Path evidence = Path.of(".verification-results", "ticket-05", "heap-child.log").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx96m", "-XX:MaxDirectMemorySize=32m", "-XX:ActiveProcessorCount=2",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                StreamingResourceProcess.class.getName(), directory.toString())
                .redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        try {
            assertThat(process.waitFor(50, TimeUnit.SECONDS)).as("child exit deadline; log: %s", evidence).isTrue();
            String output = Files.readString(evidence);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("RESOURCE_OK", "bytes=268435456", "captureBudget=65536");
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
