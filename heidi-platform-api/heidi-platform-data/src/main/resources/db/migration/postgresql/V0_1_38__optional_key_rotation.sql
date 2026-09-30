ALTER TABLE t_signing_key
    ADD COLUMN rotation_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ALTER COLUMN rotation_interval_seconds DROP NOT NULL,
    ALTER COLUMN rotation_interval_seconds DROP DEFAULT,
    DROP CONSTRAINT chk_key_rotation_interval;

UPDATE t_signing_key SET rotation_interval_seconds = NULL;

ALTER TABLE t_signing_key
    ADD CONSTRAINT chk_key_rotation_mode
        CHECK (rotation_mode IN ('MANUAL', 'AUTOMATIC', 'EXTERNAL')),
    ADD CONSTRAINT chk_key_rotation_interval
        CHECK ((rotation_mode = 'AUTOMATIC' AND rotation_interval_seconds > 0)
            OR (rotation_mode <> 'AUTOMATIC' AND rotation_interval_seconds IS NULL));
