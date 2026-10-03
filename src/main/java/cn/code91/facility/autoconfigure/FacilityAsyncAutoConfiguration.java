package cn.code91.facility.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

/**
 * Lets Boot and user executors win by type. Consumers inject the chosen Executor and
 * pass it to Async; static Async factories do not look up application contexts.
 */
@AutoConfiguration
@AutoConfigureAfter(TaskExecutionAutoConfiguration.class)
public class FacilityAsyncAutoConfiguration {
    /**
     * Container-owned fallback: four platform workers, 256 queued tasks, abort on
     * saturation, interrupt/cancel on shutdown and wait at most one second.
     * An uncooperative task may remain alive after that wait; inspect the underlying
     * ExecutorService's isTerminated(). User/Boot executor shutdown policies stay theirs.
     */
    @Bean(name = "facilityAsyncExecutor")
    @ConditionalOnMissingBean(Executor.class)
    public ThreadPoolTaskExecutor facilityAsyncExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("facility-async-");
        executor.setDaemon(true);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(30);
        // Skip the earlier SmartLifecycle drain: destruction interrupts instead of
        // waiting an additional container-wide lifecycle phase for blocked work.
        executor.setAcceptTasksAfterContextClose(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationMillis(1000);
        return executor;
    }
}
