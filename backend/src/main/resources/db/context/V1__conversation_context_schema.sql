CREATE SCHEMA IF NOT EXISTS agent_context;

CREATE TABLE agent_context.conversation_session (
    conversation_id UUID PRIMARY KEY,
    resume_token_hash BYTEA NOT NULL,
    token_key_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    context_count INTEGER NOT NULL CHECK (context_count BETWEEN 0 AND 32),
    payload_bytes INTEGER NOT NULL CHECK (payload_bytes BETWEEN 0 AND 524288),
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT conversation_session_token_hash_length
        CHECK (octet_length(resume_token_hash) = 32),
    CONSTRAINT conversation_session_expiry_order
        CHECK (idle_expires_at <= absolute_expires_at),
    CONSTRAINT conversation_session_token_hash_unique UNIQUE (resume_token_hash)
);

CREATE TABLE agent_context.conversation_context (
    conversation_id UUID NOT NULL,
    context_handle VARCHAR(64) NOT NULL,
    context_type VARCHAR(32) NOT NULL,
    parent_context_handle VARCHAR(64),
    source_task_id VARCHAR(100) NOT NULL,
    content_version_binding VARCHAR(128) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    encryption_key_id VARCHAR(64) NOT NULL,
    nonce BYTEA NOT NULL,
    typed_context_ciphertext BYTEA NOT NULL,
    payload_bytes INTEGER NOT NULL CHECK (payload_bytes BETWEEN 1 AND 16384),
    created_at TIMESTAMPTZ NOT NULL,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (conversation_id, context_handle),
    CONSTRAINT conversation_context_type_closed
        CHECK (context_type IN ('RECENT_SEMANTIC_TASK', 'RECOMMENDATION')),
    CONSTRAINT conversation_context_handle_length
        CHECK (length(context_handle) BETWEEN 32 AND 64),
    CONSTRAINT conversation_context_nonce_length
        CHECK (octet_length(nonce) = 12),
    CONSTRAINT conversation_context_ciphertext_present
        CHECK (octet_length(typed_context_ciphertext) > 0),
    CONSTRAINT conversation_context_expiry_order
        CHECK (expires_at <= absolute_expires_at),
    CONSTRAINT conversation_context_session_fk
        FOREIGN KEY (conversation_id) REFERENCES agent_context.conversation_session(conversation_id)
        ON DELETE CASCADE,
    CONSTRAINT conversation_context_parent_fk
        FOREIGN KEY (conversation_id, parent_context_handle)
        REFERENCES agent_context.conversation_context(conversation_id, context_handle)
        ON DELETE RESTRICT
);

CREATE TABLE agent_context.conversation_active_context (
    conversation_id UUID NOT NULL,
    active_slot VARCHAR(32) NOT NULL,
    context_handle VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (conversation_id, active_slot),
    CONSTRAINT conversation_active_slot_closed
        CHECK (active_slot IN ('ACTIVE_FACT_CONTEXT', 'ACTIVE_COMPARE_CONTEXT', 'ACTIVE_RECOMMENDATION')),
    CONSTRAINT conversation_active_context_fk
        FOREIGN KEY (conversation_id, context_handle)
        REFERENCES agent_context.conversation_context(conversation_id, context_handle)
        ON DELETE CASCADE,
    CONSTRAINT conversation_active_revision_nonnegative CHECK (revision >= 0)
);

CREATE TABLE agent_context.conversation_request_receipt (
    request_token UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    request_fingerprint BYTEA NOT NULL,
    parent_context_handle VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    lease_id UUID,
    lease_expires_at TIMESTAMPTZ,
    completion_key_id VARCHAR(64),
    completion_nonce BYTEA,
    completion_ciphertext BYTEA,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT conversation_receipt_status_closed
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT conversation_receipt_fingerprint_length
        CHECK (octet_length(request_fingerprint) = 32),
    CONSTRAINT conversation_receipt_completion_nonce_length
        CHECK (completion_nonce IS NULL OR octet_length(completion_nonce) = 12),
    CONSTRAINT conversation_receipt_parent_fk
        FOREIGN KEY (conversation_id, parent_context_handle)
        REFERENCES agent_context.conversation_context(conversation_id, context_handle)
        ON DELETE CASCADE,
    CONSTRAINT conversation_receipt_session_fk
        FOREIGN KEY (conversation_id) REFERENCES agent_context.conversation_session(conversation_id)
        ON DELETE CASCADE
);

CREATE INDEX conversation_session_idle_expiry_idx
    ON agent_context.conversation_session (idle_expires_at);
CREATE INDEX conversation_session_absolute_expiry_idx
    ON agent_context.conversation_session (absolute_expires_at);
CREATE INDEX conversation_context_created_idx
    ON agent_context.conversation_context (conversation_id, created_at DESC);
CREATE INDEX conversation_context_type_created_idx
    ON agent_context.conversation_context (conversation_id, context_type, created_at DESC);
CREATE INDEX conversation_context_expiry_idx
    ON agent_context.conversation_context (expires_at);
CREATE INDEX conversation_receipt_conversation_expiry_idx
    ON agent_context.conversation_request_receipt (conversation_id, expires_at);
CREATE INDEX conversation_receipt_lease_idx
    ON agent_context.conversation_request_receipt (status, lease_expires_at);
