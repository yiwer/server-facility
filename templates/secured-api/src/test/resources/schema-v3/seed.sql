-- Independently authored checkpoint data. Digests are literal Python/PostgreSQL protocol goldens.
insert into workspace(id, name, command_count)
values ('00000000-0000-0000-0000-000000000030', 'V3 checkpoint', 1);
insert into workspace_member(workspace_id, issuer, subject)
values ('00000000-0000-0000-0000-000000000030', 'https://issuer.example', '用户🌱');
insert into note(id, workspace_id, slug, title, body)
values ('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-000000000030',
        'golden', '独立金样 🌱', E'line one\nline two');
insert into note_command(workspace_id, actor_hash, issuer, subject, operation, command_key, fingerprint,
                         receipt_expires_at, note_id, slug, title, body)
values ('00000000-0000-0000-0000-000000000030',
        decode('c4cdeb9118548b7367f5129bd2630cfdd8ac593aa93168b60133dfb232afd808', 'hex'),
        'https://issuer.example', '用户🌱', 'create', 'frozen-v3',
        decode('5dd3aabf543d4bc3491abb3a069813bd59cb084fed09c73f057c2b3f53a73918', 'hex'),
        clock_timestamp() + interval '24 hours', '00000000-0000-0000-0000-000000000031',
        'golden', '独立金样 🌱', E'line one\nline two');
