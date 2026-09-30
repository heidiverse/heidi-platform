-- Thirty-day rotation is automatic only for keys bound exclusively to decryption slots.
ALTER TABLE t_signing_key
    ADD COLUMN rotation_interval_seconds BIGINT NOT NULL DEFAULT 2592000,
    ADD CONSTRAINT chk_key_rotation_interval CHECK (rotation_interval_seconds > 0);
