package com.portfolio.agent.selection.benchmark;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public final class PortfolioSelectionObservation {
    private final BenchmarkRoute route;
    private final String caseId;
    private final String releaseVersion;
    private final List<String> rankedCandidateSubjectIds;
    private final List<String> selectedSubjectIds;
    private final long elapsedMilliseconds;
    private final String retrievalMode;
    private final String selectionMode;
    private final ObservationState state;
    private final String errorCode;
    private final boolean sufficient;
    private final List<SelectedSubjectObservation> selectedSubjects;

    @JsonCreator
    public PortfolioSelectionObservation(
            @JsonProperty("route") BenchmarkRoute route,
            @JsonProperty("caseId") String caseId,
            @JsonProperty("releaseVersion") String releaseVersion,
            @JsonProperty("rankedCandidateSubjectIds") List<String> rankedCandidateSubjectIds,
            @JsonProperty("selectedSubjectIds") List<String> selectedSubjectIds,
            @JsonProperty("elapsedMilliseconds") long elapsedMilliseconds,
            @JsonProperty("retrievalMode") String retrievalMode,
            @JsonProperty("selectionMode") String selectionMode,
            @JsonProperty("state") ObservationState state,
            @JsonProperty("errorCode") String errorCode,
            @JsonProperty("sufficient") boolean sufficient,
            @JsonProperty("selectedSubjects") List<SelectedSubjectObservation> selectedSubjects) {
        this.route = Objects.requireNonNull(route, "route");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.rankedCandidateSubjectIds = List.copyOf(
                Objects.requireNonNullElse(rankedCandidateSubjectIds, List.of()));
        this.selectedSubjectIds = List.copyOf(Objects.requireNonNullElse(selectedSubjectIds, List.of()));
        if (elapsedMilliseconds < 0) {
            throw new IllegalArgumentException("elapsedMilliseconds must be non-negative");
        }
        this.elapsedMilliseconds = elapsedMilliseconds;
        this.retrievalMode = retrievalMode;
        this.selectionMode = selectionMode;
        this.state = Objects.requireNonNull(state, "state");
        this.errorCode = errorCode;
        this.sufficient = sufficient;
        this.selectedSubjects = List.copyOf(Objects.requireNonNullElse(selectedSubjects, List.of()));
        if (state == ObservationState.AVAILABLE
                && !this.selectedSubjectIds.equals(this.selectedSubjects.stream()
                        .map(SelectedSubjectObservation::getSubjectId).toList())) {
            throw new IllegalArgumentException("selected subject details must match selectedSubjectIds");
        }
    }

    public BenchmarkRoute getRoute() { return route; }
    public String getCaseId() { return caseId; }
    public String getReleaseVersion() { return releaseVersion; }
    public List<String> getRankedCandidateSubjectIds() { return rankedCandidateSubjectIds; }
    public List<String> getSelectedSubjectIds() { return selectedSubjectIds; }
    public long getElapsedMilliseconds() { return elapsedMilliseconds; }
    public String getRetrievalMode() { return retrievalMode; }
    public String getSelectionMode() { return selectionMode; }
    public ObservationState getState() { return state; }
    public String getErrorCode() { return errorCode; }
    public boolean isSufficient() { return sufficient; }
    public List<SelectedSubjectObservation> getSelectedSubjects() { return selectedSubjects; }
}
