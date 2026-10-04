package com.example.api;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises the native database fixture across actual JUnit method boundaries. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class PostgresScopeLifecycleTest {
    private static String previousDatabase;

    @Test @Order(1) void oneTestCanShareItsFreshDatabaseAcrossConnections() throws Exception {
        String url = Postgres.freshUrl();
        previousDatabase = url.substring(url.lastIndexOf('/') + 1);
        Postgres.execute(url, "create table shared_effect(value integer); insert into shared_effect values (29)");
        try (var first = Postgres.connect(url); var second = Postgres.connect(url);
             var a = first.createStatement(); var b = second.createStatement();
             var one = a.executeQuery("select value from shared_effect"); var two = b.executeQuery("select value from shared_effect")) {
            assertThat(one.next()).isTrue(); assertThat(one.getInt(1)).isEqualTo(29);
            assertThat(two.next()).isTrue(); assertThat(two.getInt(1)).isEqualTo(29);
        }
    }

    @Test @Order(2) void theNextTestCannotSeeThePreviousTestsOwnedDatabaseButTheSharedFixtureRemains() throws Exception {
        assertThat(previousDatabase).startsWith("app_");
        assertThat(PersistenceFailureHttpTest.count(Postgres.adminUrl(), "select count(*) from pg_database where datname = '" + previousDatabase + "'"))
                .as("fresh databases are owned by one test, not retained until JVM shutdown").isZero();
        assertThat(PersistenceFailureHttpTest.count(Postgres.adminUrl(), "select count(*) from pg_database where datname = 'app_shared'"))
                .isEqualTo(1);
        try (var shared = Postgres.connect(Postgres.sharedUrl())) { assertThat(shared.isValid(2)).isTrue(); }
    }
}
