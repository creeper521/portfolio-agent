package com.portfolio.agent.answer.composition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.portfolio.agent.answer.composition.codec.PortfolioExpressionDraftCodec;
import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.CompositionMode;
import com.portfolio.agent.answer.composition.domain.ControlledPredicate;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionDisposition;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
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
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import com.portfolio.agent.answer.composition.projection.ExpressionInputDocument;
import com.portfolio.agent.answer.composition.projection.ModelExpressionInputProjector;
import com.portfolio.agent.answer.composition.validation.FactDraftValidator;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioAnswerCompositionTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private DeterministicPortfolioAnswerComposer deterministicComposer;
    private PortfolioAnswerPlanValidator planValidator;
    private PortfolioExpressionPort expressionPort;
    private ModelExpressionInputProjector projector;
    private PortfolioExpressionDraftCodec codec;
    private FactDraftValidator draftValidator;
    private PortfolioAnswerPlan fallback;

    @BeforeEach
    void setUp() {
        deterministicComposer = mock(DeterministicPortfolioAnswerComposer.class);
        planValidator = mock(PortfolioAnswerPlanValidator.class);
        expressionPort = mock(PortfolioExpressionPort.class);
        projector = mock(ModelExpressionInputProjector.class);
        codec = mock(PortfolioExpressionDraftCodec.class);
        draftValidator = mock(FactDraftValidator.class);
        fallback = new PortfolioAnswerPlan("公开项目", null, List.of(
                new PortfolioAnswerSection(AnswerSectionType.SOLUTION, "方案", "已完成公开实现",
                        List.of(), List.of("source-1"))));
        when(deterministicComposer.compose(any())).thenReturn(fallback);
        when(projector.project(any(), any())).thenReturn(
                new ExpressionInputDocument("{}", new ExpressionAliasRegistry(), false));
    }

    @Test
    void disabledReturnsThePrebuiltPlanAndNeverProjectsOrCallsProvider() {
        com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult result =
                composition(null, false).compose(material(), context());

        assertThat(result.getPlan()).isSameAs(fallback);
        assertThat(result.getCompositionMode()).isEqualTo(CompositionMode.DETERMINISTIC);
        assertThat(result.getExpressionDisposition())
                .isEqualTo(ExpressionDisposition.NOT_ATTEMPTED_DISABLED);
        assertThat(result.isExpressionDegraded()).isFalse();
        verify(projector, never()).project(any(), any());
        verify(expressionPort, never()).express(any(), any());
    }

    @Test
    void providerAndSchemaFailuresAtomicallyReturnTheSameFallbackWithDistinctDisposition() {
        when(expressionPort.express(any(), any())).thenThrow(new IllegalStateException("secret"));
        com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult providerFailure =
                composition(expressionPort, true).compose(material(), context());
        assertFallback(providerFailure, ExpressionDisposition.FALLBACK_PROVIDER_FAILURE);

        reset(expressionPort);
        when(expressionPort.express(any(), any())).thenReturn(ModelExpressionResult.success("{}"));
        when(codec.decode("{}", MaterialKind.FACT)).thenThrow(new IllegalArgumentException("schema"));
        com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult schemaFailure =
                composition(expressionPort, true).compose(material(), context());
        assertFallback(schemaFailure, ExpressionDisposition.FALLBACK_SCHEMA_INVALID);
    }

    @Test
    void emptyResponseHasItsOwnDispositionAndCountsAsDegradedFallback() {
        when(expressionPort.express(any(), any())).thenReturn(ModelExpressionResult.empty());
        assertFallback(composition(expressionPort, true).compose(material(), context()),
                ExpressionDisposition.FALLBACK_EMPTY_RESPONSE);
    }

    @Test
    void projectorInvariantFailureIsPlanFallbackNotInputLimit() {
        when(projector.project(any(), any())).thenThrow(new IllegalStateException("projection invariant"));

        com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult result =
                composition(expressionPort, true).compose(material(), context());

        assertFallback(result, ExpressionDisposition.FALLBACK_PLAN_INVALID);
        verify(expressionPort, never()).express(any(), any());
    }

    private PortfolioAnswerComposition composition(PortfolioExpressionPort port, boolean enabled) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new PortfolioAnswerComposition(deterministicComposer, planValidator, port,
                projector, codec, new ExpressionCircuitBreaker(clock), draftValidator,
                new ModelExpressionEligibilityPolicy(), clock, enabled);
    }

    private void assertFallback(com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult result,
            ExpressionDisposition disposition) {
        assertThat(result.getPlan()).isSameAs(fallback);
        assertThat(result.getCompositionMode()).isEqualTo(CompositionMode.FALLBACK);
        assertThat(result.getExpressionDisposition()).isEqualTo(disposition);
        assertThat(result.isExpressionDegraded()).isTrue();
    }

    private FactAnswerMaterial material() {
        SubjectReference subject = new SubjectReference("公开项目");
        PublicSourceReferenceValue source = new PublicSourceReferenceValue(
                "source-1", "公开来源", "v1", "PORTFOLIO", "/projects/demo", "/projects/demo#evidence");
        GroundedStatement statement = new GroundedStatement(
                StatementType.FACT, List.of(subject), ControlledPredicate.IMPLEMENTED,
                "已完成公开实现", null, AnswerClaimCategory.IMPLEMENTATION,
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerMateriality.KEY,
                SupportTarget.SUBJECT, List.of(source));
        ExpressionStatement expression = new ExpressionStatement(
                statement, PresentationRole.REQUIRED, AnswerSectionType.SOLUTION, 0);
        return new FactAnswerMaterial("公开项目", subject, FocusMode.OVERVIEW,
                List.of(new FactAnswerMaterial.FactSection(
                        AnswerSectionType.SOLUTION, List.of(expression), OrderingPolicy.STABLE)),
                SummaryPolicy.REQUIRED, List.of(), List.of());
    }

    private PortfolioCompositionContext context() {
        ExpressionIntent intent = new ExpressionIntent(
                TaskKind.FACT, FocusMode.OVERVIEW, List.of(RequestedFacet.SOLUTION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.GUEST,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("公开项目"));
        return new PortfolioCompositionContext(intent,
                new ExpressionAllowance(true, NOW.plusSeconds(10), 4_000, 16, 1));
    }
}
