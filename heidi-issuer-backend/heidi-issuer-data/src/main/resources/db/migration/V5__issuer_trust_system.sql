ALTER TABLE issuance_session
    ADD COLUMN trust_system VARCHAR(32) NOT NULL DEFAULT 'Default';
