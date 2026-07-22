package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.adapter.tool.LocalPublicKnowledgeTools;
import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.dto.request.AnswerContextRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.dto.request.ContextEnvelopeRequest;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioAgentRuntimeToolTest {

    @Test
    void validFollowUpExecutesFixedToolsBeforeBuildingProviderSafePlan() {
        Fixture fixture = fixture("claim-solution");
        when(fixture.modelCoordinator.generate(any(), any())).thenReturn(
                new ModelAnswerOutcome(
                        new GeneratedAnswer(
                                "技术方案",
                                "展开后的公开方案",
                                List.of(new AnswerSection(
                                        AnswerSectionType.SOLUTION,
                                        "技术方案",
                                        "展开后的公开方案",
                                        List.of("claim-solution"),
                                        List.of()))),
                        GenerationMode.DETERMINISTIC,
                        null));
        when(fixture.verificationPolicy.verify(any(), any(), any()))
                .thenReturn(VerificationStatus.UNVERIFIED);

        AnswerResult result = fixture.runtime.answer(fixture.request);

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getAnswerSource()).isEqualTo(AnswerSource.RETRIEVAL);
        assertThat(result.isContextVersionUpdated()).isTrue();
        assertThat(result.getSections()).extracting(AnswerSection::getType)
                .containsExactly(AnswerSectionType.SOLUTION);
        assertThat(result.getTurnSnapshot().getApprovedEvidenceIds()).isEmpty();
        verifyNoInteractions(fixture.retrievalCoordinator);
        verify(fixture.modelCoordinator).generate(any(), any());
    }

    @Test
    void invalidStableReferenceReturnsBoundaryWithoutToolsOrModel() {
        Fixture fixture = fixture("removed-claim");

        AnswerResult result = fixture.runtime.answer(fixture.request);

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(result.getAnswerSource()).isNull();
        assertThat(result.isContextVersionUpdated()).isFalse();
        verify(fixture.modelCoordinator, never()).generate(any(), any());
        verifyNoInteractions(fixture.retrievalCoordinator);
    }

    private Fixture fixture(String referencedClaimId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-solution", AnswerClaimCategory.IMPLEMENTATION,
                "Implemented a fixed pipeline", "No model-selected tools.",
                AnswerAchievementStatus.DELIVERED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.SELF_DECLARED,
                AnswerClaimVerificationStatus.UNVERIFIED,
                AnswerMateriality.KEY, List.of("PIPELINE"), List.of());
        AnswerKnowledge project = new AnswerKnowledge(
                "sql-audit", "SQL audit", "summary", "background",
                List.of("responsibility"), "solution", List.of("decision"),
                List.of("verification"), "outcome", "handoff", "DELIVERED",
                List.of(), List.of(), List.of(claim));
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-22.1", "sha256:runtime", List.of(project));
        ContextEnvelopeRequest envelope = new ContextEnvelopeRequest(
                "2026-07-21.1", List.of("sql-audit"), null,
                List.of(referencedClaimId), AnswerSectionType.SOLUTION,
                FollowUpIntent.EXPAND_SECTION);
        AnswerRequest request = new AnswerRequest(
                "turn-follow-up", null, "展开技术方案",
                new AnswerContextRequest(
                        "sql-audit", AudienceRole.INTERVIEWER, List.of(),
                        AnswerRequestSource.AGENT_PAGE),
                envelope);
        PortfolioKnowledgeGateway gateway = mock(PortfolioKnowledgeGateway.class);
        QuestionResolver resolver = mock(QuestionResolver.class);
        AgentExecutionSnapshotFactory snapshotFactory = mock(AgentExecutionSnapshotFactory.class);
        ModelAnswerCoordinator modelCoordinator = mock(ModelAnswerCoordinator.class);
        VerificationPolicy verificationPolicy = mock(VerificationPolicy.class);
        LocalRetrievalCoordinator retrievalCoordinator = mock(LocalRetrievalCoordinator.class);
        when(gateway.getContent()).thenReturn(content);
        when(resolver.resolve(content, request)).thenReturn(
                new QuestionResolution(AnswerResolution.BOUNDARY, project, null));
        when(snapshotFactory.create(any(AnswerTurnSnapshot.class))).thenAnswer(invocation ->
                new AgentExecutionSnapshot(
                        invocation.getArgument(0), "audience-v1", "model-v1", "schema-v1",
                        false, "retrieval-v1", false,
                        com.portfolio.agent.answer.domain.RetrievalMode.KEYWORD_ONLY,
                        "none", false, false, "c3-model-registry-v1",
                        new ExecutionBudgets(5000L, 1, 4, 8, 4000)));
        ToolPlanExecutor toolExecutor = new ToolPlanExecutor(
                new LocalPublicKnowledgeTools(), new ToolResultValidator());
        PortfolioAgentRuntime runtime = new PortfolioAgentRuntime(
                gateway, resolver, new AnswerContextFactory(), new AnswerPlanBuilder(),
                snapshotFactory, modelCoordinator, verificationPolicy,
                mock(AnswerDecisionPublisher.class), retrievalCoordinator,
                RetrievalPolicy.firstRelease(), RetrievalCapability.disabled(),
                new ContextEnvelopeValidator(), new ToolPlanBuilder(), toolExecutor);
        return new Fixture(runtime, request, modelCoordinator,
                verificationPolicy, retrievalCoordinator);
    }

    private static final class Fixture {
        private final PortfolioAgentRuntime runtime;
        private final AnswerRequest request;
        private final ModelAnswerCoordinator modelCoordinator;
        private final VerificationPolicy verificationPolicy;
        private final LocalRetrievalCoordinator retrievalCoordinator;

        private Fixture(
                PortfolioAgentRuntime runtime,
                AnswerRequest request,
                ModelAnswerCoordinator modelCoordinator,
                VerificationPolicy verificationPolicy,
                LocalRetrievalCoordinator retrievalCoordinator
        ) {
            this.runtime = runtime;
            this.request = request;
            this.modelCoordinator = modelCoordinator;
            this.verificationPolicy = verificationPolicy;
            this.retrievalCoordinator = retrievalCoordinator;
        }
    }
}
