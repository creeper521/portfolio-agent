package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.FollowUpIntent;

import java.util.List;
import java.util.Objects;

public final class ContextEnvelopeResponse {

    private final String previousContentVersion;
    private final List<String> projectSlugs;
    private final List<String> caseSlugs;
    private final String questionPresetId;
    private final List<String> referencedClaimIds;
    private final AnswerSectionType selectedSectionType;
    private final FollowUpIntent followUpIntent;

    public ContextEnvelopeResponse(
            String previousContentVersion,
            List<String> projectSlugs,
            List<String> caseSlugs,
            String questionPresetId,
            List<String> referencedClaimIds,
            AnswerSectionType selectedSectionType,
            FollowUpIntent followUpIntent
    ) {
        this.previousContentVersion = previousContentVersion;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.caseSlugs = List.copyOf(caseSlugs);
        this.questionPresetId = questionPresetId;
        this.referencedClaimIds = List.copyOf(referencedClaimIds);
        this.selectedSectionType = selectedSectionType;
        this.followUpIntent = followUpIntent;
    }

    public ContextEnvelopeResponse(
            String previousContentVersion,
            List<String> projectSlugs,
            String questionPresetId,
            List<String> referencedClaimIds,
            AnswerSectionType selectedSectionType,
            FollowUpIntent followUpIntent
    ) {
        this(previousContentVersion, projectSlugs, List.of(), questionPresetId,
                referencedClaimIds, selectedSectionType, followUpIntent);
    }

    public String getPreviousContentVersion() { return previousContentVersion; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getCaseSlugs() { return caseSlugs; }
    public String getQuestionPresetId() { return questionPresetId; }
    public List<String> getReferencedClaimIds() { return referencedClaimIds; }
    public AnswerSectionType getSelectedSectionType() { return selectedSectionType; }
    public FollowUpIntent getFollowUpIntent() { return followUpIntent; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContextEnvelopeResponse that)) {
            return false;
        }
        return Objects.equals(previousContentVersion, that.previousContentVersion)
                && Objects.equals(projectSlugs, that.projectSlugs)
                && Objects.equals(caseSlugs, that.caseSlugs)
                && Objects.equals(questionPresetId, that.questionPresetId)
                && Objects.equals(referencedClaimIds, that.referencedClaimIds)
                && selectedSectionType == that.selectedSectionType
                && followUpIntent == that.followUpIntent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(previousContentVersion, projectSlugs, caseSlugs, questionPresetId,
                referencedClaimIds, selectedSectionType, followUpIntent);
    }

    @Override
    public String toString() {
        return "ContextEnvelopeResponse{" +
                "previousContentVersion='" + previousContentVersion + '\'' +
                ", projectCount=" + projectSlugs.size() +
                ", caseCount=" + caseSlugs.size() +
                ", claimCount=" + referencedClaimIds.size() +
                ", selectedSectionType=" + selectedSectionType +
                ", followUpIntent=" + followUpIntent +
                '}';
    }
}
