package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.dto.request.ConversationMessageRequest;
import com.portfolio.agent.answer.gateway.ConversationSummaryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConversationWindowManager {

    private final ConversationSummaryPort summaryPort;
    private final int maxInputTokens;
    private final int recentRawRounds;

    public ConversationWindowManager(
            ConversationSummaryPort summaryPort,
            int maxInputTokens,
            int recentRawRounds
    ) {
        if (maxInputTokens <= 0) {
            throw new IllegalArgumentException("maxInputTokens must be positive");
        }
        if (recentRawRounds < 0) {
            throw new IllegalArgumentException("recentRawRounds must not be negative");
        }
        this.summaryPort = summaryPort;
        this.maxInputTokens = maxInputTokens;
        this.recentRawRounds = recentRawRounds;
    }

    public ConversationWindow prepare(
            List<ConversationMessageRequest> history,
            String currentQuestion
    ) {
        List<ConversationMessage> messages = toDomainMessages(history);
        int originalTokens = estimateMessages(messages) + estimateTokens(currentQuestion);
        if (originalTokens <= maxInputTokens) {
            return new ConversationWindow(null, messages, originalTokens);
        }

        int recentMessageCount = Math.min(messages.size(), recentRawRounds * 2);
        int splitIndex = messages.size() - recentMessageCount;
        List<ConversationMessage> olderMessages = messages.subList(0, splitIndex);
        List<ConversationMessage> recentMessages = messages.subList(splitIndex, messages.size());
        Optional<String> summary = olderMessages.isEmpty()
                ? Optional.empty()
                : summaryPort.summarize(List.copyOf(olderMessages));
        int estimatedTokens = estimateMessages(recentMessages)
                + estimateTokens(currentQuestion)
                + summary.map(this::estimateTokens).orElse(0);
        return new ConversationWindow(
                summary.orElse(null),
                recentMessages,
                estimatedTokens);
    }

    int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        int asciiRunLength = 0;
        for (int offset = 0; offset < content.length();) {
            int codePoint = content.codePointAt(offset);
            if (codePoint <= 0x7F) {
                asciiRunLength++;
            } else {
                tokens += divideRoundUp(asciiRunLength, 4);
                asciiRunLength = 0;
                tokens++;
            }
            offset += Character.charCount(codePoint);
        }
        return tokens + divideRoundUp(asciiRunLength, 4);
    }

    private List<ConversationMessage> toDomainMessages(
            List<ConversationMessageRequest> history
    ) {
        List<ConversationMessage> messages = new ArrayList<>(history.size());
        for (ConversationMessageRequest message : history) {
            messages.add(new ConversationMessage(message.getRole(), message.getContent()));
        }
        return List.copyOf(messages);
    }

    private int estimateMessages(List<ConversationMessage> messages) {
        int tokens = 0;
        for (ConversationMessage message : messages) {
            tokens += estimateTokens(message.getContent());
        }
        return tokens;
    }

    private int divideRoundUp(int value, int divisor) {
        return value == 0 ? 0 : (value + divisor - 1) / divisor;
    }
}
