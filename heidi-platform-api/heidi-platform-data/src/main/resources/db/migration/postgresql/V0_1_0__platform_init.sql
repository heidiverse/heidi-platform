-- Squashed pre-release schema for the combined Heidi platform database.
-- Future schema changes must be added as new Flyway migrations.

CREATE TABLE t_metadata
(
    pk_credential_identifier TEXT                     NOT NULL,
    pk_version               TEXT                     NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    published                BOOLEAN                  NOT NULL,
    PRIMARY KEY (pk_credential_identifier, pk_version)
);

CREATE TABLE t_attributes
(
    pk_attribute_id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_identifier TEXT  NOT NULL,
    fk_version               TEXT  NOT NULL,
    attributes               jsonb NOT NULL,
    FOREIGN KEY (fk_credential_identifier, fk_version) REFERENCES t_metadata (pk_credential_identifier, pk_version)
);

CREATE TABLE t_styles
(
    pk_style_id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_identifier TEXT  NOT NULL,
    fk_version               TEXT  NOT NULL,
    style                    jsonb NOT NULL,
    FOREIGN KEY (fk_credential_identifier, fk_version) REFERENCES t_metadata (pk_credential_identifier, pk_version)
);

ALTER TABLE t_styles
    ADD COLUMN oca_bundle jsonb,
    ADD COLUMN oca_bundle_file_name TEXT;

ALTER TABLE t_metadata
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

drop table t_styles;
drop table t_attributes;
drop table t_metadata;

create table t_proof_scheme
(
    pk_proof_scheme_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title              TEXT                     NOT NULL,
    purpose            TEXT                     NOT NULL,
    validation_logic   jsonb,
    archived           BOOLEAN                  NOT NULL DEFAULT FALSE,
    trusted_authorities jsonb                  NOT NULL DEFAULT '[]'::jsonb,
    presentation_profile_id TEXT                NOT NULL,
    verifier_trust_system TEXT,
    validation_mode    varchar(16)             NOT NULL DEFAULT 'DISABLED',
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE
);

create table t_credential_scheme
(
    pk_credential_scheme_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credential_identifier   TEXT                     NOT NULL,
    display_name            TEXT,
    version                 TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE,
    state                   TEXT                     NOT NULL DEFAULT 'CREATED',
    issuance_profile_id    TEXT                     NOT NULL
);

create table t_proof_scheme_credential_scheme
(
    pk_proof_scheme_credential_scheme_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_proof_scheme_id                   INTEGER NOT NULL,
    fk_credential_scheme_id              INTEGER NOT NULL,
    FOREIGN KEY (fk_credential_scheme_id) REFERENCES t_credential_scheme (pk_credential_scheme_id),
    FOREIGN KEY (fk_proof_scheme_id) REFERENCES t_proof_scheme (pk_proof_scheme_id)
);

create table t_credential_scheme_attribute
(
    pk_credential_scheme_attribute_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_scheme_id           INTEGER NOT NULL,
    field_name                        TEXT    NOT NULL,
    field_type                        TEXT    NOT NULL,
    FOREIGN KEY (fk_credential_scheme_id) REFERENCES t_credential_scheme (pk_credential_scheme_id)
);

create table t_credential_scheme_attribute_detail
(
    pk_credential_scheme_attribute_detail_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_scheme_attribute_id        INTEGER NOT NULL,
    language                                 TEXT    NOT NULL DEFAULT 'DE',
    display_name                             TEXT    NOT NULL,
    FOREIGN KEY (fk_credential_scheme_attribute_id) REFERENCES t_credential_scheme_attribute (pk_credential_scheme_attribute_id)
);

create table t_proof_scheme_credential_scheme_requested_attribute
(
    pk_proof_scheme_requested_attribute_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_scheme_attribute_id      INTEGER NOT NULL,
    fk_proof_scheme_credential_scheme_id   INTEGER NOT NULL,
    FOREIGN KEY (fk_credential_scheme_attribute_id) REFERENCES t_credential_scheme_attribute (pk_credential_scheme_attribute_id),
    FOREIGN KEY (fk_proof_scheme_credential_scheme_id) REFERENCES t_proof_scheme_credential_scheme (pk_proof_scheme_credential_scheme_id)
);

