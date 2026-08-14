package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticPlanValidatorTest {

    private final PlanFingerprintService fingerprints = new PlanFingerprintService();
    private final SemanticPlanValidator validator = new SemanticPlanValidator(fingerprints);

    @Test
    void rejectsCycle() {
        SemanticTurnPlan plan = plan(
                List.of(fact("task-01", "project-a"), fact("task-02", "project-b")),
                List.of(
                        dependency("task-01", "task-02"),
                        dependency("task-02", "task-01")),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(plan, "stp-v1").isValid());
    }

    @Test
    void rejectsDependencyWithMissingTaskReference() {
        SemanticTurnPlan plan = plan(
                List.of(fact("task-01", "project-a")),
                List.of(dependency("task-01", "missing-task")),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(plan, "stp-v1").isValid());
    }

    @Test
    void rejectsDuplicateTaskIds() {
        SemanticTurnPlan plan = plan(
                List.of(fact("task-01", "project-a"), fact("task-01", "project-b")),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(plan, "stp-v1").isValid());
    }

    @Test
    void rejectsMoreThanSixTasks() {
        List<SemanticTask> tasks = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            tasks.add(fact("task-0" + index, "project-" + index));
        }
        SemanticTurnPlan plan = plan(
                tasks,
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(plan, "stp-v1").isValid());
    }

    @Test
    void rejectsSynthesisWhenItsDependenciesDoNotMatchItsDeclaredSources() {
        SemanticTask synthesis = SemanticTask.create(
                "task-03",
                SemanticRoutingTypes.SemanticTaskType.SYNTHESIS,
                SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS,
                "Synthesize findings",
                new SemanticTaskParameters.Synthesis(
                        List.of("task-01", "task-02"), "Synthesize findings", Set.of()),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of());
        SemanticTurnPlan plan = plan(
                List.of(fact("task-01", "project-a"), fact("task-02", "project-b"), synthesis),
                List.of(dependency("task-01", "task-03")),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(plan, "stp-v1").isValid());
    }

    @Test
    void rejectsPlansWithoutAPrimaryFulfillmentRole() {
        SemanticTurnPlan plan = plan(
                List.of(
                        factWithRole("task-01", "project-a", TaskFulfillmentRole.SUPPORTING),
                        factWithRole("task-02", "project-b", TaskFulfillmentRole.OPTIONAL)),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        PlanValidationResult result = validator.validate(plan, "stp-v2");

        assertFalse(result.isValid());
        assertTrue(result.getIssues().contains("PLAN_PRIMARY_FULFILLMENT_ROLE_MISSING"));
    }

    @Test
    void rejectsTaskThatReintroducesAnExcludedSubject() {
        SemanticTurnPlan plan = plan(
                List.of(fact("task-01", "project-a")),
                List.of(),
                List.of(PlanExclusion.planSubject(SubjectReference.project("project-a", "public-v1"))),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(plan, "stp-v1").isValid());
    }

    @Test
    void fingerprintIsStableAcrossSetConstructionOrder() {
        SemanticTurnPlan left = plan(
                List.of(compareWithOutputs("task-01", orderedOutputs(
                        SemanticRoutingTypes.RequestedOutput.SUMMARY,
                        SemanticRoutingTypes.RequestedOutput.EVIDENCE))),
                List.of(),
                List.of(),
                orderedOutputs(SemanticRoutingTypes.RequestedOutput.SUMMARY,
                        SemanticRoutingTypes.RequestedOutput.EVIDENCE));
        SemanticTurnPlan right = plan(
                List.of(compareWithOutputs("task-01", orderedOutputs(
                        SemanticRoutingTypes.RequestedOutput.EVIDENCE,
                        SemanticRoutingTypes.RequestedOutput.SUMMARY))),
                List.of(),
                List.of(),
                orderedOutputs(SemanticRoutingTypes.RequestedOutput.EVIDENCE,
                        SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertEquals(fingerprints.fingerprint(left, "stp-v1"), fingerprints.fingerprint(right, "stp-v1"));
    }

    @Test
    void fingerprintChangesWhenAnExclusionChanges() {
        SemanticTurnPlan baseline = plan(
                List.of(fact("task-01", "project-a")),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));
        SemanticTurnPlan changed = plan(
                List.of(fact("task-01", "project-a")),
                List.of(),
                List.of(PlanExclusion.planSubject(SubjectReference.project("project-c", "public-v1"))),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertNotEquals(fingerprints.fingerprint(baseline, "stp-v1"),
                fingerprints.fingerprint(changed, "stp-v1"));
    }

    @Test
    void validPlanExposesTrustedWrapperOnlyAfterValidation() {
        SemanticTurnPlan plan = plan(
                List.of(fact("task-01", "project-a")),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        PlanValidationResult result = validator.validate(plan, "stp-v1");

        assertTrue(result.isValid());
        assertTrue(result.getValidatedPlan().isPresent());
        assertEquals(fingerprints.fingerprint(plan, "stp-v1"),
                result.getValidatedPlan().orElseThrow().getPlanFingerprint());
        assertEquals(result.getValidatedPlan().orElseThrow().getPlanFingerprint(),
                result.getValidatedPlan().orElseThrow().getPlan().getPlanFingerprint());
        assertFalse(validator.validate(plan, "unsupported").getValidatedPlan().isPresent());
    }

    @Test
    void mismatchingCandidateFingerprintIsRejectedBeforeTrustedWrapperCreation() {
        SemanticTurnPlan candidate = new SemanticTurnPlan(
                "plan-private-id",
                "public-v1",
                SemanticTurnPlan.PlanSource.RULE,
                List.of(fact("task-01", "project-a")),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation(),
                "sha256:attacker-selected");

        PlanValidationResult result = validator.validate(candidate, "stp-v1");

        assertFalse(result.isValid());
        assertTrue(result.getIssues().contains("PLAN_FINGERPRINT_MISMATCH"));
        assertTrue(result.getValidatedPlan().isEmpty());
    }

    @Test
    void invalidCandidateCannotObtainTrustedPlanThroughPublicApi() {
        SemanticTurnPlan invalid = plan(
                List.of(
                        fact("task-01", "project-a"),
                        fact("task-02", "project-b"),
                        fact("task-03", "project-c"),
                        fact("task-04", "project-d"),
                        fact("task-05", "project-e"),
                        fact("task-06", "project-f"),
                        fact("task-07", "project-g")),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY));

        assertFalse(validator.validate(invalid, "stp-v1").isValid());
        assertTrue(ValidatedSemanticTurnPlan.class.isSealed());
        assertEquals(1, ValidatedSemanticTurnPlan.class.getPermittedSubclasses().length);
        assertEquals(SemanticPlanValidator.ValidatedPlan.class,
                ValidatedSemanticTurnPlan.class.getPermittedSubclasses()[0]);
        assertEquals(0, SemanticPlanValidator.ValidatedPlan.class.getConstructors().length);
        assertEquals(0,
                ValidatedSemanticTurnPlan.class
                        .getDeclaredConstructors().length);
        assertTrue(java.util.Arrays.stream(
                        ValidatedSemanticTurnPlan.class.getMethods())
                .noneMatch(method -> method.getName().equals("fromValidated")));
    }

    private static SemanticTurnPlan plan(
            List<SemanticTask> tasks,
            List<TaskDependency> dependencies,
            List<PlanExclusion> exclusions,
            Set<SemanticRoutingTypes.RequestedOutput> outputs) {
        return new SemanticTurnPlan(
                "plan-private-id",
                "public-v1",
                SemanticTurnPlan.PlanSource.RULE,
                tasks,
                dependencies,
                exclusions,
                outputs,
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation(),
                null);
    }

    private static SemanticTask fact(String taskId, String projectId) {
        return factWithRole(taskId, projectId, TaskFulfillmentRole.PRIMARY);
    }

    private static SemanticTask factWithRole(
            String taskId, String projectId, TaskFulfillmentRole role) {
        SubjectReference subject = SubjectReference.project(projectId, "public-v1");
        SemanticTaskParameters.PortfolioFact parameters = new SemanticTaskParameters.PortfolioFact(
                subject, Set.of(), "INTERVIEWER");
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Describe the project",
                parameters,
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of(subject), role);
    }

    private static SemanticTask compareWithOutputs(
            String taskId, Set<SemanticRoutingTypes.RequestedOutput> outputs) {
        SubjectReference first = SubjectReference.project("project-a", "public-v1");
        SubjectReference second = SubjectReference.project("project-b", "public-v1");
        SemanticTaskParameters.PortfolioCompare parameters = new SemanticTaskParameters.PortfolioCompare(
                List.of(first, second), Set.of("ARCHITECTURE"), "INTERVIEWER");
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Compare the projects",
                parameters,
                outputs,
                TaskConfidence.highRule(),
                List.of(first, second));
    }

    private static TaskDependency dependency(String fromTaskId, String toTaskId) {
        return new TaskDependency(
                fromTaskId,
                toTaskId,
                SemanticRoutingTypes.TaskDependencyType.REQUIRES_SUCCESS,
                SemanticRoutingTypes.DependencyOrigin.USER_EXPLICIT);
    }

    private static Set<SemanticRoutingTypes.RequestedOutput> orderedOutputs(
            SemanticRoutingTypes.RequestedOutput first,
            SemanticRoutingTypes.RequestedOutput second) {
        Set<SemanticRoutingTypes.RequestedOutput> outputs = new LinkedHashSet<>();
        outputs.add(first);
        outputs.add(second);
        return outputs;
    }
}
