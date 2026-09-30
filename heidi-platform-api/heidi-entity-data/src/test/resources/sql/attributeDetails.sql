-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

-- This sql script runs before each test method, in a separate transaction that is roll-backed, but
-- sequences must be reset to not break fk constraint

ALTER SEQUENCE public.t_credential_scheme_attribute_pk_credential_scheme_attribu_seq1 RESTART;

INSERT INTO t_credential_scheme_attribute_detail(fk_credential_scheme_attribute_id, language, display_name)
VALUES (1, 'DE', 'Vorname'),
       (1, 'EN', 'First Name'),
       (2, 'DE', 'Nachname'),
       (2, 'EN', 'Last Name'),
       (3, 'DE', 'Alter'),
       (3, 'EN', 'Age'),
       (4, 'DE', 'Vorname'),
       (4, 'EN', 'First Name'),
       (5, 'DE', 'Alter'),
       (5, 'EN', 'Age'),
       (6, 'DE', 'Vorname'),
       (6, 'EN', 'First Name'),
       (7, 'DE', 'Alter'),
       (7, 'EN', 'Age');




