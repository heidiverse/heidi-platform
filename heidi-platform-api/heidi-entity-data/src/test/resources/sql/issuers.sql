-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

-- This sql script runs before each test method, in a separate transaction that is roll-backed, but
-- sequences must be reset to not break fk constraint

ALTER SEQUENCE public.t_issuer_pk_issuer_id_seq RESTART;

INSERT INTO t_issuer(logo, slug)
VALUES ('', 'abcd');


