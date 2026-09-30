CREATE TABLE t_status_list
(
    pk_status_list_id   UUID PRIMARY KEY,
    tenant_id           TEXT        NOT NULL,
    name                TEXT        NOT NULL,
    type                TEXT        NOT NULL,
    bits                INTEGER     NOT NULL,
    entry_count         INTEGER     NOT NULL,
    publish_mode        TEXT        NOT NULL,
    endpoint            TEXT,
    signing_keychain_id UUID        NOT NULL,
    ttl                 BIGINT,
    status_data         BYTEA       NOT NULL,
    published_token     TEXT,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_status_list_keychain
        FOREIGN KEY (signing_keychain_id) REFERENCES t_signing_keychain (pk_keychain_id),
    CONSTRAINT uq_status_list_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_status_list_type CHECK (type IN ('SD_JWT_VC')),
    CONSTRAINT chk_status_list_bits CHECK (bits IN (1, 2, 4, 8)),
    CONSTRAINT chk_status_list_entry_count CHECK (entry_count > 0),
    CONSTRAINT chk_status_list_publish_mode CHECK (publish_mode IN ('LOCAL', 'REMOTE')),
    CONSTRAINT chk_status_list_endpoint CHECK (
        (publish_mode = 'LOCAL' AND endpoint IS NULL)
        OR (publish_mode = 'REMOTE' AND endpoint IS NOT NULL)
    ),
    CONSTRAINT chk_status_list_ttl CHECK (ttl IS NULL OR ttl > 0)
);

ALTER TABLE t_credential_scheme
    ADD COLUMN fk_status_list_id UUID,
    ADD CONSTRAINT fk_credential_scheme_status_list
        FOREIGN KEY (fk_status_list_id) REFERENCES t_status_list (pk_status_list_id);

CREATE INDEX idx_credential_scheme_status_list
    ON t_credential_scheme (fk_status_list_id);
