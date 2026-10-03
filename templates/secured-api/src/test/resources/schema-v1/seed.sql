-- Independently authored predecessor data; not produced by the current Notes Module.
insert into workspace(id, name) values ('00000000-0000-0000-0000-000000000028', 'First schema team');
insert into workspace_member(workspace_id, issuer, subject)
values ('00000000-0000-0000-0000-000000000028', 'https://migration.example/issuer', 'historic-member');
insert into note(id, workspace_id, slug, title, body, created_at)
values ('00000000-0000-0000-0000-000000000029', '00000000-0000-0000-0000-000000000028',
        'prior-note', '旧数据 🌱', E'first line\nsecond line', '2025-12-31T23:59:59Z');
