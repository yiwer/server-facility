package cn.code91.facility.web.filter;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class MdcFailureHttpContractTest {
    @TempDir Path directory;
    @ParameterizedTest @ValueSource(strings = {"get", "put", "rollback", "post", "servlet"}) @Timeout(45)
    void partialMdcInstallationAlwaysClearsIdentityAndRetainsTheOriginalFailure(String mode) throws Exception {
        Path evidence = Path.of(".verification-results", "ticket-06", "mdc-child-" + mode + ".log").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx128m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Dslf4j.provider=" + FaultingMdcProvider.class.getName(), "-Dfacility.test.mdcFault=" + mode,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                MdcFailureProcess.class.getName(), directory.toString()).redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        try {
            assertThat(process.waitFor(35, TimeUnit.SECONDS)).as("child deadline: %s", evidence).isTrue();
            String output = Files.readString(evidence);
            assertThat(process.exitValue()).as(output).isZero(); assertThat(output).contains("MDC_RECOVERY_OK mode=" + mode);
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
}
