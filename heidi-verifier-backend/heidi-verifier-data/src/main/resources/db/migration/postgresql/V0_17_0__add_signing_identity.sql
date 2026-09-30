ALTER TABLE t_verification_request
    ADD COLUMN signing_identity TEXT,
    ADD COLUMN signing_trust_system TEXT,
    ADD COLUMN client_id_scheme TEXT;
