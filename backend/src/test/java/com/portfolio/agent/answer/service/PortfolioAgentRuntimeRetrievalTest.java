package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.dto.request.AnswerContextRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioAgentRuntimeRetrievalTest {

    @Test
    void sufficientFreeTextBuildsASelectedTurnAndReturnsRetrievalAnswer() {
        Fixture fixture = fixture(AnswerResolution.BOUNDARY);
        when(fixture.retrievalCoordinator().retrieve(
                eq(fixture.request().getQuestion()), eq("sql-audit"), same(fixture.corpus()),
                same(fixture.project().getClaims()), same(fixture.project().getEvidence()),
                eq(RetrievalMode.HYBRID_ENABLED), same(fixture.policy())))
                .thenReturn(new RetrievalDecision(
                        RetrievalDecisionType.SUFFICIENT,
                        RetrievalMode.HYBRID_ENABLED,
                        List.of("chunk-1"),
                        List.of("claim-1")));
        when(fixture.modelCoordinator().generate(any(), any())).thenReturn(
                new ModelAnswerOutcome(
                        new GeneratedAnswer(
                                "Selected title",
                                "Selected summary",
                                List.of(new AnswerSection(
                                        AnswerSectionType.STATUS,
                                        "Status",
                                        "Selected outcome",
                                        List.of("claim-1"),
                                        List.of("evidence-1")))),
                        GenerationMode.DETERMINISTIC,
                        null));
        when(fixture.verificationPolicy().verify(eq(AnswerResolution.ANSWERED), any(), any()))
                .thenReturn(VerificationStatus.PARTIALLY_VERIFIED);

        AnswerResult result = fixture.runtime().answer(fixture.request());

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getAnswerSource()).isEqualTo(AnswerSource.RETRIEVAL);
        assertThat(result.getVerification()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
        assertThat(result.getEvidenceIds()).containsExactly("evidence-1");
        assertThat(result.getTurnSnapshot().getQuestionPresetId()).isNull();
        assertThat(result.getTurnSnapshot().getApprovedEvidenceIds())
                .containsExactly("evidence-1");

        ArgumentCaptor<AnswerPlan> planCaptor = ArgumentCaptor.forClass(AnswerPlan.class);
        verify(fixture.modelCoordinator()).generate(any(), planCaptor.capture());
        assertThat(planCaptor.getValue().getCanonicalIntent())
                .contains("sql-audit", "OUTCOME", "audit")
                .doesNotContain(fixture.request().getQuestion());
    }

    @Test
    void nonSufficientDecisionStaysBoundaryAndNeverInvokesExpression() {
        Fixture fixture = fixture(AnswerResolution.BOUNDARY);
        when(fixture.retrievalCoordinator().retrieve(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RetrievalDecision(
                        RetrievalDecisionType.AMBIGUOUS,
                        RetrievalMode.HYBRID_ENABLED,
                        List.of("chunk-1"),
                        List.of()));

        AnswerResult result = fixture.runtime().answer(fixture.request());

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(result.getAnswerSource()).isNull();
        assertThat(result.getVerification()).isEqualTo(VerificationStatus.NOT_APPLICABLE);
        verifyNoInteractions(fixture.modelCoordinator(), fixture.verificationPolicy());
    }

    @Test
    void presetAnswerNeverTouchesRetrieval() {
        Fixture fixture = fixture(AnswerResolution.ANSWERED);
        when(fixture.modelCoordinator().generate(any(), any())).thenReturn(
                new ModelAnswerOutcome(
                        new GeneratedAnswer("Title", "Summary", List.of()),
                        GenerationMode.DETERMINISTIC,
                        null));
        when(fixture.verificationPolicy().verify(any(), any(), any()))
                .thenReturn(VerificationStatus.UNVERIFIED);

        AnswerResult result = fixture.runtime().answer(fixture.request());

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getAnswerSource()).isEqualTo(AnswerSource.PRESET);
        verifyNoInteractions(fixture.retrievalCoordinator());
    }

    @Test
    void incompatibleBundleEmbeddingIdentityStaysBoundaryWithoutRetrieval() {
        Fixture fixture = fixture(AnswerResolution.BOUNDARY, new AnswerRetrievalCorpus(
                mock(com.portfolio.agent.answer.domain.AnswerKeywordIndex.class),
                java.util.Map.of(), java.util.Map.of(),
                "BAAI/bge-small-zh-v1.5", "sha256:different", 512));

        AnswerResult result = fixture.runtime().answer(fixture.request());

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        verifyNoInteractions(fixture.retrievalCoordinator());
    }

    private Fixture fixture(AnswerResolution initialResolution) {
        return fixture(initialResolution, new AnswerRetrievalCorpus(
                mock(com.portfolio.agent.answer.domain.AnswerKeywordIndex.class),
                java.util.Map.of(), java.util.Map.of(),
                "BAAI/bge-small-zh-v1.5", "sha256:expected", 512));
    }

    private Fixture fixture(
            AnswerResolution initialResolution,
            AnswerRetrievalCorpus corpus
    ) {
        AnswerEvidence evidence = new AnswerEvidence(
                "evidence-1", "Public evidence", "TEST_REPORT",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                1, "Approved evidence summary", "APPROVED", false);
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.OUTCOME,
                "Audit workflow delivered", "The workflow is available publicly.",
                AnswerAchievementStatus.DELIVERED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("audit"),
                List.of("evidence-1"));
        AnswerQuestion preset = new AnswerQuestion(
                "overview", "Describe the project", List.of(), "Overview");
        AnswerKnowledge project = new AnswerKnowledge(
                "sql-audit", "SQL audit", "Whole summary", "Whole background",
                List.of("Whole responsibility"), "Whole solution",
                List.of("Whole decision"), List.of("Whole verification"),
                "Whole outcome", "Whole handoff", "DELIVERED",
                List.of(preset), List.of(evidence), List.of(claim));
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-22", "sha256:runtime", List.of(project), corpus);
        String presetId = initialResolution == AnswerResolution.ANSWERED ? "overview" : null;
        AnswerRequest request = new AnswerRequest(
                "turn-1", presetId, "visitor-secret-retrieval-question",
                new AnswerContextRequest(
                        "sql-audit", AudienceRole.GUEST, List.of(),
                        AnswerRequestSource.AGENT_PAGE));
        QuestionResolution resolution = new QuestionResolution(
                initialResolution, project,
                initialResolution == AnswerResolution.ANSWERED ? preset : null);

        PortfolioKnowledgeGateway gateway = mock(PortfolioKnowledgeGateway.class);
        QuestionResolver resolver = mock(QuestionResolver.class);
        AgentExecutionSnapshotFactory snapshotFactory = mock(AgentExecutionSnapshotFactory.class);
        ModelAnswerCoordinator modelCoordinator = mock(ModelAnswerCoordinator.class);
        VerificationPolicy verificationPolicy = mock(VerificationPolicy.class);
        LocalRetrievalCoordinator retrievalCoordinator = mock(LocalRetrievalCoordinator.class);
        RetrievalPolicy policy = RetrievalPolicy.firstRelease();
        when(gateway.getContent()).thenReturn(content);
        when(resolver.resolve(content, request)).thenReturn(resolution);
        when(snapshotFactory.create(any(AnswerTurnSnapshot.class)))
                .thenReturn(mock(AgentExecutionSnapshot.class));

        PortfolioAgentRuntime runtime = new PortfolioAgentRuntime(
                gateway, resolver, new AnswerContextFactory(), new AnswerPlanBuilder(),
                snapshotFactory, modelCoordinator, verificationPolicy,
                mock(AnswerDecisionPublisher.class), retrievalCoordinator, policy,
                RetrievalCapability.hybridEnabled(
                        "BAAI/bge-small-zh-v1.5", "sha256:expected", 512));
        return new Fixture(runtime, request, project, corpus, retrievalCoordinator,
                policy, modelCoordinator, verificationPolicy);
    }

    private static final class Fixture {
        private final PortfolioAgentRuntime runtime;
        private final AnswerRequest request;
        private final AnswerKnowledge project;
        private final AnswerRetrievalCorpus corpus;
        private final LocalRetrievalCoordinator retrievalCoordinator;
        private final RetrievalPolicy policy;
        private final ModelAnswerCoordinator modelCoordinator;
        private final VerificationPolicy verificationPolicy;

        private Fixture(
                PortfolioAgentRuntime runtime,
                AnswerRequest request,
                AnswerKnowledge project,
                AnswerRetrievalCorpus corpus,
                LocalRetrievalCoordinator retrievalCoordinator,
                RetrievalPolicy policy,
                ModelAnswerCoordinator modelCoordinator,
                VerificationPolicy verificationPolicy
        ) {
            this.runtime = runtime;
            this.request = request;
            this.project = project;
            this.corpus = corpus;
            this.retrievalCoordinator = retrievalCoordinator;
            this.policy = policy;
            this.modelCoordinator = modelCoordinator;
            this.verificationPolicy = verificationPolicy;
        }

        private PortfolioAgentRuntime runtime() { return runtime; }
        private AnswerRequest request() { return request; }
        private AnswerKnowledge project() { return project; }
        private AnswerRetrievalCorpus corpus() { return corpus; }
        private LocalRetrievalCoordinator retrievalCoordinator() { return retrievalCoordinator; }
        private RetrievalPolicy policy() { return policy; }
        private ModelAnswerCoordinator modelCoordinator() { return modelCoordinator; }
        private VerificationPolicy verificationPolicy() { return verificationPolicy; }
    }
}
