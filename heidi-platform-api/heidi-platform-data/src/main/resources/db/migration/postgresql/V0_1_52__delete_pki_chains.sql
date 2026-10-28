-- Hide deleted CA branches while preserving issued paths and certificate publication.
ALTER TABLE t_issuing_subca ADD COLUMN deleted_at timestamptz;
