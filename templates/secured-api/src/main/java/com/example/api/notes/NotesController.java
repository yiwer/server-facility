package com.example.api.notes;

import com.example.api.greeting.Actor;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
class NotesController {
    private final Notes notes;
    NotesController(Notes notes) { this.notes = notes; }
    record WorkspaceInput(String name) {}
    @PostMapping("/api/workspaces") ResponseEntity<Notes.Workspace> workspace(@AuthenticationPrincipal Jwt jwt, @RequestBody WorkspaceInput input) {
        var result = notes.createWorkspace(actor(jwt), input.name());
        return ResponseEntity.created(URI.create("/api/workspaces/" + result.id() + "/notes")).body(result);
    }
    @PostMapping("/api/workspaces/{workspace}/notes") ResponseEntity<Notes.Note> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @RequestBody Notes.NewNote input) {
        var result = notes.create(actor(jwt), workspace, input);
        return ResponseEntity.created(URI.create("/api/workspaces/" + workspace + "/notes/" + result.id())).body(result);
    }
    @GetMapping("/api/workspaces/{workspace}/notes/{id}") Notes.Note get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @PathVariable UUID id) {
        return notes.get(actor(jwt), workspace, id);
    }
    private static Actor actor(Jwt jwt) { return new Actor(jwt.getClaimAsString("iss"), jwt.getSubject()); }
}
