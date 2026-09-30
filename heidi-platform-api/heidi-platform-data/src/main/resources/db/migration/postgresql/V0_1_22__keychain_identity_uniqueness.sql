-- A logical key is one identity-owned object. Its old purpose discriminator allowed two
-- keychains with the same logical id to be reached through one identity.
DO $$
BEGIN
    IF EXISTS (
        SELECT tenant_id, logical_key_id
        FROM t_signing_keychain
        GROUP BY tenant_id, logical_key_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot remove keychain purpose from uniqueness: duplicate tenant/logical_key_id rows exist';
    END IF;
END
$$;

ALTER TABLE t_signing_keychain
    DROP CONSTRAINT uq_signing_keychain_tenant_key;

DROP INDEX uq_signing_keychain_global_key;

ALTER TABLE t_signing_keychain
    ADD CONSTRAINT uq_signing_keychain_tenant_key UNIQUE (tenant_id, logical_key_id);

CREATE UNIQUE INDEX uq_signing_keychain_global_key
    ON t_signing_keychain (logical_key_id)
    WHERE tenant_id IS NULL;