CREATE TABLE t_style
(
    pk_style_id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_scheme_id INTEGER NOT NULL,
    style                   jsonb   NOT NULL,
    oca_bundle              jsonb,
    oca_bundle_file_name    TEXT,
    FOREIGN KEY (fk_credential_scheme_id) REFERENCES t_credential_scheme (pk_credential_scheme_id)
);

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

alter table t_proof_scheme
    add column uuid uuid;

update t_proof_scheme
set uuid = gen_random_uuid()
where uuid is null;

alter table t_proof_scheme
    alter column uuid set not null;

-- Issuer Definition
create table t_issuer
(
    pk_issuer_id        INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key_type            text  not null,
    logo                bytea not null,
    slug                text  not null unique,
    eudi_verification_trust_anchors JSONB,
    swiss_verification_trust_anchor TEXT,
    key_storage_type    text,
    user_authentication text
);

create table t_issuer_detail
(
    pk_issuer_detail_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_issuer_id        integer not null,
    language            text    not null,
    display_name        text    not null,
    foreign key (fk_issuer_id) references t_issuer (pk_issuer_id)
);

alter table t_credential_scheme
    add column fk_issuer_id integer not null default 1;

alter table t_credential_scheme
    alter column fk_issuer_id drop default;

alter table t_credential_scheme
    add constraint fk_issuer_id_cs foreign key (fk_issuer_id) references t_issuer (pk_issuer_id);

-- add uuid
alter table t_credential_scheme
    add column uuid uuid;

update t_credential_scheme
set uuid = gen_random_uuid()
where uuid is null;

alter table t_credential_scheme
    alter column uuid set not null;

-- uniqueness for backwards compatibility
alter table t_credential_scheme
    add constraint backwards_compatibility unique (credential_identifier, version);

-- add redirect uri field
alter table t_proof_scheme
    add column redirect_uri text;

create table t_metadata
(
    pk_metadata_id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_scheme_id integer not null,
    credential_definition   text    not null,
    foreign key (fk_credential_scheme_id) references t_credential_scheme (pk_credential_scheme_id)
);

create table t_metadata_attribute
(
    pk_metadata_attribute_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_metadata_id           integer not null,
    attribute_key            text    not null,
    foreign key (fk_metadata_id) references t_metadata (pk_metadata_id)
);

create table t_metadata_attribute_detail
(
    pk_metadata_attribute_detail_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_metadata_attribute_id        integer not null,
    language                        text    not null,
    display_name                    text    not null,
    foreign key (fk_metadata_attribute_id) references t_metadata_attribute (pk_metadata_attribute_id)
);

alter table t_proof_scheme_credential_scheme
    add constraint unique_relational_entity unique (fk_proof_scheme_id, fk_credential_scheme_id);

alter table t_credential_scheme_attribute
    add constraint unique_credential_attribute_field_name unique (fk_credential_scheme_id, field_name);

alter table t_metadata_attribute
    add constraint unique_metadata_attribute_field_name unique (fk_metadata_id, attribute_key);

alter table t_issuer_detail
    add constraint unique_language_issuer_detail unique (fk_issuer_id, language);

alter table t_credential_scheme_attribute_detail
    add constraint unique_language_credential_scheme_attribute unique (fk_credential_scheme_attribute_id, language);

alter table t_metadata_attribute_detail
    add constraint unique_language_metadata_attribute unique (fk_metadata_attribute_id, language);

alter table t_proof_scheme
    alter column validation_logic type text;

alter table t_metadata
    drop column credential_definition;

-- Drop the columns key_storage_type and user_authentication from t_issuer
alter table t_issuer
drop column key_storage_type;

alter table t_issuer
drop column user_authentication;

-- Step 1: Add a temporary column for the text-based logo
alter table t_issuer add column logo_text text;

-- Step 2: Migrate data from bytea to text
-- Assuming the bytea data is in a format convertible to text (e.g., base64-encoded)
update t_issuer
set logo_text = encode(logo, 'base64');

-- Step 3: Drop the original bytea column
alter table t_issuer drop column logo;

-- Step 4: Rename the temporary column to logo
alter table t_issuer rename column logo_text to logo;

