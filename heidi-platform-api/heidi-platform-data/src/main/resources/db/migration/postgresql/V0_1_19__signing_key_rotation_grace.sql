-- A previous version remains authorized only for this wallet/request cache window.
ALTER TABLE t_signing_keychain
    ADD COLUMN rotation_grace_period_seconds BIGINT NOT NULL DEFAULT 86400,
    ADD CONSTRAINT chk_signing_keychain_rotation_grace
        CHECK (rotation_grace_period_seconds >= 0);

ALTER TABLE t_signing_key_version
    ADD COLUMN previous_until TIMESTAMPTZ;

-- Legacy previous versions had no expiry. Give them one bounded migration window so a stale
-- version cannot remain authorized indefinitely.
UPDATE t_signing_key_version
SET previous_until = CURRENT_TIMESTAMP + INTERVAL '1 day'
WHERE status = 'PREVIOUS' AND previous_until IS NULL;
