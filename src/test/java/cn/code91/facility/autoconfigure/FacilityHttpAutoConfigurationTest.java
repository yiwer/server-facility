package cn.code91.facility.autoconfigure;

import cn.code91.facility.http.FacilityHttpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityHttpAutoConfiguration - HTTP client 装配(默认 RestClient bean,可被用户 bean 覆盖)")
class FacilityHttpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityHttpAutoConfiguration.class));

    @Test
    @DisplayName("默认装配 RestClient bean")
    void registersRestClientByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(RestClient.class));
    }

    @Test
    @DisplayName("已存在用户 RestClient bean → facility 不再装配,沿用用户 bean")
    void userRestClient_backsOff() {
        RestClient userClient = RestClient.create();
        runner
            .withBean(RestClient.class, () -> userClient)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(RestClient.class);
                assertThat(ctx.getBean(RestClient.class)).isSameAs(userClient);
            });
    }

    @Test
    @DisplayName("properties 绑定:facility.http.read-timeout/connect-timeout 生效,RestClient 装配不抛")
    void propertiesBind_noException() {
        runner
            .withPropertyValues("facility.http.read-timeout=3s", "facility.http.connect-timeout=2s")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(RestClient.class);
                FacilityHttpProperties props = ctx.getBean(FacilityHttpProperties.class);
                assertThat(props.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                assertThat(props.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
            });
    }
}
