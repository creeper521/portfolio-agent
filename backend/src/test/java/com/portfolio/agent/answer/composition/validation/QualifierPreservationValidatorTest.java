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
import org.junit.jupiter.api.Test;

class QualifierPreservationValidatorTest {
    private final QualifierPreservationValidator validator = new QualifierPreservationValidator();

    @Test void acceptsEquivalentQualifierClasses() {
        GroundedStatement source = statement("拟通过试验观察阶段性效果，协作支持，结果可能有效");
        assertThat(validator.isPreserved(
                "计划以原型观察局部效果，参与支持，结果倾向有效", List.of(source))).isTrue();
    }

    @Test void rejectsDeletedOrStrengthenedQualifierClasses() {
        GroundedStatement source = statement("计划交付原型，部分验证，协作参与，结果可能有效");
        assertThat(validator.isPreserved("交付系统并验证结果", List.of(source))).isFalse();
        assertThat(validator.isPreserved(
                "已上线生产系统，全部完成验证，由我独立完成，结果已经证明有效", List.of(source)))
                .isFalse();
    }

    @Test void preservesUncoveredAndUncertainInsteadOfTurningAbsenceIntoInability() {
        GroundedStatement source = statement("尚未覆盖该方向，能力不确定");
        assertThat(validator.isPreserved("该方向未覆盖，目前仍不确定", List.of(source))).isTrue();
        assertThat(validator.isPreserved("不具备该方向能力", List.of(source))).isFalse();
    }

    @Test void rejectsInventedPlanPartialOrUncertaintyOnAQualifiedFreeFact() {
        GroundedStatement source = statement("系统通过公开测试");
        assertThat(validator.isPreserved("系统计划通过公开测试", List.of(source))).isFalse();
        assertThat(validator.isPreserved("系统可能通过部分公开测试", List.of(source))).isFalse();
    }

    private static GroundedStatement statement(String text) {
        return new GroundedStatement(StatementType.FACT, List.of(new SubjectReference("公开项目")),
                ControlledPredicate.DESCRIBES, text, null, AnswerClaimCategory.OUTCOME,
                AnswerAchievementStatus.DELIVERED, AnswerContributionType.INDEPENDENT,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerMateriality.KEY, SupportTarget.SUBJECT,
                List.of(new PublicSourceReferenceValue("REF-1", "来源", "v1", "CASE",
                        "/projects/public", "/evidence/public")));
    }
}
