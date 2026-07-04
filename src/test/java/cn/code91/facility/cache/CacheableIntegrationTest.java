package cn.code91.facility.cache;

import cn.code91.facility.autoconfigure.FacilityCacheAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link FacilityCacheAutoConfiguration} 装配出的 {@code CacheManager} 之上,
 * Spring {@code @Cacheable} 注解无需额外接线即可自然生效(装配层只需提供 CacheManager bean,
 * {@code @EnableCaching} 之后的代理/织入完全是 Spring 自身职责,不属于本工程代码路径)。
 *
 * <p>bean 经 {@code ApplicationContextRunner} 容器获取(触发 CGLIB 代理),而非
 * 测试内直接 {@code new}——后者会绕过代理、观测不到缓存行为。</p>
 */
@DisplayName("CacheableIntegrationTest - 装配 CacheManager 后 @Cacheable 自然可用")
class CacheableIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityCacheAutoConfiguration.class))
            .withUserConfiguration(CachingConfig.class);

    @Test
    @DisplayName("同一 key 调用两次,方法体只执行一次(第二次命中缓存)")
    void sameKey_secondCallHitsCache_methodBodyRunsOnce() {
        runner.run(ctx -> {
            ItemService service = ctx.getBean(ItemService.class);

            String first = service.getItem("k1");
            String second = service.getItem("k1");

            assertThat(first).isEqualTo("value-for-k1");
            assertThat(second).isEqualTo("value-for-k1");
            assertThat(service.getCallCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("不同 key 各自未命中,方法体各执行一次")
    void differentKeys_eachTriggersMethodBodyOnce() {
        runner.run(ctx -> {
            ItemService service = ctx.getBean(ItemService.class);

            service.getItem("k1");
            service.getItem("k2");

            assertThat(service.getCallCount()).isEqualTo(2);
        });
    }

    @Configuration
    @EnableCaching
    static class CachingConfig {

        @Bean
        ItemService itemService() {
            return new ItemService();
        }
    }

    /** 方法须 public——{@code @Cacheable} 依赖 Spring 的 CGLIB 子类代理拦截调用。 */
    static class ItemService {

        private final AtomicInteger callCount = new AtomicInteger();

        @Cacheable("items")
        public String getItem(String key) {
            callCount.incrementAndGet();
            return "value-for-" + key;
        }

        public int getCallCount() {
            return callCount.get();
        }
    }
}
