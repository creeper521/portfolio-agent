package com.portfolio.agent.evaluation.execution;

import java.util.Objects;

public final class EvalHttpRequest {

    private final String baseUrl;
    private final String turnId;
    private final String caseId;
    private final String question;

    public EvalHttpRequest(
            String baseUrl,
            String turnId,
            String caseId,
            String question) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.turnId = Objects.requireNonNull(turnId, "turnId");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.question = Objects.requireNonNull(question, "question");
    }

    public String getBaseUrl() { return baseUrl; }
    public String getTurnId() { return turnId; }
    public String getCaseId() { return caseId; }
    public String getQuestion() { return question; }
}
