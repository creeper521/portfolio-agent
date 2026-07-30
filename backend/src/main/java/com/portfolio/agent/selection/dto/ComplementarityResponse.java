package com.portfolio.agent.selection.dto;

public final class ComplementarityResponse {

    private final String leftSubjectId;
    private final String rightSubjectId;
    private final String reason;

    public ComplementarityResponse(String leftSubjectId, String rightSubjectId, String reason) {
        this.leftSubjectId = leftSubjectId;
        this.rightSubjectId = rightSubjectId;
        this.reason = reason;
    }

    public String getLeftSubjectId() {
        return leftSubjectId;
    }

    public String getRightSubjectId() {
        return rightSubjectId;
    }

    public String getReason() {
        return reason;
    }
}
