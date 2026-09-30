-- Public verification material remains bound to the identity after slots change.
CREATE TABLE t_identity_key_publication (
    identity_id INTEGER NOT NULL REFERENCES t_issuer(pk_issuer_id),
    slot_type TEXT NOT NULL,
    trust_system TEXT NOT NULL,
    key_version_id UUID NOT NULL REFERENCES t_signing_key_version(pk_key_version_id),
    PRIMARY KEY (identity_id, slot_type, trust_system, key_version_id)
);
CREATE INDEX idx_publication_version ON t_identity_key_publication(key_version_id);

INSERT INTO t_identity_key_publication(identity_id, slot_type, trust_system, key_version_id)
SELECT s.fk_identity_id, s.slot_type, COALESCE(s.trust_system, 'Default'), v.pk_key_version_id
FROM t_identity_key_slot s JOIN t_signing_key_version v ON v.keychain_id = s.keychain_id
WHERE s.slot_type <> 'DECRYPTION'
    AND (v.status IN ('ACTIVE', 'PREVIOUS') OR (v.status = 'REVOKED' AND v.previous_until IS NOT NULL))
ON CONFLICT DO NOTHING;
