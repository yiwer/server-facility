package cn.code91.facility.web.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UploadResourceContractTest {
    @TempDir Path directory;

    @Test @Timeout(60)
    void increasingInputAndRepeatedFailuresStayBoundedInAnIndependentHeap() throws Exception {
        Path evidence = Path.of(".verification-results", "ticket-13", "heap-child.log").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        Path temporary = Files.createDirectory(directory.resolve("temporary"));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx96m", "-XX:MaxDirectMemorySize=16m", "-XX:ActiveProcessorCount=2",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djava.io.tmpdir=" + temporary, "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                UploadResourceProcess.class.getName(), directory.resolve("uploads").toString())
                .redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        try {
            assertThat(process.waitFor(50, TimeUnit.SECONDS)).as("child exit deadline; log: %s", evidence).isTrue();
            String output = Files.readString(evidence);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("UPLOAD_RESOURCE_OK", "bytes=268435456", "failures=100", "temporaryFiles=0");
            try (var files = Files.list(temporary)) { assertThat(files).isEmpty(); }
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    @Test @Timeout(30)
    void optionalTikaAbsenceCannotTurnATypePolicyIntoSuccessfulStorage() throws Exception {
        Path evidence = Path.of(".verification-results", "ticket-13", "missing-tika-child.log").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        String classpath = java.util.Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                        .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(path -> !path.contains("tika-core"))
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx96m", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp",
                classpath, UploadResourceProcess.class.getName(), directory.resolve("uploads").toString(), "missing-tika")
                .redirectErrorStream(true).redirectOutput(evidence.toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            String output = Files.readString(evidence);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("MISSING_TIKA_REJECTED stageFiles=0 rawSaveWorks=true");
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
}
