package com.example.api;

import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** The test JVM owns the shared Tomcat home; individual applications still own disposable base directories. */
class TestHostLifecycleTest {
    @Test void tomcatHomeOutlivesAnIndividualApplicationAndItsDisposedBaseDirectory() throws Exception {
        Path home, base;
        try (var issuer = new TestIssuer()) {
            try (var app = new RunningApp(issuer)) {
                assertThat(app.get("/health", null).statusCode()).isEqualTo(200);
                home = Path.of(System.getProperty("catalina.home"));
                base = Path.of(System.getProperty("catalina.base"));
            }
            assertThat(base).doesNotExist();
            assertThat(home).isDirectory();
            // Closing the first server must not leave a JVM-global home pointing at
            // its deleted base. Real subsequent servers still initialize in parallel.
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> new RunningApp(issuer));
                var second = executor.submit(() -> new RunningApp(issuer));
                try (var appA = first.get(45, TimeUnit.SECONDS);
                     var appB = second.get(45, TimeUnit.SECONDS)) {
                    assertThat(appA.get("/health", null).statusCode()).isEqualTo(200);
                    assertThat(appB.get("/health", null).statusCode()).isEqualTo(200);
                    assertThat(Path.of(System.getProperty("catalina.home"))).isEqualTo(home);
                }
            }
        }
    }
}
