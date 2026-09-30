-- Preserve the legacy effective identity before proof schemas become explicit-only.
UPDATE t_proof_scheme proof
SET fk_verifier_identity_id = credential.fk_issuer_id
FROM t_proof_scheme_credential_scheme relation
JOIN t_credential_scheme credential
  ON credential.pk_credential_scheme_id = relation.fk_credential_scheme_id
WHERE proof.pk_proof_scheme_id = relation.fk_proof_scheme_id
  AND relation.credential_position = 0
  AND proof.fk_verifier_identity_id IS NULL;
