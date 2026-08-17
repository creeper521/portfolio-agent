package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validates the closed semantic-plan contract before execution is permitted. */
public final class SemanticPlanValidator {

    private final PlanFingerprintService fingerprints;
    private final SemanticTurnContractPolicy contractPolicy = new SemanticTurnContractPolicy();

    public SemanticPlanValidator(PlanFingerprintService fingerprints) {
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
    }

    public PlanValidationResult validate(SemanticTurnPlan candidate, String contract) {
        if (!contractPolicy.isSupported(contract)) {
            return PlanValidationResult.invalid(List.of("PLAN_CONTRACT_UNSUPPORTED"));
        }
        if (candidate == null) {
            return PlanValidationResult.invalid(List.of("PLAN_MISSING"));
        }
        List<String> issues = collectIssues(candidate);
        if (!issues.isEmpty()) {
            return PlanValidationResult.invalid(issues);
        }
        String fingerprint = fingerprints.fingerprint(candidate, contract);
        if (candidate.getPlanFingerprint() != null
                && !fingerprint.equals(candidate.getPlanFingerprint())) {
            return PlanValidationResult.invalid(List.of("PLAN_FINGERPRINT_MISMATCH"));
        }
        return PlanValidationResult.valid(
                new ValidatedPlan(normalizeFingerprint(candidate, fingerprint), fingerprint));
    }

    private SemanticTurnPlan normalizeFingerprint(SemanticTurnPlan candidate, String fingerprint) {
        if (fingerprint.equals(candidate.getPlanFingerprint())) {
            return candidate;
        }
        return new SemanticTurnPlan(
                candidate.getPlanId(),
                candidate.getContentVersion(),
                candidate.getSource(),
                candidate.getTasks(),
                candidate.getDependencies(),
                candidate.getExclusions(),
                candidate.getRequestedOutputs(),
                candidate.getConfirmationPolicy(),
                fingerprint);
    }

    private List<String> collectIssues(SemanticTurnPlan plan) {
        List<String> issues = new ArrayList<>();
        List<SemanticTask> tasks = plan.getTasks();
        if (tasks.isEmpty() || tasks.size() > 6) {
            issues.add("PLAN_TASK_COUNT_INVALID");
        }

        Set<String> taskIds = new HashSet<>();
        Set<SemanticTaskKey> semanticKeys = new HashSet<>();
        for (SemanticTask task : tasks) {
            if (!taskIds.add(task.getTaskId())) {
                issues.add("PLAN_DUPLICATE_TASK_ID");
            }
            if (!semanticKeys.add(semanticKey(task))) {
                issues.add("PLAN_SEMANTIC_TASK_DUPLICATE");
            }
            validateTask(task, plan.getContentVersion(), issues);
        }
        if (tasks.stream().noneMatch(task -> task.getFulfillmentRole() == TaskFulfillmentRole.PRIMARY)) {
            issues.add("PLAN_PRIMARY_FULFILLMENT_ROLE_MISSING");
        }

        validateDependencies(plan.getDependencies(), taskIds, issues);
        validateRoleDependencies(tasks, plan.getDependencies(), issues);
        validateSynthesisDependencies(tasks, plan.getDependencies(), issues);
        validateExclusions(plan, taskIds, issues);
        return List.copyOf(issues);
    }

    private SemanticTaskKey semanticKey(SemanticTask task) {
        return new SemanticTaskKey(
                task.getTaskType(), task.getSourceDomain(), canonicalParameters(task.getParameters()),
                subjectKeys(task.getSubjectReferences()),
                task.getRequestedOutputs().stream().map(Enum::name).sorted().toList(),
                task.getFulfillmentRole());
    }

