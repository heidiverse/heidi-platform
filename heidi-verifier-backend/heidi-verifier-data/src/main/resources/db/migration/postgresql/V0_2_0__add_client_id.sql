alter table t_verification_request
    add column client_id varchar(255) not null default 'demo.heidiverse.dev';
