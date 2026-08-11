package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.InvalidatedPlanReferenceRequest;
import com.portfolio.agent.answer.dto.request.PlanConfirmationRequest;
import com.portfolio.agent.answer.dto.request.PortfolioRecommendationContextRequest;
import com.portfolio.agent.answer.dto.request.PortfolioReferenceContextRequest;
import com.portfolio.agent.answer.dto.request.SemanticContextRequest;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.service.LegacySemanticContextAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Maps HTTP DTOs to the action-aware routing input without reading message text. */
@Component
public final class SemanticTurnRequestMapper {

    public SemanticTurnInput toInput(ConversationAnswerRequest request, String contentVersion) {
        Objects.requireNonNull(request, "request");
        String effectiveContentVersion = requireText(contentVersion, "contentVersion");
        SemanticContext semanticContext = toSemanticContext(
                request.getSemanticContext(), effectiveContentVersion);
        LegacySemanticContextAdapter.LegacyContext legacyContext = toLegacyContext(
                request.getContext(), effectiveContentVersion);
        return new SemanticTurnInput(
                request.getTurnId(), toAction(request.getAction()), request.getQuestion(), semanticContext,
                legacyContext,
                semanticContext == null ? List.of() : semanticContext.getResultReferences(),
                semanticContext == null ? List.of() : semanticContext.getActiveSubjects(), List.of(),
                toSubmission(request.getPlanConfirmation()),
                toInvalidatedReference(request.getInvalidatedPlanReference()),
                request.getRequestToken() == null ? null : request.getRequestToken().toString(),
                request.getAgentTurnContract(), request.getQuestionPresetId(), request.getContractVersion());
    }

    private SemanticContext toSemanticContext(
            SemanticContextRequest request, String contentVersion) {
        if (request == null) {
            return null;
        }
        return SemanticContext.of(
                mapSubjects(request.getActiveSubjects(), contentVersion),
                mapResultReferences(request.getResultReferences()),
                request.getPendingPlanReference() == null ? null
                        : new SemanticContext.PendingPlanReference(
                                request.getPendingPlanReference().getPlanId(), List.of()),
                request.getAudienceRole(), request.getRequestSource(),
                new LinkedHashSet<>(request.getCoveredTopics()));
    }

    private LegacySemanticContextAdapter.LegacyContext toLegacyContext(
            ConversationAnswerContextRequest context, String contentVersion) {
        if (context == null) {
            return null;
        }
        PortfolioRecommendationContextRequest recommendation = context.getRecommendationContext();
        PortfolioReferenceContextRequest reference = context.getReferenceContext();
        List<String> recommendationSubjects = recommendation == null
                ? List.of() : nullToEmpty(recommendation.getSelectedPortfolioIds());
        List<String> referenceProjects = reference == null ? List.of() : reference.getProjectSlugs();
        List<String> referenceCases = reference == null ? List.of() : reference.getCaseSlugs();
        Set<String> coveredTopics = new LinkedHashSet<>();
        context.getCoveredTopics().forEach(topic -> coveredTopics.add(topic.name()));
        return LegacySemanticContextAdapter.LegacyContext.ofWithTypedReferences(
                context.getProjectSlug(), context.getCaseSlug(), recommendationSubjects,
                referenceProjects, referenceCases,
                context.getAudienceRole() == null ? null : context.getAudienceRole().name(),
                context.getSource() == null ? null : context.getSource().name(),
                coveredTopics, contentVersion);
    }

    private List<SubjectReference> mapSubjects(
            List<SemanticContextRequest.SubjectReferenceRequest> subjects,
            String contentVersion) {
        List<SubjectReference> mapped = new ArrayList<>();
        for (SemanticContextRequest.SubjectReferenceRequest subject : subjects) {
            SubjectType type = SubjectType.valueOf(subject.getSubjectType());
            mapped.add(new SubjectReference(type, subject.getSubjectId(),
                    type == SubjectType.RESULT
                            ? SubjectResolutionSource.STRUCTURED_RESULT
                            : SubjectResolutionSource.EXPLICIT_REFERENCE,
                    type == SubjectType.RESULT ? null : contentVersion));
        }
        return List.copyOf(mapped);
    }

    private List<SubjectReference> mapResultReferences(
            List<SemanticContextRequest.ResultReferenceRequest> references) {
        List<SubjectReference> mapped = new ArrayList<>();
        for (SemanticContextRequest.ResultReferenceRequest reference : references) {
            mapped.add(new SubjectReference(
                    SubjectType.RESULT, reference.getReferenceId(),
                    SubjectResolutionSource.STRUCTURED_RESULT, null));
        }
        return List.copyOf(mapped);
    }

    private static SemanticTurnInput.Action toAction(ConversationAnswerRequest.TurnAction action) {
        return switch (action) {
            case ASK -> SemanticTurnInput.Action.ASK;
            case CONFIRM_PLAN -> SemanticTurnInput.Action.CONFIRM_PLAN;
            case REGENERATE_PLAN -> SemanticTurnInput.Action.REGENERATE_PLAN;
        };
    }

    private static PlanConfirmation.Submission toSubmission(PlanConfirmationRequest request) {
        if (request == null) {
            return null;
        }
        return new PlanConfirmation.Submission(request.getConfirmationId(), request.getConfirmationPlan(),
                request.getPlanFingerprint(), request.getIntegrityToken());
    }

    private static SemanticTurnInput.InvalidatedPlanReference toInvalidatedReference(
            InvalidatedPlanReferenceRequest request) {
        if (request == null) {
            return null;
        }
        return new SemanticTurnInput.InvalidatedPlanReference(
                request.getPlanId(), request.getPlanFingerprint());
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
