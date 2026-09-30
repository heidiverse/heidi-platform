-- Private authority outlives rotation while an issued flow still needs the version.
CREATE TABLE t_signing_flow_reference (
    flow_id UUID NOT NULL,
    client TEXT NOT NULL CHECK (client IN ('issuer', 'verifier')),
    key_version_id UUID NOT NULL REFERENCES t_signing_key_version(pk_key_version_id),
    purpose TEXT NOT NULL CHECK (purpose IN ('SIGNING', 'OPERATIONS', 'DECRYPT')),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (flow_id, client, key_version_id, purpose)
);
CREATE INDEX idx_signing_flow_expiry ON t_signing_flow_reference(expires_at);
