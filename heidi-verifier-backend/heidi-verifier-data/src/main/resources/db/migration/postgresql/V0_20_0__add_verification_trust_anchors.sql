ALTER TABLE t_verification_request
    ADD COLUMN eudi_trust_anchors JSONB,
    ADD COLUMN swiss_trust_anchor TEXT,
    ADD COLUMN swiss_trust_registry_base_url TEXT;
