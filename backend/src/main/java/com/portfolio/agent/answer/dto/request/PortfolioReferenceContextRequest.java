package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

public final class PortfolioReferenceContextRequest {

    @NotBlank(message = "previousContentVersion is required")
    @Pattern(
            regexp = "[A-Za-z0-9._-]{1,100}",
            message = "previousContentVersion format is invalid")
    private final String previousContentVersion;

    @NotNull(message = "projectSlugs is required")
    @Size(max = 4, message = "projectSlugs must not contain more than 4 values")
    private final List<@Pattern(
            regexp = "[a-z0-9-]{1,64}",
            message = "projectSlug format is invalid") String> projectSlugs;

    @NotNull(message = "caseSlugs is required")
    @Size(max = 1, message = "caseSlugs must not contain more than 1 value")
    private final List<@Pattern(
            regexp = "[a-z0-9-]{1,64}",
            message = "caseSlug format is invalid") String> caseSlugs;

    @Pattern(
            regexp = "[a-z0-9-]{1,100}",
            message = "questionPresetId format is invalid")
    private final String questionPresetId;

    @NotNull(message = "referencedClaimIds is required")
    @Size(max = 8, message = "referencedClaimIds must not contain more than 8 values")
    private final List<@Pattern(
            regexp = "[a-z0-9-]{1,100}",
            message = "claimId format is invalid") String> referencedClaimIds;

    private final AnswerSectionType selectedSectionType;

    @NotNull(message = "followUpAction is required")
    private final PortfolioFollowUpAction followUpAction;

    @JsonCreator
    public PortfolioReferenceContextRequest(
            @JsonProperty("previousContentVersion") String previousContentVersion,
            @JsonProperty("projectSlugs") List<String> projectSlugs,
            @JsonProperty("caseSlugs") List<String> caseSlugs,
            @JsonProperty("questionPresetId") String questionPresetId,
            @JsonProperty("referencedClaimIds") List<String> referencedClaimIds,
            @JsonProperty("selectedSectionType") AnswerSectionType selectedSectionType,
            @JsonProperty("followUpAction") PortfolioFollowUpAction followUpAction
    ) {
        this.previousContentVersion = previousContentVersion;
        this.projectSlugs = projectSlugs == null ? List.of() : List.copyOf(projectSlugs);
        this.caseSlugs = caseSlugs == null ? List.of() : List.copyOf(caseSlugs);
        this.questionPresetId = questionPresetId;
        this.referencedClaimIds = referencedClaimIds == null
                ? List.of()
                : List.copyOf(referencedClaimIds);
        this.selectedSectionType = selectedSectionType;
        this.followUpAction = followUpAction;
    }

    public String getPreviousContentVersion() { return previousContentVersion; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getCaseSlugs() { return caseSlugs; }
    public String getQuestionPresetId() { return questionPresetId; }
    public List<String> getReferencedClaimIds() { return referencedClaimIds; }
    public AnswerSectionType getSelectedSectionType() { return selectedSectionType; }
    public PortfolioFollowUpAction getFollowUpAction() { return followUpAction; }

    @AssertTrue(message = "stable references must be unique")
    public boolean isStableReferencesUnique() {
        return new HashSet<>(projectSlugs).size() == projectSlugs.size()
                && new HashSet<>(caseSlugs).size() == caseSlugs.size()
                && new HashSet<>(referencedClaimIds).size() == referencedClaimIds.size();
    }

    @AssertTrue(message = "exactly one subject type is required")
    public boolean isSubjectSelectionValid() {
        return projectSlugs.isEmpty() != caseSlugs.isEmpty();
    }

    @Override
    public String toString() {
        return "PortfolioReferenceContextRequest{" +
                "previousContentVersion='" + previousContentVersion + '\'' +
                ", projectCount=" + projectSlugs.size() +
                ", caseCount=" + caseSlugs.size() +
                ", claimCount=" + referencedClaimIds.size() +
                ", selectedSectionType=" + selectedSectionType +
                ", followUpAction=" + followUpAction +
                '}';
    }
}
