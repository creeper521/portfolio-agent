-- A clarification is reserved by one claimed Turn before execution and is
-- consumed only inside that Turn's terminal settlement transaction.
ALTER TABLE agent_context.agent_turn_clarification
    ADD COLUMN reserved_by_request_id UUID,
    ADD COLUMN reservation_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT agent_turn_clarification_reservation_shape CHECK (
        (reserved_by_request_id IS NULL AND reservation_expires_at IS NULL)
        OR
        (reserved_by_request_id IS NOT NULL
            AND reservation_expires_at IS NOT NULL)),
    ADD CONSTRAINT agent_turn_clarification_reservation_expiry_bound CHECK (
        reservation_expires_at IS NULL
            OR reservation_expires_at <= expires_at),
    ADD CONSTRAINT agent_turn_clarification_consumed_not_reserved CHECK (
        consumed = false OR reserved_by_request_id IS NULL);

CREATE INDEX agent_turn_clarification_reservation_expiry_idx
    ON agent_context.agent_turn_clarification (
        reservation_expires_at, clarification_id)
    WHERE reserved_by_request_id IS NOT NULL;
