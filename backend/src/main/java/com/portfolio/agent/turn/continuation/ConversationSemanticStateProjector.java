package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.projection.AnswerGoalResult;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicPresentation;
import com.portfolio.agent.turn.projection.PublicSection;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Projects only backend-owned enums and public identifiers; no generated text survives. */
public final class ConversationSemanticStateProjector {

    public ConversationSemanticState project(
            SemanticTurnPlan plan, PublicAgentTurn turn, Instant updatedAt) {
        if (!(turn instanceof PublicAgentTurn.Answer answer)) return null;
        List<ConversationSemanticState.GoalSummary> goals = answer.getAnswer()
                .getGoalResults().stream()
                .filter(result -> result.getCoverage() != AnswerGoalResult.Coverage.NONE)
                .map(result -> goal(plan, result))
                .filter(ConversationSemanticState.GoalSummary::isPortfolioContinuationSafe)
                .toList();
        return goals.isEmpty() ? null : new ConversationSemanticState(
                plan.getContentReleaseId(), goals, updatedAt);
    }

    private ConversationSemanticState.GoalSummary goal(
            SemanticTurnPlan plan, AnswerGoalResult result) {
        UserGoal goal = plan.getUserGoals().stream()
                .filter(value -> value.getGoalId().equals(result.getGoalId()))
                .findFirst().orElseThrow();
        SemanticTask task = plan.findTask(goal.getFulfillmentTaskId()).orElseThrow();
        UserGoalProposal.GoalParameters parameters = task.getParameters().getParameters();
        Set<UserGoalProposal.Facet> facets = parameters
                instanceof UserGoalProposal.PortfolioFactParameters value
                ? value.getFacets() : Set.of();
        UserGoalProposal.Depth depth = switch (parameters) {
            case UserGoalProposal.PortfolioFactParameters value -> value.getDepth();
            case UserGoalProposal.GeneralExplanationParameters value -> value.getDepth();
            case UserGoalProposal.ApplyConceptParameters value -> value.getDepth();
            default -> null;
        };
        Set<UserGoalProposal.PortfolioComparisonDimension> dimensions = parameters
                instanceof UserGoalProposal.PortfolioCompareParameters value
                ? value.getDimensions() : Set.of();
        Integer requestedSize = parameters
                instanceof UserGoalProposal.PortfolioRecommendationParameters value
                ? value.getRequestedSize() : null;
        Set<String> constraints = parameters
                instanceof UserGoalProposal.PortfolioRecommendationParameters value
                ? value.getConstraints() : Set.of();
        return new ConversationSemanticState.GoalSummary(
                goal.getGoalId(), goal.getKind(), goal.getSubjects().stream()
                .filter(subject -> subject.getKind()
                        != com.portfolio.agent.turn.planning.GoalSubjectReference.Kind.RESULT)
                .map(subject -> new ConversationSemanticState.Subject(
                        subject.getKind(), subject.getReference())).distinct().toList(),
                goal.getRequestedOutputs(), facets, depth, dimensions,
                requestedSize, constraints, sections(result.getPresentation()));
    }

    private List<ConversationSemanticState.SectionReference> sections(
            PublicPresentation presentation) {
        if (presentation instanceof PublicPresentation.Sectioned sectioned) {
            return sectioned.getSections().stream().map(this::section).toList();
        }
        if (presentation instanceof PublicPresentation.Recommendation recommendation) {
            return recommendation.getSupportingSections().stream()
                    .map(this::section).toList();
        }
        return List.of();
    }

    private ConversationSemanticState.SectionReference section(PublicSection section) {
        return new ConversationSemanticState.SectionReference(
                section.getSectionId(), section.getSectionKind());
    }
}
