package com.example.api.configuration;

import cn.code91.facility.web.exception.FacilityHttpErrors;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean JwkRequests jwkRequests(Environment environment, TrustPolicy policy) {
        var timeout = Binder.get(environment)
                .bind("app.security.jwk-timeout", Duration.class).orElse(Duration.ofSeconds(2));
        return new JwkRequests(timeout, policy);
    }

    @Bean JwkSetUriJwtDecoderBuilderCustomizer jwkClient(JwkRequests requests) {
        return builder -> builder.restOperations(new RestTemplate(requests))
                .jwsAlgorithms(algorithms -> {
                    algorithms.clear(); algorithms.add(SignatureAlgorithm.RS256);
                });
    }

    @Bean JwtTimestampValidator tokenTimestamps() {
        var validator = new JwtTimestampValidator(Duration.ofSeconds(60));
        validator.setAllowEmptyExpiryClaim(false);
        return validator;
    }

    @Bean OAuth2TokenValidator<Jwt> tokenSubject() {
        return new JwtClaimValidator<Object>("sub",
                value -> value instanceof String subject && !subject.isBlank());
    }

    @Bean TrustPolicy trustPolicy(OAuth2ResourceServerProperties properties, Environment environment) {
        return TrustPolicy.validate(properties, environment);
    }

    @Bean AuthenticationEntryPoint authenticationEntryPoint(FacilityHttpErrors errors) {
        return (request, response, failure) -> {
            var safe = new ErrorResponseException(HttpStatus.UNAUTHORIZED);
            safe.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                    failure instanceof OAuth2AuthenticationException
                            ? "Bearer error=\"invalid_token\"" : "Bearer");
            errors.write(request, response, safe);
        };
    }

    @Bean AccessDeniedHandler accessDeniedHandler(FacilityHttpErrors errors) {
        return (request, response, failure) -> {
            var safe = new ErrorResponseException(HttpStatus.FORBIDDEN);
            safe.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
            errors.write(request, response, safe);
        };
    }

    @Bean SecurityFilterChain apiSecurity(HttpSecurity http, AuthenticationEntryPoint entryPoint, TrustPolicy policy,
                                         AccessDeniedHandler denied, FacilityHttpErrors errors) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.requestMatchers("/health").permitAll()
                        .requestMatchers("/api/bench/**").hasAuthority("SCOPE_greeting:read")
                        .requestMatchers("/api/greeting", "/api/greeting/**").hasAuthority("SCOPE_greeting:read")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/workspaces/**").hasAuthority("SCOPE_notes:read")
                        .requestMatchers("/api/workspaces", "/api/workspaces/**").hasAuthority("SCOPE_notes:write").anyRequest().denyAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}).authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(value -> value.resource(policy.resourceUri())))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler((request, response, failure) -> {
                                    if (failure instanceof AuthenticationServiceException) {
                                        // Dependency diagnostics may contain endpoint responses; do not copy their cause or text.
                                        errors.write(request, response, new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE));
                                    } else entryPoint.commence(request, response, failure);
                                });
                                return filter;
                            }
                        }))
                .build();
    }
}
