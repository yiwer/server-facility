package cn.code91.facility.autoconfigure;

import cn.code91.facility.lock.DistributedLock;
import cn.code91.facility.lock.FacilityLockProperties;
import cn.code91.facility.lock.InMemoryDistributedLock;
import cn.code91.facility.lock.LocalKeyedMutex;
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
    @DisplayName("默认仅装配诚实命名的进程内互斥")
    void registersLocalMutexWithoutAdvertisingDistributedGuarantee() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(LocalKeyedMutex.class);
            assertThat(ctx).doesNotHaveBean(DistributedLock.class);
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
            .run(ctx -> {
                assertThat(ctx).doesNotHaveBean(DistributedLock.class);
                assertThat(ctx).doesNotHaveBean(LocalKeyedMutex.class);
            });
    }

    @Test
    @DisplayName("properties 绑定:facility.lock.max-locks 生效")
    void propertiesBind() {
        runner
            .withPropertyValues("facility.lock.max-locks=5")
            .run(ctx -> assertThat(ctx.getBean(FacilityLockProperties.class).getMaxLocks())
                .isEqualTo(5));
    }

    @Test
    void requiredDistributedConsumerFailsAtStartupWithoutAnAdapter() {
        runner.withBean(RequiredDistributedConsumer.class).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void disabledAndInvalidDefaultCannotSupplyTheRequiredLocalCapability() {
        runner.withPropertyValues("facility.lock.enabled=false").withBean(RequiredLocalConsumer.class)
                .run(ctx -> assertThat(ctx).hasFailed());
        for (int invalid : new int[]{0, -1}) {
            runner.withPropertyValues("facility.lock.max-locks=" + invalid)
                    .run(ctx -> assertThat(ctx).hasFailed());
        }
    }

    @Test
    void explicitLocalBeanOwnsItsBudgetAndDefaultCloseStopsAdmission() {
        var explicit = new LocalKeyedMutex(2);
        runner.withPropertyValues("facility.lock.max-locks=0").withBean(LocalKeyedMutex.class, () -> explicit)
                .run(ctx -> assertThat(ctx.getBean(LocalKeyedMutex.class)).isSameAs(explicit));
        var owned = new java.util.concurrent.atomic.AtomicReference<LocalKeyedMutex>();
        runner.run(ctx -> {
            owned.set(ctx.getBean(LocalKeyedMutex.class));
            assertThat(owned.get().executeWithLock("one", Duration.ZERO, () -> "work")).isEqualTo("work");
        });
        assertThat(owned.get().tryLock("after-close", Duration.ZERO)).isFalse();
    }

    @Test
    void twoApplicationsOwnIndependentMutexesAndCloseIndependently() {
        runner.run(first -> runner.run(second -> {
            var a = first.getBean(LocalKeyedMutex.class);
            var b = second.getBean(LocalKeyedMutex.class);
            assertThat(a).isNotSameAs(b);
            assertThat(a.tryLock("same", Duration.ZERO)).isTrue();
            assertThat(b.tryLock("same", Duration.ZERO)).isTrue();
            first.close();
            assertThat(a.tryLock("new", Duration.ZERO)).isFalse();
            a.unlock("same");
            b.unlock("same");
            assertThat(b.executeWithLock("after-peer-close", Duration.ZERO, () -> "available")).isEqualTo("available");
        }));
    }

    record RequiredDistributedConsumer(DistributedLock lock) { }
    record RequiredLocalConsumer(LocalKeyedMutex mutex) { }

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
