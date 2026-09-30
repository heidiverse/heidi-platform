-- Credential-request decryption may be global or trust-framework scoped and may have
-- multiple active keys for rotation or fallback. The service publishes matching slots.
DROP INDEX IF EXISTS uq_identity_key_slot_identity_role;

CREATE UNIQUE INDEX uq_identity_key_slot_identity_role
    ON t_identity_key_slot (
        fk_identity_id,
        slot_type,
        COALESCE(trust_system, ''),
        COALESCE(operation, ''))
    WHERE slot_type <> 'CREDENTIAL_SIGNING'
      AND slot_type <> 'DECRYPTION';
