-- Keep legacy bundles intact; existing styles receive their swiyu bundle on first use.
ALTER TABLE t_style
    ADD COLUMN swiyu_oca_bundle TEXT,
    ADD COLUMN swiyu_oca_file_name TEXT;

CREATE INDEX idx_style_swiyu_oca_file_name ON t_style (swiyu_oca_file_name);
