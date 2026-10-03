package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.greeting.Greetings;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BusinessBoundaryTest {
    @Test void operationRequiresAnExplicitActorAndPreservesItsIssuerSubjectIdentity() {
        Actor actor = new Actor("https://issuer.example", "person-1");
        assertThat(new Greetings().greet(actor).actor()).isEqualTo(actor);
        assertThatThrownBy(() -> new Greetings().greet(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Actor(null, "person-1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Actor("https://issuer.example", null)).isInstanceOf(NullPointerException.class);
    }

    @Test void compiledBusinessCodeHasNoServletSpringSecurityOrStaticSessionDependency() throws Exception {
        for (Class<?> type : new Class<?>[]{Actor.class, Greetings.class, Greetings.Greeting.class}) {
            // Offline coverage modifies class files before tests; inspect the compiler's original output.
            Path original = Path.of("target/generated-classes/jacoco", type.getName().replace('.', '/') + ".class");
            assertThat(original).isRegularFile();
            var model = ClassFile.of().parse(Files.readAllBytes(original));
            assertThat(model.majorVersion()).isEqualTo(69);
            for (var entry : model.constantPool()) {
                if (entry instanceof ClassEntry referenced) {
                    assertThat(referenced.asInternalName()).matches("(?:java/.*|com/example/api/greeting/.*)");
                }
            }
        }
    }
}
