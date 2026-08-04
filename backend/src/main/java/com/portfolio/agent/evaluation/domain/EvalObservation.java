package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import java.util.List;
import java.util.Objects;

/**
 * Safe, structured result of one evaluation executor call.
 *
 * <p>This value deliberately excludes the original prompt, raw answer text, local paths,
 * private evidence, and oracle expectations.</p>
 */
public final class EvalObservation {

    private final String caseId;
    private final EvalLayer layer;
    private final int trialIndex;
    private final EvalObservationStatus status;
    private final String selectedProjectSlug;
    private final String selectedCaseSlug;
    private final List<String> selectedClaimIds;
    private final List<String> selectedEvidenceIds;
    private final List<String> selectedChunkIds;
    private final AnswerResolution resolution;
    private final ConversationAnswerScope answerScope;
    private final GenerationMode generationMode;
    private final AnswerSource answerSource;
    private final List<String> reasonCodes;
    private final long durationMilliseconds;
    private final EvalProviderUsage providerUsage;

    public EvalObservation(
            String caseId,
            EvalLayer layer,
            int trialIndex,
            EvalObservationStatus status,
            String selectedProjectSlug,
            String selectedCaseSlug,
            List<String> selectedClaimIds,
            List<String> selectedEvidenceIds,
            List<String> selectedChunkIds,
            AnswerResolution resolution,
            ConversationAnswerScope answerScope,
            GenerationMode generationMode,
            AnswerSource answerSource,
            List<String> reasonCodes,
            long durationMilliseconds,
            EvalProviderUsage providerUsage
    ) {
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.layer = Objects.requireNonNull(layer, "layer");
        if (trialIndex < 1) {
            throw new IllegalArgumentException("trialIndex must be at least 1");
        }
        this.trialIndex = trialIndex;
        this.status = Objects.requireNonNull(status, "status");
        this.selectedProjectSlug = selectedProjectSlug;
        this.selectedCaseSlug = selectedCaseSlug;
        this.selectedClaimIds = immutable(selectedClaimIds);
        this.selectedEvidenceIds = immutable(selectedEvidenceIds);
        this.selectedChunkIds = immutable(selectedChunkIds);
        this.resolution = resolution;
        this.answerScope = answerScope;
        this.generationMode = generationMode;
        this.answerSource = answerSource;
        this.reasonCodes = immutable(reasonCodes);
        this.durationMilliseconds = nonNegative(durationMilliseconds, "durationMilliseconds");
        this.providerUsage = Objects.requireNonNull(providerUsage, "providerUsage");
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static long nonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    public String getCaseId() { return caseId; }
    public EvalLayer getLayer() { return layer; }
    public int getTrialIndex() { return trialIndex; }
    public EvalObservationStatus getStatus() { return status; }
    public String getSelectedProjectSlug() { return selectedProjectSlug; }
    public String getSelectedCaseSlug() { return selectedCaseSlug; }
    public List<String> getSelectedClaimIds() { return selectedClaimIds; }
    public List<String> getSelectedEvidenceIds() { return selectedEvidenceIds; }
    public List<String> getSelectedChunkIds() { return selectedChunkIds; }
    public AnswerResolution getResolution() { return resolution; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public List<String> getReasonCodes() { return reasonCodes; }
    public long getDurationMilliseconds() { return durationMilliseconds; }
    public EvalProviderUsage getProviderUsage() { return providerUsage; }
}
