package com.example.api.configuration;

import cn.code91.facility.web.exception.FacilityHttpErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.ErrorResponseException;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean JwkRequests jwkRequests(org.springframework.core.env.Environment environment, TrustPolicy policy) {
        var timeout = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("app.security.jwk-timeout", java.time.Duration.class).orElse(java.time.Duration.ofSeconds(2));
        return new JwkRequests(timeout, policy);
    }

    @Bean org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer jwkClient(JwkRequests requests) {
        return builder -> builder.restOperations(new org.springframework.web.client.RestTemplate(requests))
                .jwsAlgorithms(algorithms -> {
                    algorithms.clear(); algorithms.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
                });
    }

    @Bean org.springframework.security.oauth2.jwt.JwtTimestampValidator tokenTimestamps() {
        var validator = new org.springframework.security.oauth2.jwt.JwtTimestampValidator(java.time.Duration.ofSeconds(60));
        validator.setAllowEmptyExpiryClaim(false);
        return validator;
    }

    @Bean org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> tokenSubject() {
        return new org.springframework.security.oauth2.jwt.JwtClaimValidator<Object>("sub",
                value -> value instanceof String subject && !subject.isBlank());
    }

    @Bean TrustPolicy trustPolicy(org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties properties,
                                  org.springframework.core.env.Environment environment) {
        return TrustPolicy.validate(properties, environment);
    }

    @Bean AuthenticationEntryPoint authenticationEntryPoint(FacilityHttpErrors errors) {
        return (request, response, failure) -> {
            var safe = new ErrorResponseException(HttpStatus.UNAUTHORIZED);
            safe.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                    failure instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException
                            ? "Bearer error=\"invalid_token\"" : "Bearer");
            errors.write(request, response, safe);
        };
    }

    @Bean org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler(FacilityHttpErrors errors) {
        return (request, response, failure) -> {
            var safe = new ErrorResponseException(HttpStatus.FORBIDDEN);
            safe.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
            errors.write(request, response, safe);
        };
    }

    @Bean SecurityFilterChain apiSecurity(HttpSecurity http, AuthenticationEntryPoint entryPoint, TrustPolicy policy,
                                         org.springframework.security.web.access.AccessDeniedHandler denied,
                                         FacilityHttpErrors errors) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.requestMatchers("/health").permitAll()
                        .requestMatchers("/api/greeting", "/api/greeting/**").hasAuthority("SCOPE_greeting:read").anyRequest().denyAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}).authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(value -> value.resource(policy.resourceUri())))
                        .withObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<
                                org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter>() {
                            @Override public <O extends org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler((request, response, failure) -> {
                                    if (failure instanceof org.springframework.security.authentication.AuthenticationServiceException) {
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
