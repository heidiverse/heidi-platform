-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

ALTER TABLE t_proof_scheme
    ADD COLUMN IF NOT EXISTS verifier_infos JSONB;
