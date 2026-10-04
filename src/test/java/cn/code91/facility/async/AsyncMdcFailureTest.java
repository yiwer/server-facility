package cn.code91.facility.async;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class AsyncMdcFailureTest {
    @ParameterizedTest
    @ValueSource(strings = {"caller-snapshot", "snapshot", "install", "rollback", "restore-success", "restore-failure", "restore-same"})
    @Timeout(30)
    void mdcProviderFailuresSettleThePublicResultAndKeepWorkerOwnership(String mode) throws Exception {
        Path evidence = Path.of(".verification-results", "ticket-33-review", "async-mdc-" + mode + ".log").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        String java = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", java).toString(),
                "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                "-Dslf4j.provider=" + AsyncMdcFailureProcess.FaultingMdcProvider.class.getName(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AsyncMdcFailureProcess.class.getName(), mode).redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("child deadline: %s", evidence).isTrue();
            String output = Files.readString(evidence);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("ASYNC_MDC_FAILURE_PASS mode=" + mode, "WORKER_TERMINATED");
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
