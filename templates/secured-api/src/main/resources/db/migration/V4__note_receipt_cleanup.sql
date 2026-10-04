-- Keep command identities and their quota charge; only the bounded result representation expires.
create index note_command_receipt_expiry on note_command(receipt_expires_at) where note_id is not null;

-- Bind the effective Flyway application schema once, including deployments outside public.
do $migration$
declare
    application_schema name := current_schema();
    function_body text;
begin
    if application_schema is null then raise exception 'Application migration schema is required'; end if;
    function_body := format($definition$
        declare changed integer;
        begin
            if cutoff is null or not isfinite(cutoff) or cutoff > clock_timestamp()
                    or batch_size is null or batch_size < 1 or batch_size > 1000 then
                raise exception using errcode = '22023', message = 'Invalid note receipt cleanup parameters';
            end if;
            with expired as materialized (
                select workspace_id, actor_hash, operation, command_key
                from %1$I.note_command
                where note_id is not null and receipt_expires_at <= cutoff
                order by receipt_expires_at
                limit batch_size for update skip locked
            )
            update %1$I.note_command as command
            set note_id = null, slug = null, title = null, body = null
            from expired
            where command.workspace_id = expired.workspace_id and command.actor_hash = expired.actor_hash
                and command.operation = expired.operation and command.command_key = expired.command_key;
            get diagnostics changed = row_count;
            return changed;
        end
    $definition$, application_schema);
    -- Quote the completed body as a string literal: even legal schema names can contain dollar tags.
    execute format('create function %I.cleanup_note_receipts(cutoff timestamp with time zone, batch_size integer)
        returns integer language plpgsql volatile parallel unsafe security invoker
        set search_path = pg_catalog, pg_temp as %L', application_schema, function_body);
    execute format('revoke all on function %I.cleanup_note_receipts(timestamp with time zone, integer) from public', application_schema);
end
$migration$;
