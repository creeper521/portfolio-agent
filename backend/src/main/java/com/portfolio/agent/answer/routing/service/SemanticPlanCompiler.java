package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.CapabilityCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.PortfolioFacet;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Compiles deterministic signals into a closed candidate plan; validation remains a separate step. */
public final class SemanticPlanCompiler {

    private final SemanticRoutingPolicy routingPolicy;
    private final Supplier<String> planIdGenerator;

    public SemanticPlanCompiler(SemanticRoutingPolicy routingPolicy) {
        this(routingPolicy, () -> "plan-" + UUID.randomUUID().toString().replace("-", ""));
    }

    SemanticPlanCompiler(SemanticRoutingPolicy routingPolicy, Supplier<String> planIdGenerator) {
        this.routingPolicy = Objects.requireNonNull(routingPolicy, "routingPolicy");
        this.planIdGenerator = Objects.requireNonNull(planIdGenerator, "planIdGenerator");
    }

    public SemanticTurnPlan compile(SemanticSignals signals) {
        Objects.requireNonNull(signals, "signals");
        List<SemanticTask> tasks = new ArrayList<>();
        for (SemanticSignals.GoalCandidate goal : signals.getGoals()) {
            if (goal.getIntent() == SemanticSignals.Intent.SYNTHESIS) {
                continue;
            }
            tasks.add(createTask(goal.getIntent(), nextTaskId(tasks), goal.getSubjects()));
        }
        if (signals.getGoals().stream().anyMatch(goal -> goal.getIntent() == SemanticSignals.Intent.SYNTHESIS)
                && tasks.size() >= 2) {
            tasks.add(synthesisTask(nextTaskId(tasks), tasks));
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("deterministic signals must contain at least one compilable task");
        }
        List<TaskDependency> dependencies = dependencies(tasks, signals.isUserDeclaredOrder());
        Set<RequestedOutput> outputs = requestedOutputs(tasks);
        SemanticRoutingPolicy.DecisionFacts facts = SemanticRoutingPolicy.DecisionFacts.of(
                false, signals.isOrderAdjusted(), signals.hasNodeCapabilityBoundary());
        return new SemanticTurnPlan(
                freshPlanId(), contentVersion(signals.getSubjects()), SemanticTurnPlan.PlanSource.RULE,
                tasks, dependencies, signals.getExclusions(), outputs,
                routingPolicy.confirmationPolicy(tasks, dependencies, facts));
    }

    private SemanticTask createTask(
            SemanticSignals.Intent intent, String taskId, List<SubjectReference> subjects) {
        return switch (intent) {
            case PORTFOLIO_FACT -> factTask(taskId, requireSubject(subjects));
            case PORTFOLIO_COMPARE -> comparisonTask(taskId, subjects);
            case PORTFOLIO_RECOMMEND -> recommendationTask(taskId, subjects);
            case GENERAL_EXPLANATION -> generalTask(taskId);
            case SYNTHESIS -> throw new IllegalArgumentException("synthesis must be compiled after upstream tasks");
        };
    }

    private SemanticTask factTask(String taskId, SubjectReference subject) {
        SemanticTaskParameters.PortfolioFact parameters = new SemanticTaskParameters.PortfolioFact(
                subject, Set.of(PortfolioFacet.OVERVIEW.name()), "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_FACT, TaskSourceDomain.PORTFOLIO, "介绍公开项目",
                parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(subject));
    }

    private SemanticTask comparisonTask(String taskId, List<SubjectReference> subjects) {
        if (subjects.size() < 2) {
            throw new IllegalArgumentException("portfolio comparison requires two resolved subjects");
        }
        List<SubjectReference> comparisonSubjects = List.copyOf(subjects.subList(0, Math.min(subjects.size(), 3)));
        SemanticTaskParameters.PortfolioCompare parameters = new SemanticTaskParameters.PortfolioCompare(
                comparisonSubjects, Set.of(ComparisonDimension.ARCHITECTURE.name()), "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_COMPARE, TaskSourceDomain.PORTFOLIO, "比较公开项目",
                parameters, Set.of(RequestedOutput.SUMMARY, RequestedOutput.COMPARISON),
                TaskConfidence.highRule(), comparisonSubjects);
    }

