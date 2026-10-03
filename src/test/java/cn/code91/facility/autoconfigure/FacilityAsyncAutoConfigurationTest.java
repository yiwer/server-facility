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
    @Test
    void bootPlatformAndVirtualSettingsControlActualAsyncExecution() {
        for (boolean virtual : new boolean[]{false, true}) {
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                    FacilityAsyncAutoConfiguration.class, TaskExecutionAutoConfiguration.class))
                    .withPropertyValues("spring.threads.virtual.enabled=" + virtual,
                            "spring.task.execution.thread-name-prefix=boot-contract-")
                    .run(ctx -> {
                        var executor = ctx.getBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME, Executor.class);
                        var observed = cn.code91.facility.async.Async.supply(() -> Thread.currentThread().isVirtual(), executor)
                                .map(value -> value && Thread.currentThread().isVirtual()).awaitValue();
                        assertThat(observed).isEqualTo(virtual);
                        assertThat(cn.code91.facility.async.Async.supply(() -> Thread.currentThread().getName(), executor).awaitValue())
                                .startsWith("boot-contract-");
                    });
        }
    }

    @Test
    void closingOneApplicationDoesNotAffectAnotherApplicationExecutor() {
        var first = new java.util.concurrent.atomic.AtomicReference<Executor>();
        runner.run(ctx -> { first.set(ctx.getBean(Executor.class));
            assertThat(cn.code91.facility.async.Async.supply(() -> "first", first.get()).awaitValue()).isEqualTo("first"); });
        runner.run(ctx -> {
            var second = ctx.getBean(Executor.class);
            assertThat(cn.code91.facility.async.Async.supply(() -> "closed", first.get()).await().getErr())
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            assertThat(cn.code91.facility.async.Async.supply(() -> "second", second).awaitValue()).isEqualTo("second");
        });
    }

    @Test
    void saturatedFallbackRejectsAndShutdownCancelsQueuedTasks() {
        runner.run(ctx -> {
            var executor = ctx.getBean(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
            executor.setCorePoolSize(1); executor.setMaxPoolSize(1);
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var running = cn.code91.facility.async.Async.run(() -> { entered.countDown(); release.await(); }, executor).submit();
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var pending = new java.util.ArrayList<java.util.concurrent.CompletableFuture<cn.code91.facility.result.Result<String, Throwable>>>();
            try {
                for (int i = 0; i < 256; i++) pending.add(cn.code91.facility.async.Async.supply(() -> "queued", executor).submit());
                assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(256);
                assertThat(cn.code91.facility.async.Async.supply(() -> "overflow", executor).await().getErr())
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                executor.shutdown();
                for (var future : pending) assertThat(future.get(2, java.util.concurrent.TimeUnit.SECONDS).getErr())
                        .isInstanceOf(java.util.concurrent.CancellationException.class);
                assertThat(running.get(2, java.util.concurrent.TimeUnit.SECONDS).getErr()).isInstanceOf(InterruptedException.class);
                assertThat(executor.getThreadPoolExecutor().isTerminated()).isTrue();
                assertThat(executor.getThreadPoolExecutor().getQueue()).isEmpty();
            } finally { release.countDown(); executor.shutdown(); }
        });
    }
    @Test
    void contextCloseIsBoundedWhenTaskIgnoresInterruption() throws Exception {
        var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext(FacilityAsyncAutoConfiguration.class);
        var executor = context.getBean("facilityAsyncExecutor", Executor.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var closed = new java.util.concurrent.CountDownLatch(1);
        var task = cn.code91.facility.async.Async.run(() -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { /* deliberately uncooperative */ }
            }
        }, executor).submit();
        assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        Thread.ofPlatform().daemon().start(() -> { context.close(); closed.countDown(); });
        try {
            assertThat(closed.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(task.isDone()).isFalse();
            assertThat(cn.code91.facility.async.Async.supply(() -> "late", executor).await().getErr())
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        } finally {
            release.countDown();
            assertThat(closed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            task.get(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    @Test
    void userPlainExecutorSuppressesFacilityDefaultAndRunsAsync() {
        Executor custom = Runnable::run;
        runner.withBean("customExecutor", Executor.class, () -> custom).run(ctx -> {
            assertThat(ctx).doesNotHaveBean("facilityAsyncExecutor");
            assertThat(cn.code91.facility.async.Async.supply(() -> "ok", ctx.getBean(Executor.class)).awaitValue()).isEqualTo("ok");
        });
    }

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
