-- Slot backfill is complete. The signer tables remain mapped for old unit-test fixtures, but
-- application data must no longer expose or resolve the removed signer layer.
DELETE FROM t_issuer_signer_key_certificate;
DELETE FROM t_issuer_signer_key;
DELETE FROM t_issuer_signing_certificate;
DELETE FROM t_issuer_signing_binding;
DELETE FROM t_issuer_signing_definition;
