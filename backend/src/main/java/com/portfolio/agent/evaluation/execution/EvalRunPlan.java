package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import java.util.List;
import java.util.Objects;

public final class EvalRunPlan {

    private final EvalRunMode mode;
    private final List<EvalCase> plannedCases;
    private final boolean offlinePassRequired;
    private final boolean blocking;

    public EvalRunPlan(EvalRunMode mode, List<EvalCase> plannedCases,
                       boolean offlinePassRequired, boolean blocking) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.plannedCases = List.copyOf(Objects.requireNonNull(plannedCases, "plannedCases"));
        this.offlinePassRequired = offlinePassRequired;
        this.blocking = blocking;
    }

    public EvalRunMode getMode() { return mode; }
    public List<EvalCase> getPlannedCases() { return plannedCases; }
    public boolean isOfflinePassRequired() { return offlinePassRequired; }
    public boolean isBlocking() { return blocking; }
}
