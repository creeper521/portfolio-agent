-- Agent State 是短期、可丢弃的数据。V3 直接删除已退出生产链的 V1
-- Context/Receipt 表，不保留兼容视图、reader 或双写路径。
DROP TABLE IF EXISTS agent_context.conversation_request_receipt CASCADE;
DROP TABLE IF EXISTS agent_context.conversation_active_context CASCADE;
DROP TABLE IF EXISTS agent_context.conversation_context CASCADE;

-- 会话撤销先变为不可见，再由有界 cleanup 物理删除，避免撤销请求与清理竞争。
ALTER TABLE agent_context.conversation_session
    ADD COLUMN revoked_at TIMESTAMPTZ;

ALTER TABLE agent_context.agent_turn_execution
    ADD COLUMN fingerprint_key_id VARCHAR(64) NOT NULL DEFAULT 'retired-unversioned',
    ADD CONSTRAINT agent_turn_fingerprint_key_id_present CHECK (
        length(fingerprint_key_id) BETWEEN 1 AND 64),
    ADD CONSTRAINT agent_turn_terminal_status_shape CHECK (
        (status = 'CLAIMED' AND terminal_at IS NULL)
        OR (status IN ('COMPLETED', 'CANCELLED') AND terminal_at IS NOT NULL)),
    ADD CONSTRAINT agent_turn_ciphertext_minimum CHECK (
        settlement_ciphertext IS NULL OR octet_length(settlement_ciphertext) >= 16);

ALTER TABLE agent_context.agent_turn_execution
    ALTER COLUMN fingerprint_key_id DROP DEFAULT;

ALTER TABLE agent_context.agent_turn_context
    ADD CONSTRAINT agent_turn_context_ciphertext_minimum CHECK (
        octet_length(payload_ciphertext) >= 16);

ALTER TABLE agent_context.agent_turn_clarification
    ADD CONSTRAINT agent_turn_clarification_ciphertext_minimum CHECK (
        octet_length(payload_ciphertext) >= 16);

CREATE INDEX conversation_session_cleanup_idx
    ON agent_context.conversation_session (absolute_expires_at, conversation_id);
CREATE INDEX conversation_session_revoked_cleanup_idx
    ON agent_context.conversation_session (revoked_at, conversation_id)
    WHERE revoked_at IS NOT NULL;
CREATE INDEX conversation_session_token_key_idx
    ON agent_context.conversation_session (token_key_id);

CREATE INDEX agent_turn_absolute_expiry_idx
    ON agent_context.agent_turn_execution (absolute_expires_at, request_id);
CREATE INDEX agent_turn_settlement_key_idx
    ON agent_context.agent_turn_execution (settlement_key_id)
    WHERE settlement_key_id IS NOT NULL;
CREATE INDEX agent_turn_context_cleanup_idx
    ON agent_context.agent_turn_context (expires_at, conversation_id, context_handle);
CREATE INDEX agent_turn_context_payload_key_idx
    ON agent_context.agent_turn_context (payload_key_id);
CREATE INDEX agent_turn_clarification_cleanup_idx
    ON agent_context.agent_turn_clarification (expires_at, clarification_id);
CREATE INDEX agent_turn_clarification_payload_key_idx
    ON agent_context.agent_turn_clarification (payload_key_id);
