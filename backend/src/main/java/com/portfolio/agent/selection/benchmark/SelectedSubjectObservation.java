package com.portfolio.agent.selection.benchmark;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

public final class SelectedSubjectObservation {
    private final String subjectId;
    private final String releaseVersion;
    private final Set<String> capabilities;
    private final boolean approvedEvidenceValid;
    private final boolean supported;

    @JsonCreator
    public SelectedSubjectObservation(
            @JsonProperty("subjectId") String subjectId,
            @JsonProperty("releaseVersion") String releaseVersion,
            @JsonProperty("capabilities") Set<String> capabilities,
            @JsonProperty("approvedEvidenceValid") boolean approvedEvidenceValid,
            @JsonProperty("supported") boolean supported) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        this.approvedEvidenceValid = approvedEvidenceValid;
        this.supported = supported;
    }

    public String getSubjectId() { return subjectId; }
    public String getReleaseVersion() { return releaseVersion; }
    public Set<String> getCapabilities() { return capabilities; }
    public boolean isApprovedEvidenceValid() { return approvedEvidenceValid; }
    public boolean isSupported() { return supported; }
}
