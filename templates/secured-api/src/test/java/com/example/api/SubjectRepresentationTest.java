package com.example.api;

import com.example.fixtures.RawSubjectFixture;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SubjectRepresentationTest {
    @Test void standardProcessorAlreadyNormalizesNumericAndStringSubjectsToTheSameIdentity() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, new Class<?>[]{RawSubjectFixture.class})) {
            var numeric = app.get("/api/greeting", issuer.token("a", Map.of("sub", 42), Set.of()));
            Object subject = app.context.getBean("seenSubject", AtomicReference.class).get();
            System.out.println("NIMBUS_PROCESSOR_SUBJECT=" + (subject == null ? "null" : subject.getClass().getName() + ":" + subject));
            assertThat(subject).isEqualTo("42");
            var string = app.get("/api/greeting", issuer.token("a", Map.of("sub", "42"), Set.of()));
            assertThat(numeric.statusCode()).isEqualTo(200); assertThat(string.statusCode()).isEqualTo(200);
            assertThat(numeric.body()).isEqualTo(string.body());
            assertThat(app.context.getBean("seenSubject", AtomicReference.class).get()).isEqualTo("42");
        }
    }
}
