package cn.code91.facility.json.support;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JsonConfig / JsonConfig.Builder 盲区补测(P7-T3)。
 * <p>standard()/prettyPrint()/canonical()/useDefaultDateFormat() 等已被
 * {@link cn.code91.facility.json.JsonsRegistry} 的默认装配间接覆盖,此处只补
 * 未被任何现有测试触达的预设入口与 Builder 选项。</p>
 */
@DisplayName("JsonConfig — 配置构建器盲区补测")
class JsonConfigTest {

    private record NamePojo(String name) {}

    private record NullableFields(String a, String b) {}

    // ==================== 预设入口:strict / withDateFormat / builder(裸) ====================

    @Nested
    @DisplayName("预设入口:strict / withDateFormat / builder(裸)")
    class PresetEntryTests {

        @Test
        @DisplayName("strict 不忽略未知属性,遇到额外字段抛异常")
        void strict_failsOnUnknownProperties() {
            ObjectMapper mapper = JsonConfig.strict().build();
            assertThatThrownBy(() -> mapper.readValue("{\"name\":\"a\",\"extra\":1}", NamePojo.class))
                    .isInstanceOf(JacksonException.class);
        }

        @Test
        @DisplayName("strict 日期以字符串形式序列化")
        void strict_datesAsString() throws Exception {
            ObjectMapper mapper = JsonConfig.strict().build();
            assertThat(mapper.writeValueAsString(LocalDate.of(2025, 6, 15))).isEqualTo("\"2025-06-15\"");
        }

        @Test
        @DisplayName("withDateFormat 应用自定义日期时间格式")
        void withDateFormat_customPattern() throws Exception {
            ObjectMapper mapper = JsonConfig.withDateFormat("dd/MM/yyyy HH:mm", "dd/MM/yyyy", "HH:mm").build();
            String json = mapper.writeValueAsString(LocalDateTime.of(2025, 6, 15, 10, 30));
            assertThat(json).isEqualTo("\"15/06/2025 10:30\"");
        }

        @Test
        @DisplayName("builder() 在 Jackson3 中内置 Java 时间支持")
        void builder_bare_includesJavaTimeSupport() {
            ObjectMapper mapper = JsonConfig.builder().build();
            assertThat(mapper.writeValueAsString(LocalDate.of(2025, 6, 15))).isEqualTo("\"2025-06-15\"");
        }
    }

    // ==================== Builder 选项:模块 / 数值 ====================

    @Nested
    @DisplayName("Builder 选项:addModule / longToString / numberToString")
    class ModuleAndNumberTests {

        @Test
        @DisplayName("addModule 注册的自定义模块生效")
        void addModule_customModuleApplied() throws Exception {
            SimpleModule module = new SimpleModule();
            module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            ObjectMapper mapper = JsonConfig.builder().addModule(module).build();
            assertThat(mapper.writeValueAsString(new BigDecimal("3.140"))).isEqualTo("\"3.140\"");
        }

        @Test
        @DisplayName("longToString 将 Long 序列化为带引号字符串")
        void longToString_quotesLongValue() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().longToString().build();
            assertThat(mapper.writeValueAsString(123456789012345L)).isEqualTo("\"123456789012345\"");
        }

