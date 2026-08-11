package com.portfolio.agent.answer.domain;

public enum AnswerResolution {
    ANSWERED,
    AWAITING_CONFIRMATION,
    NEEDS_CLARIFICATION,
    NOT_SUPPORTED,
    CAPABILITY_UNAVAILABLE,
    BOUNDARY,
    REJECTED,
    INVALID_INPUT
}
