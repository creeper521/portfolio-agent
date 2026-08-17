package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;

import java.util.Objects;
import java.util.Optional;

/** Closed outcome of compiling an untrusted proposal; it never retains raw model content. */
public final class ProposalCompilationResult {

    private final SemanticTurnPlan plan;
    private final ReasonCode reasonCode;

    private ProposalCompilationResult(SemanticTurnPlan plan, ReasonCode reasonCode) {
        this.plan = plan;
        this.reasonCode = reasonCode;
        if ((plan == null) == (reasonCode == null)) {
            throw new IllegalArgumentException("compilation result must contain exactly one outcome");
        }
    }

    public static ProposalCompilationResult compiled(SemanticTurnPlan plan) {
        return new ProposalCompilationResult(Objects.requireNonNull(plan, "plan"), null);
    }

    public static ProposalCompilationResult rejected(ReasonCode reasonCode) {
        return new ProposalCompilationResult(null, Objects.requireNonNull(reasonCode, "reasonCode"));
    }

    public boolean isCompiled() { return plan != null; }
    public Optional<SemanticTurnPlan> getPlan() { return Optional.ofNullable(plan); }
    public ReasonCode getReasonCode() { return reasonCode; }

    public enum ReasonCode {
        SUBJECT_NOT_PUBLIC,
        SUBJECT_BASIS_INVALID,
        TASK_TYPE_UNSUPPORTED,
        PROPOSAL_INVALID
    }
}
