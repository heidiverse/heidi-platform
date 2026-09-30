-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

ALTER TABLE t_issuer
    ADD COLUMN IF NOT EXISTS custom_profile_name TEXT;
