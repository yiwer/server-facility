package cn.code91.facility.web.filter;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.repeatable-request")
public class FacilityWebRepeatableRequestProperties {
    private boolean enabled = false;

    /**
     * Reject requests whose actual body exceeds this positive byte budget.
     * Invalid values fail when the enabled filter is constructed (ADR-0028).
     */
    private long maxBodyBytes = 10L * 1024 * 1024;

    /** Media-type patterns, including structured suffixes; historical text/ is accepted as text/*. */
    @Getter(onMethod_ = @Nullable)
    @Setter(onParam_ = @Nullable)
    private List<String> includeContentTypes = List.of(
        "application/json", "application/*+json", "application/xml", "application/*+xml", "text/*"
    );

    /** Explicit enabled filter may be narrowed to selected Ant-style paths. Empty means no targets. */
    @Getter(onMethod_ = @Nullable)
    @Setter(onParam_ = @Nullable)
    private List<String> includePaths = List.of("/**");

    /** Skip wrapping for requests matching these Ant-style paths. */
    @Getter(onMethod_ = @Nullable)
    @Setter(onParam_ = @Nullable)
    private List<String> excludePaths = List.of("/actuator/**");
}
