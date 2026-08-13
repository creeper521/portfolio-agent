package com.portfolio.agent.answer.composition.validation;

import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class QualifierPreservationValidator {
    private enum QualifierClass { PLANNED, PROTOTYPE_OBSERVED, PARTIAL, UNCERTAIN,
        COLLABORATIVE, POSSIBLE }

    public boolean isPreserved(String draft, List<GroundedStatement> supported) {
        Set<QualifierClass> required = EnumSet.noneOf(QualifierClass.class);
        for (GroundedStatement statement : supported) {
            required.addAll(classes(statement.getPublicStatement() + " "
                    + (statement.getPublicDetail() == null ? "" : statement.getPublicDetail())));
            if (statement.getAchievementStatus() == AnswerAchievementStatus.PLANNED) required.add(QualifierClass.PLANNED);
            if (statement.getAchievementStatus() == AnswerAchievementStatus.PROTOTYPE
                    || statement.getAchievementStatus() == AnswerAchievementStatus.INVESTIGATED) {
                required.add(QualifierClass.PROTOTYPE_OBSERVED);
            }
            if (statement.getContributionType() == AnswerContributionType.COLLABORATIVE
                    || statement.getContributionType() == AnswerContributionType.OBSERVED_LEARNING) {
                required.add(QualifierClass.COLLABORATIVE);
            }
            if (statement.getVerificationBasis() == AnswerVerificationBasis.INFERRED
                    || statement.getVerificationBasis() == AnswerVerificationBasis.UNSUPPORTED) {
                required.add(QualifierClass.POSSIBLE);
            }
        }
        Set<QualifierClass> actual = classes(draft);
        if (!actual.containsAll(required)) return false;
        if (!required.containsAll(actual)) return false;
        String normalized = draft.toLowerCase(Locale.ROOT);
        if (required.contains(QualifierClass.PLANNED)
                && containsAny(normalized, "已交付", "已上线", "delivered")) return false;
        if (required.contains(QualifierClass.PROTOTYPE_OBSERVED)
                && containsAny(normalized, "生产验证", "生产环境", "production", "已经证明")) return false;
        if (required.contains(QualifierClass.COLLABORATIVE)
                && containsAny(normalized, "独立完成", "独自完成", "solely")) return false;
        if (required.contains(QualifierClass.PARTIAL)
                && containsAny(normalized, "全部完成", "完整覆盖", "complete")) return false;
        if (required.contains(QualifierClass.UNCERTAIN)
                && containsAny(normalized, "不具备能力", "确定没有", "definitely")) return false;
        return true;
    }

    private static Set<QualifierClass> classes(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        Set<QualifierClass> result = EnumSet.noneOf(QualifierClass.class);
        if (containsAny(normalized, "计划", "拟", "将要", "planned")) result.add(QualifierClass.PLANNED);
        if (containsAny(normalized, "原型", "试验", "实验", "观察", "prototype", "observed")) result.add(QualifierClass.PROTOTYPE_OBSERVED);
        if (containsAny(normalized, "部分", "局部", "阶段性", "partial", "staged")) result.add(QualifierClass.PARTIAL);
        if (containsAny(normalized, "尚未", "未覆盖", "不确定", "uncertain", "uncovered")) result.add(QualifierClass.UNCERTAIN);
        if (containsAny(normalized, "参与", "协作", "支持", "collaborative", "supporting")) result.add(QualifierClass.COLLABORATIVE);
        if (containsAny(normalized, "可能", "推测", "倾向", "possibly", "inferred")) result.add(QualifierClass.POSSIBLE);
        return result;
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
