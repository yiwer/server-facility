package com.example.fixtures;

import cn.code91.facility.web.ratelimit.RateLimit;
import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import com.example.api.notes.NotesFailure;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** An optional consumer-selected ingress policy around the public business Module. */
@TestConfiguration(proxyBeanMethods = false)
public class NoteQuotaFixture {
    @RestController public static class Operation {
        private final Notes notes;
        public Operation(Notes notes) { this.notes = notes; }
        @PostMapping("/api/workspaces/{workspace}/limited-commands")
        @RateLimit(key = "note-command-test", scope = RateLimit.Scope.PRINCIPAL, capacity = 4, permitsPerSecond = 0.000001)
        public Notes.Note create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace,
                                 @RequestHeader("Idempotency-Key") String key, @RequestBody Notes.NewNote input) {
            return notes.create(new Actor(jwt.getClaimAsString("iss"), jwt.getSubject()), workspace, key, input);
        }
        @ExceptionHandler(NotesFailure.class)
        ResponseEntity<ProblemDetail> business(NotesFailure failure) {
            var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            problem.setProperty("code", failure.code());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
    }
}
