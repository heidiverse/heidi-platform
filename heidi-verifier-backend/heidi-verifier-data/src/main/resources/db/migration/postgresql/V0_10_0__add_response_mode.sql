alter table t_verification_request
    add column response_mode text DEFAULT 'direct_post.jwt';