    private List<String> canonicalParameters(SemanticTaskParameters parameters) {
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            return List.of("subject=" + subjectKey(fact.getSubject()),
                    "facets=" + enumNames(fact.getFacets()), "audience=" + fact.getAudienceRole());
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            return List.of("subjects=" + subjectKeys(comparison.getSubjects()),
                    "dimensions=" + enumNames(comparison.getDimensions()),
                    "audience=" + comparison.getAudienceRole());
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            return List.of("candidates=" + subjectKeys(recommendation.getCandidateSubjects()),
                    "track=" + recommendation.getCareerTrack(),
                    "capabilities=" + enumNames(recommendation.getCapabilityCodes()),
                    "goal=" + recommendation.getGoal().trim().toLowerCase(java.util.Locale.ROOT),
                    "size=" + recommendation.getRequestedSize(), "audience=" + recommendation.getAudienceRole());
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRefinement refinement) {
            return List.of("base=" + subjectKey(refinement.getBaseResultReference()),
                    "constraints=" + enumNames(refinement.getAddedConstraints()),
                    "removed=" + subjectKeys(List.copyOf(refinement.getRemovedSubjects())));
        }
        if (parameters instanceof SemanticTaskParameters.GeneralExplanation explanation) {
            return List.of("topic=" + explanation.getTopic().trim().toLowerCase(java.util.Locale.ROOT),
                    "depth=" + explanation.getDepth(), "audience=" + explanation.getAudienceRole());
        }
        if (parameters instanceof SemanticTaskParameters.GeneralComparison comparison) {
            return List.of("subjects=" + comparison.getSubjects().stream()
                            .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT)).sorted().toList(),
                    "dimensions=" + enumNames(comparison.getDimensions()),
                    "depth=" + comparison.getDepth(), "audience=" + comparison.getAudienceRole());
        }
        if (parameters instanceof SemanticTaskParameters.Synthesis synthesis) {
            return List.of("sources=" + synthesis.getSourceTaskIds().stream().sorted().toList(),
                    "goal=" + synthesis.getSynthesisGoal().trim().toLowerCase(java.util.Locale.ROOT),
                    "dimensions=" + enumNames(synthesis.getDimensions()));
        }
        throw new IllegalArgumentException("unsupported semantic task parameters");
    }

    private List<String> subjectKeys(List<SubjectReference> subjects) {
        return subjects.stream().map(this::subjectKey).sorted().toList();
    }

    private String subjectKey(SubjectReference subject) {
        return subject.getSubjectType() + ":" + subject.getSubjectId() + ":" + subject.getContentVersion();
    }

    private List<String> enumNames(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private record SemanticTaskKey(
            SemanticRoutingTypes.SemanticTaskType taskType,
            SemanticRoutingTypes.TaskSourceDomain sourceDomain,
            List<String> parameters,
            List<String> subjects,
            List<String> outputs,
            TaskFulfillmentRole fulfillmentRole) { }

    private void validateRoleDependencies(
            List<SemanticTask> tasks, List<TaskDependency> dependencies, List<String> issues) {
        Map<String, SemanticTask> tasksById = new HashMap<>();
        for (SemanticTask task : tasks) {
            tasksById.put(task.getTaskId(), task);
        }
        for (TaskDependency dependency : dependencies) {
            SemanticTask from = tasksById.get(dependency.getFromTaskId());
            SemanticTask to = tasksById.get(dependency.getToTaskId());
            if (from != null && to != null
                    && to.getFulfillmentRole() == TaskFulfillmentRole.PRIMARY
                    && from.getFulfillmentRole() == TaskFulfillmentRole.OPTIONAL) {
                issues.add("PLAN_PRIMARY_DEPENDS_ON_OPTIONAL");
            }
        }
    }

    private void validateTask(SemanticTask task, String contentVersion, List<String> issues) {
        if (!isSupportedMatrix(task)) {
            issues.add("PLAN_TASK_MATRIX_INVALID");
        }
        for (SubjectReference reference : task.getSubjectReferences()) {
            if (reference.getSubjectType() != SemanticRoutingTypes.SubjectType.RESULT
                    && !contentVersion.equals(reference.getContentVersion())) {
                issues.add("PLAN_SUBJECT_CONTENT_VERSION_MISMATCH");
            }
        }
    }

    private boolean isSupportedMatrix(SemanticTask task) {
        return switch (task.getTaskType()) {
            case PORTFOLIO_FACT -> task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                    && task.getParameters() instanceof SemanticTaskParameters.PortfolioFact;
            case PORTFOLIO_COMPARE -> task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                    && task.getParameters() instanceof SemanticTaskParameters.PortfolioCompare;
            case PORTFOLIO_RECOMMEND -> task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                    && task.getParameters() instanceof SemanticTaskParameters.PortfolioRecommend recommendation
                    && recommendation.getCandidateSubjects().stream().allMatch(subject ->
                    subject.getSubjectType() == SemanticRoutingTypes.SubjectType.PROJECT);
            case PORTFOLIO_REFINE_RECOMMENDATION -> task.getSourceDomain()
                    == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                    && task.getParameters() instanceof SemanticTaskParameters.PortfolioRefinement;
            case GENERAL_EXPLANATION -> task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.GENERAL
                    && task.getParameters() instanceof SemanticTaskParameters.GeneralExplanation;
            case GENERAL_COMPARISON -> task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.GENERAL
                    && task.getParameters() instanceof SemanticTaskParameters.GeneralComparison;
            case SYNTHESIS -> task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS
                    && task.getParameters() instanceof SemanticTaskParameters.Synthesis synthesis
                    && synthesis.getSourceTaskIds().size() >= 2;
        };
    }

    private void validateDependencies(
            List<TaskDependency> dependencies, Set<String> taskIds, List<String> issues) {
        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (TaskDependency dependency : dependencies) {
            if (!taskIds.contains(dependency.getFromTaskId()) || !taskIds.contains(dependency.getToTaskId())) {
                issues.add("PLAN_DEPENDENCY_REFERENCE_MISSING");
                continue;
            }
            if (dependency.getFromTaskId().equals(dependency.getToTaskId())) {
                issues.add("PLAN_DEPENDENCY_SELF_EDGE");
                continue;
            }
            String edgeKey = dependency.getFromTaskId() + "\u0000" + dependency.getToTaskId()
                    + "\u0000" + dependency.getType().name();
            if (!edgeKeys.add(edgeKey)) {
                issues.add("PLAN_DEPENDENCY_DUPLICATE");
            }
            outgoing.computeIfAbsent(dependency.getFromTaskId(), ignored -> new ArrayList<>())
                    .add(dependency.getToTaskId());
        }
        if (containsCycle(taskIds, outgoing)) {
            issues.add("PLAN_DEPENDENCY_CYCLE");
        }
    }

    private void validateSynthesisDependencies(
            List<SemanticTask> tasks, List<TaskDependency> dependencies, List<String> issues) {
        for (SemanticTask task : tasks) {
            if (!(task.getParameters() instanceof SemanticTaskParameters.Synthesis synthesis)) {
                continue;
            }
            Set<String> declaredSources = Set.copyOf(synthesis.getSourceTaskIds());
            Set<String> dependencySources = new HashSet<>();
            for (TaskDependency dependency : dependencies) {
                if (task.getTaskId().equals(dependency.getToTaskId())) {
                    dependencySources.add(dependency.getFromTaskId());
                }
            }
            if (!declaredSources.equals(dependencySources)) {
                issues.add("PLAN_SYNTHESIS_DEPENDENCY_MISMATCH");
            }
        }
    }

    private void validateExclusions(SemanticTurnPlan plan, Set<String> taskIds, List<String> issues) {
        for (PlanExclusion exclusion : plan.getExclusions()) {
            List<SemanticTask> affectedTasks = affectedTasks(plan, exclusion, taskIds, issues);
            if (affectedTasks.isEmpty() && exclusion.getScope() == SemanticRoutingTypes.ExclusionScope.TASK) {
                continue;
            }
            if (exclusion.getControlledValue() instanceof PlanExclusion.SubjectValue subjectValue) {
                validateExcludedSubject(affectedTasks, subjectValue.getSubject(), issues);
            } else if (exclusion.getControlledValue() instanceof PlanExclusion.OutputValue outputValue) {
                validateExcludedOutput(plan, exclusion, affectedTasks, outputValue.getOutput(), issues);
            } else if (exclusion.getControlledValue() instanceof PlanExclusion.DimensionValue dimensionValue) {
                validateExcludedDimension(affectedTasks, dimensionValue.getDimension(), issues);
            } else if (exclusion.getControlledValue() instanceof PlanExclusion.ConstraintValue constraintValue) {
                validateExcludedConstraint(affectedTasks, constraintValue.getConstraint(), issues);
            } else {
                issues.add("PLAN_EXCLUSION_VALUE_INVALID");
            }
        }
    }

    private List<SemanticTask> affectedTasks(
            SemanticTurnPlan plan,
            PlanExclusion exclusion,
            Set<String> taskIds,
            List<String> issues) {
        if (exclusion.getScope() == SemanticRoutingTypes.ExclusionScope.PLAN) {
            return plan.getTasks();
        }
        if (!taskIds.contains(exclusion.getTaskId())) {
            issues.add("PLAN_EXCLUSION_TASK_REFERENCE_MISSING");
            return List.of();
        }
        for (SemanticTask task : plan.getTasks()) {
            if (exclusion.getTaskId().equals(task.getTaskId())) {
                return List.of(task);
            }
        }
        return List.of();
    }

    private void validateExcludedSubject(
            List<SemanticTask> tasks, SubjectReference excludedSubject, List<String> issues) {
        for (SemanticTask task : tasks) {
            if (task.getSubjectReferences().contains(excludedSubject)) {
                issues.add("PLAN_EXCLUDED_SUBJECT_REINTRODUCED");
                return;
            }
        }
    }

    private void validateExcludedOutput(
            SemanticTurnPlan plan,
            PlanExclusion exclusion,
            List<SemanticTask> tasks,
            SemanticRoutingTypes.RequestedOutput output,
            List<String> issues) {
        if (exclusion.getScope() == SemanticRoutingTypes.ExclusionScope.PLAN
                && plan.getRequestedOutputs().contains(output)) {
            issues.add("PLAN_EXCLUDED_OUTPUT_REINTRODUCED");
            return;
        }
        for (SemanticTask task : tasks) {
            if (task.getRequestedOutputs().contains(output)) {
                issues.add("PLAN_EXCLUDED_OUTPUT_REINTRODUCED");
                return;
            }
        }
    }

    private void validateExcludedDimension(
            List<SemanticTask> tasks,
            SemanticRoutingTypes.ComparisonDimension dimension,
            List<String> issues) {
        for (SemanticTask task : tasks) {
            if (containsDimension(task.getParameters(), dimension)) {
                issues.add("PLAN_EXCLUDED_DIMENSION_REINTRODUCED");
                return;
            }
        }
    }

    private boolean containsDimension(
            SemanticTaskParameters parameters, SemanticRoutingTypes.ComparisonDimension dimension) {
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            return comparison.getDimensions().contains(dimension);
        }
        if (parameters instanceof SemanticTaskParameters.GeneralComparison comparison) {
            return comparison.getDimensions().contains(dimension);
        }
        if (parameters instanceof SemanticTaskParameters.Synthesis synthesis) {
            return synthesis.getDimensions().contains(dimension);
        }
        return false;
    }

    private void validateExcludedConstraint(
            List<SemanticTask> tasks,
            SemanticRoutingTypes.ConstraintCode constraint,
            List<String> issues) {
        for (SemanticTask task : tasks) {
            if (containsConstraint(task, constraint)) {
                issues.add("PLAN_EXCLUDED_CONSTRAINT_REINTRODUCED");
                return;
            }
        }
    }

    private boolean containsConstraint(SemanticTask task, SemanticRoutingTypes.ConstraintCode constraint) {
        if (task.getParameters() instanceof SemanticTaskParameters.PortfolioRefinement refinement
                && refinement.getAddedConstraints().contains(constraint)) {
            return true;
        }
        return constraint == SemanticRoutingTypes.ConstraintCode.EXCLUDE_RECOMMENDATIONS
                && (task.getTaskType() == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_RECOMMEND
                || task.getTaskType() == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION
                || task.getRequestedOutputs().contains(SemanticRoutingTypes.RequestedOutput.RECOMMENDATION));
    }

    private boolean containsCycle(Set<String> taskIds, Map<String, List<String>> outgoing) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String taskId : taskIds) {
            if (visit(taskId, outgoing, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean visit(
            String taskId,
            Map<String, List<String>> outgoing,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(taskId)) {
            return false;
        }
        if (!visiting.add(taskId)) {
            return true;
        }
        for (String nextTaskId : outgoing.getOrDefault(taskId, List.of())) {
            if (visit(nextTaskId, outgoing, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(taskId);
        visited.add(taskId);
        return false;
    }

    public static final class ValidatedPlan implements ValidatedSemanticTurnPlan {

        private final SemanticTurnPlan plan;
        private final String planFingerprint;

        private ValidatedPlan(SemanticTurnPlan plan, String planFingerprint) {
            this.plan = Objects.requireNonNull(plan, "plan");
            this.planFingerprint = requireText(planFingerprint, "planFingerprint");
        }

        @Override
        public SemanticTurnPlan getPlan() {
            return plan;
        }

        @Override
        public String getPlanId() {
            return plan.getPlanId();
        }

        @Override
        public String getContentVersion() {
            return plan.getContentVersion();
        }

        @Override
        public SemanticTurnPlan.PlanSource getSource() {
            return plan.getSource();
        }

        @Override
        public List<SemanticTask> getTasks() {
            return plan.getTasks();
        }

        @Override
        public List<TaskDependency> getDependencies() {
            return plan.getDependencies();
        }

        @Override
        public List<PlanExclusion> getExclusions() {
            return plan.getExclusions();
        }

        @Override
        public Set<SemanticRoutingTypes.RequestedOutput> getRequestedOutputs() {
            return plan.getRequestedOutputs();
        }

        @Override
        public SemanticTurnPlan.PlanConfirmationPolicy getConfirmationPolicy() {
            return plan.getConfirmationPolicy();
        }

        @Override
        public String getPlanFingerprint() {
            return planFingerprint;
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }
    }
}

final class PlanValidationResult {

    private final ValidatedSemanticTurnPlan validatedPlan;
    private final List<String> issues;

    private PlanValidationResult(ValidatedSemanticTurnPlan validatedPlan, List<String> issues) {
        this.validatedPlan = validatedPlan;
        this.issues = List.copyOf(issues);
    }

    static PlanValidationResult valid(ValidatedSemanticTurnPlan validatedPlan) {
        return new PlanValidationResult(Objects.requireNonNull(validatedPlan, "validatedPlan"), List.of());
    }

    static PlanValidationResult invalid(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("issues are required for an invalid plan");
        }
        return new PlanValidationResult(null, issues);
    }

    boolean isValid() {
        return validatedPlan != null;
    }

    Optional<ValidatedSemanticTurnPlan> getValidatedPlan() {
        return Optional.ofNullable(validatedPlan);
    }

    List<String> getIssues() {
        return issues;
    }

    @Override
    public String toString() {
        return "PlanValidationResult{valid=" + isValid() + ", issueCount=" + issues.size() + '}';
    }
}