-- Step 5: Apply any necessary constraints (if any are needed)
-- For example, if `logo` must not be null:
alter table t_issuer alter column logo set not null;

alter table t_credential_scheme add column key_type text;
update t_credential_scheme cs set key_type = (select key_type from t_issuer where pk_issuer_id = cs.fk_issuer_id);
alter table t_credential_scheme alter column key_type set not null;
alter table t_issuer drop column key_type;

create table t_user
(
    pk_user_id   text primary key,
    display_name text,
    thumbnail    text
);

ALTER TABLE t_credential_scheme
    ADD COLUMN tenant_id VARCHAR(255);

ALTER TABLE t_proof_scheme
    ADD COLUMN tenant_id VARCHAR(255);

CREATE TABLE t_tenant
(
    pk_tenant_id  VARCHAR(255) PRIMARY KEY, -- Primary key for tenant ID
    thumbnail     TEXT,                     -- Base64-encoded thumbnail
    display_name  TEXT,
    default_language TEXT NOT NULL DEFAULT 'en'
);

ALTER TABLE t_issuer
    ADD COLUMN tenant_id VARCHAR(255),
    ADD CONSTRAINT fk_issuer_tenant
        FOREIGN KEY (tenant_id) REFERENCES t_tenant (pk_tenant_id);

CREATE INDEX idx_issuer_tenant_id ON t_issuer (tenant_id);

CREATE TABLE t_tenant_translations
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- Auto-incrementing primary key
    fk_tenant_id  VARCHAR(255) NOT NULL,                            -- Foreign key to t_tenant
    translation   TEXT         NOT NULL,                            -- Translation text
    CONSTRAINT fk_tenant FOREIGN KEY (fk_tenant_id)
        REFERENCES t_tenant (pk_tenant_id)
        ON DELETE CASCADE                                           -- Ensures translations are deleted when a tenant is deleted
);

ALTER TABLE t_tenant
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_user
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_credential_scheme
    ADD COLUMN doctype   VARCHAR(255),
    ADD COLUMN namespace VARCHAR(255),
    ADD COLUMN vct       VARCHAR(255);

ALTER TABLE t_credential_scheme ADD COLUMN template_id UUID;

CREATE TABLE t_library_source
(
    id          UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    library_url TEXT      NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE t_tenant_issuers
(
    fk_tenant_id  VARCHAR(255) NOT NULL,       -- Foreign key to t_tenant
    fk_issuer_id  INTEGER      NOT NULL,       -- Foreign key to t_issuer

    PRIMARY KEY (fk_tenant_id, fk_issuer_id),  -- Composite primary key

    CONSTRAINT fk_tenant FOREIGN KEY (fk_tenant_id)
        REFERENCES t_tenant (pk_tenant_id)
        ON DELETE CASCADE,                     -- Ensures issuers are unlinked when a tenant is deleted

    CONSTRAINT fk_issuer FOREIGN KEY (fk_issuer_id)
        REFERENCES t_issuer (pk_issuer_id)
        ON DELETE CASCADE                      -- Ensures orphaned entries are deleted when an issuer is removed
);

CREATE TABLE t_integration
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name           VARCHAR(255) NOT NULL,
    credential_identifiers TEXT[],
    api_key                VARCHAR(255) NOT NULL UNIQUE,
    tenant_id              VARCHAR(255) NOT NULL
);

ALTER TABLE t_integration
    ALTER COLUMN credential_identifiers TYPE JSONB
        USING to_jsonb(credential_identifiers);

ALTER TABLE t_issuer
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_credential_scheme
    ADD COLUMN supported_credential_types JSON;

-- Set default value for existing records
UPDATE t_credential_scheme
SET supported_credential_types = '[
  "SD_JWT",
  "MSO_MDOC",
  "ZKP_VC"
]'
WHERE supported_credential_types IS NULL;

ALTER TABLE t_credential_scheme_attribute
    ADD COLUMN is_sensitive BOOLEAN NOT NULL DEFAULT FALSE;

-- Create table for AttributeCatalogEntity
CREATE TABLE t_attribute_catalog
(
    pk_attribute_catalog_id    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_display_name       VARCHAR(255) NOT NULL,
    specification_display_name VARCHAR(255) NOT NULL,
    specification_url          VARCHAR(512),
    CONSTRAINT uk_catalog_display_name UNIQUE (catalog_display_name)
);

