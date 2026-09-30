create sequence t_verification_request_seq start with 1 increment by 50;
create sequence t_verification_submission_seq start with 1 increment by 50;
create table t_verification_request
(
    pk_verification_request_id integer                     not null,
    created_at                 timestamp(6) with time zone not null,
    expires_at                 timestamp(6) with time zone not null,
    nonce                      varchar(255)                not null,
    request_id                 varchar(255)                not null unique,
    transaction_id             varchar(255)                not null unique,
    presentation_profile_id    text                        not null,
    presentation_definition    jsonb                       not null,
    primary key (pk_verification_request_id)
);
create table t_verification_submission
(
    pk_verification_submission_id integer                     not null,
    created_at                    timestamp(6) with time zone not null,
    transaction_id                varchar(255)                not null unique,
    disclosures                   jsonb                       not null,
    primary key (pk_verification_submission_id)
);
