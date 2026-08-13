package com.portfolio.agent.answer.composition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositionValueSemanticsTest {
    @Test void intentAllowanceContextAndResultHaveValueSemanticsAndSafeToString() {
        List<String> labels = new ArrayList<>(List.of("PUBLIC_LABEL_SENTINEL"));
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT, labels);
        labels.add("MUTATION");
        ExpressionIntent sameIntent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("PUBLIC_LABEL_SENTINEL"));
        assertThat(intent).isEqualTo(sameIntent).hasSameHashCodeAs(sameIntent);
        assertThat(intent.getSubjectDisplayLabels()).containsExactly("PUBLIC_LABEL_SENTINEL");
        assertThat(intent.toString()).doesNotContain("PUBLIC_LABEL_SENTINEL");

        Instant deadline = Instant.parse("2026-08-13T00:00:00Z");
        ExpressionAllowance allowance = new ExpressionAllowance(true, deadline, 1800, 16, 1);
        ExpressionAllowance sameAllowance = new ExpressionAllowance(true, deadline, 1800, 16, 1);
        PortfolioCompositionContext context = new PortfolioCompositionContext(intent, allowance);
        PortfolioCompositionContext sameContext = new PortfolioCompositionContext(sameIntent, sameAllowance);
        assertThat(context).isEqualTo(sameContext).hasSameHashCodeAs(sameContext);

        PortfolioAnswerPlan plan = new PortfolioAnswerPlan("CONTENT_SENTINEL", null,
                List.of(new PortfolioAnswerSection(AnswerSectionType.SOLUTION, "标题", "正文",
                        List.of(), List.of("REF"))));
        PortfolioCompositionResult result = new PortfolioCompositionResult(plan,
                CompositionMode.MODEL_GROUNDED, ExpressionDisposition.ACCEPTED, false);
        PortfolioCompositionResult sameResult = new PortfolioCompositionResult(plan,
                CompositionMode.MODEL_GROUNDED, ExpressionDisposition.ACCEPTED, false);
        assertThat(result).isEqualTo(sameResult).hasSameHashCodeAs(sameResult);
        assertThat(result.toString()).doesNotContain("CONTENT_SENTINEL", "REF");
    }
}
