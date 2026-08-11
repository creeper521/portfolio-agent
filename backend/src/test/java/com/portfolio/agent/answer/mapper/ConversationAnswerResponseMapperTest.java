package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.service.ClarificationRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerResponseMapperTest {

    @Test
    void mapsConfirmationToAwaitingOnlyForStpV1AndKeepsDisplayPlanFreeOfInternalIds() throws Exception {
        AgentTurnResult agentTurn = AgentTurnResult.confirmationRequired(
                confirmationPlan(), confirmationChallenge(), true);
        ConversationAnswerResult result = answerResult().withAgentTurn(agentTurn)
                .withContractIdentity("preset-1", "pcv1-0123456789abcdef");

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(result);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
        com.fasterxml.jackson.databind.JsonNode displayPlan = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json).path("agentTurn").path("plan");

        assertThat(response.getResolution()).isEqualTo(AnswerResolution.AWAITING_CONFIRMATION);
        assertThat(json).contains("\"contractVersion\":\"pcv1-0123456789abcdef\"")
                .contains("\"contractVersion\":\"stp-v1\"")
                .contains("\"confirmationPlan\":\"opaque-envelope\"");
        assertThat(displayPlan.toString()).doesNotContain("task-01", "REQUIRES_SUCCESS", "sha256:");
    }

    @Test
    void keepsLegacyConfirmationProjectionAtNeedsClarification() {
        ConversationAnswerResult result = answerResult().withAgentTurn(
                AgentTurnResult.confirmationRequired(
                        confirmationPlan(), confirmationChallenge(), false));

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(result);

        assertThat(response.getResolution()).isEqualTo(AnswerResolution.NEEDS_CLARIFICATION);
        assertThat(response.getBlocks()).isEmpty();
    }

    @Test
    void mapsClarificationCorrelationAndClosedSubjectResolution() {
        ClarificationRequest clarification = new ClarificationRequest(
                "clarify-0123456789abcdef0123456789abcdef",
                ClarificationRequest.Scope.CRITICAL,
                "ROUTING_SUBJECT_CLARIFICATION_REQUIRED",
                "请选择公开主体",
                List.of(new ClarificationRequest.Field(
                        "subject", ClarificationRequest.InputMode.SINGLE_CHOICE,
                        List.of(new ClarificationRequest.Option(
                                "project-a", "Project A", "PROJECT", "project-a")),
                        true, List.of("继续当前请求"))),
                1, 0, List.of(),
                List.of(new ClarificationRequest.BlockedGoal(
                        "继续当前请求", "WAITING_FOR_SUBJECT")));

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(
                answerResult().withAgentTurn(AgentTurnResult.clarificationRequired(clarification)));

        var mapped = response.getAgentTurn().getClarification();
        assertThat(mapped.getClarificationId())
                .isEqualTo("clarify-0123456789abcdef0123456789abcdef");
        assertThat(mapped.getFields().getFirst().getOptions().getFirst().getResolution().getKind())
                .isEqualTo("SUBJECT_REFERENCE");
        assertThat(mapped.getFields().getFirst().getOptions().getFirst().getResolution().getSubjectType())
                .isEqualTo("PROJECT");
    }

    @Test
    void mapsOnlyCompletedTaskBodiesWhenThePlanHasBlockedTasks() throws Exception {
        TaskOutcome answered = TaskOutcome.answered(
                "task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("安全完成的正文"), "摘要"),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, List.of(), List.of()), false);
        TaskOutcome blocked = TaskOutcome.blocked(
                "task-02", SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                "EXECUTION_DEPENDENCY_BLOCKED");
        ConversationAnswerResult result = answerResult().withAgentTurn(AgentTurnResult.ready(
                partialPlan(), new SemanticTurnOutcome(List.of(answered, blocked))));

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(result);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);

        assertThat(response.getBlocks()).extracting(block -> block.getContent())
                .containsExactly("安全完成的正文");
        assertThat(response.getAgentTurn().getCompletedTasks()).hasSize(1);
        assertThat(response.getAgentTurn().getOutcome().getTaskSummary().getItems())
                .extracting(com.portfolio.agent.answer.dto.response.TaskSummaryResponse.Item::getStatus)
                .containsExactly("COMPLETED", "BLOCKED");
        assertThat(response.getAgentTurn().getOutcome().getTaskSummary().getItems().get(1).getReasonCodes())
                .containsExactly("EXECUTION_DEPENDENCY_BLOCKED");
        assertThat(json).doesNotContain("task-01", "task-02");
    }

    @Test
    void mapsCompleteRecommendationWithoutExposingFreeTextGoal() {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "public-2026-07-31", "BACKEND", "INTERVIEWER", Set.of("RAG", "POSTGRESQL"),
                2, List.of("project-1"));
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                context.getRecommendationBatchId(), context,
                List.of(new PortfolioRecommendationItem(
                        "project-1", "Public project", "/portfolio/project-1",
                        List.of("covers RAG"), List.of("evidence-1"))),
                List.of("RAG"), List.of("KUBERNETES"));
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-2026-07-31", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false, GenerationMode.DETERMINISTIC, null, null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING), recommendation);

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(result);

        assertThat(response.getPortfolioRecommendation().getRecommendationBatchId())
                .isEqualTo(context.getRecommendationBatchId());
        assertThat(response.getPortfolioRecommendation().getContext().getContentVersion())
                .isEqualTo("public-2026-07-31");
        assertThat(response.getPortfolioRecommendation().getContext().getCapabilityCodes())
                .containsExactlyInAnyOrder("RAG", "POSTGRESQL");
        assertThat(response.getPortfolioRecommendation().getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getPortfolioId()).isEqualTo("project-1");
                    assertThat(item.getEvidenceIds()).containsExactly("evidence-1");
                });
    }

    @Test
    void serializesDisplayPlanOutcomeAndConfirmationTriggerCodesForFrontendWireContract() throws Exception {
        AgentTurnResult agentTurn = AgentTurnResult.confirmationRequired(
                confirmationPlan(), confirmationChallenge(), true);
        ConversationAnswerResponse response = new ConversationAnswerResponseMapper()
                .toResponse(answerResult().withAgentTurn(agentTurn));
        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response))
                .path("agentTurn");

        assertThat(json.path("plan").path("taskCount").asInt()).isEqualTo(1);
        assertThat(json.path("plan").path("tasks").isArray()).isTrue();
        assertThat(json.path("planConfirmation").path("triggerCodes").isArray()).isTrue();
        assertThat(json.has("outcome")).isFalse();
    }

    @Test
    void hidesSummaryForSingleSuccessfulTask() {
        TaskOutcome answered = TaskOutcome.answered(
                "task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("content"), null),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, List.of(), List.of()), false);
        AgentTurnResult agentTurn = AgentTurnResult.ready(
                new SemanticTurnPlan("plan-1", "public-1", SemanticTurnPlan.PlanSource.RULE,
                        List.of(portfolioFact()), List.of(), List.of(), Set.of(),
                        SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation()),
                new SemanticTurnOutcome(List.of(answered)));
        ConversationAnswerResponse response = new ConversationAnswerResponseMapper()
                .toResponse(answerResult().withAgentTurn(agentTurn));

        assertThat(response.getAgentTurn().getOutcome().getTaskSummary().getDisplayMode())
                .isEqualTo("HIDDEN");
    }

    @Test
    void serializesTypedSectionRecommendationAndSynthesisPayloads() throws Exception {
        SemanticTask portfolio = portfolioFact();
        SemanticTask general = SemanticTask.create(
                "task-02", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "general",
                new SemanticTaskParameters.GeneralExplanation("general", "BRIEF", "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());
        SemanticTask synthesis = SemanticTask.create(
                "task-03", SemanticRoutingTypes.SemanticTaskType.SYNTHESIS,
                SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS, "synthesis",
                new SemanticTaskParameters.Synthesis(List.of("task-01", "task-02"), "SUMMARY", Set.of()),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());
        TaskOutcome section = TaskOutcome.answered("task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("section"), null),
                TaskResultProvenance.direct(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, List.of(), List.of()), false);
        TaskOutcome recommendation = TaskOutcome.answered("task-02", SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                new TaskResultPayload.RecommendationResultPayload("recommendation", List.of("support")),
                TaskResultProvenance.direct(SemanticRoutingTypes.TaskSourceDomain.GENERAL, List.of(), List.of()), false);
        TaskOutcome synthesisOutcome = TaskOutcome.answered("task-03", SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS,
                new TaskResultPayload.SynthesisResultPayload(List.of("synthesis"),
                        TaskResultProvenance.synthesized(Set.of(
                                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                                SemanticRoutingTypes.TaskSourceDomain.GENERAL),
                                List.of("task-01", "task-02"), List.of(), List.of())),
                TaskResultProvenance.synthesized(Set.of(
                                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                                SemanticRoutingTypes.TaskSourceDomain.GENERAL),
                        List.of("task-01", "task-02"), List.of(), List.of()), false);
        SemanticTurnPlan plan = new SemanticTurnPlan("plan-wire", "public-1", SemanticTurnPlan.PlanSource.RULE,
                List.of(portfolio, general, synthesis), List.of(), List.of(), Set.of(),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        ConversationAnswerResponse response = new ConversationAnswerResponseMapper()
                .toResponse(answerResult().withAgentTurn(AgentTurnResult.ready(
                        plan, new SemanticTurnOutcome(List.of(section, recommendation, synthesisOutcome)))));
        com.fasterxml.jackson.databind.JsonNode tasks = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response))
                .path("agentTurn").path("completedTasks");

        assertThat(tasks.get(0).path("resultPayload").path("kind").asText()).isEqualTo("SECTION_RESULT");
        assertThat(tasks.get(0).path("resultPayload").path("blocks").isArray()).isTrue();
        assertThat(tasks.get(1).path("resultPayload").path("kind").asText()).isEqualTo("RECOMMENDATION_RESULT");
        assertThat(tasks.get(1).path("resultPayload").path("recommendations").isArray()).isTrue();
        assertThat(tasks.get(2).path("resultPayload").path("kind").asText()).isEqualTo("SYNTHESIS_RESULT");
        assertThat(tasks.get(2).path("resultPayload").path("originDomains").toString())
                .contains("PORTFOLIO", "GENERAL");
    }

    @Test
    void projectsLegacyRecommendationOnlyWhenExactlyOneRecommendationTaskExists() throws Exception {
        TaskOutcome one = recommendationOutcome("task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO);
        TaskOutcome blocked = TaskOutcome.blocked(
                "task-02", SemanticRoutingTypes.TaskSourceDomain.GENERAL, "DEPENDENCY_BLOCKED");
        TaskOutcome section = TaskOutcome.answered(
                "task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("section"), null),
                TaskResultProvenance.direct(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, List.of(), List.of()), false);
        AgentTurnResult noRecommendations = AgentTurnResult.ready(partialPlan(),
                new SemanticTurnOutcome(List.of(section, blocked)));
        AgentTurnResult oneRecommendation = AgentTurnResult.ready(partialPlan(),
                new SemanticTurnOutcome(List.of(one, blocked)));

        String zeroJson = recommendationAnswer(noRecommendations);
        String oneJson = recommendationAnswer(oneRecommendation);

        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(zeroJson)
                .path("portfolioRecommendation").isMissingNode()).isTrue();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(oneJson)
                .path("portfolioRecommendation").isObject()).isTrue();
    }

    @Test
    void doesNotDuplicateLegacyRecommendationAcrossMultipleRecommendationTasks() throws Exception {
        TaskOutcome first = recommendationOutcome("task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO);
        TaskOutcome second = recommendationOutcome("task-02", SemanticRoutingTypes.TaskSourceDomain.GENERAL);
        String json = recommendationAnswerWithPlan(
                new SemanticTurnPlan("plan-recs", "public-1", SemanticTurnPlan.PlanSource.RULE,
                        List.of(portfolioFact(), generalTask()), List.of(), List.of(), Set.of(),
                        SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation()),
                new SemanticTurnOutcome(List.of(first, second)));
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json);

        assertThat(root.path("portfolioRecommendation").isMissingNode()).isTrue();
        assertThat(root.path("agentTurn").path("completedTasks").get(0)
                .path("resultPayload").path("recommendations")).isEmpty();
        assertThat(root.path("agentTurn").path("completedTasks").get(1)
                .path("resultPayload").path("recommendations")).isEmpty();
    }

    @Test
    void mapsEachSemanticRecommendationPayloadWithoutCopyingAnAggregateRecommendation() {
        TaskOutcome first = typedRecommendationOutcome(
                "task-01", "project-a", "Project A", "/portfolio/project-a");
        TaskOutcome second = typedRecommendationOutcome(
                "task-02", "project-b", "Project B", "/portfolio/project-b");
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-typed-recommendations", "public-1", SemanticTurnPlan.PlanSource.RULE,
                List.of(portfolioFact(), generalTask()), List.of(), List.of(), Set.of(),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(
                answerResult().withAgentTurn(AgentTurnResult.ready(
                        plan, new SemanticTurnOutcome(List.of(first, second)))));

        assertThat(response.getPortfolioRecommendation()).isNull();
        assertThat(response.getAgentTurn().getCompletedTasks()).extracting(
                        task -> task.getResultPayload().getRecommendations())
                .allSatisfy(items -> assertThat(items).hasSize(1));
        assertThat(response.getAgentTurn().getCompletedTasks().get(0).getResultPayload()
                .getRecommendations().get(0).getPortfolioId()).isEqualTo("project-a");
        assertThat(response.getAgentTurn().getCompletedTasks().get(1).getResultPayload()
                .getRecommendations().get(0).getPortfolioId()).isEqualTo("project-b");
    }

    @Test
    void projectsSingleTypedRecommendationAndPreservesItsSupportingBlocks() {
        TaskResultPayload.RecommendationItem item = new TaskResultPayload.RecommendationItem(
                "project-a", "Project A", "/portfolio/project-a",
                List.of("covers RAG"), List.of("evidence-1"));
        TaskResultPayload.RecommendationProjection projection =
                new TaskResultPayload.RecommendationProjection(
                        "rec-public-1", "public-1", "BACKEND", "INTERVIEWER",
                        Set.of("RAG"), 2, List.of("project-a"), List.of(item),
                        List.of("RAG"), List.of("KUBERNETES"));
        TaskOutcome recommendation = TaskOutcome.answered(
                "task-01", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.RecommendationResultPayload(
                        projection, List.of("有证据支持的推荐说明")),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        List.of("claim-1"), List.of("evidence-1")), false);
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-single-typed-recommendation", "public-1",
                SemanticTurnPlan.PlanSource.RULE, List.of(portfolioFact()),
                List.of(), List.of(), Set.of(),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(
                answerResult().withAgentTurn(AgentTurnResult.ready(
                        plan, new SemanticTurnOutcome(List.of(recommendation)))));

        assertThat(response.getPortfolioRecommendation()).isNotNull();
        assertThat(response.getPortfolioRecommendation().getRecommendationBatchId())
                .isEqualTo("rec-public-1");
        assertThat(response.getPortfolioRecommendation().getItems()).singleElement()
                .satisfies(value -> assertThat(value.getPortfolioId()).isEqualTo("project-a"));
        assertThat(response.getAgentTurn().getCompletedTasks()).singleElement()
                .satisfies(completed -> {
                    assertThat(completed.getResultPayload().getBlocks()).singleElement()
                            .satisfies(block -> {
                                assertThat(block.getContent()).isEqualTo("有证据支持的推荐说明");
                                assertThat(block.getClaimIds()).containsExactly("claim-1");
                                assertThat(block.getEvidenceIds()).containsExactly("evidence-1");
                            });
                    assertThat(completed.getResultPayload().getRecommendations()).hasSize(1);
                });
    }

    private String recommendationAnswer(AgentTurnResult agentTurn) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new ConversationAnswerResponseMapper().toResponse(
                        answerWithRecommendation(agentTurn)));
    }

    private String recommendationAnswerWithPlan(
            SemanticTurnPlan plan, SemanticTurnOutcome outcome) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new ConversationAnswerResponseMapper().toResponse(
                        answerWithRecommendation(AgentTurnResult.ready(plan, outcome))));
    }

    private ConversationAnswerResult answerWithRecommendation(AgentTurnResult agentTurn) {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "public-1", "BACKEND", "INTERVIEWER", Set.of("RAG"), 2, List.of());
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                context.getRecommendationBatchId(), context,
                List.of(new PortfolioRecommendationItem(
                        "project-1", "Public project", "/portfolio/project-1",
                        List.of("covers RAG"), List.of("evidence-1"))),
                List.of("RAG"), List.of());
        return new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false, GenerationMode.DETERMINISTIC, null, null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING), recommendation)
                .withAgentTurn(agentTurn);
    }

    private static TaskOutcome recommendationOutcome(
            String taskId, SemanticRoutingTypes.TaskSourceDomain sourceDomain) {
        return TaskOutcome.answered(taskId, sourceDomain,
                new TaskResultPayload.RecommendationResultPayload("recommendation", List.of("support")),
                TaskResultProvenance.direct(sourceDomain, List.of(), List.of()), false);
    }

    private static TaskOutcome typedRecommendationOutcome(
            String taskId, String portfolioId, String title, String route) {
        return TaskOutcome.answered(taskId, SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.RecommendationResultPayload(
                        List.of(new TaskResultPayload.RecommendationItem(
                                portfolioId, title, route, List.of("match"), List.of("evidence-1"))),
                        List.of("support")),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, List.of(), List.of()), false);
    }

    private static SemanticTask generalTask() {
        return SemanticTask.create(
                "task-02", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "general",
                new SemanticTaskParameters.GeneralExplanation("general", "BRIEF", "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());
    }

    private static ConversationAnswerResult answerResult() {
        return new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false);
    }

    private static SemanticTurnPlan confirmationPlan() {
        SemanticTask task = portfolioFact();
        return new SemanticTurnPlan(
                "plan-1", "public-1", SemanticTurnPlan.PlanSource.RULE, List.of(task), List.of(),
                List.of(), Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.confirmationRequired(Set.of(
                        SemanticTurnPlan.ConfirmationTrigger.TASK_COUNT_REQUIRES_CONFIRMATION)));
    }

    private static SemanticTurnPlan partialPlan() {
        SemanticTask fact = portfolioFact();
        SemanticTask general = SemanticTask.create(
                "task-02", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "解释恢复策略",
                new SemanticTaskParameters.GeneralExplanation("恢复策略", "BRIEF", "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());
        return new SemanticTurnPlan(
                "plan-2", "public-1", SemanticTurnPlan.PlanSource.RULE, List.of(fact, general), List.of(),
                List.of(), Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
    }

    private static SemanticTask portfolioFact() {
        SubjectReference subject = SubjectReference.project("project-a", "public-1");
        return SemanticTask.create(
                "task-01", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "审阅 SQL 项目",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("OVERVIEW"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(),
                List.of(subject));
    }

    private static PlanConfirmation.Challenge confirmationChallenge() {
        PlanConfirmation.Identity identity = new PlanConfirmation.Identity(
                "confirm-1", Instant.parse("2026-08-10T00:00:00Z"),
                Instant.parse("2026-08-10T00:10:00Z"), "sha256:plan");
        return new PlanConfirmation.Challenge(identity, "opaque-envelope", "opaque-token");
    }
}
