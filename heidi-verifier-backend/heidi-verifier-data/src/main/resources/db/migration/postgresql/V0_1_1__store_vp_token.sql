ALTER TABLE t_verification_request
    ADD COLUMN store_vp_token BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_verification_submission
    ADD COLUMN vp_token JSONB;
