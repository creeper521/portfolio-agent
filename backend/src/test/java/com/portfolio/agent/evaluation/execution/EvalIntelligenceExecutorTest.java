package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvalIntelligenceExecutorTest {

    @Test
    void answeredRunExtractsClaimsAndEvidenceWithoutOracleAccess() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        when(intelligence.tryResolve(org.mockito.ArgumentMatchers.any(PortfolioTurn.class)))
                .thenReturn(new PortfolioDecision(
                        PortfolioDisposition.ANSWERED,
                        result(AnswerIntentSource.MODEL)));
        EvalIntelligenceExecutor executor = new EvalIntelligenceExecutor(intelligence);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(
                        new EvalMessage("user", "请介绍 SQL 审计项目")),
                        EvalLayer.INTELLIGENCE, 1),
                new EvalRunContext("run-1", "2026-08-06.1"));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.PASS);
        assertThat(observation.getSelectedClaimIds()).contains("claim-sql-audit");
        assertThat(observation.getSelectedEvidenceIds()).contains("E-01");
        assertThat(observation.isProviderInvoked()).isTrue();
        assertThat(observation.getReasonCodes()).contains("ANSWERED");
    }

    @Test
    void intelligenceLoopbackBecomesASanitizedErrorObservation() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        when(intelligence.tryResolve(org.mockito.ArgumentMatchers.any(PortfolioTurn.class)))
                .thenThrow(new IllegalStateException("sensitive loopback detail"));
        EvalIntelligenceExecutor executor = new EvalIntelligenceExecutor(intelligence);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(
                        new EvalMessage("user", "请介绍 SQL 审计项目")),
                        EvalLayer.INTELLIGENCE, 1),
                new EvalRunContext("run-1", "2026-08-06.1"));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.ERROR);
        assertThat(observation.getReasonCodes()).contains("EXECUTOR_ERROR");
    }

    @Test
    void invalidInputMapsToFailureWithClientInvalidCode() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        PortfolioIntelligenceResult emptyResult = result(AnswerIntentSource.RULE);
        when(intelligence.tryResolve(org.mockito.ArgumentMatchers.any(PortfolioTurn.class)))
                .thenReturn(new PortfolioDecision(
                        PortfolioDisposition.INVALID_INPUT, emptyResult));
        EvalIntelligenceExecutor executor = new EvalIntelligenceExecutor(intelligence);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(
                        new EvalMessage("user", "无法理解的问题")),
                        EvalLayer.INTELLIGENCE, 1),
                new EvalRunContext("run-1", "2026-08-06.1"));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.FAIL);
        assertThat(observation.getResolution())
                .isEqualTo(AnswerResolution.INVALID_INPUT);
        assertThat(observation.getReasonCodes()).contains("INVALID_INPUT");
    }

    @Test
    void clarificationMapsToFailureWithClarificationCode() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        when(intelligence.tryResolve(org.mockito.ArgumentMatchers.any(PortfolioTurn.class)))
                .thenReturn(new PortfolioDecision(
                        PortfolioDisposition.NEEDS_CLARIFICATION,
                        result(AnswerIntentSource.RULE)));
        EvalIntelligenceExecutor executor = new EvalIntelligenceExecutor(intelligence);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(
                        new EvalMessage("user", "需要澄清的问题")),
                        EvalLayer.INTELLIGENCE, 1),
                new EvalRunContext("run-1", "2026-08-06.1"));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.FAIL);
        assertThat(observation.getResolution())
                .isEqualTo(AnswerResolution.NEEDS_CLARIFICATION);
        assertThat(observation.getReasonCodes()).contains("NEEDS_CLARIFICATION");
    }

    private PortfolioIntelligenceResult result(AnswerIntentSource source) {
        return new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(new com.portfolio.agent.answer.intelligence.domain
                        .PortfolioRetrievedSubject(
                        "sql-audit", "PROJECT", "SQL 审计与故障排查工具",
                        "SQL 审计与故障排查工具", "PORTFOLIO",
                        java.util.Set.of())),
                List.of(new PortfolioRetrievedPassage(
                        "p-1", "sql-audit", "正文",
                        new com.portfolio.agent.answer.domain.AnswerClaimProjection(
                                "claim-sql-audit",
                                com.portfolio.agent.answer.domain.AnswerClaimCategory.IMPLEMENTATION,
                                "正文",
                                "验证范围以公开证据为限。",
                                com.portfolio.agent.answer.domain.AnswerAchievementStatus.IMPLEMENTED_TESTED,
                                com.portfolio.agent.answer.domain.AnswerContributionType.PRIMARY,
                                com.portfolio.agent.answer.domain.AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                                com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED,
                                com.portfolio.agent.answer.domain.AnswerMateriality.KEY,
                                List.of(),
                                List.of("E-01")),
                        List.of(new PortfolioRetrievedEvidenceReference(
                                "E-01", "E-01", "APPROVED")))),
                null, null, "2026-08-06.1", false, null,
                source, false);
    }
}