-- Create table for CatalogAttributeEntity
CREATE TABLE t_catalog_attribute
(
    pk_catalog_attribute_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_attribute_catalog_id INTEGER      NOT NULL,
    attribute_key           VARCHAR(100) NOT NULL,
    attribute_type          VARCHAR(50)  NOT NULL,
    attribute_display_name  JSONB        NOT NULL,
    CONSTRAINT fk_catalog_attribute_catalog FOREIGN KEY (fk_attribute_catalog_id)
        REFERENCES t_attribute_catalog (pk_attribute_catalog_id) ON DELETE CASCADE,
    CONSTRAINT uk_catalog_attribute_key UNIQUE (fk_attribute_catalog_id, attribute_key)
);

CREATE INDEX idx_fk_attribute_catalog_id ON t_catalog_attribute (fk_attribute_catalog_id);

-- Insert sample catalog within a transaction
DO
$$
    DECLARE
        catalog_id INTEGER;
    BEGIN
        -- Insert catalog and get the primary key directly
        INSERT INTO t_attribute_catalog (catalog_display_name, specification_display_name, specification_url)
        VALUES ('Heidi Katalog (JWT claims)', 'RFC7519', 'https://www.iana.org/assignments/jwt/jwt.xhtml#claims')
        ON CONFLICT ON CONSTRAINT uk_catalog_display_name DO NOTHING
        RETURNING pk_attribute_catalog_id INTO catalog_id;

        -- Skip if catalog not inserted (e.g., already exists)
        IF catalog_id IS NULL THEN
            RETURN;
        END IF;

        -- Insert attributes, preventing duplicates
        INSERT INTO t_catalog_attribute (fk_attribute_catalog_id, attribute_key, attribute_type, attribute_display_name)
        VALUES (catalog_id, 'name', 'STRING', '{
          "en": "Full Name",
          "de": "Vollständiger Name"
        }'::jsonb),
               (catalog_id, 'email', 'STRING', '{
                 "en": "Email Address",
                 "de": "E-Mail-Adresse"
               }'::jsonb),
               (catalog_id, 'birthdate', 'DATEOFBIRTH', '{
                 "en": "Birthdate",
                 "de": "Geburtsdatum"
               }'::jsonb),
               (catalog_id, 'place_of_birth', 'STRING', '{
                 "en": "Place of Birth",
                 "de": "Geburtsort"
               }'::jsonb),
               (catalog_id, 'given_name', 'STRING', '{
                 "en": "Given Name",
                 "de": "Vorname"
               }'::jsonb),
               (catalog_id, 'family_name', 'STRING', '{
                 "en": "Family Name",
                 "de": "Nachname"
               }'::jsonb),
               (catalog_id, 'phone_number', 'PHONE', '{
                 "en": "Phone Number",
                 "de": "Telefonnummer"
               }'::jsonb),
               (catalog_id, 'address', 'STRING', '{
                 "en": "Address",
                 "de": "Adresse"
               }'::jsonb),
               (catalog_id, 'gender', 'STRING', '{
                 "en": "Gender",
                 "de": "Geschlecht"
               }'::jsonb),
               (catalog_id, 'profile', 'LINK', '{
                 "en": "Profile URL",
                 "de": "Profil-URL"
               }'::jsonb)
        ON CONFLICT (fk_attribute_catalog_id, attribute_key) DO NOTHING;
    END
$$;

CREATE TABLE t_credential_scheme_format_specific_attribute_name
(
    pk_credential_scheme_format_specific_attribute_name_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_credential_scheme_attribute_id                      INTEGER NOT NULL,
    attribute_name_override                                TEXT    NOT NULL,
    credential_format                                      TEXT    NOT NULL,
    FOREIGN KEY (fk_credential_scheme_attribute_id) REFERENCES t_credential_scheme_attribute (pk_credential_scheme_attribute_id),
    CONSTRAINT unique_credential_format_per_format_specific_attribute_name UNIQUE (fk_credential_scheme_attribute_id, credential_format)
);

