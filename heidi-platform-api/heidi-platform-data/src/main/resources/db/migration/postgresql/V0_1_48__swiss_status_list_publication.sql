-- Preserve the local status-list state while recording its Swiss registry resource.
ALTER TABLE t_status_list
    ADD COLUMN swiss_status_list_id UUID,
    ADD COLUMN swiss_status_list_url TEXT,
    ADD COLUMN swiss_published_at TIMESTAMPTZ;

-- Existing lists receive the new one-year default instead of remaining non-expiring.
UPDATE t_status_list
SET ttl = 31536000
WHERE ttl IS NULL;
