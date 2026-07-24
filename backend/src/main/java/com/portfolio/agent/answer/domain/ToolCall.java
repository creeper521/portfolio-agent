package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ToolCall {

    private final ToolKind kind;
    private final List<String> projectSlugs;
    private final List<String> caseSlugs;
    private final List<String> claimIds;
    private final AnswerSectionType sectionType;

    public ToolCall(
            ToolKind kind,
            List<String> projectSlugs,
            List<String> caseSlugs,
            List<String> claimIds,
            AnswerSectionType sectionType
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.projectSlugs = List.copyOf(projectSlugs);
        this.caseSlugs = List.copyOf(caseSlugs);
        this.claimIds = List.copyOf(claimIds);
        this.sectionType = sectionType;
    }

    public ToolCall(
            ToolKind kind,
            List<String> projectSlugs,
            List<String> claimIds,
            AnswerSectionType sectionType
    ) {
        this(kind, projectSlugs, List.of(), claimIds, sectionType);
    }

    public ToolKind getKind() { return kind; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getCaseSlugs() { return caseSlugs; }
    public List<String> getClaimIds() { return claimIds; }
    public AnswerSectionType getSectionType() { return sectionType; }
}