ALTER TABLE t_tenant
    ADD COLUMN registrar_rp_id          uuid,
    ADD COLUMN verifier_key_uri         TEXT,
    ADD COLUMN verifier_public_jwk      TEXT,
    ADD COLUMN verifier_certificate_chain TEXT;

-- Provider configuration is tenant-scoped. Connection details are encrypted by the entity
-- service; this table intentionally contains no plaintext endpoint credentials.
CREATE TABLE t_signing_provider
(
    pk_signing_provider_id   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- NULL is the single platform-wide provider scope; non-NULL is a tenant override.
    tenant_id                TEXT REFERENCES t_tenant (pk_tenant_id),
    name                     TEXT NOT NULL,
    encryption_salt          TEXT NOT NULL UNIQUE,
    encrypted_configuration  TEXT NOT NULL,
    default_provider         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_signing_provider_tenant_name UNIQUE (tenant_id, name)
);

CREATE UNIQUE INDEX uq_signing_provider_tenant_default
    ON t_signing_provider (tenant_id)
    WHERE default_provider;

CREATE UNIQUE INDEX uq_signing_provider_global_name
    ON t_signing_provider (name)
    WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX uq_signing_provider_global_default
    ON t_signing_provider ((1))
    WHERE tenant_id IS NULL AND default_provider;

