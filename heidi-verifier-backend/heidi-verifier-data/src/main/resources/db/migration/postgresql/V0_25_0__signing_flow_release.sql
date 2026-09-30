-- Completed requests retry release until the platform acknowledges it.
ALTER TABLE t_verification_request ADD COLUMN signing_flow_released BOOLEAN NOT NULL DEFAULT FALSE;
