ALTER TABLE t_issuer_signing_binding
    DROP CONSTRAINT uq_issuer_trust_system,
    ADD COLUMN operation TEXT,
    ADD CONSTRAINT ck_issuer_signing_binding_operation
        CHECK (operation IS NULL OR BTRIM(operation) <> '');

-- Existing BBS rows were created before operation bindings existed.
UPDATE t_issuer_signing_binding binding
SET operation = 'w3c.bbs-data-integrity-credential-issuance'
FROM t_issuer_signing_definition definition
WHERE binding.fk_signing_definition_id = definition.pk_signing_definition_id
  AND definition.algorithm = 'BBS'
  AND binding.operation IS NULL;

CREATE UNIQUE INDEX uq_issuer_jose_signing_binding
    ON t_issuer_signing_binding (fk_issuer_id, trust_system)
    WHERE operation IS NULL;

CREATE UNIQUE INDEX uq_issuer_operation_signing_binding
    ON t_issuer_signing_binding (fk_issuer_id, trust_system, operation)
    WHERE operation IS NOT NULL;
