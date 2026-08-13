package com.portfolio.agent.answer.composition.assembly;

public final class PlanAssemblyException extends RuntimeException {
    public PlanAssemblyException(String safeCode) { super(safeCode); }
    public PlanAssemblyException(String safeCode, Throwable cause) { super(safeCode, cause); }
}
