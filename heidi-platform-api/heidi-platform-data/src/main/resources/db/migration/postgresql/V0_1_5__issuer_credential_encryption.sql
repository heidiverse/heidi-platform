-- Per-issuer OID4VCI message-encryption policy. NULL keeps the issuer backend's full default set,
-- which is what every issuer advertised before this column existed.
ALTER TABLE t_issuer
    ADD COLUMN credential_encryption JSONB;
