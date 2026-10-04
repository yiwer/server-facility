package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.example.api.PersistenceFailureHttpTest.count;

class NoteReceiptMaintenanceTest {
    @Test void seededCleanupPermissionAndClockTransitionsNeverAuthorizeTheSameEffectAgain() throws Exception {
        long seed = 30005364L; var random = new Random(seed);
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var connection = maintenance(database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Seeded recovery");
            var input = new Notes.NewNote("original", "Original", "");
            var original = notes.create(actor, workspace.id(), "original", input);
            boolean member = true, expired = false, cleared = false, exists = true;
            int step = 0;
            for (int cycle = 0; cycle < 6; cycle++) {
                var actions = new ArrayList<>(List.of("replay", "conflict", "expire", "clear", "revoke", "restore", "delete", "clock-back"));
                Collections.shuffle(actions, random);
                for (String action : actions) {
                    String where = "seed=" + seed + " step=" + step++ + " action=" + action;
                    switch (action) {
                        case "replay" -> {
                            if (!member || expired || cleared) {
                                String code = !member ? "workspace_forbidden" : "command_receipt_expired";
                                assertThatThrownBy(() -> notes.create(actor, workspace.id(), "original", input)).as(where)
                                        .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage(code);
                            } else assertThat(notes.create(actor, workspace.id(), "original", input)).as(where).isEqualTo(original);
                        }
                        case "conflict" -> {
                            String code = member ? "command_conflict" : "workspace_forbidden";
                            assertThatThrownBy(() -> notes.create(actor, workspace.id(), "original", new Notes.NewNote("original", "Different", "")))
                                    .as(where).isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage(code);
                        }
                        case "expire", "clock-back" -> {
                            expired = action.equals("expire");
                            Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() "
                                    + (expired ? "- interval '1 hour'" : "+ interval '1 hour'"));
                        }
                        case "clear" -> {
                            int expected = expired && !cleared ? 1 : 0;
                            assertThat(cleanup(connection, "public", "clock_timestamp(), 1")).as(where).isEqualTo(expected);
                            cleared |= expected == 1;
                        }
                        case "revoke" -> { Postgres.execute(database, "delete from workspace_member"); member = false; }
                        case "restore" -> {
                            try (var statement = connection.prepareStatement("insert into workspace_member(workspace_id, issuer, subject) values (?, ?, ?) on conflict do nothing")) {
                                statement.setObject(1, workspace.id()); statement.setString(2, actor.issuer()); statement.setString(3, actor.subject()); statement.executeUpdate();
                            }
                            member = true;
                        }
                        case "delete" -> {
                            if (member && exists) { notes.delete(actor, workspace.id(), original.id()); exists = false; }
                            else {
                                String code = member ? "note_not_found" : "workspace_forbidden";
                                assertThatThrownBy(() -> notes.delete(actor, workspace.id(), original.id())).as(where)
                                        .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage(code);
                            }
                        }
                        default -> throw new AssertionError(action);
                    }
                    assertThat(count(database, "select count(*) from note")).as(where).isEqualTo(exists ? 1 : 0);
                    assertThat(count(database, "select count(*) from note_command")).as(where).isEqualTo(1);
                    assertThat(count(database, "select command_count from workspace")).as(where).isEqualTo(1);
                }
            }
            assertThat(step).isEqualTo(48);
        }
    }
    @Test void cleanupIncludesTheExactCutoffAndLeavesLaterReceiptsAndAllIdentityFieldsAlone() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var connection = maintenance(database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Cutoff boundaries");
            for (String key : List.of("before", "equal", "after", "available"))
                notes.create(actor, workspace.id(), key, new Notes.NewNote(key, "Original " + key, ""));
            Postgres.execute(database, """
                update note_command set receipt_expires_at = timestamptz '2000-01-01 00:00:00Z' +
                  case command_key when 'before' then interval '-1 microsecond'
                                   when 'equal' then interval '0' else interval '1 microsecond' end
                where command_key <> 'available'
                """);
            var identities = identities(database);
            assertThat(cleanup(connection, "public", "'2000-01-01 00:00:00Z', 1000")).isEqualTo(2);
            assertThat(count(database, "select count(*) from note_command where command_key in ('before','equal') and note_id is null")).isEqualTo(2);
            assertThat(count(database, "select count(*) from note_command where command_key in ('after','available') and note_id is not null")).isEqualTo(2);
            assertThat(identities(database)).isEqualTo(identities);
            var available = notes.create(actor, workspace.id(), "available", new Notes.NewNote("available", "Original available", ""));
            assertThat(available.title()).isEqualTo("Original available");
            assertThat(cleanup(connection, "public", "clock_timestamp(), 1")).isEqualTo(1);
            assertThat(cleanup(connection, "public", "clock_timestamp(), 1")).isZero();
            assertThat(count(database, "select count(*) from note")).isEqualTo(4);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(4);
        }
    }

    @Test @org.junit.jupiter.api.Timeout(120)
    void oneThousandIsAnActualBatchLimitAndRepeatedBatchesNeverReleaseIdentityOrQuota() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var connection = maintenance(database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Batch bounds");
            var latest = notes.create(actor, workspace.id(), "create", new Notes.NewNote("one", "Original", ""));
            for (int i = 1; i <= 1000; i++)
                latest = notes.update(actor, workspace.id(), latest.id(), "edit-" + i, new Notes.EditNote("Edit " + i, ""));
            Postgres.execute(database, "update note_command set receipt_expires_at = timestamptz '2000-01-01 00:00:00Z'");
            var identities = identities(database);
            assertThat(cleanup(connection, "public", "clock_timestamp(), 1000")).isEqualTo(1000);
            assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(1);
            assertThat(cleanup(connection, "public", "clock_timestamp(), 1000")).isEqualTo(1);
            assertThat(cleanup(connection, "public", "clock_timestamp(), 1")).isZero();
            assertThat(identities(database)).isEqualTo(identities);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(1001);
            assertThat(notes.get(actor, workspace.id(), latest.id())).isEqualTo(latest);
        }
    }
    @Test void invalidCutoffsAndBatchBudgetsAreRejectedWithoutClearingReceipts() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var connection = maintenance(database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Validated maintenance");
            var original = notes.create(actor, workspace.id(), "retained", new Notes.NewNote("retained", "Original", ""));
            for (String parameters : List.of("null, 1", "'infinity', 1", "'-infinity', 1",
                    "clock_timestamp() + interval '1 hour', 1", "clock_timestamp(), null", "clock_timestamp(), 0",
                    "clock_timestamp(), -1", "clock_timestamp(), 1001", "clock_timestamp(), 2147483647",
                    "clock_timestamp(), -2147483648")) {
                assertThatThrownBy(() -> cleanup(connection, "public", parameters)).as(parameters)
                        .isInstanceOf(SQLException.class)
                        .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("22023"));
            }
            for (int batch : new int[]{1, 999, 1000}) assertThat(cleanup(connection, "public", "clock_timestamp(), " + batch)).isZero();
            assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(1);
            assertThat(notes.get(actor, workspace.id(), original.id())).isEqualTo(original);
        }
    }
    @Test void clearingAReceiptRetainsTheCommandAndCannotReviveItWhenItsDeadlineIsLaterThanTheClock() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "alice");
            var workspace = notes.createWorkspace(actor, "Receipt cleanup");
            var input = new Notes.NewNote("retained", "Original", "Private receipt");
            var original = notes.create(actor, workspace.id(), "retained", input);
            Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() - interval '1 second'");
            try (var maintenance = maintenance(database); var statement = maintenance.createStatement();
                 var result = statement.executeQuery("select public.cleanup_note_receipts(clock_timestamp(), 1)")) {
                assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isEqualTo(1);
            }
            assertThat(count(database, "select count(*) from note_command where note_id is null and slug is null and title is null and body is null"))
                    .isEqualTo(1);
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(1);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(1);
            assertThat(notes.get(actor, workspace.id(), original.id())).isEqualTo(original);
            // Makes the time predicate false, as a backward database clock would, without changing the host clock.
            Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() + interval '1 day'");
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            var replay = NoteCommandsHttpTest.command(app, "POST", "/api/workspaces/" + workspace.id() + "/notes", token,
                    "retained", "{\"slug\":\"retained\",\"title\":\"Original\",\"body\":\"Private receipt\"}");
            assertThat(replay.statusCode()).isEqualTo(410);
            assertThat(NotesHttpTest.JSON.readTree(replay.body()).path("code").asString()).isEqualTo("command_receipt_expired");
            assertThat(count(database, "select count(*) from note")).isEqualTo(1);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(1);
        }
    }

    /** Dedicated operational connection: each SET completes before the autocommit cleanup call. */
    static Connection maintenance(String database) throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", "postgres"); properties.setProperty("password", "");
        properties.setProperty("connectTimeout", "2"); properties.setProperty("socketTimeout", "10");
        properties.setProperty("cancelSignalTimeout", "1");
        Connection connection = DriverManager.getConnection(database, properties);
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute("set session statement_timeout = '5s'");
            statement.execute("set session lock_timeout = '500ms'");
        } catch (SQLException failure) {
            try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        return connection;
    }
    static int cleanup(Connection connection, String schema, String parameters) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("select \"" + schema + "\".cleanup_note_receipts(" + parameters + ")")) {
            if (!rows.next()) throw new AssertionError("Maintenance did not return a row count");
            return rows.getInt(1);
        }
    }
    private static List<List<String>> identities(String database) throws SQLException {
        var result = new ArrayList<List<String>>();
        try (var connection = maintenance(database); var statement = connection.createStatement(); var rows = statement.executeQuery("""
                select workspace_id::text, encode(actor_hash, 'hex'), issuer, subject, operation, command_key,
                       encode(fingerprint, 'hex'), receipt_expires_at::text from note_command
                order by workspace_id, actor_hash, operation, command_key
                """)) {
            while (rows.next()) {
                var identity = new ArrayList<String>();
                for (int field = 1; field <= 8; field++) identity.add(rows.getString(field));
                result.add(List.copyOf(identity));
            }
        }
        return List.copyOf(result);
    }
}
