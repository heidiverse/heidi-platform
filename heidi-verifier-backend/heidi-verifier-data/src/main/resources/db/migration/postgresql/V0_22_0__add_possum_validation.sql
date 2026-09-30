ALTER TABLE t_verification_request
    ADD COLUMN validation_logic text,
    ADD COLUMN validation_mode varchar(16) NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN validation_result boolean;
