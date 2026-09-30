-- Keep enabled federation settings linked to the neutral logical key after the DTO rename.
UPDATE t_issuer
SET federation = (federation - 'signingKeychainId')
        || jsonb_build_object('signingKeyId', federation -> 'signingKeychainId')
WHERE federation ? 'signingKeychainId';
