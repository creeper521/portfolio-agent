package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;

import java.util.List;
import java.util.Objects;

/**
 * Sanitized HTTP answer result: status code, parsed public DTO fields, latency
 * and a closed failure classification. The response body is never retained.
 */
public final class EvalHttpResult {

    public enum FailureCode {
        NONE,
        HTTP_ERROR,
        INVALID_JSON,
        CLIENT_ERROR,
        TIMEOUT,
        TRANSPORT_FAILURE,
        POLICY_LEAK
    }

    private final int statusCode;
    private final AnswerResolution resolution;
    private final ConversationAnswerScope answerScope;
    private final GenerationMode generationMode;
    private final AnswerSource answerSource;
    private final String intentSource;
    private final String evidenceState;
    private final List<String> claimIds;
    private final List<String> evidenceIds;
    private final boolean degraded;
    private final String noticeCode;
    private final long durationMilliseconds;
    private final FailureCode failureCode;
    private final EvalAnswerShape answerShape;

    public EvalHttpResult(
            int statusCode,
            AnswerResolution resolution,
            ConversationAnswerScope answerScope,
            GenerationMode generationMode,
            AnswerSource answerSource,
            String intentSource,
            String evidenceState,
            List<String> claimIds,
            List<String> evidenceIds,
            boolean degraded,
            String noticeCode,
            long durationMilliseconds,
            FailureCode failureCode,
            EvalAnswerShape answerShape) {
        this.statusCode = statusCode;
        this.resolution = resolution;
        this.answerScope = answerScope;
        this.generationMode = generationMode;
        this.answerSource = answerSource;
        this.intentSource = intentSource;
        this.evidenceState = evidenceState;
        this.claimIds = List.copyOf(Objects.requireNonNull(claimIds, "claimIds"));
        this.evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        this.degraded = degraded;
        this.noticeCode = noticeCode;
        this.durationMilliseconds = durationMilliseconds;
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
        this.answerShape = Objects.requireNonNull(answerShape, "answerShape");
    }

    public int getStatusCode() { return statusCode; }
    public AnswerResolution getResolution() { return resolution; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public String getIntentSource() { return intentSource; }
    public String getEvidenceState() { return evidenceState; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public boolean isDegraded() { return degraded; }
    public String getNoticeCode() { return noticeCode; }
    public long getDurationMilliseconds() { return durationMilliseconds; }
    public FailureCode getFailureCode() { return failureCode; }
    public EvalAnswerShape getAnswerShape() { return answerShape; }
}
