-- One immutable record per chain; renewal appends instead of destroying the old certificate.
ALTER TABLE t_signing_certificate ADD COLUMN certificate_chain JSONB;
WITH chains AS (
    SELECT key_version_id, profile, trust_system,
           (array_agg(pk_certificate_id ORDER BY chain_index))[1] AS leaf,
           jsonb_agg(certificate ORDER BY chain_index) AS chain
    FROM t_signing_certificate GROUP BY key_version_id, profile, trust_system
)
UPDATE t_signing_certificate c SET certificate_chain = chains.chain
FROM chains WHERE c.pk_certificate_id = chains.leaf;

-- A slot always selects the leaf record of its chain.
UPDATE t_identity_key_slot s SET certificate_id = leaf.pk_certificate_id
FROM t_signing_certificate selected, t_signing_certificate leaf
WHERE s.certificate_id = selected.pk_certificate_id
  AND selected.key_version_id = leaf.key_version_id AND selected.profile = leaf.profile
  AND selected.trust_system IS NOT DISTINCT FROM leaf.trust_system
  AND leaf.certificate_chain IS NOT NULL;
DELETE FROM t_signing_certificate WHERE certificate_chain IS NULL;
DROP INDEX uq_signing_certificate_without_trust;
DROP INDEX uq_signing_certificate_with_trust;
ALTER TABLE t_signing_certificate DROP COLUMN chain_index, DROP COLUMN certificate;
ALTER TABLE t_signing_certificate ALTER COLUMN certificate_chain SET NOT NULL;
DROP TABLE t_signing_key_version_certificate;
