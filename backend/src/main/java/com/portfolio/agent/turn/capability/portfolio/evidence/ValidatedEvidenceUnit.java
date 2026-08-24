package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;

import java.util.Objects;
import java.util.Set;

public final class ValidatedEvidenceUnit {
    private final String subjectId;
    private final AnswerClaimProjection claim;
    private final PublicSourceReferenceValue sourceReference;
    private final String subjectTitle;
    private final String careerTrack;
    private final Set<String> capabilityCodes;
    public ValidatedEvidenceUnit(
            String subjectId, AnswerClaimProjection claim, PublicSourceReferenceValue sourceReference) {
        this(subjectId, subjectId, null, Set.of(), claim, sourceReference);
    }
    public ValidatedEvidenceUnit(
            String subjectId, String subjectTitle, String careerTrack,
            Set<String> capabilityCodes, AnswerClaimProjection claim,
            PublicSourceReferenceValue sourceReference) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectTitle = Objects.requireNonNull(subjectTitle, "subjectTitle");
        this.careerTrack = careerTrack;
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(
                capabilityCodes, "capabilityCodes"));
        this.claim = Objects.requireNonNull(claim, "claim");
        this.sourceReference = Objects.requireNonNull(sourceReference, "sourceReference");
    }
    public String getSubjectId() { return subjectId; }
    public String getSubjectTitle() { return subjectTitle; }
    public String getCareerTrack() { return careerTrack; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public AnswerClaimProjection getClaim() { return claim; }
    public PublicSourceReferenceValue getSourceReference() { return sourceReference; }
}
