package cn.code91.facility.autoconfigure;

import cn.code91.facility.async.DefaultAsync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityAsyncAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityAsyncAutoConfiguration.class));

    @Test
    void registersFacilityAsyncExecutorAndNoDefaultAsyncBean() {
        runner.run(ctx -> {
            assertThat(ctx).hasBean("facilityAsyncExecutor");
            assertThat(ctx).doesNotHaveBean(DefaultAsync.class);
        });
    }

    /**
     * RP-04：条件为 @ConditionalOnMissingBean(TaskExecutor.class)。
     * 用户提供 TaskExecutor bean 时，facilityAsyncExecutor 不装配；
     * 已存在同名 TaskExecutor bean 覆盖场景验证。
     */
    @Test
    @DisplayName("用户提供 TaskExecutor bean 时，facilityAsyncExecutor 不覆盖（RP-04 类型匹配）")
    void userTaskExecutorSuppressesFacilityDefault() {
        TaskExecutor custom = command -> command.run();
        runner
            .withBean("customExecutor", TaskExecutor.class, () -> custom)
            .run(ctx -> {
                // facilityAsyncExecutor 条件 @ConditionalOnMissingBean(TaskExecutor.class) 不满足，不注册
                assertThat(ctx).doesNotHaveBean("facilityAsyncExecutor");
                // 用户 bean 正常可得
                assertThat(ctx.getBean(TaskExecutor.class)).isSameAs(custom);
            });
    }

    @Test
    @DisplayName("无用户 TaskExecutor 时，facilityAsyncExecutor 装配（RP-04 默认路径）")
    void noUserExecutorRegistersFacilityDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasBean("facilityAsyncExecutor");
            Executor executor = ctx.getBean("facilityAsyncExecutor", Executor.class);
            assertThat(executor).isNotNull();
        });
    }
}
