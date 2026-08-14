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
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;

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
        return compile(signals, contentVersion(signals.getSubjects()));
    }

    SemanticTurnPlan compile(SemanticSignals signals, String currentContentVersion) {
        Objects.requireNonNull(signals, "signals");
        String effectiveContentVersion = requireText(currentContentVersion, "currentContentVersion");
        List<SemanticTask> tasks = new ArrayList<>();
        boolean hasExplicitSynthesisGoal = signals.getGoals().stream()
                .anyMatch(goal -> goal.getIntent() == SemanticSignals.Intent.SYNTHESIS);
        for (SemanticSignals.GoalCandidate goal : signals.getGoals()) {
            if (goal.getIntent() == SemanticSignals.Intent.SYNTHESIS) {
                continue;
            }
            tasks.add(createTask(
                    goal.getIntent(), nextTaskId(tasks), goal.getSubjects(), goal.getTopics(),
                    goal.getPortfolioFacets(),
                    signals.getQuestion(), hasExplicitSynthesisGoal
                            ? TaskFulfillmentRole.SUPPORTING : TaskFulfillmentRole.PRIMARY));
        }
        if (hasExplicitSynthesisGoal && tasks.size() >= 2) {
            tasks.add(synthesisTask(nextTaskId(tasks), tasks, TaskFulfillmentRole.PRIMARY));
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("deterministic signals must contain at least one compilable task");
        }
        List<TaskDependency> dependencies = dependencies(tasks, signals.isUserDeclaredOrder());
        Set<RequestedOutput> outputs = requestedOutputs(tasks);
        SemanticRoutingPolicy.DecisionFacts facts = SemanticRoutingPolicy.DecisionFacts.of(
                false, signals.isOrderAdjusted(), signals.hasNodeCapabilityBoundary());
        return new SemanticTurnPlan(
                freshPlanId(), effectiveContentVersion, SemanticTurnPlan.PlanSource.RULE,
                tasks, dependencies, signals.getExclusions(), outputs,
                routingPolicy.confirmationPolicy(tasks, dependencies, facts));
    }

    private SemanticTask createTask(
            SemanticSignals.Intent intent,
            String taskId,
            List<SubjectReference> subjects,
            List<String> topics,
            Set<PortfolioFacet> portfolioFacets,
            String question,
            TaskFulfillmentRole role) {
        return switch (intent) {
            case PORTFOLIO_FACT -> factTask(taskId, requireSubject(subjects), portfolioFacets, role);
            case PORTFOLIO_COMPARE -> comparisonTask(taskId, subjects, role);
            case PORTFOLIO_RECOMMEND -> recommendationTask(taskId, subjects, question, role);
            case PORTFOLIO_REFINE_RECOMMENDATION -> refinementTask(taskId, requireResult(subjects), role);
            case GENERAL_EXPLANATION -> generalTask(taskId, question, role);
            case GENERAL_COMPARISON -> generalComparisonTask(taskId, topics, role);
            case SYNTHESIS -> throw new IllegalArgumentException("synthesis must be compiled after upstream tasks");
        };
    }

    private SemanticTask factTask(
            String taskId,
            SubjectReference subject,
            Set<PortfolioFacet> requestedFacets,
            TaskFulfillmentRole role) {
        Set<PortfolioFacet> facets = requestedFacets.isEmpty()
                ? Set.of(PortfolioFacet.OVERVIEW)
                : Set.copyOf(requestedFacets);
        SemanticTaskParameters.PortfolioFact parameters = new SemanticTaskParameters.PortfolioFact(
                subject, facets.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()), "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_FACT, TaskSourceDomain.PORTFOLIO, "介绍公开项目",
                parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(subject),
                role);
    }

    private SemanticTask comparisonTask(String taskId, List<SubjectReference> subjects, TaskFulfillmentRole role) {
        if (subjects.size() < 2) {
            throw new IllegalArgumentException("portfolio comparison requires two resolved subjects");
        }
        List<SubjectReference> comparisonSubjects = List.copyOf(subjects.subList(0, Math.min(subjects.size(), 3)));
        SemanticTaskParameters.PortfolioCompare parameters = new SemanticTaskParameters.PortfolioCompare(
                comparisonSubjects, Set.of(ComparisonDimension.ARCHITECTURE.name()), "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_COMPARE, TaskSourceDomain.PORTFOLIO, "比较公开项目",
                parameters, Set.of(RequestedOutput.SUMMARY, RequestedOutput.COMPARISON),
                TaskConfidence.highRule(), comparisonSubjects, role);
    }

    private SemanticTask recommendationTask(
            String taskId, List<SubjectReference> subjects, String question, TaskFulfillmentRole role) {
        SemanticTaskParameters.PortfolioRecommend parameters = new SemanticTaskParameters.PortfolioRecommend(
                subjects, "BACKEND_ENGINEERING", requestedCapabilities(question), "岗位匹配",
                requestedRecommendationSize(question), "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_RECOMMEND, TaskSourceDomain.PORTFOLIO, "给出岗位推荐",
                parameters, Set.of(RequestedOutput.RECOMMENDATION), TaskConfidence.highRule(), subjects,
                role);
    }

    private static int requestedRecommendationSize(String question) {
        String normalized = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
        java.util.regex.Matcher digits = java.util.regex.Pattern
                .compile("([1-5])\\s*(?:个|项)?\\s*(?:项目|案例|推荐)")
                .matcher(normalized);
        if (digits.find()) {
            return Integer.parseInt(digits.group(1));
        }
        if (normalized.matches(".*(?:推荐|给我|选择)\\s*(?:一|1)\\s*个.*")) return 1;
        if (normalized.matches(".*(?:推荐|给我|选择)\\s*(?:二|两|2)\\s*个.*")) return 2;
        if (normalized.matches(".*(?:推荐|给我|选择)\\s*(?:三|3)\\s*个.*")) return 3;
        if (normalized.matches(".*(?:推荐|给我|选择)\\s*(?:四|4)\\s*个.*")) return 4;
        if (normalized.matches(".*(?:推荐|给我|选择)\\s*(?:五|5)\\s*个.*")) return 5;
        return 2;
    }

    private static Set<String> requestedCapabilities(String question) {
        String normalized = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
        Set<String> capabilities = new LinkedHashSet<>();
        if (normalized.contains("java")) capabilities.add(CapabilityCode.JAVA.name());
        if (normalized.contains("spring boot") || normalized.contains("springboot")) {
            capabilities.add(CapabilityCode.SPRING_BOOT.name());
        }
        if (normalized.contains("postgresql")) capabilities.add(CapabilityCode.POSTGRESQL.name());
        if (normalized.contains("sql")) capabilities.add(CapabilityCode.SQL.name());
        if (normalized.contains("vue")) capabilities.add(CapabilityCode.VUE.name());
        if (normalized.contains("typescript")) capabilities.add(CapabilityCode.TYPESCRIPT.name());
        if (normalized.contains("测试")) capabilities.add(CapabilityCode.TESTING.name());
        if (normalized.contains("系统设计")) capabilities.add(CapabilityCode.SYSTEM_DESIGN.name());
        return Set.copyOf(capabilities);
    }

    private SemanticTask refinementTask(
            String taskId, SubjectReference resultReference, TaskFulfillmentRole role) {
        SemanticTaskParameters.PortfolioRefinement parameters =
                new SemanticTaskParameters.PortfolioRefinement(resultReference, Set.of(), Set.of());
        return SemanticTask.create(
                taskId, SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION,
                TaskSourceDomain.PORTFOLIO, "调整岗位推荐",
                parameters, Set.of(RequestedOutput.RECOMMENDATION),
                TaskConfidence.highRule(), List.of(resultReference), role);
    }

    private SemanticTask generalTask(String taskId, String question, TaskFulfillmentRole role) {
        SemanticTaskParameters.GeneralExplanation parameters = new SemanticTaskParameters.GeneralExplanation(
                question, "STANDARD", "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.GENERAL_EXPLANATION, TaskSourceDomain.GENERAL, "解释通用概念",
                parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(), role);
    }

    private SemanticTask generalComparisonTask(
            String taskId, List<String> topics, TaskFulfillmentRole role) {
        SemanticTaskParameters.GeneralComparison parameters = new SemanticTaskParameters.GeneralComparison(
                topics, Set.of(ComparisonDimension.ARCHITECTURE.name()), "STANDARD", "GUEST");
        return SemanticTask.create(
                taskId, SemanticTaskType.GENERAL_COMPARISON, TaskSourceDomain.GENERAL, "比较通用主题",
                parameters, Set.of(RequestedOutput.SUMMARY, RequestedOutput.COMPARISON),
                TaskConfidence.highRule(), List.of(), role);
    }

    private SemanticTask synthesisTask(
            String taskId, List<SemanticTask> upstream, TaskFulfillmentRole role) {
        List<String> upstreamIds = upstream.stream().map(SemanticTask::getTaskId).toList();
        SemanticTaskParameters.Synthesis parameters = new SemanticTaskParameters.Synthesis(
                upstreamIds, "形成综合结论", Set.of(ComparisonDimension.ARCHITECTURE.name()));
        return SemanticTask.create(
                taskId, SemanticTaskType.SYNTHESIS, TaskSourceDomain.SYNTHESIS, "形成综合结论",
                parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(), role);
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

    private SubjectReference requireResult(List<SubjectReference> subjects) {
        if (subjects.size() != 1 || subjects.getFirst().getSubjectType()
                != com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.RESULT) {
            throw new IllegalArgumentException("portfolio refinement requires one result reference");
        }
        return subjects.getFirst();
    }

    private String contentVersion(List<SubjectReference> subjects) {
        for (SubjectReference subject : subjects) {
            if (subject.getContentVersion() != null && !subject.getContentVersion().isBlank()) {
                return subject.getContentVersion();
            }
        }
        return "public-v1";
    }

    private String freshPlanId() {
        String generated = planIdGenerator.get();
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("plan identity generator returned blank value");
        }
        return generated.trim();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
