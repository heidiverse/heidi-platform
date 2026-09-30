UPDATE t_signing_certificate
SET profile = 'CREDENTIAL_SIGNING'
WHERE profile = 'ATTESTATION';

UPDATE t_signing_certificate
SET profile = 'ACCESS'
WHERE profile = 'TRUST_LIST';
