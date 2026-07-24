package com.portfolio.agent.answer.domain;

import java.util.List;

public final class ConversationDraftValidationResult {

    private final boolean valid;
    private final String failureCode;
    private final String title;
    private final AnswerResolution resolution;
    private final List<ConversationAnswerBlock> acceptedBlocks;

    private ConversationDraftValidationResult(
            boolean valid,
            String failureCode,
            String title,
            AnswerResolution resolution,
            List<ConversationAnswerBlock> acceptedBlocks
    ) {
        this.valid = valid;
        this.failureCode = failureCode;
        this.title = title;
        this.resolution = resolution;
        this.acceptedBlocks = List.copyOf(acceptedBlocks);
    }

    public static ConversationDraftValidationResult valid(
            ConversationDraft draft,
            List<ConversationAnswerBlock> acceptedBlocks
    ) {
        return new ConversationDraftValidationResult(
                true,
                null,
                draft.getTitle(),
                draft.getResolution(),
                acceptedBlocks);
    }

    public static ConversationDraftValidationResult invalid(String failureCode) {
        return new ConversationDraftValidationResult(
                false,
                failureCode,
                null,
                null,
                List.of());
    }

    public boolean isValid() { return valid; }
    public String getFailureCode() { return failureCode; }
    public String getTitle() { return title; }
    public AnswerResolution getResolution() { return resolution; }
    public List<ConversationAnswerBlock> getAcceptedBlocks() {
        return acceptedBlocks;
    }
}
