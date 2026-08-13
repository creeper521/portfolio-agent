package com.portfolio.agent.answer.composition.domain;

import java.util.List;
import java.util.Objects;

/** Closed intent projection. It cannot represent a question, goal label, history or context. */
public final class ExpressionIntent {
    private final TaskKind taskKind;
    private final FocusMode focusMode;
    private final List<RequestedFacet> requestedFacets;
    private final List<RequestedDimension> requestedDimensions;
    private final List<RequestedOutput> requestedOutputs;
    private final AudienceRole audienceRole;
    private final ResponseDepth responseDepth;
    private final LocaleCode locale;
    private final TaskSource taskSource;
    private final List<String> subjectDisplayLabels;

    public ExpressionIntent(TaskKind taskKind, FocusMode focusMode,
            List<RequestedFacet> requestedFacets,
            List<RequestedDimension> requestedDimensions,
            List<RequestedOutput> requestedOutputs,
            AudienceRole audienceRole, ResponseDepth responseDepth, LocaleCode locale,
            TaskSource taskSource, List<String> subjectDisplayLabels) {
        this.taskKind = Objects.requireNonNull(taskKind, "taskKind");
        this.focusMode = Objects.requireNonNull(focusMode, "focusMode");
        this.requestedFacets = DomainValues.distinctCopy(requestedFacets, "requestedFacets");
        this.requestedDimensions = DomainValues.distinctCopy(
                requestedDimensions, "requestedDimensions");
        this.requestedOutputs = DomainValues.distinctCopy(requestedOutputs, "requestedOutputs");
        this.audienceRole = Objects.requireNonNull(audienceRole, "audienceRole");
        this.responseDepth = Objects.requireNonNull(responseDepth, "responseDepth");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.taskSource = Objects.requireNonNull(taskSource, "taskSource");
        this.subjectDisplayLabels = DomainValues.distinctTextCopy(
                subjectDisplayLabels, "subjectDisplayLabels");
        if (this.subjectDisplayLabels.isEmpty()) {
            throw new IllegalArgumentException("subjectDisplayLabels must not be empty");
        }
    }

    public TaskKind getTaskKind() { return taskKind; }
    public FocusMode getFocusMode() { return focusMode; }
    public List<RequestedFacet> getRequestedFacets() { return requestedFacets; }
    public List<RequestedDimension> getRequestedDimensions() { return requestedDimensions; }
    public List<RequestedOutput> getRequestedOutputs() { return requestedOutputs; }
    public AudienceRole getAudienceRole() { return audienceRole; }
    public ResponseDepth getResponseDepth() { return responseDepth; }
    public LocaleCode getLocale() { return locale; }
    public TaskSource getTaskSource() { return taskSource; }
    public List<String> getSubjectDisplayLabels() { return subjectDisplayLabels; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExpressionIntent that)) return false;
        return taskKind == that.taskKind && focusMode == that.focusMode
                && requestedFacets.equals(that.requestedFacets)
                && requestedDimensions.equals(that.requestedDimensions)
                && requestedOutputs.equals(that.requestedOutputs)
                && audienceRole == that.audienceRole && responseDepth == that.responseDepth
                && locale == that.locale && taskSource == that.taskSource
                && subjectDisplayLabels.equals(that.subjectDisplayLabels);
    }

    @Override public int hashCode() {
        return Objects.hash(taskKind, focusMode, requestedFacets, requestedDimensions,
                requestedOutputs, audienceRole, responseDepth, locale, taskSource,
                subjectDisplayLabels);
    }

    @Override
    public String toString() {
        return "ExpressionIntent{taskKind=" + taskKind + ", focusMode=" + focusMode
                + ", facetCount=" + requestedFacets.size()
                + ", dimensionCount=" + requestedDimensions.size()
                + ", outputCount=" + requestedOutputs.size()
                + ", subjectCount=" + subjectDisplayLabels.size() + '}';
    }
}
