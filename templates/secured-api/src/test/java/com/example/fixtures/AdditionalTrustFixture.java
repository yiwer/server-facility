package com.example.fixtures;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** An application can compose its own validation policy without replacing the standard decoder. */
@TestConfiguration(proxyBeanMethods = false)
public class AdditionalTrustFixture {
    @Bean OAuth2TokenValidator<Jwt> businessSubjectPolicy() {
        return jwt -> "blocked-subject".equals(jwt.getSubject())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token")) : OAuth2TokenValidatorResult.success();
    }
}
