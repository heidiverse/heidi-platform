ALTER TABLE t_signing_auth_registration
    ADD COLUMN client_name TEXT;

CREATE INDEX ix_signing_auth_registration_client
    ON t_signing_auth_registration (client_name);

-- What a client may do with a keychain. Held per keychain, not per key: rotation issues a new key
-- inside the keychain and must not drop a backend's access.
CREATE TABLE t_signing_grant
(
    keychain    TEXT NOT NULL,
    client_name TEXT NOT NULL,
    purposes    TEXT NOT NULL,
    PRIMARY KEY (keychain, client_name)
);
