create table if not exists auth_flow (
    uuid uuid primary key,
    credential_identifier varchar(255) not null,
    credential_version varchar(255) not null,
    presentation_definition text,
    credential_mapping text,
    display_name varchar(255)
);

create table if not exists issuance_session (
    id uuid primary key,
    connection_id varchar(255) not null unique,
    issuer_slug varchar(255) not null,
    variant varchar(16) not null,
    credential_identifier varchar(255) not null,
    credential_version varchar(255) not null,
    process_token text,
    auth_flow_id uuid,
    auth_url text,
    issuer_state varchar(255) unique,
    pre_authorized_code varchar(255) unique,
    authorization_code varchar(255) unique,
    access_token varchar(255) unique,
    redirect_uri text,
    client_state varchar(255),
    code_challenge varchar(255),
    issuance_profile_id text not null,
    status varchar(64) not null,
    created_at timestamptz not null
);
