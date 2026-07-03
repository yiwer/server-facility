package cn.code91.facility.locale;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocaleUtil - i18n 门面与 C1 边界本地化(ADR-0010)")
class LocaleUtilTest {

    @BeforeEach
    @AfterEach
    void clearHolder() throws Exception {
        Field f = SpringContextHolder.class.getDeclaredField("CONTEXT_REF");
        f.setAccessible(true);
        ((AtomicReference<?>) f.get(null)).set(null);
    }

    private static ErrorTypeInterface customType(String key, String template) {
        return new ErrorTypeInterface() {
            @Override public int getCode() { return 1; }
            @Override public String getMessageKey() { return key; }
            @Override public String getDefaultMessage() { return template; }
        };
    }

    private static void installBundleContext() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/facility-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        // GenericApplicationContext 不预绑 messageSource(StaticApplicationContext 构造器会预绑
        // StaticMessageSource 致 registerSingleton 抛 ISE——fixture 勘误);refresh 前注册,
        // initMessageSource 即采用之,getBean(MessageSource.class) 恰一个 bean
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("messageSource", ms);
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);
    }

    @Nested
    @DisplayName("无 Spring(holder 空)——回退语义")
    class NoSpring {

        @Test
        void localize_withArgs_rendersDefaultTemplate() {
            assertThat(LocaleUtil.localize(customType("t.k", "用户 {0} 不存在"), "张三"))
                    .isEqualTo("用户 张三 不存在");
        }

        @Test
        void localize_noArgs_returnsTemplateVerbatim() {
            // 无参不经 MessageFormat:单引号与花括号原样保留(镜像 renderFallback 语义)
            assertThat(LocaleUtil.localize(customType("t.k", "it's {raw}")))
                    .isEqualTo("it's {raw}");
        }

        @Test
        void localize_nullTemplate_returnsKey() {
            assertThat(LocaleUtil.localize(customType("t.only.key", null))).isEqualTo("t.only.key");
        }

        @Test
        void localize_facilityErrorType_fallsBackToDefaultMessage() {
            assertThat(LocaleUtil.localize(FacilityErrorType.JSON_SERIALIZE_ERROR))
                    .isEqualTo("对象序列化异常");
        }

        @Test
        void translateMessage_returnsKeyItself() {
            assertThat(LocaleUtil.translateMessage("absent.key", Locale.ENGLISH)).isEqualTo("absent.key");
        }

        @Test
        void translateMessageWithArgs_returnsKeyItself() {
            assertThat(LocaleUtil.translateMessageWithArgs("absent.key", new Object[]{"x"}))
                    .isEqualTo("absent.key");
        }

        @Test
        void translateMessageWithFallback_rendersFallbackPattern() {
            assertThat(LocaleUtil.translateMessageWithFallback("absent.key", new Object[]{"7"}, "共 {0} 条", Locale.ENGLISH))
                    .isEqualTo("共 7 条");
        }
    }

    @Nested
    @DisplayName("有 Spring + facility bundle——命中与未命中")
    class WithBundle {

        @Test
        void localize_englishBundleHit() {
            installBundleContext();
            assertThat(LocaleUtil.localize(FacilityErrorType.JSON_SERIALIZE_ERROR, new Object[]{}, Locale.ENGLISH))
                    .isEqualTo("Failed to serialize object to JSON");
        }

        @Test
        void localize_simplifiedChineseBundleHit() {
            installBundleContext();
            // zh_CN bundle 值恰与默认模板同文——本用例锚定的是"命中路径可用",与英文用例互证
            assertThat(LocaleUtil.localize(FacilityErrorType.JSON_SERIALIZE_ERROR, new Object[]{}, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("对象序列化异常");
        }

        @Test
        void localize_bundleMiss_fallsBackToTemplate() {
            installBundleContext();
            assertThat(LocaleUtil.localize(customType("facility.test.absent", "兜底 {0}"), new Object[]{"X"}, Locale.ENGLISH))
                    .isEqualTo("兜底 X");
        }
    }

    @Test
    @DisplayName("getLocale 取 LocaleContextHolder 当前值")
    void getLocale_returnsLocaleContextHolderValue() {
        Locale original = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(Locale.FRANCE);
            assertThat(LocaleUtil.getLocale()).isEqualTo(Locale.FRANCE);
        } finally {
            LocaleContextHolder.setLocale(original);
        }
    }
}
