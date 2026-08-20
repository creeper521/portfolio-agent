package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.execution.CancellationSignal;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class CrossDomainTestFixtures {
    private CrossDomainTestFixtures() { }

    static GeneralSemanticResult general(String topic) {
        return new GeneralSemanticResult(topic, List.of(
                new GeneralSemanticResult.Statement(
                        GeneralSemanticResult.Role.DEFINITION, "并发控制协调同时发生的工作。", null, null),
                new GeneralSemanticResult.Statement(
                        GeneralSemanticResult.Role.MECHANISM, "有界调度限制竞争。", null, null)),
                List.of("机制取决于运行环境。"), "public-1");
    }

    static PortfolioSemanticResult portfolio() {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.IMPLEMENTATION,
                "任务引擎使用有界并发调度。", "detail",
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("evidence-1"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit(
                "project-a", claim, new PublicSourceReferenceValue(
                        "E-01", "并发实现证据", "public-1", "DOCUMENT",
                        "/projects/project-a", "/evidence/e-01"));
        return new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.FULL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), List.of(unit), List.of());
    }

    static TaskExecutionContext context(String concept) {
        TaskExecutionContext context = mock(TaskExecutionContext.class);
        UserGoalProposal.ApplyConceptParameters parameters =
                new UserGoalProposal.ApplyConceptParameters(
                        new UserGoalProposal.InputAnchor(concept, 0), UserGoalProposal.Facet.SOLUTION);
        when(context.getTask()).thenReturn(SemanticTask.of(
                "task-synthesis", SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS,
                new SemanticTaskParameters(
                        GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO, parameters, List.of())));
        when(context.getDependencyResults()).thenReturn(List.of(general(concept), portfolio()));
        when(context.getDeadline()).thenReturn(
                TurnDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()));
        when(context.getCancellation()).thenReturn(new CancellationSignal());
        return context;
    }
}
