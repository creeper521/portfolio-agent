package com.portfolio.agent.answer.intelligence.execution.capability;

import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioRetrievalCandidateSet;
import com.portfolio.agent.answer.intelligence.execution.domain.SafeReasonCode;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;

import java.util.Objects;
import java.util.Optional;

/** Closed result of one atomic capability attempt. */
public final class CapabilityExecutionResult {
    public enum Status { SUCCESS, EMPTY, UNAVAILABLE, TIMED_OUT, INTEGRITY_FAILED }

    private final Status status;
    private final PortfolioRetrievalCandidateSet candidateSet;
    private final ValidatedEvidenceBundle evidenceBundle;
    private final boolean degraded;
    private final SafeReasonCode safeReasonCode;

    private CapabilityExecutionResult(
            Status status, PortfolioRetrievalCandidateSet candidateSet,
            ValidatedEvidenceBundle evidenceBundle, boolean degraded,
            SafeReasonCode safeReasonCode) {
        this.status = Objects.requireNonNull(status, "status");
        this.candidateSet = candidateSet;
        this.evidenceBundle = evidenceBundle;
        this.degraded = degraded;
        this.safeReasonCode = safeReasonCode;
        if (status == Status.SUCCESS && (candidateSet == null || evidenceBundle == null)) {
            throw new IllegalArgumentException("success requires candidate set and evidence bundle");
        }
        if (status == Status.EMPTY && (candidateSet == null || evidenceBundle == null)) {
            throw new IllegalArgumentException("empty requires an atomic empty candidate set and bundle");
        }
        if ((status == Status.UNAVAILABLE || status == Status.TIMED_OUT)
                && safeReasonCode == null) {
            throw new IllegalArgumentException("availability failure requires a safe reason code");
        }
        if (status == Status.INTEGRITY_FAILED && safeReasonCode != SafeReasonCode.EVIDENCE_INTEGRITY_FAILURE) {
            throw new IllegalArgumentException("integrity failure requires its safe reason code");
        }
    }

    public static CapabilityExecutionResult success(
            PortfolioRetrievalCandidateSet candidateSet, ValidatedEvidenceBundle evidenceBundle) {
        return new CapabilityExecutionResult(Status.SUCCESS, candidateSet, evidenceBundle, false, null);
    }

    public static CapabilityExecutionResult empty(
            PortfolioRetrievalCandidateSet candidateSet, ValidatedEvidenceBundle evidenceBundle) {
        return new CapabilityExecutionResult(Status.EMPTY, candidateSet, evidenceBundle, false, null);
    }

    public static CapabilityExecutionResult unavailable(SafeReasonCode reasonCode) {
        return new CapabilityExecutionResult(Status.UNAVAILABLE, null, null, false,
                Objects.requireNonNull(reasonCode, "reasonCode"));
    }

    public static CapabilityExecutionResult timedOut() {
        return new CapabilityExecutionResult(Status.TIMED_OUT, null, null, false,
                SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE);
    }

    public static CapabilityExecutionResult integrityFailed() {
        return new CapabilityExecutionResult(Status.INTEGRITY_FAILED, null, null, false,
                SafeReasonCode.EVIDENCE_INTEGRITY_FAILURE);
    }

    public CapabilityExecutionResult asDegraded() {
        return new CapabilityExecutionResult(status, candidateSet, evidenceBundle, true, safeReasonCode);
    }

    public Status getStatus() { return status; }
    public Optional<PortfolioRetrievalCandidateSet> getCandidateSet() { return Optional.ofNullable(candidateSet); }
    public Optional<ValidatedEvidenceBundle> getEvidenceBundle() { return Optional.ofNullable(evidenceBundle); }
    public boolean isDegraded() { return degraded; }
    public Optional<SafeReasonCode> getSafeReasonCode() { return Optional.ofNullable(safeReasonCode); }
}
