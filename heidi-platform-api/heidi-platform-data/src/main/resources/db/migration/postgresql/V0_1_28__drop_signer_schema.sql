-- The identity-slot model is the only runtime path. No deployed data needs a compatibility window.
DROP TABLE IF EXISTS t_issuer_signer_key_certificate;
DROP TABLE IF EXISTS t_issuer_signer_key;
DROP TABLE IF EXISTS t_issuer_signing_certificate;
DROP TABLE IF EXISTS t_issuer_signing_binding;
DROP TABLE IF EXISTS t_issuer_signing_definition;

ALTER TABLE t_issuer
    DROP COLUMN IF EXISTS trust_signer_key_id;
