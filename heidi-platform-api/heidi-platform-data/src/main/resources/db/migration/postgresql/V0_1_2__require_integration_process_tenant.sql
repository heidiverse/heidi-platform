DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM t_integration_process WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION
            'Cannot enforce tenant ownership for integration processes: existing rows have no tenant_id';
    END IF;
END
$$;

ALTER TABLE t_integration_process
    ADD CONSTRAINT fk_integration_process_tenant
        FOREIGN KEY (tenant_id) REFERENCES t_tenant (pk_tenant_id);

ALTER TABLE t_integration_process
    ALTER COLUMN tenant_id SET NOT NULL;
