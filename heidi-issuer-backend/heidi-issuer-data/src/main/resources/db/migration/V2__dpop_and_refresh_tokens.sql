alter table issuance_session
    add column if not exists access_token_expires_at timestamptz,
    add column if not exists refresh_token varchar(255),
    add column if not exists refresh_token_expires_at timestamptz,
    add column if not exists dpop_jkt varchar(255);

create unique index if not exists issuance_session_refresh_token_idx
    on issuance_session (refresh_token)
    where refresh_token is not null;
