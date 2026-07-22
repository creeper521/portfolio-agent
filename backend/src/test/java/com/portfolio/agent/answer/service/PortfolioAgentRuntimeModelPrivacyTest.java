package com.portfolio.agent.answer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.dto.request.AnswerContextRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioAgentRuntimeModelPrivacyTest {

    @Test
    void modelBoundaryReceivesOnlyProviderSafePlanAndNeverVisitorTextOrIds() throws Exception {
        String visitorText = "visitor-secret-sentinel-8dd57a";
        String turnId = "memory-turn-secret-42";
        String requestMarker = "request-marker-must-not-be-modeled";
        AnswerQuestion preset = new AnswerQuestion(
                "approved-overview",
                "Describe the approved project",
                List.of(),
                "Approved overview"
        );
        AnswerKnowledge project = new AnswerKnowledge(
                "project-1", "Approved project", "Approved summary", "Approved background",
                List.of("Approved responsibility"), "Approved solution",
                List.of("Approved decision"), List.of("Approved verification"),
                "Approved outcome", "Approved handoff", "DELIVERED",
                List.of(preset), List.of()
        );
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-22", "sha256:runtime", List.of(project));
        AnswerRequest request = new AnswerRequest(
                turnId, "approved-overview", visitorText,
                new AnswerContextRequest(
                        "project-1", AudienceRole.INTERVIEWER, List.of(),
                        AnswerRequestSource.AGENT_PAGE));
        QuestionResolution resolution = new QuestionResolution(
                AnswerResolution.ANSWERED, project, preset);

        PortfolioKnowledgeGateway knowledgeGateway = mock(PortfolioKnowledgeGateway.class);
        QuestionResolver resolver = mock(QuestionResolver.class);
        AnswerContextFactory contextFactory = mock(AnswerContextFactory.class);
        AgentExecutionSnapshotFactory snapshotFactory = mock(AgentExecutionSnapshotFactory.class);
        ModelExpressionPort modelPort = mock(ModelExpressionPort.class);
        AnswerEngine deterministicEngine = mock(AnswerEngine.class);
        VerificationPolicy verificationPolicy = mock(VerificationPolicy.class);

        when(knowledgeGateway.getContent()).thenReturn(content);
        when(resolver.resolve(content, request)).thenReturn(resolution);
        when(contextFactory.create(any(), any(), any())).thenAnswer(invocation ->
                new ResolvedAnswerContext(invocation.getArgument(0), project, preset, List.of()));
        when(snapshotFactory.create(any())).thenAnswer(invocation ->
                new AgentExecutionSnapshot(
                        invocation.getArgument(0), "audience-v1", requestMarker,
                        "c1.answer.v1", true, "none", false,
                        com.portfolio.agent.answer.domain.RetrievalMode.KEYWORD_ONLY,
                        "none", false, false, "c3-model-registry-v1",
                        new ExecutionBudgets(5000, 1)));
        when(modelPort.express(any())).thenReturn(ModelExpressionResult.failure(
                ModelExpressionFailureCode.PROVIDER_ERROR));
        when(deterministicEngine.answer(any())).thenReturn(new GeneratedAnswer(
                "Approved project", "Approved summary", List.of()));
        when(verificationPolicy.verify(any(), any(), any()))
                .thenReturn(VerificationStatus.UNVERIFIED);

        ModelAnswerCoordinator coordinator = new ModelAnswerCoordinator(
                deterministicEngine, modelPort, mock(AnswerOutputValidator.class));
        PortfolioAgentRuntime runtime = new PortfolioAgentRuntime(
                knowledgeGateway, resolver, contextFactory, new AnswerPlanBuilder(),
                snapshotFactory, coordinator, verificationPolicy,
                mock(AnswerDecisionPublisher.class),
                mock(LocalRetrievalCoordinator.class),
                RetrievalPolicy.firstRelease(),
                RetrievalCapability.disabled());

        AnswerResult result = runtime.answer(request);

        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        ArgumentCaptor<ModelExpressionRequest> captor =
                ArgumentCaptor.forClass(ModelExpressionRequest.class);
        org.mockito.Mockito.verify(modelPort).express(captor.capture());
        String providerJson = new ObjectMapper().writeValueAsString(captor.getValue());
        assertThat(providerJson)
                .contains("Describe the approved project")
                .doesNotContain(
                        visitorText, turnId, requestMarker, "requestId", "turnId",
                        "contextEnvelope", "previousContentVersion", "projectSlugs",
                        "referencedClaimIds", "selectedSectionType", "toolPlan",
                        "toolResult", "messages", "previousQuestion", "previousAnswer");
    }
}
