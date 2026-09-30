ALTER TABLE t_signing_key_version
    ADD COLUMN usages TEXT NOT NULL DEFAULT 'SIGN';

-- Preserve the technical operation of legacy decryption keychains. Without this backfill the new
-- default would mark them as signing-only and the first identity-slot integrity scan would reject
-- every imported decryption slot.
UPDATE t_signing_key_version version
SET usages = CASE
    WHEN UPPER(keychain.purpose) = 'DECRYPTION'
         AND UPPER(version.algorithm) LIKE 'RSA%' THEN 'UNWRAP'
    WHEN UPPER(keychain.purpose) = 'DECRYPTION' THEN 'KEY_AGREEMENT'
    ELSE 'SIGN'
END
FROM t_signing_keychain keychain
WHERE keychain.pk_keychain_id = version.keychain_id;
