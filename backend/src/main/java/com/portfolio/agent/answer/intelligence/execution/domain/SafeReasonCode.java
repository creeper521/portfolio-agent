package com.portfolio.agent.answer.intelligence.execution.domain;

/** Closed, public-safe reasons used by the bounded execution module. */
public enum SafeReasonCode {
    SCOPE_CONFLICT,
    UNSUPPORTED_RETRIEVAL_PROFILE,
    CONTEXT_VERSION_UNAVAILABLE,
    REQUIRED_DEPENDENCY_UNAVAILABLE,
    TURN_BUDGET_UNAVAILABLE,
    EVIDENCE_NOT_FOUND,
    EVIDENCE_PARTIALLY_COVERED,
    OUTPUT_POLICY_BLOCKED,
    CAPABILITY_TEMPORARILY_UNAVAILABLE,
    EVIDENCE_INTEGRITY_FAILURE
}
