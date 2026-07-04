package cn.code91.facility.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 直接构造 {@link CaffeineCacheManager}(不经 {@code FacilityCacheAutoConfiguration} 装配),
 * 单元验证 Caffeine 后端的 TTL 过期与 maximumSize 驱逐行为——这两项是
 * {@link FacilityCacheProperties#getDefaultTtl()}/{@link FacilityCacheProperties#getMaximumSize()}
 * 生效与否的底层事实依据。
 *
 * <p><b>时间/异步语义均先探针实证</b>:TTL 用宽松 sleep 余量(50ms TTL / 120ms sleep,
 * 2.4x 余量)避免 CI 抖动误报;驱逐用 {@code cleanUp()} 强制同步 Caffeine 内部的异步维护动作后
 * 再断言,而非依赖 {@code maximumSize} 立即生效(Caffeine 的 size 驱逐是惰性/异步的)。</p>
 */
@DisplayName("CaffeineTtlTest - Caffeine 后端 TTL 过期 / maximumSize 驱逐行为实证")
class CaffeineTtlTest {

    @Test
    @DisplayName("expireAfterWrite(50ms) 到期后 get 返回 null(sleep 120ms 宽松余量)")
    void expireAfterWrite_entryExpiresAfterTtl() throws InterruptedException {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(50)));
        var cache = cacheManager.getCache("c");

        cache.put("k", "v");
        // 写入后立即读取:未到期,应命中
        assertThat(cache.get("k")).isNotNull();
        assertThat(cache.get("k").get()).isEqualTo("v");

        Thread.sleep(120); // TTL 50ms 的 2.4x 余量,避免 CI 抖动导致的误判

        assertThat(cache.get("k")).isNull();
    }

    @Test
    @DisplayName("maximumSize=1 时写入两个不同 key,cleanUp() 强制驱逐后 estimatedSize <= 1")
    void maximumSize_evictsBeyondLimitAfterCleanUp() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(1));
        var cache = cacheManager.getCache("c");

        cache.put("k1", "v1");
        cache.put("k2", "v2");

        // Caffeine 的 size 驱逐由后台/写入路径异步维护,非 put 后立即生效;
        // cleanUp() 是 com.github.benmanes.caffeine.cache.Cache 提供的确定性同步钩子,
        // 强制立即执行待处理的驱逐动作,避免依赖异步时序的 flaky 断言。
        var nativeCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
        nativeCache.cleanUp();

        assertThat(nativeCache.estimatedSize()).isLessThanOrEqualTo(1);
    }
}
