package cn.code91.facility.cache;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.support.GenericApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CacheUtil - 缓存门面(委托 CacheManager bean + 无 bean 降级)")
class CacheUtilTest {

    @AfterEach
    void cleanup() {
        // 毒化清理:refresh 过的 GenericApplicationContext 若不清理会串到后续测试类
        // (P6-T5 事故根因),经 context 包测试桥调用包私有 clear()。
        SpringContextHolderTestSupport.reset();
    }

    private void registerCacheManager() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("cacheManager", new ConcurrentMapCacheManager());
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);
    }

    @Test
    @DisplayName("put 后 get 命中")
    void putThenGet_hit() {
        registerCacheManager();

        CacheUtil.put("c1", "k1", "v1");

        assertThat(CacheUtil.get("c1", "k1", String.class)).contains("v1");
    }

    @Test
    @DisplayName("get 未命中的 key → empty")
    void get_missing_empty() {
        registerCacheManager();

        assertThat(CacheUtil.get("c1", "missing", String.class)).isEmpty();
    }

    @Test
    @DisplayName("evict 移除单个 key,之后 get 为空")
    void evict_removes() {
        registerCacheManager();
        CacheUtil.put("c1", "k1", "v1");

        CacheUtil.evict("c1", "k1");

        assertThat(CacheUtil.get("c1", "k1", String.class)).isEmpty();
    }

    @Test
    @DisplayName("clear 移除整个 cache 下所有 key")
    void clear_removesAll() {
        registerCacheManager();
        CacheUtil.put("c1", "k1", "v1");
        CacheUtil.put("c1", "k2", "v2");

        CacheUtil.clear("c1");

        assertThat(CacheUtil.get("c1", "k1", String.class)).isEmpty();
        assertThat(CacheUtil.get("c1", "k2", String.class)).isEmpty();
    }

    @Test
    @DisplayName("getOrCompute 未命中调用 loader 并缓存;命中后不再调用 loader")
    void getOrCompute_missThenHit() {
        registerCacheManager();
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Supplier<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return "computed";
        };

        String first = CacheUtil.getOrCompute("c1", "k1", String.class, loader);
        String second = CacheUtil.getOrCompute("c1", "k1", String.class, loader);

        assertThat(first).isEqualTo("computed");
        assertThat(second).isEqualTo("computed");
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("无 CacheManager bean → get 空、getOrCompute 直调 loader 并返回其值")
    void noCacheManager_getEmpty_getOrComputeCallsLoader() {
        assertThat(CacheUtil.get("c1", "k1", String.class)).isEmpty();

        AtomicInteger loaderCalls = new AtomicInteger(0);
        String value = CacheUtil.getOrCompute("c1", "k1", String.class, () -> {
            loaderCalls.incrementAndGet();
            return "fallback";
        });

        assertThat(value).isEqualTo("fallback");
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("CacheManager 存在但 cacheName 未配置(getCache 返 null)→ get 空、put/evict/clear no-op")
    void cacheManagerPresent_butCacheNameAbsent_degradesGracefully() {
        // ConcurrentMapCacheManager 固定 cache 名(禁动态创建),未知名 getCache 返 null → 命中 c==null 降级分支
        ConcurrentMapCacheManager cm = new ConcurrentMapCacheManager("known");
        cm.setCacheNames(java.util.List.of("known"));
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("cacheManager", cm);
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);

        assertThat(CacheUtil.get("absent", "k", String.class)).isEmpty();
        // put/evict/clear 对不存在的 cache 均 no-op,不抛异常
        CacheUtil.put("absent", "k", "v");
        CacheUtil.evict("absent", "k");
        CacheUtil.clear("absent");
        assertThat(CacheUtil.get("absent", "k", String.class)).isEmpty();
    }

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throws() throws Exception {
        var ctor = CacheUtil.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        assertThatThrownBy(ctor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
