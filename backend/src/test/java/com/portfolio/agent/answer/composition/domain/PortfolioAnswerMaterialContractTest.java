package com.portfolio.agent.answer.composition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioAnswerMaterialContractTest {
    @Test void factContributionRetainsStructuredPublicSourcesBeforeAnyModelWork() {
        GroundedStatement statement = GroundedStatementContractTest.statement();
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(new ExpressionStatement(statement, PresentationRole.REQUIRED,
                                AnswerSectionType.VERIFICATION, 0)), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of("仅覆盖公开验证"), List.of("生产指标"));
        assertThat(material.toGroundedContribution().getSourceReferences())
                .containsExactlyElementsOf(statement.getPublicSourceReferences());
        assertThat(material.toGroundedContribution().getPublicSourceReferences())
                .containsExactly("REF-SECRET");
    }

    @Test void contributionDeduplicatesDifferentPublicSourceValuesByReferenceKey() {
        GroundedStatement first = GroundedStatementContractTest.statement();
        GroundedStatement second = new GroundedStatement(first.getStatementType(),
                first.getSubjectReferences(), first.getControlledPredicate(), "另一条公开事实", null,
                first.getClaimCategory(), first.getAchievementStatus(), first.getContributionType(),
                first.getVerificationBasis(), first.getMateriality(), first.getSupportTarget(),
                List.of(new PublicSourceReferenceValue("REF-SECRET", "不同标签", "v2", "CASE",
                        "/projects/another", "/evidence/another")));
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(new ExpressionStatement(first, PresentationRole.REQUIRED,
                                        AnswerSectionType.VERIFICATION, 0),
                                new ExpressionStatement(second, PresentationRole.OPTIONAL,
                                        AnswerSectionType.VERIFICATION, 1)), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of(), List.of());
        assertThat(material.toGroundedContribution().getPublicSourceReferences())
                .containsExactly("REF-SECRET");
        assertThat(material.toGroundedContribution().getSourceReferences()).containsExactly(
                first.getPublicSourceReferences().get(0));
    }
}