CREATE TABLE t_signing_keychain
(
    pk_keychain_id     UUID PRIMARY KEY,
    tenant_id          TEXT REFERENCES t_tenant (pk_tenant_id) ON DELETE CASCADE,
    logical_key_id     TEXT NOT NULL,
    purpose            TEXT NOT NULL,
    provider_id        INTEGER NOT NULL REFERENCES t_signing_provider (pk_signing_provider_id),
    active_version_id  UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_signing_keychain_logical_key_id
        CHECK (logical_key_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
    CONSTRAINT uq_signing_keychain_tenant_key UNIQUE (tenant_id, purpose, logical_key_id)
);

CREATE UNIQUE INDEX uq_signing_keychain_global_key
    ON t_signing_keychain (purpose, logical_key_id)
    WHERE tenant_id IS NULL;

CREATE TABLE t_signing_key_version
(
    pk_key_version_id UUID PRIMARY KEY,
    keychain_id       UUID NOT NULL REFERENCES t_signing_keychain (pk_keychain_id) ON DELETE CASCADE,
    version           INTEGER NOT NULL CHECK (version > 0),
    provider_key_id   TEXT NOT NULL,
    key_uri           TEXT NOT NULL UNIQUE,
    algorithm         TEXT NOT NULL,
    public_jwk        TEXT NOT NULL,
    status            TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_signing_key_version_number UNIQUE (keychain_id, version)
);

CREATE TABLE t_signing_key_version_certificate
(
    key_version_id UUID NOT NULL REFERENCES t_signing_key_version (pk_key_version_id) ON DELETE CASCADE,
    chain_index    INTEGER NOT NULL,
    certificate    TEXT NOT NULL,
    PRIMARY KEY (key_version_id, chain_index)
);

ALTER TABLE t_signing_keychain
    ADD CONSTRAINT fk_signing_keychain_active_version
    FOREIGN KEY (active_version_id) REFERENCES t_signing_key_version (pk_key_version_id);

ALTER TABLE t_tenant
    ADD COLUMN verifier_keychain_id UUID REFERENCES t_signing_keychain (pk_keychain_id),
    ADD COLUMN verifier_key_version_id UUID REFERENCES t_signing_key_version (pk_key_version_id);

ALTER TABLE t_tenant
    ADD COLUMN verifier_provider_id INTEGER REFERENCES t_signing_provider (pk_signing_provider_id);

ALTER TABLE t_proof_scheme
    ADD COLUMN registration_certificate TEXT;

CREATE TABLE t_trust_registry
(
    pk_trust_registry_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- Auto-incrementing primary key
    trust_registry       CHAR(2) NOT NULL UNIQUE                           -- Trust registry code
);

CREATE TABLE t_tenant_trust_registries
(
    pk_id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,      -- Auto-incrementing primary key
    fk_tenant_id         VARCHAR(255) NOT NULL,                                 -- Foreign key to t_tenant
    fk_trust_registry_id INTEGER      NOT NULL,                                 -- Foreign key to t_trust_registry

    CONSTRAINT fk_tenant FOREIGN KEY (fk_tenant_id)
        REFERENCES t_tenant (pk_tenant_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_trust_registry FOREIGN KEY (fk_trust_registry_id)
        REFERENCES t_trust_registry (pk_trust_registry_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_tenant_registry UNIQUE (fk_tenant_id, fk_trust_registry_id)   -- Prevent duplicate assignments
);

ALTER TABLE t_credential_scheme
    ADD COLUMN iss_claim_override VARCHAR(255);

ALTER TABLE t_credential_scheme
    ADD COLUMN kid_override VARCHAR(255);

ALTER TABLE t_credential_scheme
    ADD COLUMN bbs_credential_type VARCHAR(255);

ALTER TABLE t_integration
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_credential_scheme_attribute
    ADD COLUMN is_disclosable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE t_credential_scheme_attribute
    ADD COLUMN is_array BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_integration
    ADD scopes TEXT[];

-- Convert scopes column to JSONB
ALTER TABLE t_integration
    ALTER COLUMN scopes TYPE JSONB
        USING to_jsonb(scopes);

alter table t_style add column typst_template text default null;

ALTER TABLE t_tenant ADD COLUMN features JSONB;

ALTER TABLE t_credential_scheme
    ADD COLUMN max_batch_size INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_credential_scheme
    ADD CONSTRAINT t_credential_scheme_max_batch_size_check CHECK (max_batch_size >= 1);

CREATE TABLE t_issuer_signing_definition
(
    pk_signing_definition_id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                              TEXT NOT NULL,
    signer_type                       TEXT NOT NULL,
    key_id                            TEXT NOT NULL,
    key_uri                           TEXT,
    provider_id                       INTEGER REFERENCES t_signing_provider (pk_signing_provider_id),
    keychain_id                       UUID REFERENCES t_signing_keychain (pk_keychain_id),
    key_version_id                    UUID REFERENCES t_signing_key_version (pk_key_version_id),
    algorithm                         TEXT NOT NULL,
    issuer_jwk                        TEXT,
    CONSTRAINT chk_issuer_signing_definition_key_id
        CHECK (key_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$')
);

CREATE TABLE t_issuer_signing_certificate
(
    fk_signing_definition_id INTEGER NOT NULL REFERENCES t_issuer_signing_definition (pk_signing_definition_id),
    chain_index              INTEGER NOT NULL,
    certificate              TEXT NOT NULL,
    PRIMARY KEY (fk_signing_definition_id, chain_index)
);

CREATE TABLE t_issuer_signing_binding
(
    pk_issuer_signing_binding_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_issuer_id                 INTEGER NOT NULL REFERENCES t_issuer (pk_issuer_id),
    trust_system                 TEXT NOT NULL,
    fk_signing_definition_id     INTEGER NOT NULL REFERENCES t_issuer_signing_definition (pk_signing_definition_id),
    CONSTRAINT uq_issuer_trust_system UNIQUE (fk_issuer_id, trust_system)
);

CREATE TABLE t_issuer_operation_configuration
(
    pk_operation_configuration_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_issuer_signing_binding_id  INTEGER NOT NULL
        REFERENCES t_issuer_signing_binding (pk_issuer_signing_binding_id) ON DELETE CASCADE,
    operation                    TEXT NOT NULL,
    schema_version               INTEGER NOT NULL DEFAULT 1,
    config_json                  JSONB NOT NULL,
    CONSTRAINT uk_issuer_operation_configuration
        UNIQUE (fk_issuer_signing_binding_id, operation),
    CONSTRAINT ck_issuer_operation_configuration_object
        CHECK (jsonb_typeof(config_json) = 'object'),
    CONSTRAINT ck_issuer_operation_configuration_version
        CHECK (schema_version > 0)
);

ALTER TABLE t_issuer_signing_definition
    ADD COLUMN tenant_id TEXT REFERENCES t_tenant (pk_tenant_id);

CREATE INDEX idx_issuer_signing_definition_tenant_id
    ON t_issuer_signing_definition (tenant_id);

CREATE TABLE t_issuer_signer_key
(
    pk_signer_key_id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_signing_definition_id      INTEGER NOT NULL REFERENCES t_issuer_signing_definition (pk_signing_definition_id) ON DELETE CASCADE,
    key_id                        TEXT NOT NULL,
    algorithm                     TEXT NOT NULL,
    key_uri                       TEXT,
    provider_id                  INTEGER REFERENCES t_signing_provider (pk_signing_provider_id),
    keychain_id                  UUID REFERENCES t_signing_keychain (pk_keychain_id),
    key_version_id               UUID REFERENCES t_signing_key_version (pk_key_version_id),
    issuer_jwk                    TEXT,
    CONSTRAINT chk_issuer_signer_key_key_id
        CHECK (key_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
    CONSTRAINT uq_signer_key_id UNIQUE (fk_signing_definition_id, key_id)
);

CREATE TABLE t_issuer_signer_key_certificate
(
    fk_signer_key_id INTEGER NOT NULL REFERENCES t_issuer_signer_key (pk_signer_key_id) ON DELETE CASCADE,
    chain_index      INTEGER NOT NULL,
    certificate      TEXT NOT NULL,
    PRIMARY KEY (fk_signer_key_id, chain_index)
);

ALTER TABLE t_credential_scheme
    ADD COLUMN signing_key_ids JSONB,
    ADD COLUMN default_trust_system TEXT;

ALTER TABLE t_issuer
    ADD COLUMN default_trust_system TEXT;

ALTER TABLE t_issuer_signing_binding
    ADD COLUMN registration_certificate TEXT,
    ADD COLUMN swiss_did TEXT,
    ADD COLUMN swiss_registry_base_url TEXT,
    ADD COLUMN swiss_identity_statement TEXT,
    ADD COLUMN swiss_identity_statement_expires_at TIMESTAMPTZ,
    ADD COLUMN swiss_issuance_statements JSONB,
    ADD COLUMN swiss_issuance_statements_expires_at TIMESTAMPTZ;

ALTER TABLE t_issuer_signing_binding
    ADD COLUMN issuer_claim TEXT;

UPDATE t_issuer_signing_binding
SET issuer_claim = swiss_did
WHERE trust_system = 'Switzerland'
  AND swiss_did IS NOT NULL
  AND (issuer_claim IS NULL OR BTRIM(issuer_claim) = '');

ALTER TABLE t_proof_scheme
    ADD COLUMN fk_verifier_identity_id INTEGER REFERENCES t_issuer (pk_issuer_id),
    ADD COLUMN verifier_client_id_scheme VARCHAR(64),
    ADD COLUMN swiss_identity_statement TEXT,
    ADD COLUMN swiss_verification_query_statement TEXT,
    ADD COLUMN swiss_protected_verification_statements JSONB;

ALTER TABLE t_proof_scheme_credential_scheme
    ADD COLUMN credential_position INTEGER NOT NULL DEFAULT 0;

WITH ordered_credentials AS (
    SELECT pk_proof_scheme_credential_scheme_id,
           ROW_NUMBER() OVER (
               PARTITION BY fk_proof_scheme_id
               ORDER BY pk_proof_scheme_credential_scheme_id
           ) - 1 AS credential_position
    FROM t_proof_scheme_credential_scheme
)
UPDATE t_proof_scheme_credential_scheme relation
SET credential_position = ordered.credential_position
FROM ordered_credentials ordered
WHERE relation.pk_proof_scheme_credential_scheme_id =
      ordered.pk_proof_scheme_credential_scheme_id;

COMMENT ON COLUMN t_proof_scheme.fk_verifier_identity_id IS
    'Explicit verifier identity; NULL inherits the identity of the first requested credential';

ALTER TABLE t_proof_scheme
    ADD COLUMN verifier_signing_key_id VARCHAR(255);

CREATE TABLE t_issue_process
(
    pk_issue_process_id integer NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id TEXT  NOT NULL,
    issue_payload jsonb   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

alter table t_issue_process
    add column credential_type TEXT;

CREATE TABLE t_oidc_params
(
    auth_flow_id          UUID PRIMARY KEY,
    credential_identifier TEXT NOT NULL,
    credential_version    TEXT NOT NULL,
    oidc_params           JSONB NOT NULL
);
