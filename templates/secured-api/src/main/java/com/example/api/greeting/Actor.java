package com.example.api.greeting;

import java.util.Objects;

/** Stable business identity established by the application's authentication boundary. */
public record Actor(String issuer, String subject) {
    public Actor { Objects.requireNonNull(issuer, "issuer"); Objects.requireNonNull(subject, "subject"); }
}
