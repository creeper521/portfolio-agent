package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import java.util.List;
import java.util.Objects;

/**
 * Executor-facing input. Oracle and grading data remain outside this boundary:
 * only the resolved subject references (parsed by the orchestration layer from
 * the case maintenance metadata) travel with the input, never the oracle
 * expectations themselves.
 */
public final class EvalExecutionInput {

    private final String caseId;
    private final List<EvalMessage> messages;
    private final EvalLayer layer;
    private final int trialIndex;
    private final List<EvalSubjectRef> resolvedSubjects;

    public EvalExecutionInput(String caseId, List<EvalMessage> messages, EvalLayer layer,
                              int trialIndex) {
        this(caseId, messages, layer, trialIndex, List.of());
    }

    public EvalExecutionInput(String caseId, List<EvalMessage> messages, EvalLayer layer,
                              int trialIndex, List<EvalSubjectRef> resolvedSubjects) {
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.layer = Objects.requireNonNull(layer, "layer");
        if (trialIndex < 1) {
            throw new IllegalArgumentException("trialIndex must be at least 1");
        }
        this.trialIndex = trialIndex;
        this.resolvedSubjects = List.copyOf(
                Objects.requireNonNull(resolvedSubjects, "resolvedSubjects"));
    }

    public String getCaseId() { return caseId; }
    public List<EvalMessage> getMessages() { return messages; }
    public EvalLayer getLayer() { return layer; }
    public int getTrialIndex() { return trialIndex; }
    public List<EvalSubjectRef> getResolvedSubjects() { return resolvedSubjects; }
}
