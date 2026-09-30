-- Pin public signing configuration so certificate renewal cannot change an issued client_id.
ALTER TABLE t_verification_request ADD COLUMN signing_snapshot TEXT;
