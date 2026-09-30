CREATE TABLE t_signing_key
(
    key_id                 TEXT PRIMARY KEY,
    algorithm              TEXT NOT NULL,
    encrypted_private_key  TEXT NOT NULL,
    public_key             TEXT NOT NULL,
    encryption_salt        TEXT NOT NULL UNIQUE,
    deletable               BOOLEAN NOT NULL DEFAULT TRUE
);
