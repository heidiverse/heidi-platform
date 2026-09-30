alter table issuance_session
    add column if not exists refresh_token_usage_count bigint not null default 0;
