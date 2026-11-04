ALTER TABLE t_issuer
    ADD COLUMN issuer_display JSONB;

UPDATE t_issuer issuer
SET issuer_display = COALESCE((
    SELECT jsonb_agg(
        jsonb_strip_nulls(jsonb_build_object(
            'locale', detail.language,
            'name', detail.display_name,
            'logoUri', NULLIF(issuer.logo, ''),
            'logoAltText', NULL
        )) ORDER BY detail.language
    )
    FROM t_issuer_detail detail
    WHERE detail.fk_issuer_id = issuer.pk_issuer_id
      AND BTRIM(detail.display_name) <> ''
), '[]'::jsonb);

ALTER TABLE t_issuer
    ALTER COLUMN issuer_display SET DEFAULT '[]'::jsonb,
    ALTER COLUMN issuer_display SET NOT NULL;
