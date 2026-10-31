ALTER TABLE t_credential_scheme
    ADD COLUMN credential_expiry_mode TEXT NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN credential_lifetime_days BIGINT;

ALTER TABLE t_credential_scheme
    ADD CONSTRAINT credential_expiry_mode_check
        CHECK (credential_expiry_mode IN ('DEFAULT', 'CUSTOM', 'NONE')),
    ADD CONSTRAINT credential_lifetime_days_check
        CHECK (credential_lifetime_days IS NULL OR credential_lifetime_days > 0),
    ADD CONSTRAINT credential_expiry_days_mode_check
        CHECK ((credential_expiry_mode = 'CUSTOM') = (credential_lifetime_days IS NOT NULL));
