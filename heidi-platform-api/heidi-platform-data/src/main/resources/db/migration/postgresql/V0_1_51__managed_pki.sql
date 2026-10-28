-- Preserve existing chains while linking managed authorities and issued leaves.
ALTER TABLE t_issuing_subca ADD COLUMN parent_ca_id uuid REFERENCES t_issuing_subca(pk_subca_id);
ALTER TABLE t_signing_certificate ADD COLUMN issuing_ca_id uuid REFERENCES t_issuing_subca(pk_subca_id);
CREATE INDEX ix_issuing_subca_parent ON t_issuing_subca(parent_ca_id);
CREATE INDEX ix_signing_certificate_ca ON t_signing_certificate(issuing_ca_id);
ALTER TABLE t_issuing_subca ADD COLUMN generated_root_certificate text;
UPDATE t_issuing_subca SET generated_root_certificate = certificate_chain->>0
WHERE certificate_source = 'SELF_SIGNED';
