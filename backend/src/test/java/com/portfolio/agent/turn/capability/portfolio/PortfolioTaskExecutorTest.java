package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentationComposer;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResultFactory;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioTaskExecutorTest {
    @Test
    void followsTheSingleInvocationCapabilityResultPresentationArtifactChain() {
        PortfolioInvocationFactory invocationFactory = mock(PortfolioInvocationFactory.class);
        PortfolioEvidenceCapability capability = mock(PortfolioEvidenceCapability.class);
        PortfolioSemanticResultFactory resultFactory = mock(PortfolioSemanticResultFactory.class);
        PortfolioPresentationComposer composer = mock(PortfolioPresentationComposer.class);
        TaskExecutionContext context = mock(TaskExecutionContext.class);
        PortfolioEvidenceInvocation invocation = mock(PortfolioEvidenceInvocation.class);
        ValidatedEvidenceBundle evidence = mock(ValidatedEvidenceBundle.class);
        ValidatedEvidenceUnit unit = unit();
        PortfolioSemanticResult result = new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.PARTIAL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), List.of(unit), List.of("VERIFICATION"));
        PortfolioPresentation presentation = mock(PortfolioPresentation.class);
        when(invocationFactory.create(context)).thenReturn(invocation);
        when(context.getDeadline()).thenReturn(mock(com.portfolio.agent.turn.execution.TurnDeadline.class));
        when(capability.execute(invocation, context.getDeadline())).thenReturn(evidence);
        when(resultFactory.create(context.getTask(), invocation, evidence)).thenReturn(Optional.of(result));
        when(composer.compose(result)).thenReturn(presentation);

        TaskExecutionResult execution = new PortfolioTaskExecutor(
                invocationFactory, capability, resultFactory, composer).execute(context);

        assertThat(execution.getFulfillment())
                .isEqualTo(com.portfolio.agent.turn.execution.TaskOutcome.Fulfillment.PARTIAL);
        assertThat(execution.getArtifact().getSemanticResult()).isSameAs(result);
        assertThat(execution.getArtifact().getPresentation()).isSameAs(presentation);
        assertThat(execution.getArtifact().getProvenance().getPublicSourceKeys())
                .containsExactly("E-01");
        org.mockito.InOrder order = inOrder(invocationFactory, capability, resultFactory, composer);
        order.verify(invocationFactory).create(context);
        order.verify(capability).execute(invocation, context.getDeadline());
        order.verify(resultFactory).create(context.getTask(), invocation, evidence);
        order.verify(composer).compose(result);
    }

    private ValidatedEvidenceUnit unit() {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.IMPLEMENTATION,
                "statement", "detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of("evidence-1"));
        return new ValidatedEvidenceUnit(
                "project-a", PortfolioSubjectKind.PROJECT, claim,
                new PublicSourceReferenceValue(
                        "E-01", "Evidence", "public-1", "DOCUMENT",
                        "/projects/project-a", "/evidence/e-01"));
    }
}
