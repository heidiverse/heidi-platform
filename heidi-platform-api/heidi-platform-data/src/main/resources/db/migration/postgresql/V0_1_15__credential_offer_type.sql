ALTER TABLE t_credential_scheme
    ADD COLUMN credential_offer_type TEXT NOT NULL DEFAULT 'VALUE';

ALTER TABLE t_credential_scheme
    ADD CONSTRAINT chk_credential_scheme_offer_type
        CHECK (credential_offer_type IN ('URI', 'VALUE'));
