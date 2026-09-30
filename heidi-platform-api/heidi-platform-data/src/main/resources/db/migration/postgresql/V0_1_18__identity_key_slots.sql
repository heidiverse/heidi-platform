-- Identity-owned key slots. Legacy signer rows remain during the compatibility window; the
-- inserts below make the new model immediately usable and make every pre-existing reference
-- visible to the startup integrity report.
CREATE TABLE t_signing_certificate
(
    pk_certificate_id UUID PRIMARY KEY,
    key_version_id    UUID NOT NULL REFERENCES t_signing_key_version (pk_key_version_id) ON DELETE CASCADE,
    profile           TEXT NOT NULL,
    source            TEXT NOT NULL,
    trust_system      TEXT,
    chain_index       INTEGER NOT NULL CHECK (chain_index >= 0),
    certificate       TEXT NOT NULL,
    not_before        TIMESTAMPTZ,
    not_after         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_signing_certificate_without_trust
    ON t_signing_certificate (key_version_id, profile, chain_index)
    WHERE trust_system IS NULL;

CREATE UNIQUE INDEX uq_signing_certificate_with_trust
    ON t_signing_certificate (key_version_id, profile, trust_system, chain_index)
    WHERE trust_system IS NOT NULL;

-- Preserve the legacy effective identity before projecting proof operation slots. The explicit
-- identity column was added before this migration, but older rows may still rely on credential 0.
UPDATE t_proof_scheme proof
SET fk_verifier_identity_id = credential.fk_issuer_id
FROM t_proof_scheme_credential_scheme relation
JOIN t_credential_scheme credential
  ON credential.pk_credential_scheme_id = relation.fk_credential_scheme_id
WHERE proof.pk_proof_scheme_id = relation.fk_proof_scheme_id
  AND relation.credential_position = 0
  AND proof.fk_verifier_identity_id IS NULL;

CREATE TABLE t_identity_key_slot
(
    pk_identity_key_slot_id UUID PRIMARY KEY,
    fk_identity_id          INTEGER NOT NULL REFERENCES t_issuer (pk_issuer_id) ON DELETE CASCADE,
    slot_type               TEXT NOT NULL,
    trust_system            TEXT,
    operation               TEXT,
    consumer                TEXT,
    keychain_id             UUID REFERENCES t_signing_keychain (pk_keychain_id),
    provider_id             INTEGER REFERENCES t_signing_provider (pk_signing_provider_id),
    certificate_id          UUID REFERENCES t_signing_certificate (pk_certificate_id),
    slot_order              INTEGER NOT NULL DEFAULT 0 CHECK (slot_order >= 0),
    configuration           JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_identity_key_slot_target CHECK (
        (slot_type = 'OPERATION' AND provider_id IS NOT NULL)
        OR (slot_type <> 'OPERATION' AND keychain_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_identity_key_slot_identity_role
    ON t_identity_key_slot (
        fk_identity_id,
        slot_type,
        COALESCE(trust_system, ''),
        COALESCE(operation, ''))
    WHERE slot_type <> 'CREDENTIAL_SIGNING';

CREATE UNIQUE INDEX uq_identity_key_slot_credential_key
    ON t_identity_key_slot (fk_identity_id, slot_type, COALESCE(trust_system, ''), keychain_id)
    WHERE slot_type = 'CREDENTIAL_SIGNING';

-- Every existing provider version gets a neutral typed certificate record. The old ordered
-- collection remains readable until all binding-specific chains have been classified.
INSERT INTO t_signing_certificate (
    pk_certificate_id, key_version_id, profile, source, chain_index, certificate)
SELECT gen_random_uuid(), certificate.key_version_id, 'ATTESTATION', 'IMPORTED',
       certificate.chain_index, certificate.certificate
FROM t_signing_key_version_certificate certificate
ON CONFLICT DO NOTHING;

-- Preserve EUDI binding-specific chains as trust-list certificates instead of losing the
-- distinction when the duplicated JSON column is retired.
INSERT INTO t_signing_certificate (
    pk_certificate_id, key_version_id, profile, source, trust_system, chain_index, certificate)
SELECT gen_random_uuid(), definition.key_version_id, 'TRUST_LIST', 'IMPORTED', 'EUDI',
       certificate.chain_index, certificate.certificate
FROM t_issuer_signing_certificate certificate
JOIN t_issuer_signing_definition definition
  ON definition.pk_signing_definition_id = certificate.fk_signing_definition_id
JOIN t_issuer_signing_binding binding
  ON binding.fk_signing_definition_id = definition.pk_signing_definition_id
WHERE binding.trust_system = 'EUDI'
  AND binding.operation IS NULL
  AND definition.key_version_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO t_signing_certificate (
    pk_certificate_id, key_version_id, profile, source, trust_system, chain_index, certificate)
SELECT gen_random_uuid(), signer_key.key_version_id, 'TRUST_LIST', 'IMPORTED', 'EUDI',
       certificate.chain_index, certificate.certificate
FROM t_issuer_signer_key_certificate certificate
JOIN t_issuer_signer_key signer_key
  ON signer_key.pk_signer_key_id = certificate.fk_signer_key_id
JOIN t_issuer_signing_binding binding
  ON binding.fk_signing_definition_id = signer_key.fk_signing_definition_id
WHERE binding.trust_system = 'EUDI'
  AND binding.operation IS NULL
  AND signer_key.key_version_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Credential-signing slots contain the primary and additional keys of every ordinary binding.
INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, trust_system, keychain_id, slot_order)
SELECT gen_random_uuid(), binding.fk_issuer_id, 'CREDENTIAL_SIGNING', binding.trust_system,
       definition.keychain_id, 0
FROM t_issuer_signing_binding binding
JOIN t_issuer_signing_definition definition
  ON definition.pk_signing_definition_id = binding.fk_signing_definition_id
WHERE binding.operation IS NULL AND definition.keychain_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, trust_system, keychain_id, slot_order)
SELECT gen_random_uuid(), binding.fk_issuer_id, 'CREDENTIAL_SIGNING', binding.trust_system,
       signer_key.keychain_id,
       ROW_NUMBER() OVER (
           PARTITION BY binding.fk_issuer_id, binding.trust_system, binding.fk_signing_definition_id
           ORDER BY signer_key.pk_signer_key_id)
FROM t_issuer_signing_binding binding
JOIN t_issuer_signer_key signer_key
  ON signer_key.fk_signing_definition_id = binding.fk_signing_definition_id
WHERE binding.operation IS NULL AND signer_key.keychain_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- A trust slot is materialised per trust system. An explicit identity key wins; otherwise the
-- primary key of that binding is the legacy effective value.
WITH candidates AS (
    SELECT binding.fk_issuer_id AS identity_id,
           binding.trust_system,
           definition.keychain_id AS primary_keychain_id,
           definition.key_id AS primary_key_id,
           issuer.trust_signer_key_id,
           binding.fk_signing_definition_id
    FROM t_issuer_signing_binding binding
    JOIN t_issuer issuer ON issuer.pk_issuer_id = binding.fk_issuer_id
    JOIN t_issuer_signing_definition definition
      ON definition.pk_signing_definition_id = binding.fk_signing_definition_id
    WHERE binding.operation IS NULL
), selected AS (
    SELECT identity_id, trust_system, primary_keychain_id AS keychain_id
    FROM candidates
    WHERE primary_keychain_id IS NOT NULL
      AND (trust_signer_key_id IS NULL OR trust_signer_key_id = primary_key_id)
    UNION ALL
    SELECT candidates.identity_id, candidates.trust_system, signer_key.keychain_id
    FROM candidates
    JOIN t_issuer_signer_key signer_key
      ON signer_key.fk_signing_definition_id = candidates.fk_signing_definition_id
     AND signer_key.key_id = candidates.trust_signer_key_id
    WHERE signer_key.keychain_id IS NOT NULL
)
INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, trust_system, keychain_id, slot_order)
SELECT gen_random_uuid(), identity_id, 'IDENTITY_STATEMENT', trust_system, keychain_id, 0
FROM selected
ON CONFLICT DO NOTHING;

-- Existing operation bindings become explicit operation slots. A keyless provider can be filled
-- later; it is reported until the provider-only slot is saved.
INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, trust_system, operation,
    consumer, keychain_id, provider_id, slot_order)
