create table workspace (
    id uuid primary key,
    name text not null
);
create table workspace_member (
    workspace_id uuid not null references workspace(id),
    issuer text not null,
    subject text not null,
    primary key (workspace_id, issuer, subject)
);
create table note (
    id uuid primary key,
    workspace_id uuid not null references workspace(id),
    slug text not null,
    title text not null,
    body text not null,
    created_at timestamp with time zone not null default current_timestamp,
    unique (workspace_id, slug)
);
