ALTER TABLE t_verification_request
    ADD COLUMN include_dcql_query BOOLEAN NOT NULL DEFAULT FALSE;
