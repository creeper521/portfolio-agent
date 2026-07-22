package com.portfolio.agent.answer.domain;

import java.util.List;

public final class AnswerPlanSection {

    private final AnswerSectionType type;
    private final String canonicalTitle;
    private final String canonicalContent;
    private final List<String> allowedClaimIds;
    private final List<String> allowedEvidenceIds;

    public AnswerPlanSection(
            AnswerSectionType type,
            String canonicalTitle,
            String canonicalContent,
            List<String> allowedClaimIds,
            List<String> allowedEvidenceIds
    ) {
        this.type = type;
        this.canonicalTitle = canonicalTitle;
        this.canonicalContent = canonicalContent;
        this.allowedClaimIds = List.copyOf(allowedClaimIds);
        this.allowedEvidenceIds = List.copyOf(allowedEvidenceIds);
    }

    public AnswerSectionType getType() { return type; }
    public String getCanonicalTitle() { return canonicalTitle; }
    public String getCanonicalContent() { return canonicalContent; }
    public List<String> getAllowedClaimIds() { return allowedClaimIds; }
    public List<String> getAllowedEvidenceIds() { return allowedEvidenceIds; }
}
