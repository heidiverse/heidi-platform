ALTER TABLE t_signing_certificate
    ADD COLUMN first_used_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN retired_at TIMESTAMP WITH TIME ZONE;

-- Imported or assigned certificates may already have signed material in flight.
UPDATE t_signing_certificate
SET first_used_at = created_at
WHERE source = 'IMPORTED'
   OR EXISTS (
       SELECT 1
       FROM t_identity_key_slot slot
       WHERE slot.certificate_id = t_signing_certificate.pk_certificate_id
   );