SELECT gen_random_uuid(), binding.fk_issuer_id, 'OPERATION', binding.trust_system,
       binding.operation,
       CASE WHEN binding.operation = 'w3c.bbs-data-integrity-presentation-setup'
            THEN 'VERIFIER' ELSE 'ISSUER' END,
       definition.keychain_id, definition.provider_id, 0
FROM t_issuer_signing_binding binding
JOIN t_issuer_signing_definition definition
  ON definition.pk_signing_definition_id = binding.fk_signing_definition_id
WHERE binding.operation IS NOT NULL AND definition.provider_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Federation and status-list signatures are produced by the platform API, so their slots grant
-- signing to the platform client rather than to the issuer backend.
INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, keychain_id, slot_order)
SELECT gen_random_uuid(), issuer.pk_issuer_id, 'FEDERATION',
       (issuer.federation ->> 'signingKeychainId')::uuid, 0
FROM t_issuer issuer
WHERE CASE
          WHEN issuer.federation ->> 'enabled' IN ('true', 'false')
              THEN (issuer.federation ->> 'enabled')::boolean
          ELSE false
      END
  AND issuer.federation ->> 'signingKeychainId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
ON CONFLICT DO NOTHING;

INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, keychain_id, slot_order)
SELECT gen_random_uuid(), scheme.fk_issuer_id, 'STATUS_LIST', status.signing_keychain_id, 0
FROM t_credential_scheme scheme
JOIN t_status_list status ON status.pk_status_list_id = scheme.fk_status_list_id
WHERE scheme.fk_status_list_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Keyless BBS presentation setup runs for the proof identity and therefore needs an operation
-- slot even though it has no keychain.
INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, trust_system, operation,
    consumer, provider_id, slot_order)
SELECT gen_random_uuid(), proof.fk_verifier_identity_id, 'OPERATION', 'Default',
       'w3c.bbs-data-integrity-presentation-setup', 'VERIFIER',
       proof.fk_proof_signing_provider_id, 0
FROM t_proof_scheme proof
WHERE proof.fk_verifier_identity_id IS NOT NULL
  AND proof.fk_proof_signing_provider_id IS NOT NULL
ON CONFLICT DO NOTHING;
