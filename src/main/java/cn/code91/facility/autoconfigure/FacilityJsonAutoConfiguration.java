package cn.code91.facility.autoconfigure;

import cn.code91.facility.json.Jsons;
import cn.code91.facility.json.JsonsRegistry;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 应用拥有的 JSON 服务和 registry，复用本应用的 Jackson mapper。
 * Spring 启停不修改 JsonUtil 的 standalone 静态 registry；新应用注入 Jsons。
 */
@AutoConfiguration
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@ConditionalOnClass(JsonMapper.class)
@ConditionalOnBean(JsonMapper.class)
public class FacilityJsonAutoConfiguration {

    /**
     * 应用拥有的 JSON 服务，复用本应用的 mapper 及其 customizer，不经进程级 registry 查找。
     * 用户可以声明自己的 {@link Jsons} bean；此时其政策由用户管理。
     */
    @Bean
    @ConditionalOnMissingBean(Jsons.class)
    public Jsons jsons(JsonMapper springObjectMapper) {
        return new Jsons(springObjectMapper);
    }

    /** 应用 registry 的默认入口复用 Jsons bean，尊重用户替换。其余 namespace 是显式预设。 */
    @Bean
    @ConditionalOnMissingBean
    public JsonsRegistry jsonsRegistry(Jsons jsons) {
        return new JsonsRegistry(jsons);
    }
}
