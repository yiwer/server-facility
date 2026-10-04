-- convert_to is STABLE in PostgreSQL18.6: use an honest trigger, not an immutable wrapper/generated expression.
create function note_actor_hash(issuer text, subject text) returns bytea language sql stable strict as $$
    select sha256(decode('000000086163746f722d7631', 'hex')
        || int4send(octet_length(convert_to(issuer, 'UTF8'))) || convert_to(issuer, 'UTF8')
        || int4send(octet_length(convert_to(subject, 'UTF8'))) || convert_to(subject, 'UTF8'))
$$;
alter table workspace_member add column actor_hash bytea;
update workspace_member set actor_hash = note_actor_hash(issuer, subject);
alter table workspace_member alter column actor_hash set not null;
alter table workspace_member add constraint member_identity_bytes check (
    octet_length(convert_to(issuer, 'UTF8')) <= 65536 and octet_length(convert_to(subject, 'UTF8')) <= 65536);
alter table workspace_member drop constraint workspace_member_pkey;
alter table workspace_member add primary key (workspace_id, actor_hash);
create function note_member_identity() returns trigger language plpgsql as $$
begin
    new.actor_hash := note_actor_hash(new.issuer, new.subject);
    return new;
end
$$;
create trigger member_identity before insert or update on workspace_member for each row execute function note_member_identity();

alter table workspace add column command_count integer not null default 0 check (command_count between 0 and 10000);

create table note_command (
    workspace_id uuid not null references workspace(id),
    actor_hash bytea not null check (octet_length(actor_hash) = 32),
    issuer text not null,
    subject text not null,
    operation varchar(32) not null,
    command_key varchar(128) not null check (command_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    fingerprint bytea not null check (octet_length(fingerprint) = 32),
    receipt_expires_at timestamp with time zone,
    note_id uuid,
    slug text,
    title text,
    body text,
    primary key (workspace_id, actor_hash, operation, command_key)
);
