package cn.code91.facility.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@AutoConfiguration
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
