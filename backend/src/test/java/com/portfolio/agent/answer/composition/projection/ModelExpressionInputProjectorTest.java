package com.portfolio.agent.answer.composition.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatementContractTest;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.RequestedFacet;
import com.portfolio.agent.answer.composition.domain.RequestedOutput;
import com.portfolio.agent.answer.composition.domain.ResponseDepth;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelExpressionInputProjectorTest {
    @Test void usesPForSubjectAndSForStatementAndExcludesServerOnlyData() {
        com.portfolio.agent.answer.composition.domain.GroundedStatement statement =
                GroundedStatementContractTest.statement();
        FactAnswerMaterial material = new FactAnswerMaterial("GOAL_LABEL_SENTINEL",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(new ExpressionStatement(statement, PresentationRole.REQUIRED,
                                AnswerSectionType.VERIFICATION, 0)), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of("CAVEAT_SENTINEL"), List.of("OMITTED_SENTINEL"));
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("公开项目"));
        PortfolioCompositionContext context = new PortfolioCompositionContext(intent,
                new ExpressionAllowance(true, Instant.now().plusSeconds(5), 1800, 16, 1));
        ExpressionInputDocument result = new ModelExpressionInputProjector().project(material, context);
        assertThat(result.getSerializedJson())
                .contains("\"key\":\"P01\"", "\"key\":\"S001\"", "\"locale\":\"zh-CN\"")
                .doesNotContain("GOAL_LABEL_SENTINEL", "CAVEAT_SENTINEL", "OMITTED_SENTINEL",
                        "REF-SECRET", "/projects/public", "/evidence/public");
        assertThat(result.getAliases().subject("P01").getPublicLabel()).isEqualTo("公开项目");
        assertThat(result.getAliases().statement("S001")).isSameAs(statement);
    }

    @Test void honorsAStatementLimitTighterThanTheBuildMaximum() {
        com.portfolio.agent.answer.composition.domain.GroundedStatement statement =
                GroundedStatementContractTest.statement();
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(new ExpressionStatement(statement, PresentationRole.REQUIRED,
                                AnswerSectionType.VERIFICATION, 0)), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of(), List.of());
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("公开项目"));
        PortfolioCompositionContext context = new PortfolioCompositionContext(intent,
                new ExpressionAllowance(true, Instant.now().plusSeconds(5), 1800, 0, 1));
        assertThat(new ModelExpressionInputProjector().project(material, context).isOverLimit())
                .isTrue();
    }
}
