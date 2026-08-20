package com.portfolio.agent.turn.projection;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.general.GeneralPresentation;
import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.execution.GoalCoverage;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Set;

final class ProjectionTestFixtures {
    private ProjectionTestFixtures() { }

    static SemanticTurnPlan generalPlan() {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("幂等", 0);
        UserGoalProposal.GeneralExplanationParameters parameters =
                new UserGoalProposal.GeneralExplanationParameters(
                anchor, UserGoalProposal.Depth.STANDARD);
        SemanticTask task = SemanticTask.of(
                "task-general", SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(GoalKind.GENERAL_EXPLANATION, parameters, List.of()),
                Set.of(GoalRequestedOutput.EXPLANATION));
        UserGoal goal = new UserGoal(
                "goal-general", "解释幂等", GoalKind.GENERAL_EXPLANATION,
                List.of(), Set.of(GoalRequestedOutput.EXPLANATION), task.getTaskId());
        return new SemanticTurnPlan("public-1", List.of(goal), List.of(task), List.of());
    }

    static SemanticTurnOutcome generalOutcome() {
        GeneralSemanticResult result = new GeneralSemanticResult(
                "幂等", List.of(
                        new GeneralSemanticResult.Statement(
                                GeneralSemanticResult.Role.DEFINITION,
                                "重复执行保持稳定业务结果。", null, null),
                        new GeneralSemanticResult.Statement(
                                GeneralSemanticResult.Role.MECHANISM,
                                "系统使用请求身份识别重复执行。", null, null)),
                List.of(), "public-1");
        GeneralPresentation presentation = new GeneralPresentation(
                "幂等", List.of(new GeneralPresentation.Section(
                        AnswerSectionType.BACKGROUND, "核心概念", "重复执行保持稳定业务结果。")));
        TaskArtifact artifact = new TaskArtifact(result, presentation, TaskProvenance.none());
        return new SemanticTurnOutcome(
                List.of(new TaskOutcome("task-general",
                        new TaskOutcome.Produced(artifact, TaskOutcome.Fulfillment.FULL))),
                List.of(new GoalCoverage("goal-general", GoalCoverage.Coverage.FULL)));
    }

    static ValidatedEvidenceUnit unit(String subjectId, String key, String statement) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-" + subjectId, AnswerClaimCategory.IMPLEMENTATION,
                statement, "detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("evidence-" + subjectId));
        return new ValidatedEvidenceUnit(subjectId, claim, new PublicSourceReferenceValue(
                key, "Evidence " + subjectId, "public-1", "DOCUMENT",
                "/projects/" + subjectId, "/evidence/" + subjectId));
    }

    static TaskArtifact portfolioFactArtifact(List<ValidatedEvidenceUnit> units) {
        PortfolioSemanticResult result = new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.FULL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), units, List.of());
        List<PortfolioPresentation.Section> sections = units.stream().map(unit ->
                new PortfolioPresentation.Section(
                        AnswerSectionType.SOLUTION, "方案", unit.getClaim().getStatement(),
                        List.of(new PublicSourceReferenceValue(
                                unit.getSourceReference().getReferenceKey(),
                                unit.getSourceReference().getLabel(), "public-1", "DOCUMENT",
                                unit.getSourceReference().getSubjectRoute(),
                                unit.getSourceReference().getEvidenceRoute())))).toList();
        return new TaskArtifact(result, new PortfolioPresentation("回答", sections),
                new TaskProvenance(units.stream().map(value ->
                        value.getSourceReference().getReferenceKey()).toList()));
    }

    static SemanticTurnPlan recommendationPlan() {
        UserGoalProposal.PortfolioRecommendationParameters parameters =
                new UserGoalProposal.PortfolioRecommendationParameters(2, Set.of());
        SemanticTask task = SemanticTask.of(
                "task-recommendation", SemanticTask.Type.PORTFOLIO_RECOMMEND,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_RECOMMEND, parameters, List.of()),
                Set.of(GoalRequestedOutput.RECOMMENDATION));
        UserGoal goal = new UserGoal(
                "goal-recommendation", "推荐项目", GoalKind.PORTFOLIO_RECOMMEND,
                List.of(), Set.of(GoalRequestedOutput.RECOMMENDATION), task.getTaskId());
        return new SemanticTurnPlan("public-1", List.of(goal), List.of(task), List.of());
    }

    static SemanticTurnOutcome recommendationOutcome() {
        ValidatedEvidenceUnit unit = unit("project-a", "E-01", "项目具备完整交付证据。");
        PortfolioSemanticResult.Recommendation result = new PortfolioSemanticResult.Recommendation(
                PortfolioSemanticResult.Coverage.PARTIAL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), List.of(unit),
                List.of("REQUESTED_SIZE"), 2, List.of("project-a"));
        PortfolioPresentation presentation = new PortfolioPresentation(
                "推荐", List.of(new PortfolioPresentation.Section(
                        AnswerSectionType.SOLUTION, "推荐理由", unit.getClaim().getStatement(),
                        List.of(new PublicSourceReferenceValue(
                                "E-01", "Evidence project-a", "public-1", "DOCUMENT",
                                "/projects/project-a", "/evidence/project-a")))));
        TaskArtifact artifact = new TaskArtifact(
                result, presentation, new TaskProvenance(List.of("E-01")));
        return new SemanticTurnOutcome(
                List.of(new TaskOutcome("task-recommendation",
                        new TaskOutcome.Produced(artifact, TaskOutcome.Fulfillment.PARTIAL))),
                List.of(new GoalCoverage("goal-recommendation", GoalCoverage.Coverage.PARTIAL)));
    }
}
