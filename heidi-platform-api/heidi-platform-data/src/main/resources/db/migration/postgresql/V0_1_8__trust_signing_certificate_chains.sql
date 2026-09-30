ALTER TABLE t_issuer_signing_binding
    ADD COLUMN trust_certificate_chains JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Preserve existing EUDI metadata signing until its chain is replaced independently.
WITH key_chains AS (
    SELECT certificate.fk_signing_definition_id,
           definition.key_id,
           jsonb_agg(certificate.certificate ORDER BY certificate.chain_index) AS certificate_chain
    FROM t_issuer_signing_certificate certificate
    JOIN t_issuer_signing_definition definition
      ON definition.pk_signing_definition_id = certificate.fk_signing_definition_id
    GROUP BY certificate.fk_signing_definition_id, definition.key_id

    UNION ALL

    SELECT signer_key.fk_signing_definition_id,
           signer_key.key_id,
           jsonb_agg(certificate.certificate ORDER BY certificate.chain_index) AS certificate_chain
    FROM t_issuer_signer_key_certificate certificate
    JOIN t_issuer_signer_key signer_key
      ON signer_key.pk_signer_key_id = certificate.fk_signer_key_id
    GROUP BY signer_key.fk_signing_definition_id, signer_key.key_id
), definition_chains AS (
    SELECT fk_signing_definition_id,
           jsonb_object_agg(key_id, certificate_chain) AS certificate_chains
    FROM key_chains
    GROUP BY fk_signing_definition_id
)
UPDATE t_issuer_signing_binding binding
SET trust_certificate_chains = definition_chains.certificate_chains
FROM definition_chains
WHERE binding.fk_signing_definition_id = definition_chains.fk_signing_definition_id
  AND binding.trust_system = 'EUDI'
  AND binding.operation IS NULL;
