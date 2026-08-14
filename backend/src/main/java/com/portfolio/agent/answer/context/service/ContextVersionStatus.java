package com.portfolio.agent.answer.context.service;

public enum ContextVersionStatus {
    CURRENT,
    REVALIDATED,
    STALE,
    INVALID_HANDLE,
    SUBJECT_UNAVAILABLE,
    STORE_UNAVAILABLE,
    SOURCE_CHANGED
}
