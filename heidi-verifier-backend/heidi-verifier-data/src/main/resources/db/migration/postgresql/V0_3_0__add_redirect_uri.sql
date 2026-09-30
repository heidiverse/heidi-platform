alter table t_verification_request
    add column redirect_uri varchar(255) null;

alter table t_verification_request drop constraint t_verification_request_transaction_id_key;
