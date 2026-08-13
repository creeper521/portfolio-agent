package com.portfolio.agent.answer.composition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GroundedStatementContractTest {
    @Test void carriesFullPublicGroundingWithoutLeakingContentFromToString() {
        GroundedStatement statement = statement();
        assertThat(statement.getStatementType()).isEqualTo(StatementType.FACT);
        assertThat(statement.getSubjectReferences()).containsExactly(new SubjectReference("公开项目"));
        assertThat(statement.getPublicSourceReferences()).hasSize(1);
        assertThat(statement.toString()).doesNotContain("42%", "REF-SECRET", "公开项目");
    }

    @Test void requiresSubjectsAndPublicSourcesAndDefensivelyCopies() {
        assertThatThrownBy(() -> new GroundedStatement(StatementType.FACT, List.of(),
                ControlledPredicate.VERIFIED_BY_TEST, "事实", null,
                AnswerClaimCategory.VERIFICATION, AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.COLLABORATIVE, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerMateriality.KEY, SupportTarget.SUBJECT, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static GroundedStatement statement() {
        return new GroundedStatement(StatementType.FACT,
                List.of(new SubjectReference("公开项目")), ControlledPredicate.VERIFIED_BY_TEST,
                "计划在 2026 年协作完成 API 原型 42%", "阶段性验证",
                AnswerClaimCategory.VERIFICATION, AnswerAchievementStatus.PLANNED,
                AnswerContributionType.COLLABORATIVE, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerMateriality.KEY, SupportTarget.SUBJECT,
                List.of(new PublicSourceReferenceValue("REF-SECRET", "来源", "v1", "CASE",
                        "/projects/public", "/evidence/public")));
    }
}
