package cn.code91.facility.json.support;

import cn.code91.facility.json.Jsons;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class JsonBuilderCustomizationTest {
    public record Customer(String displayName) {}

    @Test
    void callbackOrderAndLegacyFinalOverrideAreExplicit() {
        var configured = JsonConfig.standard()
                .customizeBuilder(builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE))
                .customizeBuilder(builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE));
        assertThat(new Jsons(configured.build()).serialize(new Customer("Ada")).get())
                .isEqualTo("{\"display-name\":\"Ada\"}");
        assertThat(new Jsons(configured.customize(mapper -> mapper.setPropertyNamingStrategy(
                PropertyNamingStrategies.LOWER_CAMEL_CASE)).build()).serialize(new Customer("Ada")).get())
                .isEqualTo("{\"displayName\":\"Ada\"}");
    }

    @Test
    void builderTimezoneOverrideWinsOverEarlierPreset() {
        var mapper = JsonConfig.standard().timeZone(TimeZone.getTimeZone("UTC"))
                .dateTimeFormat("yyyy-MM-dd HH:mm:ss")
                .customizeBuilder(builder -> builder.defaultTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))).build();
        assertThat(new Jsons(mapper).serialize(new Date(0)).get()).isEqualTo("\"1970-01-01 08:00:00\"");
    }

    @Test
    void requiredCallbackFailsAtRegistration() {
        assertThatNullPointerException().isThrownBy(() -> JsonConfig.standard().customizeBuilder(null))
                .withMessage("builder customizer cannot be null");
    }

    @Test
    void constructionCallbackOverridesPresetBeforeMapperIsPublished() {
        Jsons jsons = new Jsons(JsonConfig.standard().customizeBuilder(builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).build());
        assertThat(jsons.serialize(new Customer("Ada")).get()).isEqualTo("{\"display_name\":\"Ada\"}");
        assertThat(jsons.deserialize("{\"display_name\":\"Ada\",\"extra\":1}", Customer.class).isErr()).isTrue();
        assertThat(jsons.deserialize("{\"display_name\":\"Ada\"} {}", Customer.class).isErr()).isTrue();
    }
}
