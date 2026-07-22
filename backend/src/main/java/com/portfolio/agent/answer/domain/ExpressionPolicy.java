package com.portfolio.agent.answer.domain;

import java.util.List;

public final class ExpressionPolicy {

    private final ExpressionTone tone;
    private final int maxTitleLength;
    private final int maxSummaryLength;
    private final int maxSectionTitleLength;
    private final int maxSectionContentLength;
    private final boolean mustLabelSelfDeclared;
    private final boolean mustLabelInference;
    private final List<AnswerClaimCategory> priorityCategories;

    public ExpressionPolicy(
            ExpressionTone tone,
            int maxTitleLength,
            int maxSummaryLength,
            int maxSectionTitleLength,
            int maxSectionContentLength,
            boolean mustLabelSelfDeclared,
            boolean mustLabelInference,
            List<AnswerClaimCategory> priorityCategories
    ) {
        this.tone = tone;
        this.maxTitleLength = maxTitleLength;
        this.maxSummaryLength = maxSummaryLength;
        this.maxSectionTitleLength = maxSectionTitleLength;
        this.maxSectionContentLength = maxSectionContentLength;
        this.mustLabelSelfDeclared = mustLabelSelfDeclared;
        this.mustLabelInference = mustLabelInference;
        this.priorityCategories = List.copyOf(priorityCategories);
    }

    public ExpressionTone getTone() { return tone; }
    public int getMaxTitleLength() { return maxTitleLength; }
    public int getMaxSummaryLength() { return maxSummaryLength; }
    public int getMaxSectionTitleLength() { return maxSectionTitleLength; }
    public int getMaxSectionContentLength() { return maxSectionContentLength; }
    public boolean isMustLabelSelfDeclared() { return mustLabelSelfDeclared; }
    public boolean isMustLabelInference() { return mustLabelInference; }
    public List<AnswerClaimCategory> getPriorityCategories() { return priorityCategories; }
}
