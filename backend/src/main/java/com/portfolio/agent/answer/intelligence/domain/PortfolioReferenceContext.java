package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.AnswerSectionType;

import java.util.List;
import java.util.Objects;

public final class PortfolioReferenceContext {

    private final String previousContentVersion;
    private final List<String> projectSlugs;
    private final List<String> caseSlugs;
    private final String questionPresetId;
    private final List<String> referencedClaimIds;
    private final AnswerSectionType selectedSectionType;
    private final PortfolioFollowUpAction followUpAction;

    public PortfolioReferenceContext(
            String previousContentVersion,
            List<String> projectSlugs,
            List<String> caseSlugs,
            String questionPresetId,
            List<String> referencedClaimIds,
            AnswerSectionType selectedSectionType,
            PortfolioFollowUpAction followUpAction
    ) {
        this.previousContentVersion = requireText(
                previousContentVersion, "previousContentVersion");
        this.projectSlugs = List.copyOf(Objects.requireNonNull(projectSlugs, "projectSlugs"));
        this.caseSlugs = List.copyOf(Objects.requireNonNull(caseSlugs, "caseSlugs"));
        this.questionPresetId = normalizeText(questionPresetId);
        this.referencedClaimIds = List.copyOf(
                Objects.requireNonNull(referencedClaimIds, "referencedClaimIds"));
        this.selectedSectionType = selectedSectionType;
        this.followUpAction = Objects.requireNonNull(followUpAction, "followUpAction");
    }

    public String getPreviousContentVersion() { return previousContentVersion; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getCaseSlugs() { return caseSlugs; }
    public String getQuestionPresetId() { return questionPresetId; }
    public List<String> getReferencedClaimIds() { return referencedClaimIds; }
    public AnswerSectionType getSelectedSectionType() { return selectedSectionType; }
    public PortfolioFollowUpAction getFollowUpAction() { return followUpAction; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
