-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

-- Fresh databases already contain these columns in the squashed baseline. Existing
-- installations need this forward migration before Hibernate validates the schema.
ALTER TABLE t_credential_scheme
    ADD COLUMN IF NOT EXISTS issuance_profile_id TEXT;

ALTER TABLE t_proof_scheme
    ADD COLUMN IF NOT EXISTS presentation_profile_id TEXT;

-- Backfill only legacy rows. An existing explicit profile is authoritative.
WITH resolved AS (
    SELECT credential.pk_credential_scheme_id,
           CASE
               WHEN BTRIM(credential.default_trust_system) IN ('EUDI', 'Switzerland', 'Default')
                   THEN BTRIM(credential.default_trust_system)
               WHEN BTRIM(issuer.default_trust_system) IN ('EUDI', 'Switzerland', 'Default')
                   THEN BTRIM(issuer.default_trust_system)
               ELSE NULL
           END AS trust_system
    FROM t_credential_scheme credential
    JOIN t_issuer issuer ON issuer.pk_issuer_id = credential.fk_issuer_id
    WHERE credential.issuance_profile_id IS NULL
)
UPDATE t_credential_scheme credential
SET issuance_profile_id = CASE resolved.trust_system
    WHEN 'EUDI' THEN 'EUDI_ISSUANCE_2026_1'
    WHEN 'Switzerland' THEN 'SWISS_ISSUANCE_2026_1'
    WHEN 'Default' THEN 'CUSTOM_ISSUANCE_2026_1'
END
FROM resolved
WHERE resolved.pk_credential_scheme_id = credential.pk_credential_scheme_id;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM t_credential_scheme credential
        WHERE credential.issuance_profile_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot classify credential schemas without an explicit trust system';
    END IF;
END
$migration$;

-- Prefer an explicit legacy client-ID scheme, then the verifier identity. Proofs
-- without either use the one profile shared by all referenced credentials.
WITH credential_profiles AS (
    SELECT credential.pk_credential_scheme_id,
           credential.issuance_profile_id
    FROM t_credential_scheme credential
),
requested_profiles AS (
    SELECT relation.fk_proof_scheme_id,
           CASE
               WHEN COUNT(DISTINCT credential.issuance_profile_id) = 1
                   THEN MIN(credential.issuance_profile_id)
               ELSE NULL
           END AS issuance_profile_id
    FROM t_proof_scheme_credential_scheme relation
    JOIN credential_profiles credential
        ON credential.pk_credential_scheme_id = relation.fk_credential_scheme_id
    GROUP BY relation.fk_proof_scheme_id
),
resolved AS (
    SELECT proof.pk_proof_scheme_id,
           CASE
               WHEN BTRIM(proof.verifier_client_id_scheme) = 'X509_HASH'
                   THEN 'EUDI'
               WHEN BTRIM(proof.verifier_client_id_scheme) = 'DECENTRALIZED_IDENTIFIER'
                   THEN 'Switzerland'
               WHEN BTRIM(proof.verifier_client_id_scheme) = 'X509_SAN_DNS'
                   THEN 'Default'
               WHEN BTRIM(identity.default_trust_system) IN ('EUDI', 'Switzerland', 'Default')
                   THEN BTRIM(identity.default_trust_system)
               WHEN requested.issuance_profile_id = 'EUDI_ISSUANCE_2026_1'
                   THEN 'EUDI'
               WHEN requested.issuance_profile_id = 'SWISS_ISSUANCE_2026_1'
                   THEN 'Switzerland'
               WHEN requested.issuance_profile_id = 'CUSTOM_ISSUANCE_2026_1'
                   THEN 'Default'
               ELSE NULL
           END AS trust_system
    FROM t_proof_scheme proof
    LEFT JOIN t_issuer identity ON identity.pk_issuer_id = proof.fk_verifier_identity_id
    LEFT JOIN requested_profiles requested
        ON requested.fk_proof_scheme_id = proof.pk_proof_scheme_id
    WHERE proof.presentation_profile_id IS NULL
)
UPDATE t_proof_scheme proof
SET presentation_profile_id = CASE resolved.trust_system
    WHEN 'EUDI' THEN 'EUDI_PRESENTATION_2026_1'
    WHEN 'Switzerland' THEN 'SWISS_PRESENTATION_2026_1'
    WHEN 'Default' THEN 'CUSTOM_PRESENTATION_2026_1'
END
FROM resolved
WHERE resolved.pk_proof_scheme_id = proof.pk_proof_scheme_id;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM t_proof_scheme proof
        WHERE proof.presentation_profile_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot classify presentation schemes without an explicit trust system';
    END IF;
END
$migration$;

ALTER TABLE t_credential_scheme
    ALTER COLUMN issuance_profile_id SET NOT NULL;

ALTER TABLE t_proof_scheme
    ALTER COLUMN presentation_profile_id SET NOT NULL;
