-- The existing session revision is the monotonic generation of the public
-- discussion projection. It changes only when the pointer changes.
ALTER TABLE agent_context.conversation_session
    ADD CONSTRAINT conversation_session_revision_nonnegative CHECK (
        revision >= 0);
