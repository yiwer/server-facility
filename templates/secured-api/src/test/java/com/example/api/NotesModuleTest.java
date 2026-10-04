package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import com.example.api.notes.NotesFailure;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotesModuleTest {
    @Test void aSeededPublicModuleSequencePreservesOwnershipUniquenessAndBoundedPageContents() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            var notes = app.context.getBean(Notes.class);
            var actor = new Actor(issuer.issuer(), "property-owner");
            var stranger = new Actor(issuer.issuer(), "another-owner");
            var workspace = notes.createWorkspace(actor, "Sequence");
            var random = new Random(280028L);
            var expected = new TreeMap<String, Notes.Note>();
            for (int i = 0; i < 60; i++) {
                String slug = "item-" + random.nextInt(12);
                var existing = expected.get(slug);
                if (existing == null) expected.put(slug, notes.create(actor, workspace.id(), UUID.randomUUID().toString(), new Notes.NewNote(slug, "初始🌱", "body")));
                else if (random.nextBoolean()) expected.put(slug, notes.update(actor, workspace.id(), existing.id(), UUID.randomUUID().toString(), new Notes.EditNote("changed-" + i, "body-" + i)));
                else { notes.delete(actor, workspace.id(), existing.id()); expected.remove(slug); }
                var page = notes.list(actor, workspace.id(), new Notes.PageQuery(0, 7, "slug", "asc"));
                assertThat(page.total()).isEqualTo(expected.size());
                assertThat(page.items()).containsExactlyElementsOf(expected.values().stream().limit(7).toList());
                assertThatThrownBy(() -> notes.list(stranger, workspace.id(), new Notes.PageQuery(0, 7, "slug", "asc")))
                        .isInstanceOf(NotesFailure.class).hasMessage("workspace_forbidden");
            }
            assertThatThrownBy(() -> notes.list(actor, workspace.id(), new Notes.PageQuery(0, 1, "slug", "asc")).items().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void theBusinessModuleDoesNotReadServletSecurityOrGlobalSessionState() throws Exception {
        Path directory = Path.of("target/generated-classes/jacoco/com/example/api/notes");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("Notes(?:\\$.*)?\\.class")).toList()) {
                var model = ClassFile.of().parse(Files.readAllBytes(file));
                assertThat(model.majorVersion()).isEqualTo(69);
                for (var entry : model.constantPool()) if (entry instanceof ClassEntry type) {
                    assertThat(type.asInternalName()).doesNotStartWith("jakarta/servlet/").doesNotStartWith("org/springframework/security/")
                            .doesNotContain("SessionUser", "SpringContextHolder");
                }
            }
        }
    }
}
