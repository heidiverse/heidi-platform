-- Operation configuration is owned by the identity operation slot. Legacy rows were already
-- cleared by V0_1_23, so an orphan cannot be carried into the slot model.
ALTER TABLE t_issuer_operation_configuration
    ADD COLUMN fk_identity_key_slot_id UUID;

UPDATE t_issuer_operation_configuration configuration
SET fk_identity_key_slot_id = slot.pk_identity_key_slot_id
FROM t_issuer_signing_binding binding
JOIN t_identity_key_slot slot
  ON slot.fk_identity_id = binding.fk_issuer_id
 AND slot.slot_type = 'OPERATION'
 AND slot.trust_system = binding.trust_system
WHERE configuration.fk_issuer_signing_binding_id = binding.pk_issuer_signing_binding_id
  AND slot.operation = configuration.operation;

DELETE FROM t_issuer_operation_configuration
WHERE fk_identity_key_slot_id IS NULL;

ALTER TABLE t_issuer_operation_configuration
    DROP CONSTRAINT uk_issuer_operation_configuration,
    DROP COLUMN fk_issuer_signing_binding_id,
    ALTER COLUMN fk_identity_key_slot_id SET NOT NULL,
    ADD CONSTRAINT fk_operation_configuration_slot
        FOREIGN KEY (fk_identity_key_slot_id)
        REFERENCES t_identity_key_slot (pk_identity_key_slot_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT uk_issuer_operation_configuration
        UNIQUE (fk_identity_key_slot_id, operation);
