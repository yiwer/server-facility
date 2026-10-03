package cn.code91.facility.autoconfigure;

import cn.code91.facility.json.JsonUtil;
import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.JsonsRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * <b>JSON 自动配置</b>
 * <p>把 {@link JsonUtil} 内部 {@link JsonsRegistry} 单例的默认 namespace 替换为复用 Spring
 * 自动配置的 {@link ObjectMapper} 的 {@link Jsons}。这样：
 * <ul>
 *   <li>{@code @RestController} 出口走 Spring 的 ObjectMapper</li>
 *   <li>服务代码 {@code JsonUtil.serialize(obj)} 也走同一份 ObjectMapper</li>
 * </ul>
 * 单应用内二者序列化行为一致。新代码注入应用自己的 {@link Jsons} bean，避免多个应用覆盖静态默认值。
 * 同时保留进程级 {@link JsonsRegistry} bean 的兼容行为。</p>
 */
@AutoConfiguration
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnBean(ObjectMapper.class)
public class FacilityJsonAutoConfiguration {

    /**
     * 应用拥有的 JSON 服务，复用本应用的 mapper 及其 customizer，不经进程级 registry 查找。
     * 用户可以声明自己的 {@link Jsons} bean；此时其政策由用户管理。
     */
    @Bean
    @ConditionalOnMissingBean(Jsons.class)
    public Jsons jsons(ObjectMapper springObjectMapper) {
        return new Jsons(springObjectMapper);
    }

    /**
     * 把进程级 {@link JsonsRegistry} 单例的默认 namespace 切换为复用 Spring {@code ObjectMapper}。
     * 同时返回该单例作为 bean，供 {@code @Autowired JsonsRegistry} 使用。
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonsRegistry jsonsRegistry(ObjectMapper springObjectMapper) {
        JsonsRegistry registry = JsonUtil.registry();
        registry.register(JsonsRegistry.DEFAULT, new Jsons(springObjectMapper));
        return registry;
    }
}
