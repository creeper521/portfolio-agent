package com.portfolio.agent.answer.composition.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.composition.domain.ControlledPredicate;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.StatementType;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SupportTarget;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ProtectedAtomExtractorTest {
    private final ProtectedAtomExtractor extractor = new ProtectedAtomExtractor();

    @Test void protectsNumbersDatesUnitsVersionsTechnologiesAndControlledRelations() {
        GroundedStatement source = statement(
                "2026-08-13 使用 PostgreSQL v16 将耗时降至 42ms，验证结果优于旧方案");
        assertThat(extractor.isSubsetOfSupportedAtoms(
                "2026-08-13 使用 PostgreSQL v16 将耗时降至 42ms，验证结果优于旧方案",
                List.of(source))).isTrue();
        assertThat(extractor.isSubsetOfSupportedAtoms(
                "2026-08-14 使用 Redis v17 将耗时降至 20ms，因此结果最高",
                List.of(source))).isFalse();
        assertThat(extractor.preservesSupportedAtoms(
                "使用 PostgreSQL 完成验证", List.of(source))).isFalse();
    }

    @Test void rejectsAStatementAboutAnotherKnownPublicSubject() {
        GroundedStatement source = statement("公开项目使用 PostgreSQL 完成验证");
        assertThat(extractor.containsOnlyKnownSubjects("另一个项目使用 PostgreSQL 完成验证",
                List.of(source), Set.of("公开项目", "另一个项目"))).isFalse();
    }

    public static GroundedStatement statement(String text) {
        return new GroundedStatement(StatementType.FACT, List.of(new SubjectReference("公开项目")),
                ControlledPredicate.VERIFIED_BY_TEST, text, null,
                AnswerClaimCategory.VERIFICATION, AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.COLLABORATIVE, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerMateriality.KEY, SupportTarget.SUBJECT,
                List.of(new PublicSourceReferenceValue("REF-1", "来源", "v1", "CASE",
                        "/projects/public", "/evidence/public")));
    }
}
