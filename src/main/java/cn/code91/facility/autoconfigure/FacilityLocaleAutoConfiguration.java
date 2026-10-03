package cn.code91.facility.autoconfigure;

import cn.code91.facility.locale.AggregatedMessageSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.List;

/**
 * Facility-only fallback after the host's MessageSource policy has been applied.
 * Hosts include i18n/facility-messages explicitly in spring.messages.basename
 * when they want to combine their bundles with this library's translations.
 */
@AutoConfiguration(after = MessageSourceAutoConfiguration.class)
public class FacilityLocaleAutoConfiguration {
    @Bean("messageSource")
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource facilityDefaultMessageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/facility-messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    /** @deprecated Explicit compatibility composition only; not automatic host configuration. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public MessageSource messageSource(List<MessageSource> moduleMessageSources) {
        return new AggregatedMessageSource(moduleMessageSources);
    }
}
