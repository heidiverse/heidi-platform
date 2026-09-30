-- A logical key is neutral; its business role is carried by identity slots.
ALTER TABLE t_signing_keychain RENAME TO t_signing_key;

ALTER INDEX uq_signing_keychain_tenant_key RENAME TO uq_signing_key_tenant_key;
ALTER INDEX uq_signing_keychain_global_key RENAME TO uq_signing_key_global_key;

ALTER TABLE t_signing_key DROP COLUMN purpose;
