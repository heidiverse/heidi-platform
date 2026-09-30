-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

-- This sql script runs before each test method, in a separate transaction that is roll-backed, but
-- sequences must be reset to not break fk constraint

ALTER SEQUENCE public.t_proof_scheme_pk_proof_scheme_id_seq RESTART;
ALTER SEQUENCE public.t_proof_scheme_credential_sch_pk_proof_scheme_credential_sc_seq RESTART;
ALTER SEQUENCE public.t_proof_scheme_credential_sch_pk_proof_scheme_requested_att_seq RESTART;

INSERT INTO t_proof_scheme(title, purpose, validation_logic, archived, created_at, updated_at, uuid,
                           presentation_profile_id)
VALUES ('test title', 'test purpose', 'test', false, now(), null,
        '94a96795-2083-49d0-88c1-4d498585b8f8', 'CUSTOM_PRESENTATION_2026_1'),
       ('test title2', 'test purpose2', null, false, now(), null,
        'bb0a46c0-b68d-4f6a-9b26-c7b00126cf4d', 'CUSTOM_PRESENTATION_2026_1');

INSERT INTO t_proof_scheme_credential_scheme(fk_proof_scheme_id, fk_credential_scheme_id)
VALUES (1, 1),
       (2, 2);

INSERT INTO t_proof_scheme_credential_scheme_requested_attribute(fk_credential_scheme_attribute_id,
                                                                 fk_proof_scheme_credential_scheme_id)
VALUES (1, 1),
       (2, 1),
       (4, 2),
       (5, 2);
