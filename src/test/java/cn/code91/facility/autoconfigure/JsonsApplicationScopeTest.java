package cn.code91.facility.autoconfigure;

import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.support.JsonConfig;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JsonsApplicationScopeTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, FacilityJsonAutoConfiguration.class));

    public record Customer(String displayName) {}

    @Test
    void explicitApplicationJsonsWinsOverAutomaticMapperWrapping() {
        Jsons supplied = new Jsons(JsonConfig.standard().customize(mapper ->
                mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)).build());
        runner.withBean(Jsons.class, () -> supplied).run(context -> {
            assertThat(context).hasSingleBean(Jsons.class);
            assertThat(context.getBean(Jsons.class).serialize(new Customer("mine")).get())
                    .isEqualTo("{\"display-name\":\"mine\"}");
        });
    }

    @Test
    void absentMapperDoesNotInventAnApplicationJsons() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FacilityJsonAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(Jsons.class));
    }

    @Test
    void injectedJsonsUsesItsApplicationsPolicyAcrossAnotherApplicationsCloseAndRebuild() {
        runner.withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE").run(first -> {
            Jsons service = first.getBean(Jsons.class);
            String expected = "{\"display_name\":\"A\"}";
            assertThat(service.serialize(new Customer("A")).get()).isEqualTo(expected);
            runner.run(second -> {
                assertThat(second.getBean(Jsons.class).serialize(new Customer("B")).get())
                        .isEqualTo("{\"displayName\":\"B\"}");
                assertThat(service.serialize(new Customer("A")).get()).isEqualTo(expected);
            });
            assertThat(service.serialize(new Customer("A")).get()).isEqualTo(expected);
            runner.run(rebuilt -> assertThat(rebuilt.getBean(Jsons.class).serialize(new Customer("B2")).get())
                    .isEqualTo("{\"displayName\":\"B2\"}"));
            assertThat(service.serialize(new Customer("A")).get()).isEqualTo(expected);
        });
    }
}
