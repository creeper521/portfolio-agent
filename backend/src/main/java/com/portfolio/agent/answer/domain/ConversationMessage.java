package com.portfolio.agent.answer.domain;

import java.util.Objects;

public final class ConversationMessage {

    private final ConversationMessageRole role;
    private final String content;

    public ConversationMessage(ConversationMessageRole role, String content) {
        this.role = Objects.requireNonNull(role, "role");
        this.content = Objects.requireNonNull(content, "content");
    }

    public ConversationMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
