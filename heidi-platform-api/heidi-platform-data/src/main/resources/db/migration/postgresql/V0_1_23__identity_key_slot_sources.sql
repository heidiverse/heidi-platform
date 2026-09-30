ALTER TABLE t_identity_key_slot
    ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY';

ALTER TABLE t_identity_key_slot
    ADD CONSTRAINT ck_identity_key_slot_source
    CHECK (source IN ('EXPLICIT', 'LEGACY'));
