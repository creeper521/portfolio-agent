package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import java.util.List;
import java.util.Objects;

/**
 * Executor-facing input. Oracle and grading data remain outside this boundary.
 */
public final class EvalExecutionInput {

    private final String caseId;
    private final List<EvalMessage> messages;
    private final EvalLayer layer;
    private final int trialIndex;

    public EvalExecutionInput(String caseId, List<EvalMessage> messages, EvalLayer layer,
                              int trialIndex) {
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.layer = Objects.requireNonNull(layer, "layer");
        if (trialIndex < 1) {
            throw new IllegalArgumentException("trialIndex must be at least 1");
        }
        this.trialIndex = trialIndex;
    }

    public String getCaseId() { return caseId; }
    public List<EvalMessage> getMessages() { return messages; }
    public EvalLayer getLayer() { return layer; }
    public int getTrialIndex() { return trialIndex; }
}
