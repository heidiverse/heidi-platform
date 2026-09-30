-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
-- SPDX-License-Identifier: Apache-2.0

CREATE TABLE t_imported_template
(
    id             UUID PRIMARY KEY,
    source_type    VARCHAR(64)  NOT NULL,
    source_id      UUID         NOT NULL,
    source_version VARCHAR(255) NOT NULL,
    template_json  TEXT         NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_imported_template_source
        UNIQUE (source_type, source_id, source_version)
);