    private SemanticTask recommendationTask(String taskId, List<SubjectReference> subjects) {
        SemanticTaskParameters.PortfolioRecommend parameters = new SemanticTaskParameters.PortfolioRecommend(
                subjects, "BACKEND_ENGINEERING", Set.of(CapabilityCode.JAVA.name()), "岗位匹配", 2, "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_RECOMMEND, TaskSourceDomain.PORTFOLIO, "给出岗位推荐",
                parameters, Set.of(RequestedOutput.RECOMMENDATION), TaskConfidence.highRule(), subjects);
    }

    private SemanticTask generalTask(String taskId) {
        SemanticTaskParameters.GeneralExplanation parameters = new SemanticTaskParameters.GeneralExplanation(
                "通用主题", "STANDARD", "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.GENERAL_EXPLANATION, TaskSourceDomain.GENERAL, "解释通用概念",
                parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());
    }

    private SemanticTask synthesisTask(String taskId, List<SemanticTask> upstream) {
        List<String> upstreamIds = upstream.stream().map(SemanticTask::getTaskId).toList();
        SemanticTaskParameters.Synthesis parameters = new SemanticTaskParameters.Synthesis(
                upstreamIds, "形成综合结论", Set.of(ComparisonDimension.ARCHITECTURE.name()));
        return SemanticTask.create(
                taskId, SemanticTaskType.SYNTHESIS, TaskSourceDomain.SYNTHESIS, "形成综合结论",
                parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());
    }

    private List<TaskDependency> dependencies(List<SemanticTask> tasks, boolean userDeclaredOrder) {
        List<TaskDependency> dependencies = new ArrayList<>();
        if (userDeclaredOrder) {
            for (int index = 1; index < tasks.size(); index++) {
                addDependency(dependencies, new TaskDependency(
                        tasks.get(index - 1).getTaskId(), tasks.get(index).getTaskId(),
                        TaskDependencyType.ORDER_AFTER, DependencyOrigin.USER_EXPLICIT));
            }
        }
        int synthesisIndex = indexOfSynthesis(tasks);
        if (synthesisIndex >= 0) {
            String synthesisId = tasks.get(synthesisIndex).getTaskId();
            for (int index = 0; index < synthesisIndex; index++) {
                addDependency(dependencies, new TaskDependency(
                        tasks.get(index).getTaskId(), synthesisId,
                        TaskDependencyType.REQUIRES_SUCCESS, DependencyOrigin.COMPILER_INFERRED));
            }
        }
        return List.copyOf(dependencies);
    }

    private void addDependency(List<TaskDependency> dependencies, TaskDependency candidate) {
        for (TaskDependency dependency : dependencies) {
            if (dependency.getFromTaskId().equals(candidate.getFromTaskId())
                    && dependency.getToTaskId().equals(candidate.getToTaskId())
                    && dependency.getType() == candidate.getType()
                    && dependency.getOrigin() == candidate.getOrigin()) {
                return;
            }
        }
        dependencies.add(candidate);
    }

    private int indexOfSynthesis(List<SemanticTask> tasks) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).getTaskType() == SemanticTaskType.SYNTHESIS) {
                return index;
            }
        }
        return -1;
    }

    private Set<RequestedOutput> requestedOutputs(List<SemanticTask> tasks) {
        Set<RequestedOutput> outputs = new LinkedHashSet<>();
        for (SemanticTask task : tasks) {
            outputs.addAll(task.getRequestedOutputs());
        }
        return Set.copyOf(outputs);
    }

    private String nextTaskId(List<SemanticTask> tasks) {
        return String.format("task-%02d", tasks.size() + 1);
    }

    private SubjectReference requireSubject(List<SubjectReference> subjects) {
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("portfolio fact requires a resolved subject");
        }
        return subjects.get(0);
    }

    private String contentVersion(List<SubjectReference> subjects) {
        return subjects.isEmpty() ? "public-v1" : subjects.get(0).getContentVersion();
    }

    private String freshPlanId() {
        String generated = planIdGenerator.get();
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("plan identity generator returned blank value");
        }
        return generated.trim();
    }
}
