package com.portfolio.agent.answer.composition.validation;

public final class GroundingValidationException extends RuntimeException {
    public enum Code { STRUCTURE, ALIAS_SCOPE, REQUIRED_COVERAGE, PROTECTED_ATOM, QUALIFIER, TASK_SCOPE }
    private final Code code;
    public GroundingValidationException(Code code) {
        super("expression grounding rejected: " + code.name());
        this.code = code;
    }
    public Code getCode() { return code; }
}
