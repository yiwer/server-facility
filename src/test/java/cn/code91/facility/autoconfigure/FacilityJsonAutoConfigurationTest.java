package cn.code91.facility.autoconfigure;

import cn.code91.facility.json.JsonUtil;
import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.JsonsRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityJsonAutoConfiguration - 装配 + REGISTRY 单例突变 (RV2-19 + RV2-20)")
class FacilityJsonAutoConfigurationTest {

    private Jsons originalDefault;

    @BeforeEach
    void capture() {
        // R6：保存进程级单例的原默认 namespace，测试后还原，防污染其他测试
        originalDefault = JsonUtil.registry().getDefault();
    }

    @AfterEach
    void restore() {
        JsonUtil.registry().register(JsonsRegistry.DEFAULT, originalDefault);
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, FacilityJsonAutoConfiguration.class));

    @Test @DisplayName("有 ObjectMapper 时装配 jsonsRegistry bean")
    void registersWhenObjectMapperPresent() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(JsonsRegistry.class));
    }

    @Test @DisplayName("RV2-20: 装配后进程级 JsonUtil 默认 namespace 切换（不再是原 default）")
    void mutatesProcessSingletonDefault() {
        runner.run(ctx -> assertThat(JsonUtil.registry().getDefault()).isNotSameAs(originalDefault));
    }

    @Test @DisplayName("无 ObjectMapper（@ConditionalOnBean 缺席）→ 不装配")
    void skipsWhenNoObjectMapper() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityJsonAutoConfiguration.class))
            .run(ctx -> assertThat(ctx).doesNotHaveBean(JsonsRegistry.class));
    }
}
