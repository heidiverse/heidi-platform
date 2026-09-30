-- The CP flow variant behaved exactly like C and nothing created it. Map any leftover session so
-- the enum still loads.
update issuance_session set variant = 'C' where variant = 'CP';
