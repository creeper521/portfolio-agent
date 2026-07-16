package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerMode;

import java.util.List;
import java.util.Objects;

public final class AnswerResponse {

    private final String requestId;
    private final AnswerMode answerMode;
    private final boolean matched;
    private final boolean fallback;
    private final AnswerPayload answer;
    private final List<AnswerEvidenceResponse> evidence;
    private final List<String> suggestedQuestions;

    public AnswerResponse(
            String requestId,
            AnswerMode answerMode,
            boolean matched,
            boolean fallback,
            AnswerPayload answer,
            List<AnswerEvidenceResponse> evidence,
            List<String> suggestedQuestions
    ) {
        this.requestId = requestId;
        this.answerMode = answerMode;
        this.matched = matched;
        this.fallback = fallback;
        this.answer = answer;
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
    }

    public String getRequestId() {
        return requestId;
    }

    public AnswerMode getAnswerMode() {
        return answerMode;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isFallback() {
        return fallback;
    }

    public AnswerPayload getAnswer() {
        return answer;
    }

    public List<AnswerEvidenceResponse> getEvidence() {
        return evidence;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerResponse that)) {
            return false;
        }
        return matched == that.matched
                && fallback == that.fallback
                && Objects.equals(requestId, that.requestId)
                && answerMode == that.answerMode
                && Objects.equals(answer, that.answer)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, answerMode, matched, fallback, answer, evidence,
                suggestedQuestions);
    }

    @Override
    public String toString() {
        return "AnswerResponse{" +
                "requestId='" + requestId + '\'' +
                ", answerMode=" + answerMode +
                ", matched=" + matched +
                ", fallback=" + fallback +
                ", answer=" + answer +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                '}';
    }
}
