package com.portfolio.agent.selection.dto;

import com.portfolio.agent.selection.domain.PortfolioSubjectKind;

public final class PortfolioSelectionAlternativeResponse {

    private final String subjectId;
    private final PortfolioSubjectKind subjectType;
    private final String title;
    private final String summary;
    private final String route;
    private final String reason;

    public PortfolioSelectionAlternativeResponse(
            String subjectId,
            PortfolioSubjectKind subjectType,
            String title,
            String summary,
            String route,
            String reason) {
        this.subjectId = subjectId;
        this.subjectType = subjectType;
        this.title = title;
        this.summary = summary;
        this.route = route;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }
}
