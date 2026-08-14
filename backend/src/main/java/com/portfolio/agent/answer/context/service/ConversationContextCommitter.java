package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.PublicResultItemId;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.context.domain.OrderedResultSelection;
import com.portfolio.agent.answer.context.domain.SubjectOrderKind;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.service.ConversationRequestContext;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds short-lived Context only from typed plans and task outcomes, never from rendered text. */
public final class ConversationContextCommitter {
    private final ConversationContextFacade facade;
    private final ConversationContextMutationFactory mutationFactory;

    public ConversationContextCommitter(
            ConversationContextFacade facade,
            ConversationContextMutationFactory mutationFactory) {
        this.facade = Objects.requireNonNull(facade, "facade");
        this.mutationFactory = Objects.requireNonNull(mutationFactory, "mutationFactory");
    }

    public Map<String, ContextHandle> commit(
            ConversationAnswerResult result,
            ConversationRequestContext requestContext,
            Instant now) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(requestContext, "requestContext");
        Objects.requireNonNull(now, "now");
        AgentTurnResult agentTurn = result.getAgentTurn();
        if (agentTurn == null || agentTurn.getPlan().isEmpty() || agentTurn.getOutcome().isEmpty()) {
            return Map.of();
        }
        SemanticTurnPlan plan = agentTurn.getPlan().orElseThrow();
        Map<String, TaskOutcome> outcomes = new java.util.LinkedHashMap<>();
        for (TaskOutcome outcome : agentTurn.getOutcome().orElseThrow().getTaskOutcomes()) {
            outcomes.put(outcome.getTaskId(), outcome);
        }
        Map<String, ContextHandle> handles = new java.util.LinkedHashMap<>();
        for (SemanticTask task : plan.getTasks()) {
            TaskOutcome outcome = outcomes.get(task.getTaskId());
            if (outcome == null || !outcome.hasRenderablePayload()
                    || task.getSourceDomain() != SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO) {
                continue;
            }
            ConversationContextMutation mutation = mutation(task, outcome, result, requestContext, now);
            if (mutation == null) {
                continue;
            }
            facade.save(requestContext.getConversationId(), requestContext.getResumeToken(), mutation, now);
            handles.put(task.getTaskId(), mutation.getContextHandle());
        }
        return Map.copyOf(handles);
    }

    private ConversationContextMutation mutation(
            SemanticTask task,
            TaskOutcome outcome,
            ConversationAnswerResult result,
            ConversationRequestContext requestContext,
            Instant now) {
        ContextSlot slot = ContextSlot.forTaskType(task.getTaskType());
        long revision = facade.active(
                requestContext.getConversationId(), requestContext.getResumeToken(), slot, now)
                .map(value -> value.getRevision()).orElse(0L);
        ContextHandle parent = requestContext.getContextReference()
                .map(AuthorizedContextReference::getContextHandle)
                .map(ContextHandle::fromBase64Url)
                .orElse(null);
        ConversationContextValue value = switch (task.getTaskType()) {
            case PORTFOLIO_FACT, PORTFOLIO_COMPARE ->
                    ConversationContextValue.recentSemanticTask(recent(task, result));
            case PORTFOLIO_RECOMMEND ->
                    ConversationContextValue.recommendation(recommendation(task, outcome, result, parent));
            case PORTFOLIO_REFINE_RECOMMENDATION -> {
                if (parent == null) {
                    yield null;
                }
                // The authorized parent scope is resolved by the execution layer. This
                // committer does not receive that binding, so it must not widen it by
                // manufacturing a new all-published recommendation scope.
                yield null;
            }
            default -> null;
        };
        return value == null ? null : mutationFactory.create(
                value, parent, task.getTaskId(), slot, revision);
    }

    private RecentSemanticTaskContext recent(
            SemanticTask task, ConversationAnswerResult result) {
        Set<String> facets = new LinkedHashSet<>();
        Set<String> dimensions = new LinkedHashSet<>();
        SemanticTaskParameters parameters = task.getParameters();
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            fact.getFacets().forEach(value -> facets.add(value.name()));
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare compare) {
            compare.getDimensions().forEach(value -> dimensions.add(value.name()));
        }
        return new RecentSemanticTaskContext(
                task.getTaskType(), task.getSubjectReferences(), facets, dimensions,
                result.getContentVersion(), task.getTaskId());
    }

    private RecommendationContext recommendation(
            SemanticTask task, TaskOutcome outcome,
            ConversationAnswerResult result, ContextHandle parent) {
        SemanticTaskParameters.PortfolioRecommend parameters =
                task.getParameters() instanceof SemanticTaskParameters.PortfolioRecommend recommend
                        ? recommend : null;
        if (parameters == null) {
            return null;
        }
        AuthorizedSubjectScope scope = parameters.getCandidateSubjects().isEmpty()
                ? AuthorizedSubjectScope.allPublishedCandidates(result.getContentVersion())
                : AuthorizedSubjectScope.exactSubjects(
                        parameters.getCandidateSubjects(), result.getContentVersion());
        Set<String> baseline = parameters.getCapabilityCodes().stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> constraints = Set.of(parameters.getCareerTrack().name());
        Set<String> preferences = Set.of(parameters.getAudienceRole().name());
        OrderedResultSelection selectedResults = outcome.getResultPayload()
                .filter(TaskResultPayload.RecommendationResultPayload.class::isInstance)
                .map(TaskResultPayload.RecommendationResultPayload.class::cast)
                .filter(payload -> !payload.getItems().isEmpty())
                .map(payload -> new OrderedResultSelection(
                        SubjectOrderKind.RECOMMENDATION_RANK,
                        java.util.stream.IntStream.range(0, payload.getItems().size())
                                .mapToObj(index -> new OrderedResultSelection.Item(
                                        index + 1,
                                        PublicResultItemId.forRecommendation(task.getTaskId(),
                                                payload.getItems().get(index).getPortfolioId()),
                                        payload.getItems().get(index).getPortfolioId(),
                                        payload.getItems().get(index).getRoute() != null
                                                && payload.getItems().get(index).getRoute().startsWith("/cases/")
                                                ? SemanticRoutingTypes.SubjectType.CASE
                                                : SemanticRoutingTypes.SubjectType.PROJECT))
                                .toList()))
                .orElse(null);
        return new RecommendationContext(
                scope, "p3-recommendation-profile-v1", baseline, constraints, preferences,
                Set.of(), parameters.getRequestedSize().getValue(), parent,
                selectedResults, PublicResultItemId.recommendationBatch(task.getTaskId()));
    }
}
