CREATE TABLE agent_context.agent_turn_execution (
    request_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    request_fingerprint BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    settlement_key_id VARCHAR(64),
    settlement_nonce BYTEA,
    settlement_ciphertext BYTEA,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    terminal_at TIMESTAMPTZ,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT agent_turn_status_closed CHECK (status IN ('CLAIMED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT agent_turn_fingerprint_length CHECK (octet_length(request_fingerprint) = 32),
    CONSTRAINT agent_turn_nonce_length CHECK (
        settlement_nonce IS NULL OR octet_length(settlement_nonce) = 12),
    CONSTRAINT agent_turn_settlement_shape CHECK (
        (status = 'COMPLETED' AND settlement_key_id IS NOT NULL
            AND settlement_nonce IS NOT NULL AND settlement_ciphertext IS NOT NULL
            AND terminal_at IS NOT NULL)
        OR
        (status <> 'COMPLETED' AND settlement_key_id IS NULL
            AND settlement_nonce IS NULL AND settlement_ciphertext IS NULL))
);

CREATE INDEX agent_turn_conversation_expiry_idx
    ON agent_context.agent_turn_execution (conversation_id, absolute_expires_at);
CREATE INDEX agent_turn_lease_idx
    ON agent_context.agent_turn_execution (status, lease_expires_at);

CREATE TABLE agent_context.agent_turn_context (
    conversation_id UUID NOT NULL,
    context_handle VARCHAR(256) NOT NULL,
    source_request_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    payload_key_id VARCHAR(64) NOT NULL,
    payload_nonce BYTEA NOT NULL CHECK (octet_length(payload_nonce) = 12),
    payload_ciphertext BYTEA NOT NULL,
    PRIMARY KEY (conversation_id, context_handle),
    FOREIGN KEY (source_request_id) REFERENCES agent_context.agent_turn_execution(request_id)
        ON DELETE CASCADE
);

CREATE TABLE agent_context.agent_turn_clarification (
    clarification_id VARCHAR(256) PRIMARY KEY,
    conversation_id UUID NOT NULL,
    source_request_id UUID NOT NULL,
    resume_token_hash BYTEA NOT NULL CHECK (octet_length(resume_token_hash) = 32),
    content_release_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    payload_key_id VARCHAR(64) NOT NULL,
    payload_nonce BYTEA NOT NULL CHECK (octet_length(payload_nonce) = 12),
    payload_ciphertext BYTEA NOT NULL,
    FOREIGN KEY (source_request_id) REFERENCES agent_context.agent_turn_execution(request_id)
        ON DELETE CASCADE
);

CREATE INDEX agent_turn_context_expiry_idx
    ON agent_context.agent_turn_context (conversation_id, expires_at);
CREATE INDEX agent_turn_clarification_expiry_idx
    ON agent_context.agent_turn_clarification (conversation_id, expires_at, consumed);
