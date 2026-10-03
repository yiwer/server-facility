package com.example.api.configuration;

import java.net.URI;
import java.util.Arrays;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.core.env.Environment;

/** Startup policy is evaluated before first traffic, independently of deferred key discovery. */
record TrustPolicy(String resourceUri, boolean local) {
    static TrustPolicy validate(OAuth2ResourceServerProperties properties, Environment environment) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        boolean local = profiles.contains("local");
        var jwt = properties.getJwt();
        String resource = environment.getProperty("app.security.resource-uri");
        if (local && profiles.contains("prod") || !endpoint(jwt.getIssuerUri(), local)
                || jwt.getAudiences().size() != 1 || jwt.getAudiences().getFirst().isBlank()
                || !endpoint(resource, local)
                || jwt.getJwkSetUri() != null && !endpoint(jwt.getJwkSetUri(), local)
                || jwt.getPublicKeyLocation() != null) {
            throw new IllegalArgumentException("Invalid application JWT trust policy; configure issuer, one audience and resource URI; use local only for loopback development");
        }
        return new TrustPolicy(resource, local);
    }

    boolean permits(URI uri) { return endpoint(uri.toString(), local); }

    private static boolean endpoint(String value, boolean local) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return false;
            return "https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
