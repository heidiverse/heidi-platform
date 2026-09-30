CREATE TABLE t_status_list_allocation
(
    pk_status_list_allocation_id UUID PRIMARY KEY,
    fk_status_list_id            UUID        NOT NULL,
    allocation_key               TEXT        NOT NULL,
    entry_index                  INTEGER     NOT NULL,
    created_at                   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_status_list_allocation_list
        FOREIGN KEY (fk_status_list_id) REFERENCES t_status_list (pk_status_list_id),
    CONSTRAINT uq_status_list_allocation_key
        UNIQUE (fk_status_list_id, allocation_key),
    CONSTRAINT uq_status_list_allocation_index
        UNIQUE (fk_status_list_id, entry_index),
    CONSTRAINT chk_status_list_allocation_index CHECK (entry_index >= 0)
);

CREATE INDEX idx_status_list_allocation_list
    ON t_status_list_allocation (fk_status_list_id);
