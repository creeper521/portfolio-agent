package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class QueryIntent {

    private final FollowUpIntent followUpIntent;
    private final List<String> projectSlugs;
    private final List<String> referencedClaimIds;
    private final AnswerSectionType selectedSectionType;

    public QueryIntent(
            FollowUpIntent followUpIntent,
            List<String> projectSlugs,
            List<String> referencedClaimIds,
            AnswerSectionType selectedSectionType
    ) {
        this.followUpIntent = Objects.requireNonNull(followUpIntent, "followUpIntent");
        this.projectSlugs = List.copyOf(projectSlugs);
        this.referencedClaimIds = List.copyOf(referencedClaimIds);
        this.selectedSectionType = selectedSectionType;
    }

    public FollowUpIntent getFollowUpIntent() { return followUpIntent; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getReferencedClaimIds() { return referencedClaimIds; }
    public AnswerSectionType getSelectedSectionType() { return selectedSectionType; }
}
