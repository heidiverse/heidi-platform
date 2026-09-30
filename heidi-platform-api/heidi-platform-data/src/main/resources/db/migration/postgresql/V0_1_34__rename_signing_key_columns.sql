-- Complete the key/version/slot vocabulary without rewriting applied migrations.
ALTER TABLE t_signing_key RENAME COLUMN pk_keychain_id TO pk_key_id;
ALTER TABLE t_signing_key_version RENAME COLUMN keychain_id TO key_id;
ALTER TABLE t_identity_key_slot RENAME COLUMN keychain_id TO key_id;
ALTER TABLE t_status_list RENAME COLUMN signing_keychain_id TO signing_key_id;
ALTER TABLE t_tenant RENAME COLUMN verifier_keychain_id TO verifier_key_id;

ALTER TABLE t_signing_key
    RENAME CONSTRAINT chk_signing_keychain_logical_key_id TO chk_signing_key_logical_key_id;
ALTER TABLE t_signing_key
    RENAME CONSTRAINT fk_signing_keychain_active_version TO fk_signing_key_active_version;
ALTER TABLE t_signing_key
    RENAME CONSTRAINT chk_signing_keychain_rotation_grace TO chk_signing_key_rotation_grace;
ALTER TABLE t_status_list
    RENAME CONSTRAINT fk_status_list_keychain TO fk_status_list_key;
