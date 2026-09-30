alter table issuance_session
    add column if not exists encrypted_session text,
    add column if not exists encrypted_with_session_key boolean not null default false;
