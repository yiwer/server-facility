package example;

import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.JsonsRegistry;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.result.Result;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.StaticMessageSource;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Runtime assertions use the installed library and each Maven-resolved production graph. */
public final class PlatformConsumer {
    @SpringBootConfiguration @EnableAutoConfiguration
    static class Application {}

    @SpringBootConfiguration @EnableAutoConfiguration
    static class Overrides {
        @Bean CacheManager applicationCache() { return new ConcurrentMapCacheManager(); }
        @Bean("messageSource") @Primary MessageSource applicationMessages() {
            var source = new StaticMessageSource();
            source.addMessage("platform.message", Locale.ENGLISH, "application-owned");
            return source;
        }
        @Bean JsonMapper applicationMapper() { return JsonMapper.builder().build(); }
        @Bean Executor applicationExecutor() { return Runnable::run; }
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    static class JsonsOverride {
        @Bean Jsons applicationJsons() { return new Jsons(JsonMapper.builder().build()); }
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    static class AmbiguousMappers {
        @Bean JsonMapper firstMapper() { return JsonMapper.builder().build(); }
        @Bean JsonMapper secondMapper() { return JsonMapper.builder().build(); }
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    static class PrimaryMappers {
        @Bean @Primary JsonMapper primaryMapper() { return JsonMapper.builder().build(); }
        @Bean JsonMapper secondaryMapper() { return JsonMapper.builder().build(); }
    }

    public static void main(String[] args) throws Exception {
        String graph = args[0];
        String scenario = args.length == 1 ? "default" : args[1];
        require(Set.of("minimal", "no-jackson-module", "caffeine-only", "context-support-only", "cache-pair").contains(graph), "unknown graph " + graph);
        require(Set.of("default", "disabled", "override", "jsons-override", "ambiguous", "primary", "virtual").contains(scenario), "unknown scenario " + scenario);
        presence("com.github.benmanes.caffeine.cache.Caffeine", Set.of("caffeine-only", "cache-pair").contains(graph));
        presence("org.springframework.cache.caffeine.CaffeineCacheManager", Set.of("context-support-only", "cache-pair").contains(graph));
        presence("org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration", !graph.equals("no-jackson-module"));
        for (String absent : List.of("jakarta.servlet.Servlet", "org.springframework.web.servlet.DispatcherServlet",
                "org.hibernate.validator.HibernateValidator", "org.apache.tika.Tika", "org.apache.poi.ss.usermodel.Workbook",
                "org.junit.jupiter.api.Test", "org.springframework.boot.test.context.runner.ApplicationContextRunner")) presence(absent, false);
        Path artifact = Path.of(Result.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        require(artifact.toString().endsWith(".jar"), "library must be an installed jar: " + artifact);
        System.out.println("ARTIFACT " + artifact + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))));
        Class<?> configuration = switch (scenario) {
            case "override" -> Overrides.class;
            case "jsons-override" -> JsonsOverride.class;
            case "ambiguous" -> AmbiguousMappers.class;
            case "primary" -> PrimaryMappers.class;
            default -> Application.class;
        };
        var application = new SpringApplication(configuration);
        application.setWebApplicationType(WebApplicationType.NONE);
        var properties = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--facility.id.worker-id=1"));
        if (scenario.equals("disabled")) properties.addAll(List.of("--facility.cache.enabled=false", "--facility.idempotency.enabled=false"));
        if (scenario.equals("virtual")) properties.add("--spring.threads.virtual.enabled=true");
        try (var context = application.run(properties.toArray(String[]::new))) {
            require(!scenario.equals("ambiguous"), "two unqualified mappers must fail deterministically");
            boolean disabled = scenario.equals("disabled");
            require(context.getBeansOfType(CacheManager.class).size() == (disabled ? 0 : 1), "one cache manager for each enabled graph");
            require(context.getBeansOfType(IdempotencyStore.class).size() == (disabled ? 0 : 1), "non-web capability switch");
            if (!disabled) {
                CacheManager manager = context.getBean(CacheManager.class);
                String expected = graph.equals("cache-pair") && !scenario.equals("override")
                        ? "org.springframework.cache.caffeine.CaffeineCacheManager" : ConcurrentMapCacheManager.class.getName();
                require(manager.getClass().getName().equals(expected), "cache backend for " + graph + ": " + manager.getClass());
                var cache = manager.getCache("consumer");
                cache.put("key", "value");
                require("value".equals(cache.get("key", String.class)), "cache public API must work");
                if (scenario.equals("override")) require(manager == context.getBean("applicationCache"), "user cache must win");
            }
            if (graph.equals("no-jackson-module")) {
                require(context.getBeansOfType(JsonMapper.class).isEmpty(), "no implicit mapper without technology module");
                require(context.getBeansOfType(Jsons.class).isEmpty(), "JSON capability must back off without mapper");
            } else {
                require(context.getBeansOfType(Jsons.class).size() == 1, "one application JSON service");
                Jsons jsons = context.getBean(Jsons.class);
                require(context.getBean(JsonsRegistry.class).getDefault() == jsons, "application registry owns this JSON service");
                if (scenario.equals("jsons-override")) require(jsons == context.getBean("applicationJsons"), "user JSON service must win");
                else require(jsons.mapper() == context.getBean(JsonMapper.class), "JSON must use selected application mapper");
                if (scenario.equals("primary")) require(jsons.mapper() == context.getBean("primaryMapper"), "explicit primary selects mapper");
                if (scenario.equals("override")) require(jsons.mapper() == context.getBean("applicationMapper"), "user mapper must win");
                require(jsons.serialize(Map.of("source", "consumer")).get().equals("{\"source\":\"consumer\"}"), "ordinary JSON public API");
            }
            if (scenario.equals("override")) {
                require(context.getMessage("platform.message", null, Locale.ENGLISH).equals("application-owned"), "application MessageSource wins");
                require(context.getBean("messageSource") == context.getBean(MessageSource.class), "unambiguous application MessageSource");
                require(context.getBean(Executor.class) == context.getBean("applicationExecutor"), "plain user Executor wins");
            }
            Executor executor = context.getBean(Executor.class);
            boolean virtual = CompletableFuture.supplyAsync(() -> Thread.currentThread().isVirtual(), executor).get(5, TimeUnit.SECONDS);
            require(virtual == scenario.equals("virtual"), "Boot/user execution policy must be observed on actual worker");
        } catch (RuntimeException failure) {
            if (!scenario.equals("ambiguous")) throw failure;
            Throwable cause = failure;
            for (int depth = 0; depth < 64 && !(cause instanceof org.springframework.beans.factory.NoUniqueBeanDefinitionException); depth++) {
                if (cause.getCause() == null) break;
                cause = cause.getCause();
            }
            require(cause instanceof org.springframework.beans.factory.NoUniqueBeanDefinitionException
                    && cause.getMessage().contains("firstMapper") && cause.getMessage().contains("secondMapper"), "actionable mapper ambiguity: " + failure);
        }
        System.out.println("PLATFORM_CONSUMER_OK " + graph + " " + scenario);
    }

    static void presence(String type, boolean expected) throws Exception {
        try {
            Class.forName(type, false, PlatformConsumer.class.getClassLoader());
            require(expected, "unexpected dependency leaked: " + type);
        } catch (ClassNotFoundException missing) {
            require(!expected, "required graph dependency absent: " + type);
        }
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

