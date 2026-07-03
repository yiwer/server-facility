package cn.code91.facility.autoconfigure;

import cn.code91.facility.async.DefaultAsync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
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

    /**
     * 与 Spring Boot {@code TaskExecutionAutoConfiguration} 联合装配时，字母序
     * cn.code91.* 先于 org.springframework.*，若无 {@code @AutoConfigureAfter} 约束，
     * facility 会先注册裸 Executor bean，导致 Boot 的 applicationTaskExecutor
     * （@ConditionalOnMissingBean(Executor.class)）条件不满足而缺席——"只兜底不抢占"
     * 的 ADR-0002 本意被打破。
     */
    @Test
    @DisplayName("联合 Boot TaskExecutionAutoConfiguration 时，Boot applicationTaskExecutor 胜出（不被字母序抢注压制）")
    void combinedWithBootTaskExecution_bootApplicationTaskExecutorWins() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                FacilityAsyncAutoConfiguration.class, TaskExecutionAutoConfiguration.class))
            .run(ctx -> {
                assertThat(ctx).hasBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME);
                assertThat(ctx).doesNotHaveBean("facilityAsyncExecutor");
            });
    }
}
