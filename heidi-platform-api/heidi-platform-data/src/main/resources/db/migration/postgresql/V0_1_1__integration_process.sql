CREATE TABLE t_integration_process
(
    process_id UUID NOT NULL PRIMARY KEY,
    process_token_hash TEXT NOT NULL UNIQUE,
    client_interaction_token_hash TEXT UNIQUE,
    integration_credential_hash TEXT NOT NULL,
    coordinator_connection_id TEXT,
    action TEXT NOT NULL,
    use_dc_api BOOLEAN NOT NULL DEFAULT FALSE,
    proof_scheme_id TEXT,
    include_vp_token BOOLEAN NOT NULL DEFAULT FALSE,
    tenant_id TEXT,
    tx_code TEXT,
    client_interaction_data JSONB,
    client_display_claims JSONB NOT NULL DEFAULT '[]'::jsonb,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
