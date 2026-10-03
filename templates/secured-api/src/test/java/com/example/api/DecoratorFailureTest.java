package com.example.api;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class DecoratorFailureTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void partialInstallationRestoresTheWorkerAndRetainsTheOriginalFailure(boolean rollbackFailure) throws Exception {
        var command = new ArrayList<String>(); command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:")).forEach(command::add);
        command.addAll(List.of("-Xmx96m", "-Dslf4j.provider=com.example.fixtures.FaultingMdcProvider",
                "-Dprobe.rollbackFailure=" + rollbackFailure, "-cp", System.getProperty("java.class.path"),
                "com.example.fixtures.DecoratorFaultProcess"));
        Path log = Path.of("target", "decorator-failure-" + rollbackFailure + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("DECORATOR_FAILURE_RECOVERY_PASS");
        } finally { if (process.isAlive()) process.destroyForcibly().waitFor(); }
    }
}
