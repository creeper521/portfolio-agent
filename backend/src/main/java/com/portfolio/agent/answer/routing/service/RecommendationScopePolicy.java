package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.List;
import java.util.Objects;

/** Authorizes a recommendation to narrow its candidate set only from trusted subject bindings. */
public final class RecommendationScopePolicy {

    public List<SubjectReference> authorizedSubjects(ResolvedRoutingContext context) {
        Objects.requireNonNull(context, "context");
        if (context.getStatus() != RoutingContextStatus.RESOLVED) {
            return List.of();
        }
        SubjectResolutionSource source = context.getResolutionSource();
        if (source != SubjectResolutionSource.EXPLICIT_REFERENCE
                && source != SubjectResolutionSource.EXPLICIT_TEXT
                && source != SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE
                && source != SubjectResolutionSource.PENDING_PLAN) {
            return List.of();
        }
        return context.getSubjects().stream()
                .filter(subject -> subject.getSubjectType() == SubjectType.PROJECT)
                .toList();
    }
}
