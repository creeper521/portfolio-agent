-- Text-free, typed memory of the last successfully produced semantic turn.
-- Payload is AES-GCM encrypted by the same bounded Agent state codec.
ALTER TABLE agent_context.conversation_session
    ADD COLUMN semantic_state_key_id VARCHAR(64),
    ADD COLUMN semantic_state_nonce BYTEA,
    ADD COLUMN semantic_state_ciphertext BYTEA,
    ADD COLUMN semantic_state_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT conversation_session_semantic_state_nonce_length CHECK (
        semantic_state_nonce IS NULL OR octet_length(semantic_state_nonce) = 12),
    ADD CONSTRAINT conversation_session_semantic_state_shape CHECK (
        (semantic_state_key_id IS NULL
            AND semantic_state_nonce IS NULL
            AND semantic_state_ciphertext IS NULL
            AND semantic_state_updated_at IS NULL)
        OR
        (semantic_state_key_id IS NOT NULL
            AND semantic_state_nonce IS NOT NULL
            AND semantic_state_ciphertext IS NOT NULL
            AND octet_length(semantic_state_ciphertext) >= 16
            AND semantic_state_updated_at IS NOT NULL));

CREATE INDEX conversation_session_semantic_state_key_idx
    ON agent_context.conversation_session (semantic_state_key_id)
    WHERE semantic_state_key_id IS NOT NULL;
