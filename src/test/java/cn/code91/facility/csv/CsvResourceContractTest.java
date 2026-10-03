package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class CsvResourceContractTest {
    @TempDir Path temporary;

    @Test @Timeout(100)
    void increasingRowsAndMalformedInputStayBoundedInA64MiBProcess() throws Exception {
        Path evidence = Path.of(".verification-results/ticket-15/heap-child.log").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx64m", "-XX:MaxDirectMemorySize=8m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Djava.io.tmpdir=" + temporary,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                CsvResourceProcess.class.getName()).redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        try {
            assertThat(process.waitFor(90, TimeUnit.SECONDS)).as("CSV child deadline: %s", evidence).isTrue();
            String output = Files.readString(evidence);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("CSV_RESOURCE_OK", "rows=4000000", "failures=200");
            try (var files = Files.list(temporary)) { assertThat(files).isEmpty(); }
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
}
