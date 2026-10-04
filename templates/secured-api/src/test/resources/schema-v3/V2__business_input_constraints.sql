-- PostgreSQL char_length counts Unicode characters, matching the Module's code-point limits.
alter table workspace add constraint workspace_name_size check (char_length(name) between 1 and 100);
alter table note add constraint note_slug_format check (slug ~ '^[a-z0-9][a-z0-9-]{0,63}$');
alter table note add constraint note_title_size check (char_length(title) between 1 and 200);
alter table note add constraint note_body_size check (char_length(body) <= 4096);
create index note_workspace_created on note(workspace_id, created_at, id);
