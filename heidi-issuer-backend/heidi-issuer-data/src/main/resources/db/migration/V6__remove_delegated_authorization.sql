ALTER TABLE issuance_session
    DROP COLUMN IF EXISTS auth_flow_id,
    DROP COLUMN IF EXISTS auth_url,
    DROP COLUMN IF EXISTS issuer_state,
    DROP COLUMN IF EXISTS authorization_code,
    DROP COLUMN IF EXISTS redirect_uri,
    DROP COLUMN IF EXISTS client_state,
    DROP COLUMN IF EXISTS code_challenge;

DROP TABLE IF EXISTS auth_flow;
