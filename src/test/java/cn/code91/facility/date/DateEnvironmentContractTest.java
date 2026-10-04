package cn.code91.facility.date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class DateEnvironmentContractTest {
    @TempDir Path directory;

    @Test void eachLegacyCallUsesTheCurrentDefaultFormatLocale() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
                Path.of("target/test-classes").toAbsolutePath().toUri().toASCIIString() + " "
                        + Path.of("target/classes").toAbsolutePath().toUri().toASCIIString());
        try (var ignored = new JarOutputStream(Files.newOutputStream(directory.resolve("launcher.jar")), manifest)) {
            // URI manifest entries preserve class paths when native Windows argv cannot encode them.
        }
        Path log = directory.resolve("locale.log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx32m", "-cp", "launcher.jar", LocaleProbe.class.getName())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(child.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).as(Files.readString(log)).isZero();
        } finally {
            if (child.isAlive()) child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }

    public static class LocaleProbe {
        public static void main(String[] args) {
            LocalDate day = LocalDate.of(2025, 1, 2);
            Locale.setDefault(Locale.Category.FORMAT, Locale.US);
            check("Jan".equals(DateUtil.format(day, "MMM").get()), "US month");
            Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);
            check("janv.".equals(DateUtil.format(day, "MMM").get()), "French month after US call");
            check(day.equals(DateUtil.parseDate("02 janv. 2025", "dd MMM uuuu").get()), "French parse");
            Locale.setDefault(Locale.Category.FORMAT, Locale.US);
            check(day.equals(DateUtil.parseDate("02 Jan 2025", "dd MMM uuuu").get()), "US parse after French call");
        }
        private static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }
}
