package cn.code91.facility.json.support;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.experimental.UtilityClass;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * <b>JSON配置构建器</b>
 * <p>
 * 用于构建配置良好的 ObjectMapper。
 * 注意：ObjectMapper 初始化成本较高，建议构建后全局单例复用。
 * </p>
 *
 * <p><b>功能说明：</b></p>
 * <ul>
 *   <li>提供多种预设配置模板（standard, prettyPrint, strict, canonical）</li>
 *   <li>支持 Java 8 时间类型序列化/反序列化</li>
 *   <li>支持自定义日期格式</li>
 *   <li>支持 Long 类型转 String 防止前端精度丢失</li>
 *   <li>支持灵活的序列化/反序列化特性配置</li>
 *   <li>支持自定义 Jackson Module 扩展</li>
 * </ul>
 *
 * <p><b>有意不含：</b>{@code ACCEPT_EMPTY_STRING_AS_NULL_OBJECT}(空串→null 的宽松反序列化)
 * 四个预设(standard/prettyPrint/strict/canonical)均默认关闭——空串是合法值,静默转 null
 * 会掩盖脏数据;{@link Builder#acceptEmptyStringAsNull()} 仍可显式开启。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 标准配置
 * ObjectMapper mapper = JsonConfig.standard().build();
 *
 * // 自定义日期格式
 * ObjectMapper mapper = JsonConfig.withDateFormat("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "HH:mm:ss").build();
 *
 * // 规范化配置（属性排序，空值不序列化）
 * ObjectMapper mapper = JsonConfig.canonical().build();
 *
 * // 自定义配置
 * ObjectMapper mapper = JsonConfig.builder()
 *     .enableJava8Support()
 *     .longToString()
 *     .prettyPrint()
 *     .build();
 * }</pre>
 *
 * @author yvvb
 * @since 2025/4/20
 */
@UtilityClass
public class JsonConfig {

    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";


    // ======================== 预设入口 ========================

    /**
     * <b>标准配置</b>
     * <p>
     * 包含常用的配置选项，适用于大多数场景：
     * <ul>
     *   <li>启用 Java 8 支持（时间、Optional、参数名称）</li>
     *   <li>忽略未知属性，防止反序列化时出错</li>
     *   <li>日期以字符串形式序列化而非时间戳</li>
     *   <li>允许空 Bean 序列化</li>
     * </ul>
     * </p>
     *
     * @return Builder实例
     */
    public static Builder standard() {
        return new Builder()
                .enableJava8Support() // 包含 Time, JDK8, ParamNames
                .ignoreUnknownProperties()
                .datesAsString()
                .disableFailOnEmptyBeans();
    }

    /**
     * <b>美化打印配置</b>
     * <p>
     * 在标准配置基础上增加 JSON 格式化输出，便于调试和日志查看。
     * </p>
     *
     * @return Builder实例
     */
    public static Builder prettyPrint() {
        return standard().prettyPrint();
    }

    /**
     * <b>严格模式配置</b>
     * <p>
     * 仅包含最基本的必要配置：
     * <ul>
     *   <li>启用 Java 8 支持</li>
     *   <li>日期以字符串形式序列化</li>
     * </ul>
     * 适用于需要精确控制配置的场景。
     * </p>
     *
     * @return Builder实例
     */
    public static Builder strict() {
        return new Builder()
                .enableJava8Support()
                .datesAsString();
    }

    /**
     * <b>规范化配置</b>
     * <p>
     * 属性按字母序排列，空值不序列化，适合用于对象比较和签名。
     * </p>
     *
     * @return Builder实例
     */
    public static Builder canonical() {
        return new Builder()
                .enableJava8Support()
                .ignoreUnknownProperties()
                .datesAsString()
                .sortProperties()
                .includeNonEmpty();
    }

    /**
     * <b>自定义日期格式配置</b>
     * <p>
     * 允许用户指定特定的日期、时间和日期时间格式。
     * 默认情况下会启用 Java 8 时间支持并忽略未知属性。
     * </p>
     *
     * @param dateTimeFormat 日期时间格式，如 "yyyy-MM-dd HH:mm:ss"
     * @param dateFormat     日期格式，如 "yyyy-MM-dd"
     * @param timeFormat     时间格式，如 "HH:mm:ss"
     *
     * @return Builder实例
     */
    public static Builder withDateFormat(String dateTimeFormat, String dateFormat, String timeFormat) {
        return new Builder()
                .enableJava8Support()
                .dateTimeFormat(dateTimeFormat)
                .dateFormat(dateFormat)
                .timeFormat(timeFormat)
                .ignoreUnknownProperties()
                .datesAsString();
    }

    /**
     * <b>获取基础构建器</b>
     * <p>
     * 返回一个未配置任何特性的空构建器，用户可以完全自定义配置。
     * </p>
     *
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ======================== Builder ========================

    public static class Builder {
        // 模块管理
        private final List<Module> extraModules = new ArrayList<>();
        private final List<Consumer<ObjectMapper>> customizers = new ArrayList<>();
        private final List<Consumer<JsonMapper.Builder>> builderCustomizers = new ArrayList<>();

        // 核心开关
        private boolean useJavaTimeModule = false;
        private boolean useJdk8Module = false;
        private boolean useParamNamesModule = false;
        private boolean longToString = false; // Long 转 String 防止前端精度丢失

        // 日期格式
        private String dateTimeFormat;
        private String dateFormat;
        private String timeFormat;
        private TimeZone timeZone = TimeZone.getDefault();

        // 特性开关 (使用 Boolean 允许 null 状态)
        private Boolean prettyPrint;
        private Boolean writeDatesAsTimestamps;
        private Boolean failOnEmptyBeans;
        private Boolean sortProperties;
        private JsonInclude.Include serializationInclusion;

        private Boolean failOnUnknownProperties;
        private Boolean acceptEmptyStringAsNull;

        private Boolean allowComments;
        private Boolean allowSingleQuotes;
        private Boolean allowUnquotedFieldNames;

        Builder() {
        }

        // -------------------- 模块增强 --------------------

        /**
         * 启用 Java8 全套支持 (Time, Optional, ParameterNames)
         * <p>
         * 启用以下三个模块：
         * <ul>
         *   <li>JDK8Module: 支持 Optional 等 JDK8 类型</li>
         *   <li>ParameterNamesModule: 支持通过参数名称反序列化</li>
         *   <li>JavaTimeModule: 支持 Java 8 时间类型 (LocalDate, LocalDateTime 等)</li>
         * </ul>
         * </p>
         */
        public Builder enableJava8Support() {
            this.useJavaTimeModule = true;
            this.useJdk8Module = true;
            this.useParamNamesModule = true;
            return this;
        }

        /**
         * 添加自定义 Jackson 模块
         * <p>
         * 允许用户添加自定义的 Jackson Module 来扩展 ObjectMapper 的功能。
         * </p>
         *
         * @param module 要添加的 Jackson 模块
         *
         * @return Builder实例
         */
        public Builder addModule(Module module) {
            this.extraModules.add(module);
            return this;
        }

        /**
         * <b>解决前端精度丢失问题</b>
         * <p>将 Long 类型序列化为 String</p>
         * <p>
         * JavaScript 的 Number 类型只能安全地表示 -(2^53-1) 到 2^53-1 之间的整数，
         * 超出此范围的整数可能会丢失精度。此方法将 Long 类型序列化为字符串以避免此问题。
         * </p>
         */
        public Builder longToString() {
            this.longToString = true;
            return this;
        }

        /**
         * <b>解决前端精度丢失问题</b>
         * <p>将指定类型的数值序列化为 String</p>
         * <p>
         * 允许用户指定哪些数值类型需要序列化为字符串以防止前端精度丢失。
         * </p>
         *
         * @param types 需要转换为字符串的类型
         *
         * @return Builder实例
         */
        public Builder numberToString(Class<?>... types) {
            if (types != null && types.length > 0) {
                SimpleModule numberModule = new SimpleModule();
                for (Class<?> type : types) {
                    if (type != null) {
                        numberModule.addSerializer(type, ToStringSerializer.instance);
                    }
                }
                this.extraModules.add(numberModule);
            }
            return this;
        }

        // -------------------- 日期配置 --------------------

        /**
         * 设置日期时间格式
         * <p>
         * 配置 LocalDateTime 类型的序列化和反序列化格式。
         * 调用此方法会自动启用 JavaTimeModule。
         * </p>
         *
         * @param format 日期时间格式，如 "yyyy-MM-dd HH:mm:ss"
         *
         * @return Builder实例
         */
        public Builder dateTimeFormat(String format) {
            this.dateTimeFormat = format;
            this.useJavaTimeModule = true; // 暗含启用 TimeModule
            return this;
        }

        /**
         * 设置日期格式
         * <p>
         * 配置 LocalDate 类型的序列化和反序列化格式。
         * 调用此方法会自动启用 JavaTimeModule。
         * </p>
         *
         * @param format 日期格式，如 "yyyy-MM-dd"
         *
         * @return Builder实例
         */
        public Builder dateFormat(String format) {
            this.dateFormat = format;
            this.useJavaTimeModule = true;
            return this;
        }

        /**
         * 设置时间格式
         * <p>
         * 配置 LocalTime 类型的序列化和反序列化格式。
         * 调用此方法会自动启用 JavaTimeModule。
         * </p>
         *
         * @param format 时间格式，如 "HH:mm:ss"
         *
         * @return Builder实例
         */
        public Builder timeFormat(String format) {
            this.timeFormat = format;
            this.useJavaTimeModule = true;
            return this;
        }

        /**
         * 设置时区
         * <p>
         * 配置 ObjectMapper 使用的时区，影响日期时间的序列化和反序列化。
         * </p>
         *
         * @param timeZone 时区
         *
         * @return Builder实例
         */
        public Builder timeZone(TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        // -------------------- 序列化特性 --------------------

        /**
         * 启用美化打印
         * <p>
         * 启用缩进格式化输出 JSON，使 JSON 更易读，通常用于调试和日志记录。
         * </p>
         *
         * @return Builder实例
         */
        public Builder prettyPrint() {
            this.prettyPrint = true;
            return this;
        }

        /**
         * 日期以字符串形式序列化
         * <p>
         * 禁用将日期序列化为时间戳（数字），而是序列化为格式化的字符串。
         * 这通常是推荐的做法，因为字符串格式对人类更友好且不易出错。
         * </p>
         *
         * @return Builder实例
         */
        public Builder datesAsString() {
            this.writeDatesAsTimestamps = false;
            return this;
        }

        /**
         * 禁用空 Bean 失败特性
         * <p>
         * 默认情况下，Jackson 在尝试序列化没有属性的空对象时会抛出异常。
         * 调用此方法可禁用该行为，允许序列化空对象。
         * </p>
         *
         * @return Builder实例
         */
        public Builder disableFailOnEmptyBeans() {
            this.failOnEmptyBeans = false;
            return this;
        }

        /**
         * 包含所有字段
         * <p>
         * 配置 ObjectMapper 序列化所有字段，包括 null 值和空值。
         * </p>
         *
         * @return Builder实例
         */
        public Builder includeAlways() {
            this.serializationInclusion = JsonInclude.Include.ALWAYS;
            return this;
        }

        /**
         * 仅包含非null字段
         * <p>
         * 配置 ObjectMapper 仅序列化非 null 的字段，忽略 null 值字段。
         * </p>
         *
         * @return Builder实例
         */
        public Builder includeNonNull() {
            this.serializationInclusion = JsonInclude.Include.NON_NULL;
            return this;
        }

        /**
         * 仅包含非空字段
         * <p>
         * 配置 ObjectMapper 仅序列化非空字段，忽略 null 值和空值（如空字符串、空集合等）字段。
         * </p>
         *
         * @return Builder实例
         */
        public Builder includeNonEmpty() {
            this.serializationInclusion = JsonInclude.Include.NON_EMPTY;
            return this;
        }

        // -------------------- 反序列化特性 --------------------

        /**
         * 遇到未知属性报错
         * <p>
         * 配置 ObjectMapper 在遇到 JSON 中存在但目标类中不存在的属性时抛出异常。
         * 此设置有助于及早发现数据结构不匹配的问题。
         * </p>
         *
         * @return Builder实例
         */
        public Builder failOnUnknownProperties() {
            this.failOnUnknownProperties = true;
            return this;
        }

        /**
         * 接受空字符串作为null
         * <p>
         * 允许 ObjectMapper 将空字符串（""）反序列化为 null 值。
         * 对于某些需要将空字符串视为 null 的场景非常有用。
         * </p>
         *
         * @return Builder实例
         */
        public Builder acceptEmptyStringAsNull() {
            this.acceptEmptyStringAsNull = true;
            return this;
        }

        /**
         * 忽略未知属性
         * <p>
         * 配置 ObjectMapper 在遇到 JSON 中存在但目标类中不存在的属性时忽略它们而不报错。
         * 这在处理版本兼容性或部分数据更新时很有用。
         * </p>
         *
         * @return Builder实例
         */
        public Builder ignoreUnknownProperties() {
            this.failOnUnknownProperties = false;
            return this;
        }

        /**
         * 允许JSON注释
         * <p>
         * 允许解析包含注释的 JSON 内容。注意这不是标准的 JSON 格式，但在某些配置文件中可能有用。
         * </p>
         *
         * @return Builder实例
         */
        public Builder allowComments() {
            this.allowComments = true;
            return this;
        }

        /**
         * 允许单引号
         * <p>
         * 允许使用单引号(')代替双引号(")来包围 JSON 字符串。注意这不是标准的 JSON 格式。
         * </p>
         *
         * @return Builder实例
         */
        public Builder allowSingleQuotes() {
            this.allowSingleQuotes = true;
            return this;
        }

        /**
         * 允许无引号字段名
         * <p>
         * 允许 JSON 对象的字段名不使用引号包围。注意这不是标准的 JSON 格式。
         * </p>
         *
         * @return Builder实例
         */
        public Builder allowUnquotedFieldNames() {
            this.allowUnquotedFieldNames = true;
            return this;
        }

        /**
         * 使用默认日期格式
         * <p>
         * 应用类中定义的默认日期格式：
         * <ul>
         *   <li>日期时间格式: yyyy-MM-dd HH:mm:ss</li>
         *   <li>日期格式: yyyy-MM-dd</li>
         *   <li>时间格式: HH:mm:ss</li>
         * </ul>
         * </p>
         *
         * @return Builder实例
         */
        public Builder useDefaultDateFormat() {
            return dateTimeFormat(DEFAULT_DATE_TIME_FORMAT)
                    .dateFormat(DEFAULT_DATE_FORMAT)
                    .timeFormat(DEFAULT_TIME_FORMAT);
        }

        /**
         * 日期输出为时间戳
         * <p>
         * 启用将日期序列化为时间戳（数字）而不是格式化的字符串。
         * 在某些需要紧凑表示或高性能的场景下可能有用。
         * </p>
         *
         * @return Builder实例
         */
        public Builder datesAsTimestamps() {
            this.writeDatesAsTimestamps = true;
            return this;
        }

        /**
         * 属性按字母序排列
         * <p>
         * 配置 ObjectMapper 按字母顺序排列序列化后的 JSON 属性。
         * 这对于生成一致的输出（如签名验证）很有用。
         * </p>
         *
         * @return Builder实例
         */
        public Builder sortProperties() {
            this.sortProperties = true;
            return this;
        }

        // -------------------- 自定义 --------------------

        /**
         * 自定义 ObjectMapper 配置
         * <p>
         * 允许用户通过自定义函数直接修改 ObjectMapper 实例。
         * 这提供了最大的灵活性来应用特定的配置。
         * </p>
         *
         * @param customizer 接收 ObjectMapper 实例的自定义函数
         *
         * @return Builder实例
         */
        public Builder customize(Consumer<ObjectMapper> customizer) {
            this.customizers.add(customizer);
            return this;
        }

        /**
         * 在预设、模块和特性配置之后、mapper 构建之前定制 Jackson builder。
         * 回调按注册顺序执行；旧 {@link #customize(Consumer)} 回调仍在 build 后执行并拥有最终优先级。
         * 回调仅用于构建，不应保留 builder 或 mapper 引用以供后续并发突变。
         *
         * @param customizer 必需的构建期配置函数
         * @return Builder 实例
         */
        public Builder customizeBuilder(Consumer<JsonMapper.Builder> customizer) {
            builderCustomizers.add(Objects.requireNonNull(customizer, "builder customizer cannot be null"));
            return this;
        }

        // -------------------- Build --------------------

        /**
         * 构建 ObjectMapper 实例
         * <p>
         * 根据当前配置构建并返回一个配置好的 ObjectMapper 实例。
         * 构建过程包括：
         * <ol>
         *   <li>添加配置的模块（JDK8, ParameterNames, JavaTime等）</li>
         *   <li>配置 Long 转 String 序列化</li>
         *   <li>添加用户自定义模块</li>
         *   <li>应用 MapperFeature 配置</li>
         *   <li>配置时区和日期格式</li>
         *   <li>应用序列化/反序列化特性</li>
         *   <li>执行用户自定义回调</li>
         * </ol>
         * </p>
         *
         * @return 配置好的 ObjectMapper 实例
         */
        public ObjectMapper build() {
            JsonMapper.Builder builder = JsonMapper.builder();

            // 1. 基础模块装配
            if (useJdk8Module) builder.addModule(new Jdk8Module());
            if (useParamNamesModule) builder.addModule(new ParameterNamesModule());

            // 2. 复杂的 JavaTimeModule 装配
            if (useJavaTimeModule) {
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                if (dateTimeFormat != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimeFormat);
                    javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
                    javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
                }
                if (dateFormat != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
                    javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(formatter));
                    javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(formatter));
                }
                if (timeFormat != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timeFormat);
                    javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(formatter));
                    javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(formatter));
                }
                builder.addModule(javaTimeModule);
            }

            // 3. Long 转 String 模块
            if (longToString) {
                SimpleModule longModule = new SimpleModule();
                longModule.addSerializer(Long.class, ToStringSerializer.instance);
                longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
                builder.addModule(longModule);
            }

            // 4. 用户额外模块
            extraModules.forEach(builder::addModule);

            // 5. 应用 MapperFeature
            if (Boolean.TRUE.equals(sortProperties)) {
                // POJO 属性按字母序输出；同时让 Map<K,V> 按 key 排序，保证 canonical 真正字节稳定。
                builder.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
                builder.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            }

            // 6. 配置时区 (针对 java.util.Date)
            builder.defaultTimeZone(timeZone);
            if (dateTimeFormat != null) {
                // 设置 java.util.Date 的默认格式
                builder.defaultDateFormat(new SimpleDateFormat(dateTimeFormat));
            }

            // 7. 配置特性开关
            configureFeatures(builder);

            // 8. 构建期定制先于发布；兼容的 mapper 回调保留最终优先级。
            builderCustomizers.forEach(c -> c.accept(builder));
            ObjectMapper mapper = builder.build();
            customizers.forEach(c -> c.accept(mapper));

            return mapper;
        }

        /**
         * 配置 ObjectMapper 的特性开关
         * <p>
         * 根据 Builder 中的配置选项，应用相应的序列化、反序列化和解析特性。
         * </p>
         *
         * @param mapper 要配置的 Jackson builder
         */
        private void configureFeatures(JsonMapper.Builder mapper) {
            // Serialization
            if (Boolean.TRUE.equals(prettyPrint)) mapper.enable(SerializationFeature.INDENT_OUTPUT);

            if (writeDatesAsTimestamps != null) {
                mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, writeDatesAsTimestamps);
            }

            if (failOnEmptyBeans != null) {
                mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, failOnEmptyBeans);
            }

            if (serializationInclusion != null) mapper.serializationInclusion(serializationInclusion);

            // Deserialization
            if (failOnUnknownProperties != null) {
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
            }
            if (Boolean.TRUE.equals(acceptEmptyStringAsNull)) {
                mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            }

            // Parser
            if (Boolean.TRUE.equals(allowComments)) mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            if (Boolean.TRUE.equals(allowSingleQuotes)) mapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            if (Boolean.TRUE.equals(allowUnquotedFieldNames))
                mapper.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        }
    }
}
