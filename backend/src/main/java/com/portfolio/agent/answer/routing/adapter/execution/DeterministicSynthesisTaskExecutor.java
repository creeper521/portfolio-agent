package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.service.SemanticTaskExecutor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded deterministic synthesis that only reuses successful upstream payloads and provenance. */
public final class DeterministicSynthesisTaskExecutor implements SemanticTaskExecutor {

    @Override
    public TaskSourceDomain getSourceDomain() {
        return TaskSourceDomain.SYNTHESIS;
    }

    @Override
    public TaskOutcome execute(SemanticTaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        return execute(context.getSemanticTask(), context.getDependencyOutcomes());
    }

    /** Compatibility adapter retained until the P3-E production cutover. */
    public TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(availableDependencyOutcomes, "availableDependencyOutcomes");
        if (task.getSourceDomain() != TaskSourceDomain.SYNTHESIS
                || !(task.getParameters() instanceof SemanticTaskParameters.Synthesis parameters)) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.SYNTHESIS,
                    false, "SYNTHESIS_TASK_UNSUPPORTED");
        }
        List<TaskOutcome> inputs = matchingSuccessfulInputs(parameters, availableDependencyOutcomes);
        if (inputs.size() < 2) {
            return TaskOutcome.blocked(task.getTaskId(), TaskSourceDomain.SYNTHESIS,
                    "SYNTHESIS_INPUT_INSUFFICIENT");
        }
        TaskResultProvenance provenance = provenance(inputs);
        List<String> blocks = blocks(inputs);
        if (blocks.isEmpty()) {
            return TaskOutcome.blocked(task.getTaskId(), TaskSourceDomain.SYNTHESIS,
                    "SYNTHESIS_INPUT_INSUFFICIENT");
        }
        boolean degraded = inputs.stream().anyMatch(TaskOutcome::isDegraded);
        TaskOutcome.TaskEvidenceState evidenceState = inputs.stream().anyMatch(
                outcome -> outcome.getEvidenceState() == TaskOutcome.TaskEvidenceState.PARTIAL)
                ? TaskOutcome.TaskEvidenceState.PARTIAL
                : TaskOutcome.TaskEvidenceState.SUFFICIENT;
        TaskResultPayload.SynthesisResultPayload payload =
                new TaskResultPayload.SynthesisResultPayload(blocks, provenance);
        return TaskOutcome.create(
                task.getTaskId(),
                TaskOutcome.TaskExecutionStatus.SUCCEEDED,
                TaskOutcome.TaskResolution.ANSWERED,
                evidenceState,
                degraded,
                Set.of(),
                null,
                TaskSourceDomain.SYNTHESIS,
                provenance,
                payload);
    }

    private List<TaskOutcome> matchingSuccessfulInputs(
            SemanticTaskParameters.Synthesis parameters,
            List<TaskOutcome> availableDependencyOutcomes) {
        List<TaskOutcome> inputs = new ArrayList<>();
        for (String sourceTaskId : parameters.getSourceTaskIds()) {
            TaskOutcome match = find(sourceTaskId, availableDependencyOutcomes);
            if (match != null && match.hasRenderablePayload() && match.getProvenance().isPresent()) {
                inputs.add(match);
            }
        }
        return List.copyOf(inputs);
    }

    private TaskOutcome find(String taskId, List<TaskOutcome> outcomes) {
        for (TaskOutcome outcome : outcomes) {
            if (taskId.equals(outcome.getTaskId())) {
                return outcome;
            }
        }
        return null;
    }

    private TaskResultProvenance provenance(List<TaskOutcome> inputs) {
        Set<TaskSourceDomain> originDomains = new LinkedHashSet<>();
        List<String> sourceTaskIds = new ArrayList<>();
        List<String> claimIds = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        for (TaskOutcome input : inputs) {
            TaskResultProvenance inputProvenance = input.getProvenance().orElseThrow();
            originDomains.addAll(inputProvenance.getOriginDomains());
            appendDistinct(sourceTaskIds, input.getTaskId());
            appendAllDistinct(claimIds, inputProvenance.getClaimIds());
            appendAllDistinct(evidenceIds, inputProvenance.getEvidenceIds());
        }
        return TaskResultProvenance.synthesized(
                originDomains, sourceTaskIds, claimIds, evidenceIds);
    }

    private List<String> blocks(List<TaskOutcome> inputs) {
        List<String> blocks = new ArrayList<>();
        for (TaskOutcome input : inputs) {
            if (input.getContribution().isPresent()) {
                // P4 model wording is presentation-only. Synthesis consumes the
                // immutable pre-expression contribution produced by Material.
                appendAllDistinct(blocks,
                        input.getContribution().orElseThrow().getSupportedStatements());
                continue;
            }
            TaskResultPayload payload = input.getResultPayload().orElseThrow();
            if (payload instanceof TaskResultPayload.SectionResultPayload section) {
                appendAllDistinct(blocks, section.getBlocks());
            } else if (payload instanceof TaskResultPayload.RecommendationResultPayload recommendation) {
                appendDistinct(blocks, recommendation.getRecommendation());
                appendAllDistinct(blocks, recommendation.getSupportingBlocks());
            } else if (payload instanceof TaskResultPayload.SynthesisResultPayload synthesis) {
                appendAllDistinct(blocks, synthesis.getBlocks());
            }
        }
        return List.copyOf(blocks);
    }

    private void appendAllDistinct(List<String> values, List<String> candidates) {
        for (String candidate : candidates) {
            appendDistinct(values, candidate);
        }
    }

    private void appendDistinct(List<String> values, String candidate) {
        if (candidate != null && !candidate.isBlank() && !values.contains(candidate.trim())) {
            values.add(candidate.trim());
        }
    }
}
