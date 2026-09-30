-- Legacy signer rows were removed in V0_1_25. Every slot now has identical persistence behavior.
ALTER TABLE t_identity_key_slot
    DROP CONSTRAINT ck_identity_key_slot_source,
    DROP COLUMN source;
