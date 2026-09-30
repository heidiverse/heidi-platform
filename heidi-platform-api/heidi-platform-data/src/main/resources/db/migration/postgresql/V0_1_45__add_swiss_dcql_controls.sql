ALTER TABLE t_proof_scheme
    ADD COLUMN IF NOT EXISTS swiss_verification_query_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS always_include_dcql_query BOOLEAN NOT NULL DEFAULT FALSE;
