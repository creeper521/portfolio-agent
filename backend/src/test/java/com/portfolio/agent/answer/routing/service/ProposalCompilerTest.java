package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TextAnchor;
import com.portfolio.agent.answer.routing.domain.TurnProposal;
import com.portfolio.agent.answer.routing.gateway.TurnInterpretationPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalCompilerTest {

    private final ProposalCompiler compiler = new ProposalCompiler(new SemanticRoutingPolicy());

    @Test
    void compilesAnExplicitPublicFactCandidateIntoAModelPlan() {
        ProposalCompilationResult result = compiler.compile(factProposal("project-a"), input());

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getPlan().orElseThrow().getSource())
                .isEqualTo(com.portfolio.agent.answer.routing.domain.SemanticTurnPlan.PlanSource.MODEL_ASSISTED);
        assertThat(result.getPlan().orElseThrow().getTasks().getFirst().getSubjectReferences())
                .containsExactly(new SubjectReference(
                        SubjectType.PROJECT, "project-a",
                        SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE, "public-v1"));
    }

    @Test
    void rejectsAnInventedCandidateInsteadOfDroppingItAndExecutingTheRest() {
        ProposalCompilationResult result = compiler.compile(factProposal("invented"),
                input("介绍 project-a 和 invented"));

        assertThat(result.isCompiled()).isFalse();
        assertThat(result.getPlan()).isEmpty();
        assertThat(result.getReasonCode()).isEqualTo(
                ProposalCompilationResult.ReasonCode.SUBJECT_NOT_PUBLIC);
    }

    @Test
    void compilesComparisonAndGeneralExplanationWithoutRescanningKeywords() {
        TurnProposal.SubjectCandidate first = candidate("project-a");
        TurnProposal.SubjectCandidate second = candidate("project-b");
        TurnProposal.TaskProposal comparison = new TurnProposal.TaskProposal(
                "task-a", SemanticTaskType.PORTFOLIO_COMPARE, new TextAnchor("project-a", 1),
                List.of(first, second), Set.of(RequestedOutput.COMPARISON));
        TurnProposal.TaskProposal general = new TurnProposal.TaskProposal(
                "task-b", SemanticTaskType.GENERAL_EXPLANATION, new TextAnchor("乐观锁", 1),
                List.of(), Set.of(RequestedOutput.SUMMARY));
        TurnInterpretationPort.TurnInterpretationInput input = input(
                "比较 project-a、project-b 并解释乐观锁", Set.of(
                        SemanticTaskType.PORTFOLIO_COMPARE, SemanticTaskType.GENERAL_EXPLANATION),
                List.of(publicSubject("project-a"), publicSubject("project-b")));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(comparison, general)), input);

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getPlan().orElseThrow().getTasks())
                .extracting(task -> task.getTaskType())
                .containsExactly(SemanticTaskType.PORTFOLIO_COMPARE, SemanticTaskType.GENERAL_EXPLANATION);
    }

    @Test
    void compilesRecommendationFromPublicProjectsWithoutMixingCases() {
        TurnProposal.TaskProposal recommendation = new TurnProposal.TaskProposal(
                "task-a", SemanticTaskType.PORTFOLIO_RECOMMEND, new TextAnchor("推荐", 1),
                List.of(), Set.of(RequestedOutput.RECOMMENDATION));
        TurnInterpretationPort.TurnInterpretationInput input = input(
                "请推荐", Set.of(SemanticTaskType.PORTFOLIO_RECOMMEND), List.of(
                        publicSubject("project-a"), new SubjectReference(
                                SubjectType.CASE, "case-a", SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1")));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(recommendation)), input);

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getPlan().orElseThrow().getTasks().getFirst().getSubjectReferences())
                .allMatch(subject -> subject.getSubjectType() == SubjectType.PROJECT);
    }

    @Test
    void compilesRefinementOnlyFromAnApprovedRecentResultReference() {
        TurnProposal.SubjectCandidate resultCandidate = new TurnProposal.SubjectCandidate(
                SubjectType.RESULT, "result-a", TurnProposal.SubjectBasis.RECENT_RESULT,
                null, "results-a", 1);
        TurnProposal.TaskProposal refinement = new TurnProposal.TaskProposal(
                "task-a", SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION,
                new TextAnchor("调整", 1), List.of(resultCandidate), Set.of(RequestedOutput.RECOMMENDATION));
        TurnInterpretationPort.TurnInterpretationInput input = input("调整推荐", 
                Set.of(SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION), List.of(
                        SubjectReference.result("result-a"), publicSubject("project-a")));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(refinement)), input);

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getPlan().orElseThrow().getTasks().getFirst().getTaskType())
                .isEqualTo(SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION);
    }

    @Test
    void compilesGeneralComparisonFromTwoAnchoredTopics() {
        TurnProposal.TaskProposal comparison = new TurnProposal.TaskProposal(
                "task-a", SemanticTaskType.GENERAL_COMPARISON, new TextAnchor("乐观锁", 1),
                List.of(), Set.of(RequestedOutput.COMPARISON),
                List.of(new TextAnchor("乐观锁", 1), new TextAnchor("悲观锁", 1)), List.of());
        TurnInterpretationPort.TurnInterpretationInput input = input("比较乐观锁和悲观锁",
                Set.of(SemanticTaskType.GENERAL_COMPARISON), List.of(publicSubject("project-a")));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(comparison)), input);

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getPlan().orElseThrow().getTasks().getFirst().getTaskType())
                .isEqualTo(SemanticTaskType.GENERAL_COMPARISON);
    }

    @Test
    void compilesSynthesisAndRebindsLocalDependenciesToServerTaskIds() {
        TurnProposal.TaskProposal fact = new TurnProposal.TaskProposal(
                "fact", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor("project-a", 1),
                List.of(candidate("project-a")), Set.of(RequestedOutput.SUMMARY));
        TurnProposal.TaskProposal explanation = new TurnProposal.TaskProposal(
                "explain", SemanticTaskType.GENERAL_EXPLANATION, new TextAnchor("乐观锁", 1),
                List.of(), Set.of(RequestedOutput.SUMMARY));
        TurnProposal.TaskProposal synthesis = new TurnProposal.TaskProposal(
                "combine", SemanticTaskType.SYNTHESIS, new TextAnchor("总结", 1),
                List.of(), Set.of(RequestedOutput.SUMMARY), List.of(), List.of("fact", "explain"));

        TurnInterpretationPort.TurnInterpretationInput input = input(
                "介绍 project-a、解释乐观锁并总结", Set.of(
                        SemanticTaskType.PORTFOLIO_FACT,
                        SemanticTaskType.GENERAL_EXPLANATION,
                        SemanticTaskType.SYNTHESIS), List.of(publicSubject("project-a")));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(
                List.of(fact, explanation, synthesis), List.of(
                        new TurnProposal.ProposalDependency("fact", "explain", TaskDependencyType.ORDER_AFTER))), input);

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getPlan().orElseThrow().getDependencies())
                .extracting(dependency -> dependency.getFromTaskId() + ":" + dependency.getToTaskId())
                .containsExactlyInAnyOrder("task-01:task-02", "task-01:task-03", "task-02:task-03");
        assertThat(result.getPlan().orElseThrow().getTasks().get(2).getParameters())
                .isInstanceOf(SemanticTaskParameters.Synthesis.class);
        assertThat(result.getPlan().orElseThrow().getTasks().get(2).getFulfillmentRole())
                .isEqualTo(com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole.PRIMARY);
        assertThat(result.getPlan().orElseThrow().getTasks().subList(0, 2))
                .allMatch(task -> task.getFulfillmentRole()
                        == com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole.SUPPORTING);
    }

    @Test
    void rejectsAProposalDependencyCycleAsOneInvalidProposal() {
        TurnProposal.TaskProposal first = new TurnProposal.TaskProposal(
                "first", SemanticTaskType.GENERAL_EXPLANATION, new TextAnchor("乐观锁", 1),
                List.of(), Set.of(RequestedOutput.SUMMARY));
        TurnProposal.TaskProposal second = new TurnProposal.TaskProposal(
                "second", SemanticTaskType.GENERAL_EXPLANATION, new TextAnchor("悲观锁", 1),
                List.of(), Set.of(RequestedOutput.SUMMARY));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(first, second), List.of(
                new TurnProposal.ProposalDependency("first", "second", TaskDependencyType.ORDER_AFTER),
                new TurnProposal.ProposalDependency("second", "first", TaskDependencyType.ORDER_AFTER))), input(
                "乐观锁和悲观锁", Set.of(SemanticTaskType.GENERAL_EXPLANATION), List.of(publicSubject("project-a"))));

        assertThat(result.isCompiled()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(ProposalCompilationResult.ReasonCode.PROPOSAL_INVALID);
    }

    @Test
    void rejectsAProposalTaskTypeOutsideTheServerAllowedSet() {
        TurnProposal.TaskProposal fact = new TurnProposal.TaskProposal(
                "fact", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor("project-a", 1),
                List.of(candidate("project-a")), Set.of(RequestedOutput.SUMMARY));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(fact)), input(
                "介绍 project-a", Set.of(SemanticTaskType.GENERAL_EXPLANATION), List.of(publicSubject("project-a"))));

        assertThat(result.isCompiled()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(ProposalCompilationResult.ReasonCode.TASK_TYPE_UNSUPPORTED);
    }

    @Test
    void mapsTheClosedConciseResponseModeToBriefExplanationDepth() {
        TurnProposal.TaskProposal explanation = new TurnProposal.TaskProposal(
                "explain", SemanticTaskType.GENERAL_EXPLANATION, new TextAnchor("乐观锁", 1),
                List.of(), Set.of(RequestedOutput.SUMMARY), List.of(), List.of(),
                TurnProposal.ResponseMode.CONCISE);

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(explanation)), input(
                "解释乐观锁", Set.of(SemanticTaskType.GENERAL_EXPLANATION), List.of(publicSubject("project-a"))));

        SemanticTaskParameters.GeneralExplanation parameters = (SemanticTaskParameters.GeneralExplanation)
                result.getPlan().orElseThrow().getTasks().getFirst().getParameters();
        assertThat(parameters.getDepth().name()).isEqualTo("BRIEF");
    }

    @Test
    void compilesApprovedFactFacetsInsteadOfReplacingThemWithDefaults() {
        TurnProposal.TaskProposal fact = new TurnProposal.TaskProposal(
                "fact", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor("project-a", 1),
                List.of(candidate("project-a")), Set.of(RequestedOutput.SUMMARY), List.of(), List.of(),
                TurnProposal.ResponseMode.STANDARD, Set.of("RESPONSIBILITY"), Set.of());

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(fact)), input());

        SemanticTaskParameters.PortfolioFact parameters = (SemanticTaskParameters.PortfolioFact)
                result.getPlan().orElseThrow().getTasks().getFirst().getParameters();
        assertThat(parameters.getFacets()).extracting(Enum::name).containsExactly("RESPONSIBILITY");
    }

    @Test
    void compilesRecommendationFiltersAndSizeFromClosedProposalFields() {
        TurnProposal.TaskProposal recommendation = new TurnProposal.TaskProposal(
                "recommend", SemanticTaskType.PORTFOLIO_RECOMMEND, new TextAnchor("推荐", 1),
                List.of(), Set.of(RequestedOutput.RECOMMENDATION), List.of(), List.of(),
                TurnProposal.ResponseMode.STANDARD, Set.of(), Set.of(),
                "FULL_STACK_ENGINEERING", Set.of("VUE", "TYPESCRIPT"), 3);
        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(recommendation)), input(
                "请推荐", Set.of(SemanticTaskType.PORTFOLIO_RECOMMEND), List.of(publicSubject("project-a"))));

        SemanticTaskParameters.PortfolioRecommend parameters = (SemanticTaskParameters.PortfolioRecommend)
                result.getPlan().orElseThrow().getTasks().getFirst().getParameters();
        assertThat(parameters.getCareerTrack().name()).isEqualTo("FULL_STACK_ENGINEERING");
        assertThat(parameters.getCapabilityCodes()).extracting(Enum::name)
                .containsExactlyInAnyOrder("VUE", "TYPESCRIPT");
        assertThat(parameters.getRequestedSize().getValue()).isEqualTo(3);
    }

    @Test
    void rebindsAConfirmedSubjectOnlyWhenItExistsInTheStructuredContext() {
        TurnProposal.SubjectCandidate confirmed = new TurnProposal.SubjectCandidate(
                SubjectType.PROJECT, "project-a", TurnProposal.SubjectBasis.CONFIRMED_SUBJECT,
                null, null, null);
        TurnProposal.TaskProposal fact = new TurnProposal.TaskProposal(
                "fact", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor("介绍", 1),
                List.of(confirmed), Set.of(RequestedOutput.SUMMARY));
        TurnInterpretationPort.TurnInterpretationInput input = new TurnInterpretationPort.TurnInterpretationInput(
                "介绍", List.of(publicSubject("project-a")), Set.of(SemanticTaskType.PORTFOLIO_FACT),
                List.of(publicSubject("project-a")));

        ProposalCompilationResult result = compiler.compile(TurnProposal.execution(List.of(fact)), input);

        assertThat(result.isCompiled()).isTrue();
    }

    @Test
    void rebindsAPendingInteractionSubjectOnlyFromStructuredContext() {
        TurnProposal.SubjectCandidate pending = new TurnProposal.SubjectCandidate(
                SubjectType.PROJECT, "project-a", TurnProposal.SubjectBasis.PENDING_INTERACTION,
                null, null, null);
        TurnProposal.TaskProposal fact = new TurnProposal.TaskProposal(
                "fact", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor("继续", 1),
                List.of(pending), Set.of(RequestedOutput.SUMMARY));
        TurnInterpretationPort.TurnInterpretationInput input = new TurnInterpretationPort.TurnInterpretationInput(
                "继续", List.of(publicSubject("project-a")), Set.of(SemanticTaskType.PORTFOLIO_FACT),
                List.of(), List.of(publicSubject("project-a")));

        assertThat(compiler.compile(TurnProposal.execution(List.of(fact)), input).isCompiled()).isTrue();
    }

    @Test
    void rebindsPageHintOnlyWhenTheCurrentInputUsesAnApprovedTypedMarker() {
        TurnProposal.SubjectCandidate pageHint = new TurnProposal.SubjectCandidate(
                SubjectType.PROJECT, "project-a", TurnProposal.SubjectBasis.PAGE_HINT,
                new TextAnchor("这个项目", 1), null, null);
        TurnProposal.TaskProposal fact = new TurnProposal.TaskProposal(
                "fact", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor("介绍", 1),
                List.of(pageHint), Set.of(RequestedOutput.SUMMARY));
        TurnInterpretationPort.TurnInterpretationInput input = new TurnInterpretationPort.TurnInterpretationInput(
                "介绍这个项目", List.of(publicSubject("project-a")), Set.of(SemanticTaskType.PORTFOLIO_FACT),
                List.of(), List.of(), publicSubject("project-a"));

        assertThat(compiler.compile(TurnProposal.execution(List.of(fact)), input).isCompiled()).isTrue();
    }

    private TurnProposal factProposal(String subjectId) {
        TurnProposal.SubjectCandidate candidate = candidate(subjectId);
        TurnProposal.TaskProposal task = new TurnProposal.TaskProposal(
                "task-a", SemanticTaskType.PORTFOLIO_FACT, new TextAnchor(subjectId, 1),
                List.of(candidate), Set.of(RequestedOutput.SUMMARY));
        return TurnProposal.execution(List.of(task));
    }

    private TurnInterpretationPort.TurnInterpretationInput input() {
        return input("介绍 project-a");
    }

    private TurnInterpretationPort.TurnInterpretationInput input(String currentInput) {
        return input(currentInput, Set.of(SemanticTaskType.PORTFOLIO_FACT), List.of(publicSubject("project-a")));
    }

    private TurnInterpretationPort.TurnInterpretationInput input(
            String currentInput,
            Set<SemanticTaskType> allowedTaskTypes,
            List<SubjectReference> publicSubjects) {
        return new TurnInterpretationPort.TurnInterpretationInput(
                currentInput, publicSubjects, allowedTaskTypes);
    }

    private TurnProposal.SubjectCandidate candidate(String subjectId) {
        return new TurnProposal.SubjectCandidate(SubjectType.PROJECT, subjectId,
                TurnProposal.SubjectBasis.EXPLICIT_INPUT, new TextAnchor(subjectId, 1), null, null);
    }

    private SubjectReference publicSubject(String subjectId) {
        return new SubjectReference(SubjectType.PROJECT, subjectId,
                SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1");
    }
}
