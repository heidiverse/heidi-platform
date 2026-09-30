-- A scope with no policy is open; a scope whose policy is present and empty grants nobody
-- anything. The grant rows alone cannot express the second, so the policy has a row of its own.
CREATE TABLE t_signing_grant_policy
(
    scope      TEXT PRIMARY KEY,
    written_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
