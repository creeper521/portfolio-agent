package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Intelligence layer that drives the production PortfolioIntelligence pipeline
 * with the public directory only. The case oracle never enters the executor.
 */
public final class EvalIntelligenceExecutor implements EvalExecutor {

    private final PortfolioIntelligence intelligence;
    private final RuntimeContentSnapshot bundle;

    public EvalIntelligenceExecutor(PortfolioIntelligence intelligence) {
        this(intelligence, null);
    }

    public EvalIntelligenceExecutor(
            PortfolioIntelligence intelligence,
            RuntimeContentSnapshot bundle) {
        this.intelligence = Objects.requireNonNull(intelligence, "intelligence");
        this.bundle = bundle;
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.INTELLIGENCE;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext context) {
        String question = firstUserMessage(input.getMessages());
        if (question == null || question.isBlank()) {
            return error(input, "INVALID_INPUT");
        }
        PortfolioTurn turn = PortfolioTurn.builder(input.getCaseId(), question).build();
        PortfolioDecision decision;
        try {
            decision = intelligence.tryResolve(turn);
        } catch (RuntimeException failure) {
            return error(input, "EXECUTOR_ERROR");
        }
        return map(input, decision);
    }

    private EvalObservation map(
            EvalExecutionInput input,
            PortfolioDecision decision) {
        if (decision.getMaterial().isEmpty()) {
            return new EvalObservation(
                    input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                    status(decision.getDisposition()), null, null, List.of(), List.of(), List.of(),
                    resolution(decision.getDisposition()), ConversationAnswerScope.PORTFOLIO,
                    GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                    List.of(decision.getDisposition().name()), 0L,
                    EvalProviderUsage.unavailable(), EvalAnswerShape.empty(), false, false);
        }
        PortfolioIntelligenceResult material = decision.getMaterial().get();
        List<String> claims = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        for (PortfolioRetrievedPassage passage : material.getEvidence()) {
            if (passage.getClaimId() != null) {
                claims.add(passage.getClaimId());
            }
            evidence.addAll(passage.getEvidenceIds());
        }
        String projectSlug = null;
        String caseSlug = null;
        for (PortfolioRetrievedSubject subject : material.getSubjects()) {
            if ("PROJECT".equals(subject.getSubjectType())) {
                projectSlug = projectSlug(subject.getSubjectId());
            } else if ("CASE".equals(subject.getSubjectType())) {
                caseSlug = caseSlug(subject.getSubjectId());
            }
        }
        AnswerResolution resolution = resolution(decision.getDisposition());
        EvalObservationStatus status = status(decision.getDisposition());
        boolean providerInvoked =
                material.getIntentSource() == AnswerIntentSource.MODEL;
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(), status,
                projectSlug, caseSlug, List.copyOf(claims), List.copyOf(evidence), List.of(),
                resolution, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(decision.getDisposition().name()), 0L,
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(),
                false, providerInvoked);
    }

    private String firstUserMessage(List<EvalMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(0).getContent();
    }

    private AnswerResolution resolution(PortfolioDisposition disposition) {
        return switch (disposition) {
            case ANSWERED -> AnswerResolution.ANSWERED;
            case NEEDS_CLARIFICATION -> AnswerResolution.NEEDS_CLARIFICATION;
            case NOT_PORTFOLIO -> AnswerResolution.BOUNDARY;
            case INVALID_INPUT -> AnswerResolution.INVALID_INPUT;
            default -> AnswerResolution.NOT_SUPPORTED;
        };
    }

    private EvalObservationStatus status(PortfolioDisposition disposition) {
        return disposition == PortfolioDisposition.ANSWERED
                ? EvalObservationStatus.PASS
                : EvalObservationStatus.FAIL;
    }

    private String projectSlug(String subjectId) {
        if (bundle == null) {
            return subjectId;
        }
        return bundle.getProjects().stream()
                .filter(project -> project.getId().equals(subjectId))
                .map(com.portfolio.agent.portfolio.domain.ProjectProfile::getSlug)
                .findFirst().orElse(subjectId);
    }

    private String caseSlug(String subjectId) {
        if (bundle == null) {
            return subjectId;
        }
        return bundle.getCases().stream()
                .filter(subject -> subject.getId().equals(subjectId))
                .map(com.portfolio.agent.portfolio.domain.CaseStudy::getSlug)
                .findFirst().orElse(subjectId);
    }

    private EvalObservation error(EvalExecutionInput input, String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.ERROR,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L,
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(),
                false, false);
    }
}
