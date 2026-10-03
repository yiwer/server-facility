package com.example.fixtures;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;

@TestConfiguration(proxyBeanMethods = false)
public class RawSubjectFixture {
    @Bean public AtomicReference<Object> seenSubject() { return new AtomicReference<>(); }
    @Bean JwkSetUriJwtDecoderBuilderCustomizer inspectSubject(AtomicReference<Object> seenSubject) {
        return builder -> builder.jwtProcessorCustomizer(processor -> {
            var original = processor.getJWTClaimsSetVerifier();
            processor.setJWTClaimsSetVerifier((claims, context) -> {
                original.verify(claims, context); seenSubject.set(claims.getClaims().get("sub"));
            });
        });
    }
}
