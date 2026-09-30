ALTER TABLE issuance_session
    ADD COLUMN signing_snapshot TEXT,
    ADD COLUMN offer_expires_at TIMESTAMPTZ,
    ADD COLUMN signing_expires_at TIMESTAMPTZ,
    ADD COLUMN signing_flow_released BOOLEAN NOT NULL DEFAULT FALSE;
