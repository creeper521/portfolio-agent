-- Project discussion uses the existing encrypted Context table and one
-- generation pointer on the authoritative conversation_session row.
ALTER TABLE agent_context.conversation_session
    ADD COLUMN active_discussion_handle VARCHAR(256),
    ADD COLUMN active_discussion_project_id VARCHAR(128),
    ADD COLUMN active_discussion_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT conversation_session_discussion_pointer_shape CHECK (
        (active_discussion_handle IS NULL
            AND active_discussion_project_id IS NULL
            AND active_discussion_expires_at IS NULL)
        OR
        (active_discussion_handle IS NOT NULL
            AND active_discussion_project_id IS NOT NULL
            AND active_discussion_expires_at IS NOT NULL)),
    ADD CONSTRAINT conversation_session_discussion_handle_length CHECK (
        active_discussion_handle IS NULL
            OR length(active_discussion_handle) BETWEEN 8 AND 256),
    ADD CONSTRAINT conversation_session_discussion_project_length CHECK (
        active_discussion_project_id IS NULL
            OR length(active_discussion_project_id) BETWEEN 1 AND 128),
    ADD CONSTRAINT conversation_session_discussion_expiry_bound CHECK (
        active_discussion_expires_at IS NULL
            OR active_discussion_expires_at <= absolute_expires_at);

CREATE INDEX conversation_session_discussion_expiry_idx
    ON agent_context.conversation_session (
        active_discussion_expires_at, conversation_id)
    WHERE active_discussion_handle IS NOT NULL;
