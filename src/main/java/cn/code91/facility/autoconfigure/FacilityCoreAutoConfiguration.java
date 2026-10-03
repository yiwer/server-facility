package cn.code91.facility.autoconfigure;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.log.LogPostHandler;
import cn.code91.facility.log.LogPostHandlerComposite;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

/**
 * Core capabilities: SpringContextHolder, LogPostHandlerComposite.
 * <p>
 * No conditions — these are always activated when server-facility is on the classpath.
 * <p>
 * Note: JsonConfig is a Lombok @UtilityClass (all-static, private constructor) and
 * cannot be registered as a Spring bean. It is used via its static factory methods directly.
 */
@AutoConfiguration
public class FacilityCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpringContextHolder.class)
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    @Bean
    @ConditionalOnMissingBean(LogPostHandlerComposite.class)
    public LogPostHandlerComposite logPostHandlerComposite(List<LogPostHandler> handlers) {
        return new LogPostHandlerComposite(handlers);
    }

    @Bean(name = "facilityMessageSource", defaultCandidate = false)
    @ConditionalOnMissingBean(name = "facilityMessageSource")
    public MessageSource facilityMessageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/facility-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(false);
        return ms;
    }
}
