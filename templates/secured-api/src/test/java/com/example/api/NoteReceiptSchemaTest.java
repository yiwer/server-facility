package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import com.example.api.notes.NotesFailure;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NoteReceiptSchemaTest {
    @Test void maintenanceBindsTheActualApplicationSchemaEvenWhenItsNameContainsADollarTagAndTheCallerHasAShadowTable() throws Exception {
        String database = Postgres.freshUrl(), schema = "ops$body$schema";
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                "--spring.datasource.hikari.data-source-properties.currentSchema=" + schema);
             var connection = NoteReceiptMaintenanceTest.maintenance(database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Custom schema");
            var input = new Notes.NewNote("one", "Original", "");
            var note = notes.create(actor, workspace.id(), "one", input);
            try (var statement = connection.createStatement()) {
                statement.execute("update \"" + schema + "\".note_command set receipt_expires_at = clock_timestamp() - interval '1 second'");
                statement.execute("create temporary table note_command(marker text)");
                statement.execute("insert into note_command values ('must remain untouched')");
            }
            assertThat(NoteReceiptMaintenanceTest.cleanup(connection, schema, "clock_timestamp(), 1")).isEqualTo(1);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select marker from note_command")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("must remain untouched");
                assertThat(rows.next()).isFalse();
            }
            assertThat(notes.get(actor, workspace.id(), note.id())).isEqualTo(note);
            assertThatThrownBy(() -> notes.create(actor, workspace.id(), "one", input))
                    .isInstanceOf(NotesFailure.class).hasMessage("command_receipt_expired");
        }
    }
}
