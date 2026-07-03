/**
 * <h2>cn.code91.facility.autoconfigure</h2>
 *
 * <p><b>Purpose:</b> Spring Boot 3 {@code @AutoConfiguration} composition root — the six
 * entry points that wire facility beans into a host application without an explicit
 * {@code @Import}: Core, Id, Json, Locale, Async, Web. Discovered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * (six lines, one fully-qualified class name each).</p>
 *
 * <p><b>Entry classes:</b> {@code FacilityCoreAutoConfiguration},
 * {@code FacilityIdAutoConfiguration}, {@code FacilityJsonAutoConfiguration},
 * {@code FacilityLocaleAutoConfiguration}, {@code FacilityAsyncAutoConfiguration},
 * {@code FacilityWebAutoConfiguration}.</p>
 *
 * <p><b>Every bean defers to the host application:</b> nearly all beans here are guarded by
 * {@code @ConditionalOnMissingBean}, by type ({@code SnowIdGenerator}, {@code JsonsRegistry})
 * or by name ({@code messageSource}, {@code facilityWebMvcConfigurer},
 * {@code facilityCorsWebMvcConfigurer}, {@code facilitySessionWebMvcConfigurer}) — facility
 * fills gaps, it never overrides a bean the host already declared.
 * {@code FacilityWebAutoConfiguration} additionally gates {@code TraceIdFilter},
 * {@code RepeatableRequestFilter}, {@code AccessLogInterceptor}, and the CORS configurer
 * behind individual {@code @ConditionalOnProperty(prefix = "facility.web.*",
 * name = "enabled", matchIfMissing = true)} switches, and gates the whole class on
 * {@code @ConditionalOnWebApplication(Type.SERVLET)} — it stays dark outside a servlet-stack
 * web application. {@code FacilityIdAutoConfiguration} carries the same
 * {@code matchIfMissing = true} pattern at {@code facility.id.enabled}.</p>
 *
 * <p><b>No {@code properties} sub-package:</b> since C3 (spec §4.4), every
 * {@code @ConfigurationProperties} class this layer enables lives beside its component
 * consumer instead of in a sibling configuration package: {@code FacilityIdProperties} in
 * {@code id}; the five web properties split across {@code web}, {@code web.filter},
 * {@code web.interceptor}, and {@code web.exception} per their own consumers. This package
 * imports each of them from its component's home — it declares none of its own.</p>
 *
 * <p><b>Depends on:</b> {@code context}, {@code log} (Core — {@code SpringContextHolder},
 * {@code LogPostHandlerComposite}); {@code id} (Id); {@code json} (Json); {@code locale}
 * (Locale — {@code AggregatedMessageSource}); {@code web}, {@code web.filter},
 * {@code web.interceptor}, {@code web.exception} (Web). {@code FacilityAsyncAutoConfiguration}
 * depends on none of facility's own packages: it registers a bare JDK
 * {@code java.util.concurrent.Executor} for the host to discover, independent of the
 * {@code async} package's {@code Async} facade, which takes its executor through explicit
 * fluent calls rather than dependency injection.</p>
 *
 * <p><b>Depended on by:</b> nothing else in {@code cn.code91.facility} — no main package may
 * import from {@code autoconfigure} (C3, spec §4.4), enforced by the
 * {@code autoconfigure_is_not_depended_on_by_main_packages} ArchUnit rule. Its only consumer
 * is Spring Boot's auto-configuration processor at application startup.</p>
 */
package cn.code91.facility.autoconfigure;