        @Test
        @DisplayName("numberToString 对指定类型生效")
        void numberToString_appliesToSpecifiedType() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().numberToString(Integer.class).build();
            assertThat(mapper.writeValueAsString(42)).isEqualTo("\"42\"");
        }

        @Test
        @DisplayName("numberToString 空参数为 no-op,不影响默认数值序列化")
        void numberToString_noTypes_isNoOp() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().numberToString().build();
            assertThat(mapper.writeValueAsString(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("numberToString 变长参数中的 null 元素被安全跳过")
        void numberToString_nullElement_skipped() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().numberToString(Integer.class, null).build();
            assertThat(mapper.writeValueAsString(42)).isEqualTo("\"42\"");
        }
    }

    // ==================== Builder 选项:时区 ====================

    @Nested
    @DisplayName("Builder 选项:timeZone")
    class TimeZoneTests {

        @Test
        @DisplayName("timeZone 影响 java.util.Date 按自定义日期时间格式的换算结果")
        void timeZone_affectsLegacyDateFormatting() throws Exception {
            ObjectMapper utc = JsonConfig.builder()
                    .dateTimeFormat("yyyy-MM-dd HH:mm:ss").timeZone(TimeZone.getTimeZone("UTC")).build();
            ObjectMapper shanghai = JsonConfig.builder()
                    .dateTimeFormat("yyyy-MM-dd HH:mm:ss").timeZone(TimeZone.getTimeZone("Asia/Shanghai")).build();
            Date epoch = new Date(0L); // 1970-01-01T00:00:00Z

            assertThat(utc.writeValueAsString(epoch)).isEqualTo("\"1970-01-01 00:00:00\"");
            assertThat(shanghai.writeValueAsString(epoch)).isEqualTo("\"1970-01-01 08:00:00\"");
        }
    }

    // ==================== Builder 选项:序列化包含性 ====================

    @Nested
    @DisplayName("Builder 选项:includeAlways / includeNonNull")
    class InclusionTests {

        @Test
        @DisplayName("includeAlways 保留 null 字段")
        void includeAlways_keepsNullFields() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().includeAlways().build();
            String json = mapper.writeValueAsString(new NullableFields("x", null));
            assertThat(json).contains("\"b\":null");
        }

        @Test
        @DisplayName("includeNonNull 剔除 null 字段")
        void includeNonNull_dropsNullFields() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().includeNonNull().build();
            String json = mapper.writeValueAsString(new NullableFields("x", null));
            assertThat(json).doesNotContain("\"b\"");
        }
    }

    // ==================== Builder 选项:反序列化容错 ====================

    @Nested
    @DisplayName("Builder 选项:failOnUnknownProperties / acceptEmptyStringAsNull")
    class DeserializationToleranceTests {

        @Test
        @DisplayName("failOnUnknownProperties 遇到未知字段抛异常")
        void failOnUnknownProperties_throwsOnExtraField() {
            ObjectMapper mapper = JsonConfig.builder().failOnUnknownProperties().build();
            assertThatThrownBy(() -> mapper.readValue("{\"name\":\"a\",\"extra\":1}", NamePojo.class))
                    .isInstanceOf(JacksonException.class);
        }

        private record DatedPojo(LocalDate d) {}

        @Test
        @DisplayName("acceptEmptyStringAsNull 令空字符串反序列化为 null(非字符串目标类型)")
        void acceptEmptyStringAsNull_emptyBecomesNull() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().enableJava8Support().acceptEmptyStringAsNull().build();
            DatedPojo result = mapper.readValue("{\"d\":\"\"}", DatedPojo.class);
            assertThat(result.d()).isNull();
        }
    }

    // ==================== Builder 选项:宽松解析(comments / single quotes / unquoted field names) ====================

    @Nested
    @DisplayName("Builder 选项:allowComments / allowSingleQuotes / allowUnquotedFieldNames")
    class LenientParsingTests {

        @Test
        @DisplayName("默认(未开启)不允许 JSON 注释")
        void byDefault_commentsRejected() {
            ObjectMapper mapper = JsonConfig.builder().build();
            assertThatThrownBy(() -> mapper.readTree("{\"a\":1 /* c */}"))
                    .isInstanceOf(JacksonException.class);
        }

        @Test
        @DisplayName("allowComments 允许 JSON 注释")
        void allowComments_parsesComments() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().allowComments().build();
            JsonNode node = mapper.readTree("{\"a\":1 /* c */}");
            assertThat(node.get("a").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("allowSingleQuotes 允许单引号字符串")
        void allowSingleQuotes_parsesSingleQuotedStrings() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().allowSingleQuotes().build();
            JsonNode node = mapper.readTree("{'a':'x'}");
            assertThat(node.get("a").asString()).isEqualTo("x");
        }

        @Test
        @DisplayName("allowUnquotedFieldNames 允许无引号字段名")
        void allowUnquotedFieldNames_parsesUnquotedFieldNames() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().allowUnquotedFieldNames().build();
            JsonNode node = mapper.readTree("{a:1}");
            assertThat(node.get("a").asInt()).isEqualTo(1);
        }
    }

    // ==================== Builder 选项:datesAsTimestamps / customize ====================

    @Nested
    @DisplayName("Builder 选项:datesAsTimestamps / customize")
    class TimestampsAndCustomizeTests {

        @Test
        @DisplayName("datesAsTimestamps 将 java.util.Date 序列化为纪元毫秒数值")
        void datesAsTimestamps_serializesDateAsEpochMillis() throws Exception {
            ObjectMapper mapper = JsonConfig.builder().datesAsTimestamps().build();
            assertThat(mapper.writeValueAsString(new Date(1_700_000_000_000L))).isEqualTo("1700000000000");
        }

        @Test
        @DisplayName("customize 回调作用于最终构建的 ObjectMapper")
        void customize_appliesToFinalMapper() throws Exception {
            ObjectMapper mapper = JsonConfig.builder()
                    .customizeBuilder(m -> m.enable(SerializationFeature.INDENT_OUTPUT))
                    .build();
            assertThat(mapper.writeValueAsString(Map.of("a", 1))).contains("\n");
        }
    }
}
