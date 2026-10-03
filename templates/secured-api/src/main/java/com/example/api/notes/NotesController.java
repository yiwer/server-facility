package com.example.api.notes;

import com.example.api.greeting.Actor;
import java.net.URI;
import java.util.UUID;
import java.util.Map;
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
    @GetMapping("/api/workspaces/{workspace}/notes") Notes.Page list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @RequestParam Map<String, String> input) {
        final int page, size;
        try { page = Integer.parseInt(input.getOrDefault("page", "0")); size = Integer.parseInt(input.getOrDefault("size", "20")); }
        catch (NumberFormatException invalid) { throw new NotesFailure("invalid_page"); }
        return notes.list(actor(jwt), workspace, new Notes.PageQuery(page, size, input.getOrDefault("sort", "created"), input.getOrDefault("direction", "asc")));
    }
    @PutMapping("/api/workspaces/{workspace}/notes/{id}") Notes.Note update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @PathVariable UUID id, @RequestBody Notes.EditNote input) {
        return notes.update(actor(jwt), workspace, id, input);
    }
    @DeleteMapping("/api/workspaces/{workspace}/notes/{id}") ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @PathVariable UUID id) {
        notes.delete(actor(jwt), workspace, id); return ResponseEntity.noContent().build();
    }
    private static Actor actor(Jwt jwt) { return new Actor(jwt.getClaimAsString("iss"), jwt.getSubject()); }
}
