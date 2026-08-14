package com.portfolio.agent.answer.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.ContextInvalidation;
import com.portfolio.agent.answer.domain.ContextResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class P5PublicContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesContextInvalidationAsAnAnswerWithARecoverableV2Disposition() throws Exception {
        AgentTurnResult turn = AgentTurnResult.contextInvalidated(
                new ContextInvalidation("CONTEXT_RESULT_STALE", "RESTART_FROM_CURRENT_CONTENT",
                        com.portfolio.agent.answer.context.domain.ConversationContextType.RECENT_SEMANTIC_TASK,
                        "public-2"),
                null);
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-invalidated", "public-2", ConversationIntent.GENERAL_KNOWLEDGE,
                ConversationAnswerScope.GLOBAL, AnswerResolution.NEEDS_CLARIFICATION,
                "Context invalidated", List.of(), List.of(), false)
                .withAgentTurn(turn);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new ConversationAnswerResponseMapper().toResponse(result)));

        assertThat(json.path("responseKind").asText()).isEqualTo("ANSWER");
        assertThat(json.path("resolution").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(json.path("blocks")).isEmpty();
        assertThat(json.path("contextInvalidation").path("reasonCode").asText())
                .isEqualTo("CONTEXT_RESULT_STALE");
        assertThat(json.has("contextResolution")).isFalse();
        assertThat(json.path("agentTurn").path("disposition").asText())
                .isEqualTo("CONTEXT_INVALIDATED");
    }

    @Test
    void serializesTheAuthoritativeStpV2ProjectionFromRealDomainResults() throws Exception {
        PublicSourceReferenceValue source = new PublicSourceReferenceValue(
                "evidence-project-a", "Project A evidence", "public-1", "DOCUMENT",
                "/projects/project-a", "/evidence/evidence-project-a");
        SemanticTask general = SemanticTask.create(
                "task-general", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "Explain concurrency",
                new SemanticTaskParameters.GeneralExplanation("Explain concurrency", "BRIEF", "GUEST"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(),
                TaskFulfillmentRole.SUPPORTING);
        SemanticTask portfolio = SemanticTask.create(
                "task-portfolio", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "Inspect Project A",
                new SemanticTaskParameters.PortfolioFact(
                        SubjectReference.project("project-a", "public-1"), Set.of("OVERVIEW"), "GUEST"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(),
                List.of(SubjectReference.project("project-a", "public-1")), TaskFulfillmentRole.SUPPORTING);
        SemanticTask synthesis = SemanticTask.create(
                "task-synthesis", SemanticRoutingTypes.SemanticTaskType.SYNTHESIS,
                SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS, "Relate the explanation to Project A",
                new SemanticTaskParameters.Synthesis(
                        List.of("task-general", "task-portfolio"), "RELATION", Set.of()),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(),
                TaskFulfillmentRole.PRIMARY);
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-p5", "public-1", SemanticTurnPlan.PlanSource.RULE,
                List.of(general, portfolio, synthesis), List.of(), List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());

        TaskOutcome generalOutcome = TaskOutcome.answered(
                general.getTaskId(), general.getSourceDomain(),
                new TaskResultPayload.SectionResultPayload(List.of("Concurrency is controlled coordination."), null),
                TaskResultProvenance.direct(general.getSourceDomain(), List.of(), List.of()), false)
                .withFulfillmentRole(general.getFulfillmentRole());
        TaskOutcome portfolioOutcome = TaskOutcome.answered(
                portfolio.getTaskId(), portfolio.getSourceDomain(),
                TaskResultPayload.SectionResultPayload.fromSections(List.of(
                        new TaskResultPayload.SectionBlock(AnswerSectionType.VERIFICATION, "Project evidence",
                                "Project A uses concurrency.",
                                List.of("claim-project-a"), List.of("evidence-project-a"), List.of(source))), null),
                TaskResultProvenance.direct(portfolio.getSourceDomain(),
                        List.of("claim-project-a"), List.of("evidence-project-a")), false)
                .withFulfillmentRole(portfolio.getFulfillmentRole());
        TaskResultProvenance synthesisProvenance = TaskResultProvenance.synthesized(
                Set.of(SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO),
                List.of(general.getTaskId(), portfolio.getTaskId()),
                List.of("claim-project-a"), List.of("evidence-project-a"));
        TaskOutcome synthesisOutcome = TaskOutcome.answered(
                synthesis.getTaskId(), synthesis.getSourceDomain(),
                new TaskResultPayload.SynthesisResultPayload(
                        List.of("Project A illustrates the concurrency concept."), synthesisProvenance),
                synthesisProvenance, false)
                .withFulfillmentRole(synthesis.getFulfillmentRole());

        AgentTurnResult agentTurn = AgentTurnResult.ready(
                plan, new SemanticTurnOutcome(List.of(generalOutcome, portfolioOutcome, synthesisOutcome)), false)
                .withContextResolution(new ContextResolution("REVALIDATED_TO_CURRENT",
                        com.portfolio.agent.answer.context.domain.ConversationContextType.RECENT_SEMANTIC_TASK,
                        "public-1"));
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-p5", "public-1", ConversationIntent.HYBRID, ConversationAnswerScope.HYBRID,
                AnswerResolution.ANSWERED, "P5 answer", List.of(), List.of(), false,
                GenerationMode.DETERMINISTIC, null, null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING))
                .withAgentTurn(agentTurn);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new ConversationAnswerResponseMapper().toResponse(
                        result, null, Map.of(portfolio.getTaskId(), ContextHandle.issue()))));

        assertThat(json.path("agentTurn").path("contractVersion").asText()).isEqualTo("stp-v2");
        assertThat(json.path("contextResolution").path("mode").asText())
                .isEqualTo("REVALIDATED_TO_CURRENT");
        assertThat(json.path("sourceComposition").asText()).isEqualTo("CROSS_DOMAIN_DERIVED");
        assertThat(json.path("publicSourceCatalog").size()).isEqualTo(1);
        assertThat(json.path("blocks")).allMatch(block -> block.hasNonNull("blockId")
                && block.hasNonNull("sourceDomain") && block.has("support"));
        assertThat(json.path("blocks").findValuesAsText("sourceDomain"))
                .contains("GENERAL", "PORTFOLIO", "SYNTHESIS");
        assertThat(json.path("blocks").get(2).path("sourceScope").isMissingNode()).isTrue();
        assertThat(json.path("agentTurn").path("plan").path("tasks").get(2)
                .path("fulfillmentRole").asText()).isEqualTo("PRIMARY");
        assertThat(json.path("agentTurn").path("completedTasks").get(1)
                .path("continuationContext").path("sourceTaskId").asText()).isEqualTo("task-portfolio");
        assertThat(json.path("blocks").get(1).path("support").path("kind").asText())
                .isEqualTo("VERIFIED_PUBLIC_EVIDENCE");
        assertThat(json.toString()).doesNotContain("PRESENTATION_BLOCKED", "DEPENDENCY_UNAVAILABLE",
                "CANCELLED");
    }
}
