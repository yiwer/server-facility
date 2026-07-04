package cn.code91.facility.autoconfigure;

import cn.code91.facility.lock.DistributedLock;
import cn.code91.facility.lock.FacilityLockProperties;
import cn.code91.facility.lock.InMemoryDistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityLockAutoConfiguration - 分布式锁装配(默认 InMemoryDistributedLock,可被用户 bean 覆盖)")
class FacilityLockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityLockAutoConfiguration.class));

    @Test
    @DisplayName("默认装配 DistributedLock(InMemoryDistributedLock 实现)")
    void registersDistributedLockByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DistributedLock.class);
            assertThat(ctx.getBean(DistributedLock.class)).isInstanceOf(InMemoryDistributedLock.class);
        });
    }

    @Test
    @DisplayName("已存在用户 DistributedLock bean → 不注册 InMemoryDistributedLock,沿用用户 bean")
    void userDistributedLockBean_backsOff() {
        runner
            .withBean(DistributedLock.class, StubDistributedLock::new)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(DistributedLock.class);
                assertThat(ctx.getBean(DistributedLock.class))
                    .isInstanceOf(StubDistributedLock.class)
                    .isNotInstanceOf(InMemoryDistributedLock.class);
            });
    }

    @Test
    @DisplayName("facility.lock.enabled=false → 不装配 DistributedLock")
    void enabledFalse_noBean() {
        runner
            .withPropertyValues("facility.lock.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(DistributedLock.class));
    }

    @Test
    @DisplayName("properties 绑定:facility.lock.max-locks 生效")
    void propertiesBind() {
        runner
            .withPropertyValues("facility.lock.max-locks=5")
            .run(ctx -> assertThat(ctx.getBean(FacilityLockProperties.class).getMaxLocks())
                .isEqualTo(5));
    }

    /** 用户自定义 {@link DistributedLock} 实现,验证装配层为其让位。 */
    private static final class StubDistributedLock implements DistributedLock {
        @Override
        public boolean tryLock(String key, Duration leaseTime) {
            return true;
        }

        @Override
        public void unlock(String key) {
        }
    }
}
