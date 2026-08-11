package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceLevel;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskDependency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The single non-model authority for phase-two confirmation thresholds. */
public final class SemanticRoutingPolicy {

    public SemanticTurnPlan.PlanConfirmationPolicy confirmationPolicy(
            List<SemanticTask> tasks, List<TaskDependency> dependencies, DecisionFacts facts) {
        Set<SemanticTurnPlan.ConfirmationTrigger> triggers = confirmationTriggers(tasks, dependencies, facts);
        return triggers.isEmpty()
                ? SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation()
                : SemanticTurnPlan.PlanConfirmationPolicy.confirmationRequired(triggers);
    }

    public Set<SemanticTurnPlan.ConfirmationTrigger> confirmationTriggers(
            List<SemanticTask> tasks, List<TaskDependency> dependencies, DecisionFacts facts) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(facts, "facts");
        Set<SemanticTurnPlan.ConfirmationTrigger> triggers = new LinkedHashSet<>();
        if (tasks.size() >= 4) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.TASK_COUNT_REQUIRES_CONFIRMATION);
        }
        if (hasMediumConfidence(tasks)) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.MEDIUM_CONFIDENCE_FIELD);
        }
        if (sourceDomains(tasks).size() > 1) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.MIXED_SOURCE_DOMAINS);
        }
        if (dependencies.stream().anyMatch(dependency -> dependency.getOrigin()
                == com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin.COMPILER_INFERRED)) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.INFERRED_DEPENDENCY);
        }
        if (hasBroadSubjectScope(tasks)) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.BROAD_SUBJECT_SCOPE);
        }
        if (hasLargeOutputScope(tasks)) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.LARGE_OUTPUT_SCOPE);
        }
        if (facts.isPartialExecution()) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.PARTIAL_EXECUTION);
        }
        if (facts.isOrderAdjusted()) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.ORDER_ADJUSTED);
        }
        if (facts.hasNodeCapabilityBoundary()) {
            triggers.add(SemanticTurnPlan.ConfirmationTrigger.NODE_CAPABILITY_BOUNDARY);
        }
        return Set.copyOf(triggers);
    }

    private boolean hasMediumConfidence(List<SemanticTask> tasks) {
        for (SemanticTask task : tasks) {
            if (task.getConfidence().getOverall() == ConfidenceLevel.MEDIUM
                    || task.getConfidence().getFieldLevels().containsValue(ConfidenceLevel.MEDIUM)) {
                return true;
            }
        }
        return false;
    }

    private Set<TaskSourceDomain> sourceDomains(List<SemanticTask> tasks) {
        Set<TaskSourceDomain> domains = new LinkedHashSet<>();
        for (SemanticTask task : tasks) {
            domains.add(task.getSourceDomain());
        }
        return domains;
    }

    private boolean hasBroadSubjectScope(List<SemanticTask> tasks) {
        Set<SubjectReference> allSubjects = new LinkedHashSet<>();
        for (SemanticTask task : tasks) {
            if (task.getSubjectReferences().size() > 3) {
                return true;
            }
            allSubjects.addAll(task.getSubjectReferences());
        }
        return allSubjects.size() > 5;
    }

    private boolean hasLargeOutputScope(List<SemanticTask> tasks) {
        Set<RequestedOutput> turnOutputs = new LinkedHashSet<>();
        for (SemanticTask task : tasks) {
            turnOutputs.addAll(task.getRequestedOutputs());
            if (task.getRequestedOutputs().contains(RequestedOutput.DETAILED)
                    && task.getSubjectReferences().size() > 2) {
                return true;
            }
            if (task.getParameters() instanceof SemanticTaskParameters.Synthesis synthesis
                    && synthesis.getSourceTaskIds().size() > 3) {
                return true;
            }
        }
        return turnOutputs.size() >= 3;
    }

    public static final class DecisionFacts {

        private final boolean partialExecution;
        private final boolean orderAdjusted;
        private final boolean nodeCapabilityBoundary;

        private DecisionFacts(boolean partialExecution, boolean orderAdjusted, boolean nodeCapabilityBoundary) {
            this.partialExecution = partialExecution;
            this.orderAdjusted = orderAdjusted;
            this.nodeCapabilityBoundary = nodeCapabilityBoundary;
        }

        public static DecisionFacts of(
                boolean partialExecution, boolean orderAdjusted, boolean nodeCapabilityBoundary) {
            return new DecisionFacts(partialExecution, orderAdjusted, nodeCapabilityBoundary);
        }

        public boolean isPartialExecution() { return partialExecution; }
        public boolean isOrderAdjusted() { return orderAdjusted; }
        public boolean hasNodeCapabilityBoundary() { return nodeCapabilityBoundary; }
    }
}
