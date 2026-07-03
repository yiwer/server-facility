package cn.code91.facility.autoconfigure;

import cn.code91.facility.locale.AggregatedMessageSource;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Registers an {@link AggregatedMessageSource} as the application's primary
 * {@code messageSource} bean. Runs before Spring Boot's
 * {@link MessageSourceAutoConfiguration} so that Boot's default does not claim
 * the name first.
 *
 * <p>Module autoconfigurations contribute additional named {@link MessageSource}
 * beans (e.g. {@code storageMessageSource}, {@code databaseMessageSource});
 * Spring injects all of them into the constructor list below.
 */
@AutoConfiguration
@AutoConfigureBefore(MessageSourceAutoConfiguration.class)
public class FacilityLocaleAutoConfiguration {

    @Bean("messageSource")
    @Primary
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource(List<MessageSource> moduleMessageSources) {
        return new AggregatedMessageSource(moduleMessageSources);
    }
}
