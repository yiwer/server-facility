package com.example.api;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Root-store close makes failed native cleanup a failed test run; the JVM hook is only a fallback. */
public final class PostgresLifecycle implements BeforeAllCallback {
    @Override public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(ExtensionContext.Namespace.create(PostgresLifecycle.class))
                .computeIfAbsent("owned-cluster", key -> (AutoCloseable) Postgres::closeIfStarted);
    }
}
