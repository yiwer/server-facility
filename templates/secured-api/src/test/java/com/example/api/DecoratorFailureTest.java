package com.example.api;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class DecoratorFailureTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void partialInstallationRestoresTheWorkerAndRetainsTheOriginalFailure(boolean rollbackFailure) throws Exception {
        var command = new ArrayList<String>(); command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        // Keep Unicode paths out of the Windows native launcher argument boundary.
        // The JVM resolves the manifest's escaped file URIs after it has started.
        Path classpathJar = Path.of("target", "decorator-classpath-" + rollbackFailure + ".jar");
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
                Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                        .map(path -> Path.of(path).toAbsolutePath().toUri().toASCIIString())
                        .collect(Collectors.joining(" ")));
        try (var jar = new JarOutputStream(Files.newOutputStream(classpathJar), manifest)) { }
        command.addAll(List.of("-Xmx96m", "-Dslf4j.provider=com.example.fixtures.FaultingMdcProvider",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djacoco-agent.destfile=target/jacoco.exec", "-Djacoco-agent.append=true",
                "-Dprobe.rollbackFailure=" + rollbackFailure, "-cp", classpathJar.toString(),
                "com.example.fixtures.DecoratorFaultProcess"));
        Path log = Path.of("target", "decorator-failure-" + rollbackFailure + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            // Replacement decoding also preserves diagnostics from a native launch failure.
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).withFailMessage(output).isZero();
            assertThat(output).contains("DECORATOR_FAILURE_RECOVERY_PASS");
        } finally { if (process.isAlive()) process.destroyForcibly().waitFor(); }
    }
}
