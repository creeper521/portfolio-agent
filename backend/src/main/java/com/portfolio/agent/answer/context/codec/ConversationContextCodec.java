package com.portfolio.agent.answer.context.codec;

import com.portfolio.agent.answer.context.domain.ConversationContextType;

public interface ConversationContextCodec<T> {
    ConversationContextType getContextType();
    String getSchemaVersion();
    byte[] encode(T context);
    T decode(byte[] payload);
}
