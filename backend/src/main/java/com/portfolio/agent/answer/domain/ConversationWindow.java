package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Optional;

public final class ConversationWindow {

    private final String summary;
    private final List<ConversationMessage> recentMessages;
    private final int estimatedTokens;

    public ConversationWindow(
            String summary,
            List<ConversationMessage> recentMessages,
            int estimatedTokens
    ) {
        this.summary = summary;
        this.recentMessages = List.copyOf(recentMessages);
        this.estimatedTokens = estimatedTokens;
    }

    public Optional<String> getSummary() {
        return Optional.ofNullable(summary);
    }

    public List<ConversationMessage> getRecentMessages() {
        return recentMessages;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }
}
