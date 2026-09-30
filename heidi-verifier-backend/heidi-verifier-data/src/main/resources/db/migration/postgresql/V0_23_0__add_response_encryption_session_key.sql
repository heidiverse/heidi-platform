-- The private half is envelope-encrypted with the verifier session master key.
ALTER TABLE t_verification_request
    ADD COLUMN response_encryption_key_id VARCHAR(255),
    ADD COLUMN response_encryption_public_jwk TEXT,
    ADD COLUMN response_encryption_private_jwk TEXT;

CREATE INDEX ix_verification_request_response_encryption_key_id
    ON t_verification_request (response_encryption_key_id)
    WHERE response_encryption_key_id IS NOT NULL;
