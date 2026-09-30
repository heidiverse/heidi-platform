-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

-- This sql script runs before each test method, in a separate transaction that is roll-backed, but
-- sequences must be reset to not break fk constraint

ALTER SEQUENCE public.t_credential_scheme_attribute_pk_credential_scheme_attribut_seq RESTART;

INSERT INTO t_credential_scheme_attribute(fk_credential_scheme_id, field_name, field_type)
VALUES (1, 'firstName', 'STRING'),
       (1, 'lastName', 'STRING'),
       (1, 'age', 'NUMBER'),
       (2, 'firstName', 'STRING'),
       (2, 'age', 'NUMBER'),
       (3, 'firstName', 'STRING'),
       (3, 'age', 'NUMBER');


