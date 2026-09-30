CREATE TABLE t_issuing_subca (
    pk_subca_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    key_id UUID NOT NULL REFERENCES t_signing_key(pk_key_id),
    key_version_id UUID NOT NULL REFERENCES t_signing_key_version(pk_key_version_id),
    subject_dn TEXT NOT NULL,
    certificate_chain JSONB NOT NULL DEFAULT '[]'::jsonb,
    certificate_history JSONB NOT NULL DEFAULT '[]'::jsonb,
    certificate_source TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_issuing_subca_key_version UNIQUE (key_version_id)
);
CREATE INDEX ix_issuing_subca_tenant ON t_issuing_subca(tenant_id);
