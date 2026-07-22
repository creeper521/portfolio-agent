package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ValidatedContextEnvelope {

    private final String contentVersion;
    private final List<String> projectSlugs;
    private final String questionPresetId;
    private final List<String> referencedClaimIds;
    private final AnswerSectionType selectedSectionType;
    private final FollowUpIntent followUpIntent;

    public ValidatedContextEnvelope(
            String contentVersion,
            List<String> projectSlugs,
            String questionPresetId,
            List<String> referencedClaimIds,
            AnswerSectionType selectedSectionType,
            FollowUpIntent followUpIntent
    ) {
        this.contentVersion = contentVersion;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.questionPresetId = questionPresetId;
        this.referencedClaimIds = List.copyOf(referencedClaimIds);
        this.selectedSectionType = selectedSectionType;
        this.followUpIntent = Objects.requireNonNull(followUpIntent, "followUpIntent");
    }

    public String getContentVersion() { return contentVersion; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public String getQuestionPresetId() { return questionPresetId; }
    public List<String> getReferencedClaimIds() { return referencedClaimIds; }
    public AnswerSectionType getSelectedSectionType() { return selectedSectionType; }
    public FollowUpIntent getFollowUpIntent() { return followUpIntent; }

    public QueryIntent toQueryIntent() {
        return new QueryIntent(
                followUpIntent, projectSlugs, referencedClaimIds, selectedSectionType);
    }
}
