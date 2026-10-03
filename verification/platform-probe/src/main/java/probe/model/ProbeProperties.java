package probe.model;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Builder
@ConfigurationProperties("probe")
public record ProbeProperties(int limit, String label) {}
