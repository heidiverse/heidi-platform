-- Per-identity OpenID Federation settings: the keychain that signs the identity's entity
-- statements, its authority hints, and whether it acts as an intermediate or trust anchor.
-- NULL leaves the identity outside any federation.
ALTER TABLE t_issuer
    ADD COLUMN federation JSONB;
