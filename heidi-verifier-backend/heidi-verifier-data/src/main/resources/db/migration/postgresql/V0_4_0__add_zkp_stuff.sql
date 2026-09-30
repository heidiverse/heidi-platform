alter table t_verification_request
    add column verifying_key text null;
alter table t_verification_request
    add column proving_key text null;
