package cn.code91.facility.autoconfigure;

import cn.code91.facility.cache.FacilityCacheProperties;
import cn.code91.facility.log.LogUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * 缓存自动装配(ADR-0015)。
 * <p>
 * 两个 {@code CacheManager} bean 互斥,均 {@code @ConditionalOnMissingBean(CacheManager.class)}
 * ——消费方声明自己的 {@code CacheManager} 即可整体覆盖:
 * </p>
 * <ul>
 *     <li>{@link #caffeineCacheManager}:classpath 同时存在 {@code Caffeine} 与其
 *     {@code CaffeineCacheManager} 支持类(见下)时装配,{@link FacilityCacheProperties#getDefaultTtl()}
 *     经 {@code expireAfterWrite} 应用、{@link FacilityCacheProperties#getMaximumSize()}
 *     经 {@code maximumSize} 应用;</li>
 *     <li>{@link #concurrentMapCacheManager}:classpath 不存在 {@code Caffeine} 时装配,
 *     纯 JDK {@code ConcurrentHashMap} 包装,不支持 TTL/大小上限。</li>
 * </ul>
 * <p>
 * <b>条件类探测用字符串形式:</b>{@code @ConditionalOnClass}/{@code @ConditionalOnMissingClass}
 * 均以类全限定名字符串(而非 {@code Caffeine.class} 字面量)声明——Spring Boot 的条件求值经 ASM
 * 读取字节码常量池,不会真正加载缺失的可选类,字符串形式与类字面量形式在此均安全;选用字符串形式
 * 是为了不必在 {@code @ConditionalOnClass} 处额外 import 一个仅供条件判断使用的类型,写法上与
 * {@code @ConditionalOnMissingClass}(只有字符串形式可用)保持一致。{@code Caffeine} 类本身仍在
 * {@link #caffeineCacheManager} 方法体内正常 import 使用(构造 {@code Caffeine.newBuilder()}),
 * 这也是 {@code dependency:analyze} 判定 caffeine 依赖"已使用"的直接依据。
 * </p>
 * <p>
 * 无跨簇装配顺序依赖,故不声明 {@code @AutoConfigureAfter}(对比 Json/Async 的
 * {@code @AutoConfigureAfter}、Locale 的 {@code @AutoConfigureBefore}——三者显式声明系确有依赖)。
 * </p>
 * <p>
 * <b>{@code CaffeineCacheManager} 位于 {@code spring-context-support} 而非 {@code spring-context}</b>
 * ——{@link #caffeineCacheManager} 的 {@code @ConditionalOnClass} 因此同时探测
 * {@code com.github.benmanes.caffeine.cache.Caffeine} 与
 * {@code org.springframework.cache.caffeine.CaffeineCacheManager} 两个类:消费方只添加
 * {@code caffeine} 而漏配 {@code spring-context-support}(或反之)时,该 bean 条件不满足,
 * 整体回退 {@link #concurrentMapCacheManager}——不会在 bean 方法体内触发
 * {@code NoClassDefFoundError};缓存不可用不阻断业务(ADR-0015)。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityCacheProperties.class)
@ConditionalOnProperty(prefix = "facility.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnClass(name = {
            "com.github.benmanes.caffeine.cache.Caffeine",
            "org.springframework.cache.caffeine.CaffeineCacheManager"
    })
    public CacheManager caffeineCacheManager(FacilityCacheProperties props) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(props.getDefaultTtl())
                .maximumSize(props.getMaximumSize()));
        return cacheManager;
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Caffeine")
    public CacheManager concurrentMapCacheManager() {
        // F15:回退分支装配期一次性提示——Caffeine 独有的 TTL/容量配置在此后端不生效
        LogUtil.warn("facility.cache.default-ttl / maximum-size only apply to the Caffeine backend; "
                + "falling back to ConcurrentMapCacheManager, these properties are ignored");
        return new ConcurrentMapCacheManager();
    }
}
