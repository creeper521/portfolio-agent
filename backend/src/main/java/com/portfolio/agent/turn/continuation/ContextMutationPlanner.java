package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Builds state mutations from fulfillment SemanticResults and authorized bindings only. */
public final class ContextMutationPlanner {
    private final Supplier<String> handleIssuer;
    public ContextMutationPlanner(Supplier<String> handleIssuer) {
        this.handleIssuer = Objects.requireNonNull(handleIssuer, "handleIssuer");
    }

    public List<Mutation> plan(
            String conversationId, SemanticTurnPlan plan, SemanticTurnOutcome outcome,
            Instant expiresAt) {
        Map<String, TaskOutcome> outcomes = new LinkedHashMap<>();
        outcome.getTaskOutcomes().forEach(value -> outcomes.put(value.getTaskId(), value));
        List<Mutation> mutations = new ArrayList<>();
        for (UserGoal goal : plan.getUserGoals()) {
            TaskOutcome taskOutcome = outcomes.get(goal.getFulfillmentTaskId());
            if (taskOutcome == null || taskOutcome.getProducedArtifact().isEmpty()
                    || !(taskOutcome.getProducedArtifact().orElseThrow().getSemanticResult()
                    instanceof PortfolioSemanticResult.Recommendation result)) continue;
            SemanticTask task = plan.findTask(goal.getFulfillmentTaskId()).orElseThrow();
            ContinuationContext context = context(
                    conversationId, plan.getContentReleaseId(), expiresAt,
                    goal, task, result);
            mutations.add(new Mutation(goal.getGoalId(), context));
        }
        return List.copyOf(mutations);
    }

    private ContinuationContext context(
            String conversationId, String release, Instant expiresAt,
            UserGoal goal, SemanticTask task,
            PortfolioSemanticResult.Recommendation recommendation) {
        String handle = handleIssuer.get();
        Set<String> constraints = constraints(task);
        AuthorizedSubjectScope scope =
                recommendation.getAuthorizedSubjectScope();
        boolean allPublished = scope.getMode() == AuthorizedSubjectScope.Mode.ALL_PUBLISHED;
        Set<String> authorizedSubjects = allPublished ? Set.of() : scope.getSubjects().stream()
                .map(AuthorizedSubjectScope.Subject::getReference)
                .collect(java.util.stream.Collectors.toSet());
        List<ContinuationContext.ResultItem> items = new ArrayList<>();
        for (int index = 0; index < recommendation.getSelectedSubjectIds().size(); index++) {
            items.add(new ContinuationContext.ResultItem(
                    "item-" + goal.getGoalId() + "-" + (index + 1),
                    recommendation.getSelectedSubjectIds().get(index)));
        }
        return new ContinuationContext.Recommendation(
                handle, conversationId, release, expiresAt,
                allPublished, authorizedSubjects, constraints, Set.of(), Set.of(),
                recommendation.getRequestedSize(), items);
    }

    private Set<String> constraints(SemanticTask task) {
        if (task.getParameters().getParameters()
                instanceof UserGoalProposal.PortfolioRecommendationParameters value) {
            return value.getConstraints();
        }
        return Set.of();
    }

    public record Mutation(String goalId, ContinuationContext context) {
        public Mutation {
            goalId = ContinuationContext.text(goalId, "goalId");
            Objects.requireNonNull(context, "context");
        }
    }
}
