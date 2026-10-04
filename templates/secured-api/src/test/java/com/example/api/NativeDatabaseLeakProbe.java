package com.example.api;

import java.sql.Connection;
import java.util.ArrayList;
import org.junit.jupiter.api.*;

/** Deliberate negative control, selected explicitly by Verify rather than the positive suite. */
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class NativeDatabaseLeakProbe {
    private static final ArrayList<Connection> leaked = new ArrayList<>();

    @Test void aPrimaryAssertionRemainsVisibleWhenItsDatabaseIsStillBorrowed() throws Exception {
        leaked.add(Postgres.connect(Postgres.freshUrl()));
        Assertions.fail("primary-assertion-sentinel");
    }
    @Test void anAbortedTestCannotHideFailedDatabaseCleanup() throws Exception {
        leaked.add(Postgres.connect(Postgres.freshUrl()));
        Assumptions.assumeTrue(false, "abort-sentinel");
    }
    @AfterAll static void releaseDeliberatelyBorrowedConnections() throws Exception {
        Exception failure = null;
        for (Connection connection : leaked) try { connection.close(); }
        catch (Exception close) { if (failure == null) failure = close; else failure.addSuppressed(close); }
        if (failure != null) throw failure;
    }
}
