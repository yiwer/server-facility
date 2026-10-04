package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.example.api.NoteReceiptMaintenanceTest.*;
import static com.example.api.PersistenceFailureHttpTest.count;

class NoteReceiptAuthorizationTest {
    @Test void onlyAnExplicitlyGrantedInvokerWithItsOwnTablePrivilegesCanClearReceipts() throws Exception {
        String database = Postgres.freshUrl(), role = "maintenance_" + UUID.randomUUID().toString().replace("-", "");
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var operator = maintenance(database); var admin = maintenance(database);
             var removeOwnedRole = removeOwnedRole(database, role)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Invoker policy");
            notes.create(actor, workspace.id(), "one", new Notes.NewNote("one", "Original", ""));
            Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() - interval '1 second'");
            try (var grants = admin.createStatement(); var session = operator.createStatement()) {
                grants.execute("create role " + role + " nologin");
                session.execute("set role " + role);
                assertThatThrownBy(() -> cleanup(operator, "public", "clock_timestamp(), 1"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("42501"));
                grants.execute("grant usage on schema public to " + role);
                grants.execute("grant execute on function public.cleanup_note_receipts(timestamptz, integer) to " + role);
                assertThatThrownBy(() -> cleanup(operator, "public", "clock_timestamp(), 1"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("42501"));
                assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(1);
                grants.execute("grant select, update on table public.note_command to " + role);
                assertThat(cleanup(operator, "public", "clock_timestamp(), 1")).isEqualTo(1);
                session.execute("reset role");
            }
            assertThat(count(database, """
                    select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                    where n.nspname = 'public' and p.proname = 'cleanup_note_receipts'
                      and not p.prosecdef and not p.proisstrict and p.provolatile = 'v' and p.proparallel = 'u'
                    """)).isEqualTo(1);
        }
        assertThat(count(Postgres.adminUrl(), "select count(*) from pg_roles where rolname = '" + role + "'")).isZero();
    }

    private static AutoCloseable removeOwnedRole(String database, String role) {
        if (!role.matches("maintenance_[0-9a-f]{32}")) throw new IllegalArgumentException("Not an owned role");
        return () -> {
            try (var connection = maintenance(database); var statement = connection.createStatement()) {
                SQLException failure = null;
                for (String sql : new String[]{"drop owned by " + role, "drop role if exists " + role}) {
                    try { statement.execute(sql); }
                    catch (SQLException cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
                }
                if (failure != null) throw failure;
            }
        };
    }
}
