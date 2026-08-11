package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticTaskContractTest {

    @Test
    void portfolioCompareRequiresTypedParametersAndDefensiveSubjects() {
        List<SubjectReference> subjects = new ArrayList<>();
        subjects.add(SubjectReference.project("project-a", "public-v1"));
        subjects.add(SubjectReference.project("project-b", "public-v1"));
        SemanticTaskParameters.PortfolioCompare parameters =
                new SemanticTaskParameters.PortfolioCompare(
                        subjects, Set.of("ARCHITECTURE"), "INTERVIEWER");

        SemanticTask task = SemanticTask.create(
                "task-01",
                SemanticTaskType.PORTFOLIO_COMPARE,
                TaskSourceDomain.PORTFOLIO,
                "比较两个项目",
                parameters,
                Set.of(RequestedOutput.COMPARISON),
                TaskConfidence.highRule(),
                subjects);
        subjects.clear();

        assertEquals(2, task.getSubjectReferences().size());
        assertEquals(Set.of(ComparisonDimension.ARCHITECTURE), parameters.getDimensions());
        assertThrows(UnsupportedOperationException.class,
                () -> task.getSubjectReferences().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> task.getRequestedOutputs().clear());
        assertEquals(task, SemanticTask.create(
                "task-01",
                SemanticTaskType.PORTFOLIO_COMPARE,
                TaskSourceDomain.PORTFOLIO,
                "比较两个项目",
                parameters,
                Set.of(RequestedOutput.COMPARISON),
                TaskConfidence.highRule(),
                parameters.getSubjects()));
        assertEquals(task.hashCode(), SemanticTask.create(
                "task-01",
                SemanticTaskType.PORTFOLIO_COMPARE,
                TaskSourceDomain.PORTFOLIO,
                "比较两个项目",
                parameters,
                Set.of(RequestedOutput.COMPARISON),
                TaskConfidence.highRule(),
                parameters.getSubjects()).hashCode());
    }

    @Test
    void taskFactoryRejectsMismatchedTypeSourceAndParameters() {
        SemanticTaskParameters.PortfolioCompare comparison =
                new SemanticTaskParameters.PortfolioCompare(
                        List.of(
                                SubjectReference.project("project-a", "public-v1"),
                                SubjectReference.project("project-b", "public-v1")),
                        Set.of("ARCHITECTURE"),
                        "INTERVIEWER");

        assertThrows(IllegalArgumentException.class, () -> SemanticTask.create(
                "task-01",
                SemanticTaskType.PORTFOLIO_FACT,
                TaskSourceDomain.PORTFOLIO,
                "说明项目",
                comparison,
                Set.of(RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                comparison.getSubjects()));
        assertThrows(IllegalArgumentException.class, () -> SemanticTask.create(
                "task-02",
                SemanticTaskType.PORTFOLIO_COMPARE,
                TaskSourceDomain.GENERAL,
                "比较项目",
                comparison,
                Set.of(RequestedOutput.COMPARISON),
                TaskConfidence.highRule(),
                comparison.getSubjects()));
    }

    @Test
    void synthesisRequiresSynthesisSourceAndAtLeastTwoUpstreamReferences() {
        SemanticTaskParameters.Synthesis validParameters =
                new SemanticTaskParameters.Synthesis(
                        List.of("task-01", "task-02"), "形成综合结论", Set.of("ARCHITECTURE"));

        assertThrows(IllegalArgumentException.class, () -> SemanticTask.create(
                "task-03",
                SemanticTaskType.SYNTHESIS,
                TaskSourceDomain.PORTFOLIO,
                "形成综合结论",
                validParameters,
                Set.of(RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SemanticTaskParameters.Synthesis(
                List.of("task-01"), "形成综合结论", Set.of("ARCHITECTURE")));
    }

    @Test
    void compareParametersRequireTwoToThreeSubjects() {
        assertThrows(IllegalArgumentException.class, () -> new SemanticTaskParameters.PortfolioCompare(
                List.of(SubjectReference.project("project-a", "public-v1")),
                Set.of("ARCHITECTURE"),
                "INTERVIEWER"));
        assertThrows(IllegalArgumentException.class, () -> new SemanticTaskParameters.GeneralComparison(
                List.of("Java", "Kotlin", "Go", "Rust"),
                Set.of("ARCHITECTURE"),
                "STANDARD",
                "INTERVIEWER"));
        assertThrows(IllegalArgumentException.class, () -> new SemanticTaskParameters.GeneralComparison(
                List.of("Java", "Java"),
                Set.of("ARCHITECTURE"),
                "STANDARD",
                "INTERVIEWER"));
    }

    @Test
    void taskSubjectReferencesMustMatchTypedParameterSubjects() {
        SemanticTaskParameters.PortfolioFact fact = new SemanticTaskParameters.PortfolioFact(
                SubjectReference.project("project-a", "public-v1"), Set.of(), "INTERVIEWER");
        SemanticTaskParameters.PortfolioCompare comparison = new SemanticTaskParameters.PortfolioCompare(
                List.of(
                        SubjectReference.project("project-a", "public-v1"),
                        SubjectReference.project("project-b", "public-v1")),
                Set.of("ARCHITECTURE"), "INTERVIEWER");

        assertThrows(IllegalArgumentException.class, () -> SemanticTask.create(
                "task-fact",
                SemanticTaskType.PORTFOLIO_FACT,
                TaskSourceDomain.PORTFOLIO,
                "说明项目",
                fact,
                Set.of(RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> SemanticTask.create(
                "task-compare",
                SemanticTaskType.PORTFOLIO_COMPARE,
                TaskSourceDomain.PORTFOLIO,
                "比较项目",
                comparison,
                Set.of(RequestedOutput.COMPARISON),
                TaskConfidence.highRule(),
                List.of(SubjectReference.project("project-a", "public-v1"))));
    }

    @ParameterizedTest(name = "valid task combination {0}")
    @MethodSource("validTaskCombinations")
    void allTaskTypeSourceParameterCombinationsAreAccepted(SemanticTask task) {
        assertNotNull(task);
        assertDoesNotThrow(task::getParameters);
    }

    private static Stream<SemanticTask> validTaskCombinations() {
        SubjectReference project = SubjectReference.project("project-a", "public-v1");
        SubjectReference secondProject = SubjectReference.project("project-b", "public-v1");
        return Stream.of(
                SemanticTask.create("fact", SemanticTaskType.PORTFOLIO_FACT, TaskSourceDomain.PORTFOLIO,
                        "说明项目", new SemanticTaskParameters.PortfolioFact(project, Set.of(), "INTERVIEWER"),
                        Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(project)),
                SemanticTask.create("compare", SemanticTaskType.PORTFOLIO_COMPARE, TaskSourceDomain.PORTFOLIO,
                        "比较项目", new SemanticTaskParameters.PortfolioCompare(
                                List.of(project, secondProject), Set.of("ARCHITECTURE"), "INTERVIEWER"),
                        Set.of(RequestedOutput.COMPARISON), TaskConfidence.highRule(), List.of(project, secondProject)),
                SemanticTask.create("recommend", SemanticTaskType.PORTFOLIO_RECOMMEND, TaskSourceDomain.PORTFOLIO,
                        "推荐项目", new SemanticTaskParameters.PortfolioRecommend(
                                List.of(project), "BACKEND_ENGINEERING", Set.of("JAVA"), "面试准备", 2, "INTERVIEWER"),
                        Set.of(RequestedOutput.RECOMMENDATION), TaskConfidence.highRule(), List.of(project)),
                SemanticTask.create("refine", SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION,
                        TaskSourceDomain.PORTFOLIO, "调整推荐", new SemanticTaskParameters.PortfolioRefinement(
                                SubjectReference.result("result-1"), Set.of(), Set.of()),
                        Set.of(RequestedOutput.RECOMMENDATION), TaskConfidence.highRule(),
                        List.of(SubjectReference.result("result-1"))),
                SemanticTask.create("explain", SemanticTaskType.GENERAL_EXPLANATION, TaskSourceDomain.GENERAL,
                        "解释概念", new SemanticTaskParameters.GeneralExplanation(
                                "依赖注入", "STANDARD", "INTERVIEWER"),
                        Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of()),
                SemanticTask.create("general-compare", SemanticTaskType.GENERAL_COMPARISON,
                        TaskSourceDomain.GENERAL, "比较概念", new SemanticTaskParameters.GeneralComparison(
                                List.of("Java", "Kotlin"), Set.of("ARCHITECTURE"), "STANDARD", "INTERVIEWER"),
                        Set.of(RequestedOutput.COMPARISON), TaskConfidence.highRule(), List.of()),
                SemanticTask.create("synthesis", SemanticTaskType.SYNTHESIS, TaskSourceDomain.SYNTHESIS,
                        "综合结论", new SemanticTaskParameters.Synthesis(
                                List.of("fact", "compare"), "综合结果", Set.of()),
                        Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of()));
    }

    @Test
    void taskConfidenceFieldLevelsAreImmutable() {
        TaskConfidence confidence = new TaskConfidence(
                SemanticRoutingTypes.ConfidenceLevel.MEDIUM,
                Map.of(SemanticRoutingTypes.ConfidenceField.SUBJECTS,
                        SemanticRoutingTypes.ConfidenceLevel.HIGH),
                SemanticRoutingTypes.ConfidenceOrigin.RULE);

        assertThrows(UnsupportedOperationException.class,
                () -> confidence.getFieldLevels().clear());
    }

    @Test
    void supportingValueObjectsHaveValueSemanticsAndRedactedStringForms() {
        SubjectReference reference = SubjectReference.project("project-a", "public-v1");
        SubjectReference equalReference = SubjectReference.project("project-a", "public-v1");
        TaskDependency dependency = new TaskDependency(
                "task-01", "task-02", TaskDependencyType.REQUIRES_SUCCESS,
                DependencyOrigin.USER_EXPLICIT);
        PlanExclusion exclusion = PlanExclusion.planOutput(RequestedOutput.EVIDENCE);

        assertEquals(reference, equalReference);
        assertEquals(reference.hashCode(), equalReference.hashCode());
        assertEquals(dependency, new TaskDependency(
                "task-01", "task-02", TaskDependencyType.REQUIRES_SUCCESS,
                DependencyOrigin.USER_EXPLICIT));
        assertEquals(exclusion, PlanExclusion.planOutput(RequestedOutput.EVIDENCE));
        assertFalse(reference.toString().contains("project-a"));
        assertFalse(reference.toString().contains("public-v1"));
        assertFalse(dependency.toString().contains("task-01"));
        assertFalse(dependency.toString().contains("task-02"));
        assertFalse(exclusion.toString().contains("EVIDENCE"));
        assertTrue(TaskConfidence.highRule().toString().contains("HIGH"));
    }
}
