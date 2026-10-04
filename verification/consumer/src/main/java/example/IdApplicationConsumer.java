package example;

import cn.code91.facility.id.support.SnowIdGenerator;
import cn.code91.facility.json.Jsons;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;

/** Application-owned UUID generation and existing numeric wire protocol, through a business operation. */
public class IdApplicationConsumer {
    public record Draft(UUID id, String title) { }
    static final class Drafts {
        private final Supplier<UUID> ids;
        Drafts(Supplier<UUID> ids) { this.ids = ids; }
        Draft create(String title) {
            if (title == null || title.isBlank() || title.length() > 80) throw new IllegalArgumentException("Invalid title");
            return new Draft(Objects.requireNonNull(ids.get(), "application identifier"), title);
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class DraftConfiguration {
        @Bean Drafts drafts(@Qualifier("draftIds") org.springframework.beans.factory.ObjectProvider<Supplier<UUID>> ids) {
            return new Drafts(ids.getIfAvailable(() -> UUID::randomUUID));
        }
    }
    @SpringBootConfiguration @EnableAutoConfiguration @Import(DraftConfiguration.class)
    static class Application { }
    @SpringBootConfiguration @EnableAutoConfiguration @Import(DraftConfiguration.class)
    static class Customized {
        @Bean("draftIds") Supplier<UUID> selectedIds() {
            return () -> UUID.fromString("6f9619ff-8b86-4e8a-bf2e-33f69d84314f");
        }
    }
    public static void main(String[] args) throws Exception {
        try { verifyApplications(); }
        catch (Exception | AssertionError failure) { failure.printStackTrace(); throw failure; }
    }
    private static void verifyApplications() {
        try (var first = start(Application.class); var second = start(Application.class); var custom = start(Customized.class)) {
            for (var context : List.of(first, second, custom)) check(context.getBeansOfType(SnowIdGenerator.class).isEmpty(), "implicit Snow node");
            var firstDraft = first.getBean(Drafts.class).create("first");
            var secondDraft = second.getBean(Drafts.class).create("second");
            check(firstDraft.id().version() == 4 && secondDraft.id().version() == 4, "default UUID v4");
            check(!firstDraft.id().equals(secondDraft.id()), "finite independent application sample collided");
            var selected = custom.getBean(Drafts.class).create("application title");
            check(selected.id().toString().equals("6f9619ff-8b86-4e8a-bf2e-33f69d84314f") && selected.title().equals("application title"), "user factory not observed by operation");
            var json = second.getBean(Jsons.class);
            check(json.serializeUnsafe(Map.of("id", 36028797018963967L)).equals("{\"id\":36028797018963967}"), "legacy numeric JSON");
            check(json.serializeUnsafe(Map.of("id", "36028797018963967")).equals("{\"id\":\"36028797018963967\"}"), "explicit decimal string JSON");
            check(json.serializeUnsafe(Map.of("id", selected.id())).equals("{\"id\":\"6f9619ff-8b86-4e8a-bf2e-33f69d84314f\"}"), "UUID JSON");
            first.close();
            check(second.getBean(Drafts.class).create("after sibling close").id().version() == 4, "sibling lifetime pollution");
            check(custom.getBean(Drafts.class).create("after sibling close").id().equals(selected.id()), "custom policy lost");
        }
        System.out.println("ID_APPLICATION_CONSUMER_PASS default/custom UUID operation; sibling close; old numeric and explicit string JSON");
    }
    static org.springframework.context.ConfigurableApplicationContext start(Class<?> source) {
        var application = new SpringApplication(source); application.setWebApplicationType(WebApplicationType.NONE);
        return application.run("--spring.main.banner-mode=off");
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
