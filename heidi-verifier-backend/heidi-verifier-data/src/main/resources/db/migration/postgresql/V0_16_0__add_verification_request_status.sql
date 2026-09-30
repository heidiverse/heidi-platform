alter table t_verification_request
    add column status varchar(32) not null default 'NOT_STARTED';
