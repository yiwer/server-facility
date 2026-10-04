package cn.code91.facility.autoconfigure;

import cn.code91.facility.id.FacilityIdProperties;
import cn.code91.facility.id.support.SnowIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FacilityIdProperties.class)
@ConditionalOnProperty(prefix = "facility.id", name = "enabled", havingValue = "true")
public class FacilityIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SnowIdGenerator.class)
    public SnowIdGenerator snowIdGenerator(FacilityIdProperties properties) {
        return new SnowIdGenerator(properties);
    }
}
