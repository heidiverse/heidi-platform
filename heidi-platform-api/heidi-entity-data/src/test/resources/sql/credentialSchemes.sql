-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

-- This sql script runs before each test method, in a separate transaction that is roll-backed, but
-- sequences must be reset to not break fk constraint

ALTER SEQUENCE public.t_credential_scheme_pk_credential_scheme_id_seq RESTART;

INSERT INTO t_credential_scheme(credential_identifier, version, created_at, updated_at, state, uuid,
                                fk_issuer_id, key_type, issuance_profile_id)
VALUES ('test', '0.0.1', now(), null, 'PUBLISHED', 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        1, 'HARDWARE_BIOMETRIC_AUTH', 'CUSTOM_ISSUANCE_2026_1'),
       ('test2', '0.0.1', now(), null, 'PUBLISHED', 'e92d1e26-1d4c-42c6-8a7d-5f605dc67268',
        1, 'HARDWARE_BIOMETRIC_AUTH', 'CUSTOM_ISSUANCE_2026_1'),
       ('eudi-test', '0.0.1', now(), null, 'PUBLISHED', 'b7f4f0c7-3e92-4f84-9f0a-4af9de9b3e42',
        1, 'HARDWARE_BIOMETRIC_AUTH', 'EUDI_ISSUANCE_2026_1');
