package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.evaluation.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.ConversationAnswerScope;
import com.portfolio.agent.common.observability.GenerationMode;
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
    private final EvalAnswerShape answerShape;
    private final EvalSemanticTurnShape semanticTurnShape;
    private final boolean fallbackUsed;
    private final boolean providerInvoked;

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
            EvalProviderUsage providerUsage,
            EvalAnswerShape answerShape,
            boolean fallbackUsed,
            boolean providerInvoked
    ) {
        this(caseId, layer, trialIndex, status, selectedProjectSlug, selectedCaseSlug,
                selectedClaimIds, selectedEvidenceIds, selectedChunkIds, resolution,
                answerScope, generationMode, answerSource, reasonCodes, durationMilliseconds,
                providerUsage, answerShape, EvalSemanticTurnShape.empty(), fallbackUsed,
                providerInvoked);
    }

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
            EvalProviderUsage providerUsage,
            EvalAnswerShape answerShape,
            EvalSemanticTurnShape semanticTurnShape,
            boolean fallbackUsed,
            boolean providerInvoked
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
        this.answerShape = Objects.requireNonNull(answerShape, "answerShape");
        this.semanticTurnShape = Objects.requireNonNull(semanticTurnShape, "semanticTurnShape");
        this.fallbackUsed = fallbackUsed;
        this.providerInvoked = providerInvoked;
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * Builder for the many-field observation; required fields are caseId,
     * layer, trialIndex and status, everything else defaults to the neutral
     * value (empty lists, null slugs, unavailable usage, empty shape).
     */
    public static Builder builder(String caseId, EvalLayer layer,
                                  int trialIndex, EvalObservationStatus status) {
        return new Builder(caseId, layer, trialIndex, status);
    }

    public static final class Builder {
        private final String caseId;
        private final EvalLayer layer;
        private final int trialIndex;
        private final EvalObservationStatus status;
        private String selectedProjectSlug;
        private String selectedCaseSlug;
        private List<String> selectedClaimIds = List.of();
        private List<String> selectedEvidenceIds = List.of();
        private List<String> selectedChunkIds = List.of();
        private AnswerResolution resolution;
        private ConversationAnswerScope answerScope;
        private GenerationMode generationMode;
        private AnswerSource answerSource;
        private List<String> reasonCodes = List.of();
        private long durationMilliseconds;
        private EvalProviderUsage providerUsage = EvalProviderUsage.unavailable();
        private EvalAnswerShape answerShape = EvalAnswerShape.empty();
        private EvalSemanticTurnShape semanticTurnShape = EvalSemanticTurnShape.empty();
        private boolean fallbackUsed;
        private boolean providerInvoked;

        private Builder(String caseId, EvalLayer layer,
                        int trialIndex, EvalObservationStatus status) {
            this.caseId = caseId;
            this.layer = layer;
            this.trialIndex = trialIndex;
            this.status = status;
        }

        public Builder selectedProjectSlug(String value) {
            this.selectedProjectSlug = value;
            return this;
        }

        public Builder selectedCaseSlug(String value) {
            this.selectedCaseSlug = value;
            return this;
        }

        public Builder selectedClaimIds(List<String> value) {
            this.selectedClaimIds = value == null ? List.of() : value;
            return this;
        }

        public Builder selectedEvidenceIds(List<String> value) {
            this.selectedEvidenceIds = value == null ? List.of() : value;
            return this;
        }

        public Builder selectedChunkIds(List<String> value) {
            this.selectedChunkIds = value == null ? List.of() : value;
            return this;
        }

        public Builder resolution(AnswerResolution value) {
            this.resolution = value;
            return this;
        }

        public Builder answerScope(ConversationAnswerScope value) {
            this.answerScope = value;
            return this;
        }

        public Builder generationMode(GenerationMode value) {
            this.generationMode = value;
            return this;
        }

        public Builder answerSource(AnswerSource value) {
            this.answerSource = value;
            return this;
        }

        public Builder reasonCodes(List<String> value) {
            this.reasonCodes = value == null ? List.of() : value;
            return this;
        }

        public Builder durationMilliseconds(long value) {
            this.durationMilliseconds = value;
            return this;
        }

        public Builder providerUsage(EvalProviderUsage value) {
            this.providerUsage = value == null
                    ? EvalProviderUsage.unavailable() : value;
            return this;
        }

        public Builder answerShape(EvalAnswerShape value) {
            this.answerShape = value == null ? EvalAnswerShape.empty() : value;
            return this;
        }

        public Builder semanticTurnShape(EvalSemanticTurnShape value) {
            this.semanticTurnShape = value == null ? EvalSemanticTurnShape.empty() : value;
            return this;
        }

        public Builder fallbackUsed(boolean value) {
            this.fallbackUsed = value;
            return this;
        }

        public Builder providerInvoked(boolean value) {
            this.providerInvoked = value;
            return this;
        }

        public EvalObservation build() {
            return new EvalObservation(
                    caseId, layer, trialIndex, status,
                    selectedProjectSlug, selectedCaseSlug,
                    selectedClaimIds, selectedEvidenceIds, selectedChunkIds,
                    resolution, answerScope, generationMode, answerSource,
                    reasonCodes, durationMilliseconds,
                    providerUsage, answerShape, semanticTurnShape, fallbackUsed, providerInvoked);
        }
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
    public EvalAnswerShape getAnswerShape() { return answerShape; }
    public EvalSemanticTurnShape getSemanticTurnShape() { return semanticTurnShape; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public boolean isProviderInvoked() { return providerInvoked; }
}


