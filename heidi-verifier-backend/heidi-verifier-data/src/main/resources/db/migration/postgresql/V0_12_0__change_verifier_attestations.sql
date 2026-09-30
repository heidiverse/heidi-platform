alter table t_verification_request
    drop column verifier_attestations;

alter table t_verification_request
    add column verifier_attestations jsonb