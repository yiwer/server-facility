package cn.code91.facility.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * {@code @AutoConfigureAfter(TaskExecutionAutoConfiguration.class)}：显式排在 Boot 的任务
 * 执行装配之后。若无此约束，字母序 {@code cn.code91.*} 先于 {@code org.springframework.*}，
 * facility 会先注册裸 {@code facilityAsyncExecutor}（{@link Executor} 类型），导致 Boot 的
 * {@code applicationTaskExecutor}（{@code @ConditionalOnMissingBean(Executor.class)}）
 * 条件不满足而缺席——消费方因此静默丢失 Boot 的默认线程池。显式排后使 Boot 先注册
 * {@code applicationTaskExecutor}（{@link TaskExecutor} 类型），facility 的
 * {@code @ConditionalOnMissingBean(TaskExecutor.class)} 随之回避，"只兜底不抢占"
 * 的 ADR-0002 本意得以达成。
 */
@AutoConfiguration
@AutoConfigureAfter(TaskExecutionAutoConfiguration.class)
public class FacilityAsyncAutoConfiguration {

    /**
     * Facility 默认 virtual-thread Executor。仅在 ApplicationContext 中没有任何
     * {@link TaskExecutor} 类型的 bean 时装配（RP-04 类型匹配，与 Spring Boot
     * {@code TaskExecutionAutoConfiguration} 惯例对齐）。
     *
     * <p>详见 docs/adr/0002-rp-04-async-bean-type-matching.md</p>
     */
    @Bean(name = "facilityAsyncExecutor")
    @ConditionalOnMissingBean(TaskExecutor.class)
    public Executor facilityAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
