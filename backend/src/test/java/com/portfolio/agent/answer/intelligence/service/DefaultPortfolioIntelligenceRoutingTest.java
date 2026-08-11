package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.exception.PortfolioRetrievalFailedException;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalStrategy;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalFailureKind;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultPortfolioIntelligenceRoutingTest {

    @Test
    void presetIdWinsWithoutCallingClassifier() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        AtomicReference<com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest>
                request = new AtomicReference<>();
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier,
                false,
                retrievalRequest -> {
                    request.set(retrievalRequest);
                    return retrieval();
                });

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "How is async state restored?")
                        .questionPresetId("preset-async")
                        .projectSlug("project-a")
                        .build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.ANSWERED);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource()).isEqualTo(AnswerIntentSource.PRESET));
        assertThat(request.get().getStrategy())
                .isEqualTo(PortfolioRetrievalStrategy.REFERENCE_SCOPED);
        verifyNoInteractions(classifier);
    }

    @Test
    void deterministicRuleWinsWhenClassifierIsUnavailable() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        DefaultPortfolioIntelligence intelligence = intelligence(classifier, false);

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "比较这两个项目").build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.ANSWERED);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource()).isEqualTo(AnswerIntentSource.RULE));
        verifyNoInteractions(classifier);
    }

    @Test
    void knownProjectSlugScopesGeneralQuestionWithoutCallingClassifier() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        AtomicReference<com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest>
                request = new AtomicReference<>();
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier,
                false,
                retrievalRequest -> {
                    request.set(retrievalRequest);
                    return retrieval();
                });

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "What is dependency injection?")
                        .projectSlug("project-a")
                        .build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.ANSWERED);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource()).isEqualTo(AnswerIntentSource.RULE));
        assertThat(request.get().getStrategy())
                .isEqualTo(PortfolioRetrievalStrategy.SUBJECT_SCOPED_RELEVANCE);
        assertThat(request.get().getRequiredPortfolioIds()).containsExactly("project-a");
        verifyNoInteractions(classifier);
    }

    @Test
    void unknownStructuredSlugBecomesInvalidInputWithoutCallingClassifier() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        DefaultPortfolioIntelligence intelligence = intelligence(classifier, false);

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "这个项目如何实现？")
                        .projectSlug("missing-project")
                        .build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.INVALID_INPUT);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getNoticeCode())
                        .isEqualTo("STRUCTURED_SUBJECT_INVALID"));
        verifyNoInteractions(classifier);
    }

    @Test
    void generalQuestionWithoutSubjectHintFallsToProviderGate() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        DefaultPortfolioIntelligence intelligence = intelligence(classifier, false);

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "What is dependency injection?").build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.NOT_PORTFOLIO);
        verifyNoInteractions(classifier);
    }

    @Test
    void explicitReferenceWinsWithoutCallingClassifier() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, false, request -> retrieval(
                        com.portfolio.agent.answer.domain.AnswerClaimCategory.VERIFICATION));
        PortfolioReferenceContext reference = new PortfolioReferenceContext(
                "public-1",
                List.of("project-a"),
                List.of(),
                "preset-async",
                List.of(),
                AnswerSectionType.VERIFICATION,
                PortfolioFollowUpAction.SHOW_EVIDENCE);

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "Show the supporting evidence")
                        .referenceContext(reference)
                        .build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.ANSWERED);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource()).isEqualTo(AnswerIntentSource.REFERENCE));
        verifyNoInteractions(classifier);
    }

    @Test
    void terminalRetrievalFailureBecomesSafeApplicationFailure() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        PortfolioRetriever retriever = request -> {
            throw new PortfolioRetrievalException(
                    PortfolioRetrievalFailureKind.CONTRACT_VIOLATION,
                    "sensitive adapter detail",
                    null);
        };
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, false, retriever);

        assertThatThrownBy(() -> intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "这个项目如何实现？")
                        .projectSlug("project-a")
                        .build()))
                .isInstanceOf(PortfolioRetrievalFailedException.class)
                .hasMessage("作品集检索暂不可用，请稍后重试");
    }

    @Test
    void knownProjectSlugScopesModelClassifiedQuestion() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        when(classifier.classifyPortfolioTask(
                eq("turn-1"), anyString(), isNull()))
                .thenReturn(ConversationModelResult.success(
                        new PortfolioTaskClassification(
                                PortfolioTaskMode.FACT_LOOKUP,
                                PortfolioConditions.empty(),
                                null,
                                0.95d)));
        AtomicReference<PortfolioRetrievalRequest> request = new AtomicReference<>();
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, true, retrievalRequest -> {
                    request.set(retrievalRequest);
                    return retrieval();
                });

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "Please explain it in detail")
                        .projectSlug("project-a")
                        .build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.ANSWERED);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource())
                        .isEqualTo(AnswerIntentSource.MODEL));
        assertThat(request.get().getStrategy())
                .isEqualTo(PortfolioRetrievalStrategy.SUBJECT_SCOPED_RELEVANCE);
        assertThat(request.get().getRequiredPortfolioIds())
                .containsExactly("project-a");
        verify(classifier).classifyPortfolioTask(
                eq("turn-1"), anyString(), isNull());
    }

    @Test
    void knownCaseSlugScopesModelClassifiedQuestion() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        when(classifier.classifyPortfolioTask(
                eq("turn-1"), anyString(), isNull()))
                .thenReturn(ConversationModelResult.success(
                        new PortfolioTaskClassification(
                                PortfolioTaskMode.FACT_LOOKUP,
                                PortfolioConditions.empty(),
                                null,
                                0.95d)));
        AtomicReference<PortfolioRetrievalRequest> request = new AtomicReference<>();
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, true, retrievalRequest -> {
                    request.set(retrievalRequest);
                    return retrieval();
                });

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "Please explain it in detail")
                        .caseSlug("case-a")
                        .build());

        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource())
                        .isEqualTo(AnswerIntentSource.MODEL));
        assertThat(request.get().getRequiredPortfolioIds())
                .containsExactly("case-a");
        verify(classifier).classifyPortfolioTask(
                eq("turn-1"), anyString(), isNull());
    }

    @Test
    void knownSubjectFallsBackToScopedFactLookupWhenProviderIsDisabled() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        AtomicReference<PortfolioRetrievalRequest> request = new AtomicReference<>();
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, false, retrievalRequest -> {
                    request.set(retrievalRequest);
                    return retrieval();
                });

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-2", "Please explain it in detail")
                        .projectSlug("project-a")
                        .build());

        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource())
                        .isEqualTo(AnswerIntentSource.RULE));
        assertThat(request.get().getStrategy())
                .isEqualTo(PortfolioRetrievalStrategy.SUBJECT_SCOPED_RELEVANCE);
        assertThat(request.get().getRequiredPortfolioIds())
                .containsExactly("project-a");
        verifyNoInteractions(classifier);
    }

    @Test
    void knownSlugWithDeterministicRuleDoesNotCallClassifier() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        AtomicReference<PortfolioRetrievalRequest> request = new AtomicReference<>();
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, true, retrievalRequest -> {
                    request.set(retrievalRequest);
                    return retrieval();
                });

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "怎么实现这个项目？")
                        .projectSlug("project-a")
                        .build());

        assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.ANSWERED);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getIntentSource())
                        .isEqualTo(AnswerIntentSource.RULE));
        assertThat(request.get().getRequiredPortfolioIds())
                .containsExactly("project-a");
        verifyNoInteractions(classifier);
    }

    @Test
    void unknownSubjectFailsBeforeDeterministicRuleClassifierAndRetriever() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        PortfolioRetriever retriever = mock(PortfolioRetriever.class);
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, true, retriever);

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-3", "怎么实现这个项目？")
                        .projectSlug("missing-project")
                        .build());

        assertThat(decision.getDisposition())
                .isEqualTo(PortfolioDisposition.INVALID_INPUT);
        assertThat(decision.getMaterial()).get().satisfies(material ->
                assertThat(material.getNoticeCode())
                        .isEqualTo("STRUCTURED_SUBJECT_INVALID"));
        verifyNoInteractions(classifier, retriever);
    }

    @Test
    void knownSlugModelJudgesNonPortfolioDoesNotRetrieve() {
        PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
        when(classifier.classifyPortfolioTask(
                eq("turn-1"), anyString(), isNull()))
                .thenReturn(ConversationModelResult.success(
                        PortfolioTaskClassification.notPortfolio(0.95d)));
        PortfolioRetriever retriever = mock(PortfolioRetriever.class);
        DefaultPortfolioIntelligence intelligence = intelligence(
                classifier, true, retriever);

        PortfolioDecision decision = intelligence.tryResolve(
                PortfolioTurn.builder("turn-1", "What is dependency injection?")
                        .projectSlug("project-a")
                        .build());

        assertThat(decision.getDisposition())
                .isEqualTo(PortfolioDisposition.NOT_PORTFOLIO);
        verifyNoInteractions(retriever);
    }

    private DefaultPortfolioIntelligence intelligence(
            PortfolioTaskClassifierPort classifier,
            boolean providerAllowed
    ) {
        return intelligence(classifier, providerAllowed, request -> retrieval());
    }

    private DefaultPortfolioIntelligence intelligence(
            PortfolioTaskClassifierPort classifier,
            boolean providerAllowed,
            PortfolioRetriever retriever
    ) {
        RuntimeAnswerContent content = content();
        PortfolioKnowledgeGateway knowledgeGateway = () -> content;
        PortfolioTaskResolver taskResolver = new PortfolioTaskResolver(classifier, 0.7d);
        return new DefaultPortfolioIntelligence(
                new PortfolioTaskValidator(),
                retriever,
                new PortfolioRecommendationPolicy(),
                new RecommendationContextValidator(new RecommendationBatchFingerprint()),
                knowledgeGateway,
                new PortfolioPresetResolver(),
                new PortfolioReferenceContextValidator(),
                taskResolver,
                new StructuredSubjectResolver(),
                new ConversationProviderAccess(providerAllowed));
    }

    private RuntimeAnswerContent content() {
        AnswerQuestion preset = new AnswerQuestion(
                "preset-async",
                "How is async state restored?",
                List.of("How do tasks recover after refresh?"),
                "Async recovery");
        AnswerKnowledge project = new AnswerKnowledge(
                "project-a", "Project A", "Summary", "Background",
                List.of("Responsibility"), "Solution", List.of("Decision"),
                List.of("Verification"), "Outcome", "Handoff", "ACTIVE",
                List.of(preset), List.of(), List.of());
        AnswerKnowledge caseSubject = new AnswerKnowledge(
                AnswerSubjectType.CASE,
                "case-a",
                "Case A",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "ACTIVE",
                List.of(),
                List.of(),
                List.of());
        return new RuntimeAnswerContent(
                "public-1",
                "sha256:runtime",
                List.of(project),
                List.of(caseSubject),
                null,
                List.of());
    }

    private PortfolioRetrievalResult retrieval() {
        return retrieval(com.portfolio.agent.answer.domain.AnswerClaimCategory.IMPLEMENTATION);
    }

    private PortfolioRetrievalResult retrieval(
            com.portfolio.agent.answer.domain.AnswerClaimCategory category) {
        PortfolioRetrievedSubject subject = new PortfolioRetrievedSubject(
                "project-a", "PROJECT", "Project A", "Summary", "/projects/project-a",
                "BACKEND", Set.of("JAVA"), 0.9d, 0.9d, 0.0d);
        PortfolioRetrievedPassage passage = new PortfolioRetrievedPassage(
                "project-a#claim-a", "project-a", "Verified material",
                claimProjection("claim-a", "evidence-a", category),
                List.of(new PortfolioRetrievedEvidenceReference(
                        "evidence-a", "Evidence A", "APPROVED")));
        return new PortfolioRetrievalResult(
                "public-1",
                List.of(subject),
                List.of(passage),
                new PortfolioRetrievalSource("TEST"),
                false,
                null);
    }

    private com.portfolio.agent.answer.domain.AnswerClaimProjection claimProjection(
            String claimId, String evidenceId) {
        return claimProjection(
                claimId, evidenceId,
                com.portfolio.agent.answer.domain.AnswerClaimCategory.IMPLEMENTATION);
    }

    private com.portfolio.agent.answer.domain.AnswerClaimProjection claimProjection(
            String claimId,
            String evidenceId,
            com.portfolio.agent.answer.domain.AnswerClaimCategory category) {
        return new com.portfolio.agent.answer.domain.AnswerClaimProjection(
                claimId,
                category,
                "Verified material",
                "验证范围以公开证据为限。",
                com.portfolio.agent.answer.domain.AnswerAchievementStatus.IMPLEMENTED_TESTED,
                com.portfolio.agent.answer.domain.AnswerContributionType.PRIMARY,
                com.portfolio.agent.answer.domain.AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED,
                com.portfolio.agent.answer.domain.AnswerMateriality.KEY,
                List.of("JAVA"),
                List.of(evidenceId));
    }
}
