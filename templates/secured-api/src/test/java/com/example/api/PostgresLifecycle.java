package com.example.api;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Root-store close makes failed native cleanup a failed test run; the JVM hook is only a fallback. */
public final class PostgresLifecycle implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
    @Override public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(ExtensionContext.Namespace.create(PostgresLifecycle.class))
                .computeIfAbsent("owned-cluster", key -> (AutoCloseable) Postgres::closeIfStarted);
    }
    @Override public void beforeEach(ExtensionContext context) { Postgres.beginTest(); }
    @Override public void afterEach(ExtensionContext context) throws Exception {
        // Jupiter combines teardown failures with prior assertions and does not hide them behind an aborted test.
        Postgres.endTest();
    }
}
