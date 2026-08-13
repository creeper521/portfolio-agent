package com.portfolio.agent.evaluation.domain;

/** Closed, content-free P4 correctness checks. */
public enum P4SafetyCheck {
    SUPPORT_BINDING,
    PROTECTED_ATOM,
    QUALIFIER_PRESERVATION,
    REQUIRED_COVERAGE,
    STRICT_SCHEMA,
    ATOMIC_FALLBACK
}
