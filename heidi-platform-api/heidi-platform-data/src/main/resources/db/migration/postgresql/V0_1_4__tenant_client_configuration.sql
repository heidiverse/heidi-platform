ALTER TABLE t_tenant
    ADD COLUMN client_configuration JSONB;

ALTER TABLE t_integration_process
    ADD COLUMN client_configuration JSONB;
