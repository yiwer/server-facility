package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import com.example.api.notes.NotesFailure;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NoteCommandProtocolTest {
    @Test void storedIdentityAndFingerprintMatchIndependentUtf8LengthPrefixedGoldenBytes() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            var notes = app.context.getBean(Notes.class);
            var actor = new Actor("https://issuer.example", "用户🌱");
            var workspace = notes.createWorkspace(actor, "Golden");
            var note = notes.create(actor, workspace.id(), "golden", new Notes.NewNote("golden", "独立金样 🌱", "line one\nline two"));
            assertThat(notes.get(actor, workspace.id(), note.id())).isEqualTo(note);
            // Literals generated independently with Python struct.pack('>I', utf8_length) + hashlib.sha256;
            // the actor literal also passed an independent PostgreSQL binary-string probe.
            try (var connection = Postgres.connect(database); var statement = connection.createStatement();
                 var rows = statement.executeQuery("select encode(actor_hash, 'hex'), encode(fingerprint, 'hex') from note_command")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("c4cdeb9118548b7367f5129bd2630cfdd8ac593aa93168b60133dfb232afd808");
                assertThat(rows.getString(2)).isEqualTo("5dd3aabf543d4bc3491abb3a069813bd59cb084fed09c73f057c2b3f53a73918");
                assertThat(rows.next()).isFalse();
            }
        }
    }
    @Test void seededUnicodeAndDelimiterInputsReplayExactlyAndChangedContentCannotAlias() throws Exception {
        long seed = 29005264L;
        var random = new Random(seed);
        String[] alphabet = {"a", "界", "🌱", "é", "e\u0301", ":", "|", " ", "\n", "\t"};
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "seeded");
            var workspace = notes.createWorkspace(actor, "Seeded commands");
            for (int sample = 0; sample < 64; sample++) {
                var body = new StringBuilder();
                for (int i = 0; i < 1 + random.nextInt(40); i++) body.append(alphabet[random.nextInt(alphabet.length)]);
                String key = "seed-" + sample;
                var input = new Notes.NewNote(key, "T" + body, body.toString());
                var first = notes.create(actor, workspace.id(), key, input);
                assertThat(notes.create(actor, workspace.id(), key, input)).as("seed=%s sample=%s", seed, sample).isEqualTo(first);
                assertThatThrownBy(() -> notes.create(actor, workspace.id(), key, new Notes.NewNote(key, input.title(), input.body() + "x")))
                        .as("seed=%s sample=%s", seed, sample).isInstanceOf(NotesFailure.class).hasMessage("command_conflict");
                assertThat(notes.get(actor, workspace.id(), first.id())).isEqualTo(first);
            }
            assertThat(notes.list(actor, workspace.id(), new Notes.PageQuery(0, 100, "slug", "asc")).total()).isEqualTo(64);
        }
    }
}
