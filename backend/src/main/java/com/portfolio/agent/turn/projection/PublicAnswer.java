package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PublicAnswer {
    private final Resolution resolution;
    private final String contentReleaseId;
    private final List<AnswerGoalResult> goalResults;
    private final PublicSourceCatalog sourceCatalog;
    private final List<PublicSupport.Kind> sourceComposition;
    private final List<SuggestedAction> suggestedActions;
    private final ClarificationChallenge localClarification;

    public PublicAnswer(
            Resolution resolution, String contentReleaseId,
            List<AnswerGoalResult> goalResults, PublicSourceCatalog sourceCatalog,
            List<PublicSupport.Kind> sourceComposition,
            List<SuggestedAction> suggestedActions,
            ClarificationChallenge localClarification) {
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        if (contentReleaseId == null || contentReleaseId.isBlank()) {
            throw new IllegalArgumentException("contentReleaseId is required");
        }
        this.contentReleaseId = contentReleaseId.trim();
        this.goalResults = List.copyOf(Objects.requireNonNull(goalResults, "goalResults"));
        if (this.goalResults.isEmpty()) throw new IllegalArgumentException("goalResults are required");
        this.sourceCatalog = Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        this.sourceComposition = List.copyOf(
                Objects.requireNonNull(sourceComposition, "sourceComposition"));
        this.suggestedActions = List.copyOf(
                Objects.requireNonNull(suggestedActions, "suggestedActions"));
        this.localClarification = localClarification;
        validateResolution();
        validateSources();
        validateClarification();
    }

    public Resolution getResolution() { return resolution; }
    public String getContentReleaseId() { return contentReleaseId; }
    public List<AnswerGoalResult> getGoalResults() { return goalResults; }
    public PublicSourceCatalog getSourceCatalog() { return sourceCatalog; }
    public List<PublicSupport.Kind> getSourceComposition() { return sourceComposition; }
    public List<SuggestedAction> getSuggestedActions() { return suggestedActions; }
    public ClarificationChallenge getLocalClarification() { return localClarification; }
    public enum Resolution { COMPLETE, PARTIAL, NO_RESULT }

    private void validateResolution() {
        long produced = goalResults.stream().filter(value ->
                value.getCoverage() != AnswerGoalResult.Coverage.NONE).count();
        boolean allFull = goalResults.stream().allMatch(value ->
                value.getCoverage() == AnswerGoalResult.Coverage.FULL);
        if (resolution == Resolution.COMPLETE && !allFull
                || resolution == Resolution.PARTIAL && (produced == 0 || allFull)
                || resolution == Resolution.NO_RESULT && produced != 0) {
            throw new IllegalArgumentException("answer resolution invariant failed");
        }
    }

    private void validateSources() {
        Set<String> catalogKeys = sourceCatalog.getSources().stream()
                .map(PublicSourceCatalog.Source::getKey).collect(java.util.stream.Collectors.toSet());
        for (AnswerGoalResult goal : goalResults) {
            if (goal.getPresentation() instanceof PublicPresentation.Sectioned sectioned) {
                sectioned.getSections().forEach(section -> requireKeys(section.getSupport(), catalogKeys));
            }
            if (goal.getPresentation() instanceof PublicPresentation.Recommendation recommendation) {
                recommendation.getItems().forEach(item -> requireKeys(item.getSupport(), catalogKeys));
                recommendation.getSupportingSections().forEach(
                        section -> requireKeys(section.getSupport(), catalogKeys));
            }
        }
    }

    private void requireKeys(PublicSupport support, Set<String> catalogKeys) {
        if (!catalogKeys.containsAll(support.getPublicSourceKeys())) {
            throw new IllegalArgumentException("presentation references an unknown public source");
        }
    }

    private void validateClarification() {
        if (localClarification == null) return;
        if (localClarification.getAffectedGoalIds().isEmpty()) {
            throw new IllegalArgumentException("local clarification requires affected goals");
        }
        Set<String> goalIds = goalResults.stream().map(AnswerGoalResult::getGoalId)
                .collect(java.util.stream.Collectors.toSet());
        if (!goalIds.containsAll(localClarification.getAffectedGoalIds())) {
            throw new IllegalArgumentException("local clarification references unknown goals");
        }
    }
}
