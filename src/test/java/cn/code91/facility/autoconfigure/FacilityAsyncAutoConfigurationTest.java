package cn.code91.facility.autoconfigure;

import cn.code91.facility.async.DefaultAsync;
import cn.code91.facility.async.Async;
import cn.code91.facility.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @ParameterizedTest(name = "{0} versus BOOT_VIRTUAL; close virtual first={1}")
    @CsvSource({"FALLBACK,false", "FALLBACK,true", "BOOT_PLATFORM,false", "BOOT_PLATFORM,true"})
    void closingOneApplicationDoesNotAffectAnotherApplicationExecutor(ExecutorPolicy firstPolicy,
                                                                     boolean closeVirtualFirst) {
        applicationRunner(firstPolicy).run(firstContext -> applicationRunner(ExecutorPolicy.BOOT_VIRTUAL).run(secondContext -> {
            try (var first = new ExecutorApplication(firstContext, firstPolicy);
                 var second = new ExecutorApplication(secondContext, ExecutorPolicy.BOOT_VIRTUAL);
                 var inFlight = new InFlightTask(closeVirtualFirst ? first : second)) {
                assertThat(firstContext.isActive()).isTrue();
                assertThat(secondContext.isActive()).isTrue();
                assertThat(first.executor).isNotSameAs(second.executor);
                first.probe("first-1"); second.probe("second-1");
                first.probe("first-2"); second.probe("second-2");

                var closing = closeVirtualFirst ? second : first;
                var survivor = closeVirtualFirst ? first : second;
                inFlight.start();
                assertThat(inFlight.entered.await(5, TimeUnit.SECONDS)).isTrue();
                closing.close();
                closing.assertRejected();
                assertThat(survivor.context.isActive()).isTrue();
                assertThat(inFlight.future.isDone()).isFalse();
                assertThat(inFlight.worker.get().isAlive()).isTrue();
                assertThat(inFlight.interrupted).isFalse();

                inFlight.releaseAndVerify();
                survivor.probe("survivor-after-close");
                survivor.close();
                survivor.assertRejected();
            }
        }));
    }

    private ApplicationContextRunner applicationRunner(ExecutorPolicy policy) {
        if (policy == ExecutorPolicy.FALLBACK) return runner;
        var boot = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                FacilityAsyncAutoConfiguration.class, TaskExecutionAutoConfiguration.class))
                .withPropertyValues("spring.threads.virtual.enabled=" + policy.virtual,
                        "spring.task.execution.thread-name-prefix=" + policy.prefix);
        return policy.virtual ? boot : boot.withPropertyValues("spring.task.execution.pool.core-size=1",
                "spring.task.execution.pool.max-size=1", "spring.task.execution.pool.queue-capacity=8");
    }

    private enum ExecutorPolicy {
        FALLBACK(false, "facility-async-"), BOOT_PLATFORM(false, "j05-platform-"), BOOT_VIRTUAL(true, "j05-virtual-");
        final boolean virtual;
        final String prefix;
        ExecutorPolicy(boolean virtual, String prefix) { this.virtual = virtual; this.prefix = prefix; }
    }

    /** Own direct worker references; virtual threads need not appear in global thread enumeration. */
    private static final class ExecutorApplication implements AutoCloseable {
        final ConfigurableApplicationContext context;
        final Executor executor;
        final ExecutorPolicy policy;
        final Set<Thread> workers = ConcurrentHashMap.newKeySet();
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closeThread;

        ExecutorApplication(ConfigurableApplicationContext context, ExecutorPolicy policy) {
            this.context = context; this.policy = policy;
            String name = policy == ExecutorPolicy.FALLBACK ? "facilityAsyncExecutor"
                    : TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME;
            executor = context.getBean(name, Executor.class);
            if (policy != ExecutorPolicy.FALLBACK) assertThat(context.containsBean("facilityAsyncExecutor")).isFalse();
            if (!policy.virtual) {
                assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
                var pool = (ThreadPoolTaskExecutor) executor;
                assertThat(pool.getCorePoolSize()).isEqualTo(policy == ExecutorPolicy.FALLBACK ? 4 : 1);
                assertThat(pool.getMaxPoolSize()).isEqualTo(policy == ExecutorPolicy.FALLBACK ? 4 : 1);
                assertThat(pool.getQueueCapacity()).isEqualTo(policy == ExecutorPolicy.FALLBACK ? 256 : 8);
            }
        }

        Thread capturePolicy() {
            Thread worker = Thread.currentThread(); workers.add(worker);
            assertThat(worker.isVirtual()).isEqualTo(policy.virtual);
            assertThat(worker.getName()).startsWith(policy.prefix);
            return worker;
        }

        void probe(String expected) throws Exception {
            var result = Async.supply(() -> { capturePolicy(); return expected; }, executor)
                    .map(value -> { capturePolicy(); return value; }).submit().get(5, TimeUnit.SECONDS);
            assertThat(result.isOk()).as("probe %s: %s", expected, result).isTrue();
            assertThat(result.get()).isEqualTo(expected);
        }

        void assertRejected() throws Exception {
            var result = Async.supply(() -> "closed", executor).submit().get(5, TimeUnit.SECONDS);
            assertThat(result.getErr()).isInstanceOf(RejectedExecutionException.class);
        }

        @Override public void close() throws Exception {
            if (closeThread == null) {
                closeThread = Thread.ofPlatform().daemon().name("j05-close-" + policy).start(() -> {
                    try { context.close(); } catch (Throwable failure) { closeFailure.set(failure); }
                    finally { closed.countDown(); }
                });
            }
            assertThat(closed.await(5, TimeUnit.SECONDS)).as("context close %s", policy).isTrue();
            assertThat(closeThread.join(Duration.ofSeconds(5))).as("close helper %s stopped", policy).isTrue();
            assertThat(closeFailure.get()).isNull();
            if (executor instanceof ThreadPoolTaskExecutor pool) {
                assertThat(pool.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getThreadPoolExecutor().isTerminated()).isTrue();
            }
            for (Thread worker : workers) {
                assertThat(worker.join(Duration.ofSeconds(5))).as("owned worker %s stopped", worker.getName()).isTrue();
                assertThat(worker.isAlive()).isFalse();
            }
        }
    }

    /** Registered before any probe; cleanup releases the task before either application is closed. */
    private static final class InFlightTask implements AutoCloseable {
        final ExecutorApplication owner;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean interrupted = new AtomicBoolean();
        final AtomicReference<Thread> worker = new AtomicReference<>();
        CompletableFuture<Result<String, Throwable>> future;
        InFlightTask(ExecutorApplication owner) { this.owner = owner; }

        void start() {
            future = Async.supply(() -> {
                worker.set(owner.capturePolicy()); entered.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new TimeoutException("test barrier was not released");
                } catch (InterruptedException failure) { interrupted.set(true); throw failure; }
                return "survivor-in-flight";
            }, owner.executor).submit();
        }

        void releaseAndVerify() throws Exception {
            release.countDown();
            var result = future.get(5, TimeUnit.SECONDS);
            assertThat(result.isOk()).as("in-flight result: %s", result).isTrue();
            assertThat(result.get()).isEqualTo("survivor-in-flight");
            assertThat(interrupted).isFalse();
        }

        @Override public void close() throws Exception {
            release.countDown();
            if (future != null) future.get(5, TimeUnit.SECONDS);
        }
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
