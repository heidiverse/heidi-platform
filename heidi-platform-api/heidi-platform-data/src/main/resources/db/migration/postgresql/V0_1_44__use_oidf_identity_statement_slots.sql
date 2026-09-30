-- OpenID Federation uses the same identity-statement assignment model as the other trust
-- frameworks. Preserve the old dedicated federation assignments while moving them to the OIDF
-- trust context; the federation JSON still carries the legacy key id during compatibility.
INSERT INTO t_identity_key_slot (
    pk_identity_key_slot_id, fk_identity_id, slot_type, trust_system, operation,
    consumer, key_id, provider_id, certificate_id, slot_order, configuration,
    created_at, updated_at)
SELECT gen_random_uuid(), fk_identity_id, 'IDENTITY_STATEMENT', 'OIDF', operation,
       consumer, key_id, provider_id, certificate_id, slot_order, configuration,
       created_at, updated_at
FROM t_identity_key_slot
WHERE slot_type = 'FEDERATION'
ON CONFLICT DO NOTHING;

INSERT INTO t_identity_key_publication (identity_id, slot_type, trust_system, key_version_id)
SELECT identity_id, 'IDENTITY_STATEMENT', 'OIDF', key_version_id
FROM t_identity_key_publication
WHERE slot_type = 'FEDERATION'
ON CONFLICT DO NOTHING;

DELETE FROM t_identity_key_publication WHERE slot_type = 'FEDERATION';
DELETE FROM t_identity_key_slot WHERE slot_type = 'FEDERATION';
