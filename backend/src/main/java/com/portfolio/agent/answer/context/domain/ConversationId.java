package com.portfolio.agent.answer.context.domain;

import java.util.Objects;
import java.util.UUID;

public final class ConversationId {
    private final UUID value;
    private ConversationId(UUID value) { this.value = Objects.requireNonNull(value, "value"); }
    public static ConversationId random() { return new ConversationId(UUID.randomUUID()); }
    public static ConversationId parse(String value) { return new ConversationId(UUID.fromString(value)); }
    public UUID asUuid() { return value; }
    @Override public boolean equals(Object other) { return other instanceof ConversationId that && value.equals(that.value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value.toString(); }
}
