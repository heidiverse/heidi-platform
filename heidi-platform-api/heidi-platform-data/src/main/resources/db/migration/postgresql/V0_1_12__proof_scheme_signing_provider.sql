ALTER TABLE t_proof_scheme
    ADD COLUMN fk_proof_signing_provider_id INTEGER
        REFERENCES t_signing_provider (pk_signing_provider_id);

COMMENT ON COLUMN t_proof_scheme.fk_proof_signing_provider_id IS
    'Provider used for keyless proof-scheme setup operations';
