package com.portfolio.agent.selection.dto;

import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import java.util.Set;
import java.util.List;

public final class PortfolioSelectionItemResponse {

    private final String subjectId;
    private final PortfolioSubjectKind subjectType;
    private final String title;
    private final String summary;
    private final String route;
    private final String careerTrack;
    private final Set<String> capabilities;
    private final String selectionReason;
    private final List<EvidenceReferenceResponse> evidenceRefs;

    public PortfolioSelectionItemResponse(
            String subjectId,
            PortfolioSubjectKind subjectType,
            String title,
            String summary,
            String route,
            String careerTrack,
            Set<String> capabilities,
            String selectionReason,
            List<EvidenceReferenceResponse> evidenceRefs) {
        this.subjectId = subjectId;
        this.subjectType = subjectType;
        this.title = title;
        this.summary = summary;
        this.route = route;
        this.careerTrack = careerTrack;
        this.capabilities = Set.copyOf(capabilities);
        this.selectionReason = selectionReason;
        this.evidenceRefs = List.copyOf(evidenceRefs);
    }

    public String getSubjectId() {
        return subjectId;
    }

    public PortfolioSubjectKind getSubjectType() {
        return subjectType;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getRoute() {
        return route;
    }

    public String getCareerTrack() {
        return careerTrack;
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }

    public String getSelectionReason() {
        return selectionReason;
    }

    public List<EvidenceReferenceResponse> getEvidenceRefs() {
        return evidenceRefs;
    }
}
