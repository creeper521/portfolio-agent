package com.portfolio.agent.answer.composition.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ModelProviderRegistrySnapshot;
import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.CompositionMode;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionDisposition;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatementContractTest;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.ModelExpressionResult;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.RequestedFacet;
import com.portfolio.agent.answer.composition.domain.RequestedOutput;
import com.portfolio.agent.answer.composition.domain.ResponseDepth;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionPort;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerComposition;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PortfolioExpressionProductionChainIntegrationTest {

    @Test
    void disabledConfigurationBuildsDeterministicChainWithoutExpressionPortOrExternalCall() {
        AtomicInteger calls = new AtomicInteger();
        contextRunner().withPropertyValues("portfolio.model-expression.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PortfolioExpressionPort.class);
                    PortfolioCompositionResult result = context.getBean(
                            PortfolioAnswerComposition.class).compose(material(), compositionContext());
                    assertThat(result.getCompositionMode()).isEqualTo(CompositionMode.DETERMINISTIC);
                    assertThat(result.getExpressionDisposition())
                            .isEqualTo(ExpressionDisposition.NOT_ATTEMPTED_DISABLED);
                    assertThat(calls).hasValue(0);
                });
    }

    @Test
    void enabledConfigurationUsesInjectedPortAndReturnsGroundedPlanWithPublicSources() {
        AtomicInteger calls = new AtomicInteger();
        PortfolioExpressionPort fakePort = (request, deadline) -> {
            calls.incrementAndGet();
            assertThat(request.getSerializedInput())
                    .contains("S001", "公开项目")
                    .doesNotContain("REF-SECRET", "/projects/public", "goalLabel", "question");
            return ModelExpressionResult.success("""
                    {"schemaVersion":"portfolio-expression-draft.v1","materialKind":"FACT",
                     "summary":null,"sections":[{"sectionType":"VERIFICATION","sentences":[
                     {"text":"计划在 2026 年协作完成 API 原型 42%，阶段性验证","supports":["S001"]}]}]}
                    """);
        };
        contextRunner()
                .withBean(PortfolioExpressionPort.class, () -> fakePort)
                .withPropertyValues("portfolio.model-expression.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PortfolioExpressionPort.class);
                    PortfolioCompositionResult result = context.getBean(
                            PortfolioAnswerComposition.class).compose(material(), compositionContext());
                    assertThat(result.getCompositionMode())
                            .as("disposition=%s", result.getExpressionDisposition())
                            .isEqualTo(CompositionMode.MODEL_GROUNDED);
                    assertThat(result.getExpressionDisposition()).isEqualTo(ExpressionDisposition.ACCEPTED);
                    assertThat(result.getPlan().getSections().getFirst().getSourceReferences())
                            .extracting(com.portfolio.agent.answer.domain.PublicSourceReferenceValue::getReferenceKey)
                            .containsExactly("REF-SECRET");
                    assertThat(calls).hasValue(1);
                });
    }

    @Test
    void enabledProviderFailureReturnsExactDeterministicBodyAndFallbackMetadata() {
        PortfolioExpressionPort failingPort = (request, deadline) -> {
            throw new IllegalStateException("provider detail must remain private");
        };
        contextRunner().withBean(PortfolioExpressionPort.class, () -> failingPort)
                .withPropertyValues("portfolio.model-expression.enabled=true")
                .run(context -> {
                    PortfolioAnswerComposition composition =
                            context.getBean(PortfolioAnswerComposition.class);
                    PortfolioCompositionResult deterministic = contextRunnerComposition(context)
                            .compose(material(), compositionContext());
                    PortfolioCompositionResult failed = composition.compose(
                            material(), compositionContext());
                    assertThat(failed.getCompositionMode()).isEqualTo(CompositionMode.FALLBACK);
                    assertThat(failed.getExpressionDisposition())
                            .isEqualTo(ExpressionDisposition.FALLBACK_PROVIDER_FAILURE);
                    assertThat(failed.getPlan()).isEqualTo(deterministic.getPlan());
                    assertThat(failed.getPlan().getSections()).extracting(
                                    com.portfolio.agent.answer.domain.PortfolioAnswerSection::getContent)
                            .containsExactlyElementsOf(deterministic.getPlan().getSections().stream()
                                    .map(com.portfolio.agent.answer.domain.PortfolioAnswerSection::getContent)
                                    .toList());
                });
    }

    private PortfolioAnswerComposition contextRunnerComposition(
            org.springframework.context.ApplicationContext context) {
        return new PortfolioAnswerComposition();
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(PortfolioExpressionConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ModelProviderRegistrySnapshot.class, ModelProviderRegistrySnapshot::builtIn)
                .withBean(com.portfolio.agent.common.observability.DiagnosticEventPublisher.class,
                        () -> event -> { });
    }

    private FactAnswerMaterial material() {
        ExpressionStatement statement = new ExpressionStatement(
                GroundedStatementContractTest.statement(), PresentationRole.REQUIRED,
                AnswerSectionType.VERIFICATION, 0);
        return new FactAnswerMaterial("SERVER_ONLY_GOAL_LABEL",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(
                        AnswerSectionType.VERIFICATION, List.of(statement), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of("服务端固定边界"), List.of("未覆盖项"));
    }

    private PortfolioCompositionContext compositionContext() {
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("公开项目"));
        return new PortfolioCompositionContext(intent,
                new ExpressionAllowance(true, Instant.now().plusSeconds(5), 1800, 16, 1));
    }
}
