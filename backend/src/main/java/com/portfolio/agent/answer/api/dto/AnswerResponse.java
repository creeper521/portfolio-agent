package com.portfolio.agent.answer.api.dto;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;

import java.util.List;
import java.util.Objects;

public final class AnswerResponse {

    private final String requestId;
    private final AnswerMode answerMode;
    private final boolean matched;
    private final boolean fallback;
    private final AnswerPayload answer;
    private final List<EvidenceResponse> evidence;
    private final List<String> suggestedQuestions;

    public AnswerResponse(
            String requestId,
            AnswerMode answerMode,
            boolean matched,
            boolean fallback,
            AnswerPayload answer,
            List<EvidenceResponse> evidence,
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

    public static AnswerResponse from(String requestId, AnswerResult result) {
        List<AnswerSectionResponse> sections = result.getSections().stream()
                .map(section -> new AnswerSectionResponse(section.getType(), section.getContent()))
                .toList();
        List<EvidenceResponse> evidence = result.getEvidence().stream()
                .map(AnswerResponse::toEvidenceResponse)
                .toList();

        return new AnswerResponse(
                requestId,
                result.getAnswerMode(),
                result.isMatched(),
                result.isFallback(),
                new AnswerPayload(result.getTitle(), sections),
                evidence,
                result.getSuggestedQuestions()
        );
    }

    private static EvidenceResponse toEvidenceResponse(AnswerEvidence evidence) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getTitle(),
                EvidenceType.valueOf(evidence.getType()),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                EvidenceStatus.valueOf(evidence.getPublicStatus()),
                evidence.isRawContentPublic()
        );
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

    public List<EvidenceResponse> getEvidence() {
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
