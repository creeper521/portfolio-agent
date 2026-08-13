package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.ControlledPredicate;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.ModelExpressionResult;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.RequestedFacet;
import com.portfolio.agent.answer.composition.domain.RequestedOutput;
import com.portfolio.agent.answer.composition.domain.ResponseDepth;
import com.portfolio.agent.answer.composition.domain.StatementType;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.SupportTarget;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionPort;
import com.portfolio.agent.answer.composition.service.DeterministicPortfolioAnswerComposer;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerComposition;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerPlanValidator;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class P4CompositionEvalRunnerTest {
    @Test
    void invalidFakeProviderRunsThroughProductionSeamAndObservesAtomicFallback() {
        AtomicInteger calls = new AtomicInteger();
        PortfolioExpressionPort fakeProvider = (request, deadline) -> {
            calls.incrementAndGet();
            return ModelExpressionResult.empty();
        };
        PortfolioAnswerComposition composition = new PortfolioAnswerComposition(
                new DeterministicPortfolioAnswerComposer(),
                new PortfolioAnswerPlanValidator(), fakeProvider);

        P4EvalSample sample = new P4CompositionEvalRunner(composition, calls::get)
                .run(material(), context(), true);

        assertThat(calls).hasValue(1);
        assertThat(sample.isMockProviderInvoked()).isTrue();
        assertThat(sample.getChecks()).allSatisfy((check, passed) ->
                assertThat(passed).as(check.name()).isTrue());
        assertThat(sample.getChecks()).containsKeys(P4SafetyCheck.values());
    }

    @Test
    void circuitOpenIsObservedAsZeroProviderAttempts() {
        AtomicInteger calls = new AtomicInteger();
        PortfolioExpressionPort failingProvider = (request, deadline) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("provider unavailable");
        };
        P4CompositionEvalRunner runner = new P4CompositionEvalRunner(
                new PortfolioAnswerComposition(new DeterministicPortfolioAnswerComposer(),
                        new PortfolioAnswerPlanValidator(), failingProvider), calls::get);

        assertThat(runner.run(material(), context(), true).isMockProviderInvoked()).isTrue();
        assertThat(runner.run(material(), context(), true).isMockProviderInvoked()).isTrue();
        assertThat(runner.run(material(), context(), true).isMockProviderInvoked()).isTrue();
        P4EvalSample circuitOpen = runner.run(material(), context(), true);

        assertThat(calls).hasValue(3);
        assertThat(circuitOpen.isMockProviderInvoked()).isFalse();
    }

    private FactAnswerMaterial material() {
        SubjectReference subject = new SubjectReference("公开项目");
        PublicSourceReferenceValue source = new PublicSourceReferenceValue(
                "source-1", "公开来源", "v1", "PORTFOLIO",
                "/projects/demo", "/evidence/demo");
        GroundedStatement statement = new GroundedStatement(
                StatementType.FACT, List.of(subject), ControlledPredicate.IMPLEMENTED,
                "已完成公开实现", null, AnswerClaimCategory.IMPLEMENTATION,
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerMateriality.KEY,
                SupportTarget.SUBJECT, List.of(source));
        ExpressionStatement expression = new ExpressionStatement(
                statement, PresentationRole.REQUIRED, AnswerSectionType.SOLUTION, 0);
        return new FactAnswerMaterial("公开项目", subject, FocusMode.OVERVIEW,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.SOLUTION,
                        List.of(expression), OrderingPolicy.STABLE)),
                SummaryPolicy.REQUIRED, List.of(), List.of());
    }

    private PortfolioCompositionContext context() {
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.OVERVIEW,
                List.of(RequestedFacet.SOLUTION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.GUEST,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("公开项目"));
        return new PortfolioCompositionContext(intent, new ExpressionAllowance(
                true, Instant.now().plusSeconds(10), 4_000, 16, 1));
    }
}
