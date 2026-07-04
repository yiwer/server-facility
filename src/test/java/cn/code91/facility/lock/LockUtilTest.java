package cn.code91.facility.lock;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LockUtil - 分布式锁门面(委托 DistributedLock bean + 无 bean 降级)")
class LockUtilTest {

    @AfterEach
    void cleanup() {
        // 毒化清理:refresh 过的 GenericApplicationContext 若不清理会串到后续测试类
        // (P6-T5 事故根因),经 context 包测试桥调用包私有 clear()。
        SpringContextHolderTestSupport.reset();
    }

    private void registerDistributedLock(DistributedLock lock) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("distributedLock", lock);
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);
    }

    @Test
    @DisplayName("无 DistributedLock bean 时,executeWithLock(Supplier 重载)直接执行 action 并返回其值")
    void noBean_executeWithLock_runsAction() {
        AtomicBoolean ran = new AtomicBoolean(false);

        String result = LockUtil.executeWithLock("k", Duration.ofSeconds(1), () -> {
            ran.set(true);
            return "done";
        });

        assertThat(ran).isTrue();
        assertThat(result).isEqualTo("done");
    }

    @Test
    @DisplayName("有 DistributedLock bean 时,executeWithLock(Supplier 重载)委托 bean 执行,action 执行且返回其值")
    void withBean_executeWithLock_delegates() {
        registerDistributedLock(new InMemoryDistributedLock(10));
        AtomicBoolean ran = new AtomicBoolean(false);

        String result = LockUtil.executeWithLock("k", Duration.ofSeconds(1), () -> {
            ran.set(true);
            return "done";
        });

        assertThat(ran).isTrue();
        assertThat(result).isEqualTo("done");
    }

    @Test
    @DisplayName("无 DistributedLock bean 时,tryLock 降级放行返回 true")
    void noBean_tryLock_returnsTrue() {
        assertThat(LockUtil.tryLock("k", Duration.ofMillis(50))).isTrue();
    }

    @Test
    @DisplayName("有 DistributedLock bean 时,tryLock 委托 bean 的返回值(stub 恒返 false,与降级恒真区分,证明确有委托)")
    void withBean_tryLock_delegates() {
        registerDistributedLock(new StubDistributedLock(false));

        assertThat(LockUtil.tryLock("k", Duration.ofSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("无 DistributedLock bean 时,unlock 静默降级不抛异常(no-op)")
    void noBean_unlock_noop() {
        assertThatCode(() -> LockUtil.unlock("k")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("有 DistributedLock bean 时,unlock 委托 bean 执行")
    void withBean_unlock_delegates() {
        StubDistributedLock stub = new StubDistributedLock(true);
        registerDistributedLock(stub);

        LockUtil.unlock("k");

        assertThat(stub.unlockCalled).isTrue();
    }

    @Test
    @DisplayName("无 DistributedLock bean 时,executeWithLock(Runnable 重载)直接执行 action")
    void executeWithLock_runnable_noBean_runs() {
        AtomicBoolean ran = new AtomicBoolean(false);

        LockUtil.executeWithLock("k", Duration.ofSeconds(1), () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("有 DistributedLock bean 时,executeWithLock(Runnable 重载)委托 bean 执行,action 执行")
    void withBean_executeWithLock_runnable_delegates() {
        registerDistributedLock(new InMemoryDistributedLock(10));
        AtomicBoolean ran = new AtomicBoolean(false);

        LockUtil.executeWithLock("k", Duration.ofSeconds(1), () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throws() throws Exception {
        var ctor = LockUtil.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        assertThatThrownBy(ctor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    /** 委托验证用 stub:tryLock 返回可控值(与降级路径的恒真区分),unlock 记录是否被调用。 */
    private static final class StubDistributedLock implements DistributedLock {
        private final boolean tryLockResult;
        private final AtomicBoolean unlockCalled = new AtomicBoolean(false);

        StubDistributedLock(boolean tryLockResult) {
            this.tryLockResult = tryLockResult;
        }

        @Override
        public boolean tryLock(String key, Duration leaseTime) {
            return tryLockResult;
        }

        @Override
        public void unlock(String key) {
            unlockCalled.set(true);
        }
    }
}
