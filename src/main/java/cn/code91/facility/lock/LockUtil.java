package cn.code91.facility.lock;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.log.LogUtil;
import cn.code91.facility.result.Result;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * <b>分布式锁静态门面</b>
 * <p>
 * 委托 {@link SpringContextHolder#getBean(Class)} 查找 {@link DistributedLock} bean 并转发调用；
 * 容器中不存在 {@code DistributedLock} bean 时优雅降级——{@link #tryLock} 降级放行(返回
 * {@code true}),{@link #unlock} 降级为 no-op,{@link #executeWithLock} 降级为直接执行
 * {@code action}(退化为无锁执行,记录 WARN 日志)。
 * </p>
 * <p>
 * <b>降级风险提示</b>:锁不可用时"直接放行"对单实例部署可接受(本就无并发互斥需求之外的风险),
 * 但多实例部署下若 {@code DistributedLock} bean 缺席,退化无锁执行会破坏跨实例互斥——务必确保
 * 生产环境该 bean 在场(详见 ADR-0016)。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * if (!LockUtil.tryLock("order:123", Duration.ofSeconds(5))) {
 *     throw new LockAcquisitionException("order:123");
 * }
 *
 * String result = LockUtil.executeWithLock("order:123", Duration.ofSeconds(5), () -> processOrder());
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class LockUtil {

    private LockUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取指定 key 的锁
     *
     * @param key       锁维度标识
     * @param leaseTime 等待获取锁的最长时长
     * @return {@code true} 表示获取成功;无 {@link DistributedLock} bean 时降级放行返回 {@code true}
     */
    public static boolean tryLock(String key, Duration leaseTime) {
        return SpringContextHolder.getBean(DistributedLock.class).map(l -> l.tryLock(key, leaseTime)).orElse(true);
    }

    /**
     * 释放指定 key 的锁
     *
     * @param key 锁维度标识;无 {@link DistributedLock} bean 时降级为 no-op
     */
    public static void unlock(String key) {
        SpringContextHolder.getBean(DistributedLock.class).map(l -> {
            l.unlock(key);
            return true;
        });
    }

    /**
     * 获取锁并执行 {@code action}
     *
     * @param key       锁维度标识
     * @param leaseTime 等待获取锁的最长时长
     * @param action    持锁期间执行的动作,返回执行结果
     * @param <T>       动作返回值类型
     * @return {@code action} 的执行结果;无 {@link DistributedLock} bean 时降级为直接执行
     *         {@code action}(不加锁,记录 WARN 日志)
     * @throws LockAcquisitionException 有 bean 但获取锁失败(等待超时)
     */
    public static <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action) {
        Result<DistributedLock, WrappedError> bean = SpringContextHolder.getBean(DistributedLock.class);
        if (bean.isErr()) {
            LogUtil.warn("无 DistributedLock bean,退化无锁执行: key={}", key);
            return action.get();
        }
        return bean.get().executeWithLock(key, leaseTime, action);
    }

    /**
     * 获取锁并执行 {@code action}(无返回值重载),语义同
     * {@link #executeWithLock(String, Duration, Supplier)}
     *
     * @param key       锁维度标识
     * @param leaseTime 等待获取锁的最长时长
     * @param action    持锁期间执行的动作
     * @throws LockAcquisitionException 有 bean 但获取锁失败(等待超时)
     */
    public static void executeWithLock(String key, Duration leaseTime, Runnable action) {
        executeWithLock(key, leaseTime, () -> {
            action.run();
            return null;
        });
    }
}
