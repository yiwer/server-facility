package cn.code91.facility.autoconfigure;

import cn.code91.facility.json.JsonUtil;
import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.JsonsRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityJsonAutoConfiguration - 应用 registry 与静态入口隔离")
class FacilityJsonAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, FacilityJsonAutoConfiguration.class));

    @Test @DisplayName("有 ObjectMapper 时装配 jsonsRegistry bean")
    void registersWhenObjectMapperPresent() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(JsonsRegistry.class));
    }

    @Test @DisplayName("装配拥有独立 registry，不改写旧静态默认值")
    void applicationRegistryDoesNotMutateProcessSingleton() {
        Jsons original = JsonUtil.registry().getDefault();
        runner.run(ctx -> {
            assertThat(ctx.getBean(JsonsRegistry.class)).isNotSameAs(JsonUtil.registry());
            assertThat(ctx.getBean(JsonsRegistry.class).getDefault()).isSameAs(ctx.getBean(Jsons.class));
            assertThat(JsonUtil.registry().getDefault()).isSameAs(original);
        });
        assertThat(JsonUtil.registry().getDefault()).isSameAs(original);
    }

    @Test @DisplayName("无 ObjectMapper（@ConditionalOnBean 缺席）→ 不装配")
    void skipsWhenNoObjectMapper() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityJsonAutoConfiguration.class))
            .run(ctx -> assertThat(ctx).doesNotHaveBean(JsonsRegistry.class));
    }
}
