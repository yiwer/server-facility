package cn.code91.facility.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonsRegistry — 多 namespace 注册表")
class JsonsRegistryTest {

    @Test
    @DisplayName("4 个内置 namespace 都可取到")
    void builtinNamespaces() {
        JsonsRegistry r = new JsonsRegistry();
        assertThat(r.use(JsonsRegistry.DEFAULT)).isNotNull();
        assertThat(r.use(JsonsRegistry.GENERIC)).isNotNull();
        assertThat(r.use(JsonsRegistry.CANONICAL)).isNotNull();
        assertThat(r.use(JsonsRegistry.PRETTY)).isNotNull();
    }

    @Test
    @DisplayName("use(null) 返回默认")
    void useNullReturnsDefault() {
        JsonsRegistry r = new JsonsRegistry();
        assertThat(r.use(null)).isSameAs(r.getDefault());
    }

    @Test
    @DisplayName("外部 ObjectMapper 构造默认 namespace")
    void springMapperConstructor() {
        ObjectMapper springMapper = new ObjectMapper();
        JsonsRegistry r = new JsonsRegistry(new Jsons(springMapper));
        assertThat(r.getDefault().mapper()).isSameAs(springMapper);
        // generic/canonical/pretty 仍使用 JsonConfig 自造
        assertThat(r.use(JsonsRegistry.PRETTY).mapper()).isNotSameAs(springMapper);
    }

    @Test
    @DisplayName("register 替换默认 namespace 后 getDefault 跟随")
    void registerReplacesDefault() {
        JsonsRegistry r = new JsonsRegistry();
        Jsons original = r.getDefault();
        Jsons replacement = new Jsons(new ObjectMapper());
        r.register(JsonsRegistry.DEFAULT, replacement);
        assertThat(r.getDefault()).isSameAs(replacement).isNotSameAs(original);
    }

    @Test
    @DisplayName("JsonUtil 静态门面与 registry() 共享同一实例")
    void jsonUtilSingletonIsRegistry() {
        assertThat(JsonUtil.registry()).isNotNull();
        // serialize via static facade and via registry default should produce same output
        Object value = java.util.Map.of("k", "v");
        String viaStatic = JsonUtil.serialize(value).orElse("");
        String viaRegistry = JsonUtil.registry().getDefault().serialize(value).orElse("");
        assertThat(viaStatic).isEqualTo(viaRegistry);
    }
}
