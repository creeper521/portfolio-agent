package com.portfolio.agent.turn.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SemanticPlanCompiler {
    private final SemanticPlanValidator validator;

    public SemanticPlanCompiler(SemanticPlanValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public PlanCompilationResult compile(
            UserGoalProposal proposal,
            String contentReleaseId,
            GoalResolutionContext context) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(context, "context");
        for (UserGoalProposal.ProposedGoal goal : proposal.getGoals()) {
            if (!subjectsArePublic(goal, context)) {
                return PlanCompilationResult.clarificationRequired("PUBLIC_SUBJECT_REQUIRED");
            }
            if (!shapeIsSupported(goal)) {
                return PlanCompilationResult.rejected("GOAL_SHAPE_UNSUPPORTED");
            }
        }

        List<UserGoal> goals = new ArrayList<>();
        List<SemanticTask> tasks = new ArrayList<>();
        List<TaskDependency> dependencies = new ArrayList<>();
        for (int index = 0; index < proposal.getGoals().size(); index++) {
            UserGoalProposal.ProposedGoal proposed = proposal.getGoals().get(index);
            String goalId = "goal-" + (index + 1);
            String fulfillmentTaskId = "task-" + goalId;
            goals.add(new UserGoal(
                    goalId, safeGoalLabel(proposed.getGoalKind()), proposed.getGoalKind(),
                    proposed.getSubjectCandidates(), proposed.getRequestedOutputs(), fulfillmentTaskId));
            if (proposed.getGoalKind() == GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
                compileCrossDomain(proposed, fulfillmentTaskId, tasks, dependencies);
            } else {
                tasks.add(SemanticTask.of(
                        fulfillmentTaskId, taskType(proposed.getGoalKind()),
                        new SemanticTaskParameters(
                                proposed.getGoalKind(), proposed.getParameters(),
                                proposed.getSubjectCandidates()),
                        proposed.getRequestedOutputs()));
            }
        }
        SemanticTurnPlan plan = new SemanticTurnPlan(
                contentReleaseId, List.copyOf(goals), List.copyOf(tasks), List.copyOf(dependencies));
        try {
            return PlanCompilationResult.compiled(validator.validate(plan));
        } catch (IllegalArgumentException invalidPlan) {
            return PlanCompilationResult.rejected("PLAN_INVARIANT_VIOLATION");
        }
    }

    private void compileCrossDomain(
            UserGoalProposal.ProposedGoal proposed,
            String fulfillmentTaskId,
            List<SemanticTask> tasks,
            List<TaskDependency> dependencies) {
        UserGoalProposal.ApplyConceptParameters parameters =
                (UserGoalProposal.ApplyConceptParameters) proposed.getParameters();
        String generalTaskId = fulfillmentTaskId + "-general";
        String portfolioTaskId = fulfillmentTaskId + "-portfolio";
        UserGoalProposal.GeneralExplanationParameters generalParameters =
                new UserGoalProposal.GeneralExplanationParameters(
                        parameters.getConceptAnchor(), UserGoalProposal.Depth.STANDARD);
        UserGoalProposal.PortfolioFactParameters portfolioParameters =
                new UserGoalProposal.PortfolioFactParameters(Set.of(parameters.getPortfolioFacet()));
        tasks.add(SemanticTask.of(generalTaskId, SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(
                        GoalKind.GENERAL_EXPLANATION, generalParameters, List.of())));
        tasks.add(SemanticTask.of(portfolioTaskId, SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(
                        GoalKind.PORTFOLIO_FACT, portfolioParameters,
                        proposed.getSubjectCandidates())));
        tasks.add(SemanticTask.of(fulfillmentTaskId, SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS,
                new SemanticTaskParameters(
                        proposed.getGoalKind(), proposed.getParameters(),
                        proposed.getSubjectCandidates())));
        dependencies.add(new TaskDependency(generalTaskId, fulfillmentTaskId));
        dependencies.add(new TaskDependency(portfolioTaskId, fulfillmentTaskId));
    }

    private boolean subjectsArePublic(
            UserGoalProposal.ProposedGoal goal,
            GoalResolutionContext context) {
        for (GoalSubjectReference subject : goal.getSubjectCandidates()) {
            if (subject.getKind() == GoalSubjectReference.Kind.RESULT) continue;
            boolean found = context.getPublicSubjects().stream().anyMatch(descriptor ->
                    descriptor.getKind() == subject.getKind()
                            && descriptor.getReference().equals(subject.getReference()));
            if (!found) return false;
        }
        return true;
    }

    private boolean shapeIsSupported(UserGoalProposal.ProposedGoal goal) {
        int subjects = goal.getSubjectCandidates().size();
        return switch (goal.getGoalKind()) {
            case PORTFOLIO_FACT -> subjects == 1;
            case PORTFOLIO_COMPARE -> subjects >= 2 && subjects <= 5;
            case PORTFOLIO_RECOMMEND -> subjects == 0;
            case GENERAL_EXPLANATION, GENERAL_COMPARISON -> subjects == 0;
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> subjects == 1
                    && goal.getSubjectCandidates().get(0).getKind() != GoalSubjectReference.Kind.RESULT;
        };
    }

    private SemanticTask.Type taskType(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT -> SemanticTask.Type.PORTFOLIO_FACT;
            case PORTFOLIO_COMPARE -> SemanticTask.Type.PORTFOLIO_COMPARE;
            case PORTFOLIO_RECOMMEND -> SemanticTask.Type.PORTFOLIO_RECOMMEND;
            case GENERAL_EXPLANATION -> SemanticTask.Type.GENERAL_EXPLANATION;
            case GENERAL_COMPARISON -> SemanticTask.Type.GENERAL_COMPARISON;
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO ->
                    SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS;
        };
    }

    private String safeGoalLabel(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT -> "作品集事实";
            case PORTFOLIO_COMPARE -> "项目比较";
            case PORTFOLIO_RECOMMEND -> "项目推荐";
            case GENERAL_EXPLANATION -> "通用概念说明";
            case GENERAL_COMPARISON -> "通用概念比较";
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> "概念与项目关联";
        };
    }
}
