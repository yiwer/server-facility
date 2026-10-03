package example;

import cn.code91.facility.id.support.SnowIdGenerator;
import cn.code91.facility.id.FacilityIdProperties;
import cn.code91.facility.result.Result;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Assertions at the published artifact and real application boundary; no library test classes. */
public final class Consumer {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class OverrideApplication {
        @Bean
        SnowIdGenerator applicationIds() {
            var properties = new FacilityIdProperties();
            properties.setWorkerId(1);
            properties.setDataCenterId(0);
            return new SnowIdGenerator(properties);
        }
    }

    public static void main(String[] args) throws Exception {
        String scenario = args.length == 0 ? "configured" : args[0];
        require(java.util.Set.of("configured", "override", "invalid").contains(scenario), "unknown scenario: " + scenario);
        for (String absent : java.util.List.of("org.hibernate.validator.HibernateValidator", "jakarta.servlet.Servlet",
                "org.apache.poi.ss.usermodel.Workbook", "org.apache.tika.Tika", "com.github.benmanes.caffeine.cache.Caffeine")) {
            try {
                Class.forName(absent);
                throw new AssertionError("Optional/test dependency leaked into minimal consumer: " + absent);
            } catch (ClassNotFoundException expected) {
                // The minimal consumer has only the library's declared runtime dependency graph.
            }
        }
        var location = Path.of(Result.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        require(location.toString().endsWith(".jar"), "facility must load from an installed ordinary jar: " + location);
        try (var jar = new JarFile(location.toFile())) {
            require(jar.stream().noneMatch(e -> e.getName().startsWith("BOOT-INF/")), "library must not be repackaged");
            int classes = 0;
            for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                try (var input = new DataInputStream(jar.getInputStream(entry))) {
                    require(input.readInt() == 0xcafebabe, "invalid class: " + entry);
                    require(input.readUnsignedShort() == 0, "preview bytecode is forbidden: " + entry);
                    require(input.readUnsignedShort() == 69, "expected Java 25 class major 69: " + entry);
                }
                classes++;
            }
            require(classes > 0, "library jar must contain classes");
            var imports = jar.getJarEntry("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
            require(imports != null, "published jar must contain auto-configuration imports");
            var metadata = jar.getJarEntry("META-INF/spring-configuration-metadata.json");
            require(metadata != null, "published jar must contain configuration metadata");
            try (var input = jar.getInputStream(metadata)) {
                require(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("facility.id.worker-id"),
                        "metadata must describe consumer configuration");
            }
            System.out.println("ARTIFACT_OK classes=" + classes + " major=69 preview=false location=" + location);
        }
        var application = new SpringApplication(scenario.equals("override") ? OverrideApplication.class : Application.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        if (scenario.equals("invalid")) {
            try (var unexpected = application.run("--spring.main.banner-mode=off", "--facility.id.worker-id=4")) {
                throw new AssertionError("Invalid consumer configuration must prevent startup");
            } catch (RuntimeException failure) {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                require(cause instanceof IllegalArgumentException && cause.getMessage().contains("workerId out of range"),
                        "Invalid configuration must have an actionable diagnostic: " + cause);
            }
            System.out.println("CONSUMER_OK invalid");
            return;
        }
        try (var context = application.run("--spring.main.banner-mode=off", "--facility.id.worker-id=3",
                "--facility.id.data-center-id=2")) {
            var ids = context.getBean(SnowIdGenerator.class);
            long id = ids.nextId();
            boolean override = scenario.equals("override");
            require(context.getBeansOfType(SnowIdGenerator.class).size() == 1, "one unambiguous ID generator");
            require(SnowIdGenerator.parseWorkerId(id) == (override ? 1 : 3), "user configuration/bean must affect generated IDs");
            require(SnowIdGenerator.parseDataCenterId(id) == (override ? 0 : 2), "user configuration/bean must affect generated IDs");
            require(Result.<String, String>ok("published-api").get().equals("published-api"), "public Result API");
        }
        System.out.println("CONSUMER_OK " + scenario);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
