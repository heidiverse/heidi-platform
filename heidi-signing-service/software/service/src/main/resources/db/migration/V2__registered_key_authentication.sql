CREATE TABLE t_signing_auth_registration
(
    registration_id TEXT PRIMARY KEY,
    encrypted_psk  TEXT NOT NULL,
    public_key     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_signing_auth_replay
(
    signature TEXT PRIMARY KEY,
    seen_at   BIGINT NOT NULL
);

CREATE INDEX ix_signing_auth_replay_seen_at
    ON t_signing_auth_replay (seen_at);
