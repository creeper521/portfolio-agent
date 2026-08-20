package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import java.util.List;

/**
 * 单一权威的 Category ↔ Section 映射。
 *
 * <p>Composer 的章节归属（sectionTypeFor）与 Retrieval Planner 的检索候选
 * （preferredCategoriesFor）共用同一处定义，避免新增分类时两处漂移。
 * 检索候选允许跨章节召回（例如 STATUS 追问可命中 LIMITATION 事实），
 * 因此两个方向是显式声明而非机械互逆。</p>
 */
public final class AnswerSectionMapping {

    private static final List<AnswerSectionType> AUTHORITATIVE_ORDER = List.of(
            AnswerSectionType.BACKGROUND,
            AnswerSectionType.RESPONSIBILITY,
            AnswerSectionType.SOLUTION,
            AnswerSectionType.VERIFICATION,
            AnswerSectionType.STATUS,
            AnswerSectionType.BOUNDARY);

    private AnswerSectionMapping() {
    }

    public static List<AnswerSectionType> authoritativeOrder() {
        return AUTHORITATIVE_ORDER;
    }

    public static String titleFor(AnswerSectionType sectionType) {
        return switch (sectionType) {
            case BACKGROUND -> "项目背景";
            case RESPONSIBILITY -> "我的职责";
            case SOLUTION -> "技术方案与实现";
            case VERIFICATION -> "验证过程";
            case STATUS -> "结果与当前状态";
            case BOUNDARY -> "边界与复盘";
            case GENERAL_PRINCIPLE, PORTFOLIO_EXAMPLE, RELATION, REJECTED ->
                    throw new IllegalArgumentException(
                            "section is not part of the legacy portfolio answer mapping");
        };
    }

    public static String gapMessageFor(AnswerSectionType sectionType) {
        return switch (sectionType) {
            case BACKGROUND -> "当前公开材料未覆盖项目背景。";
            case RESPONSIBILITY -> "当前公开材料未覆盖我的职责。";
            case SOLUTION -> "当前公开材料未覆盖技术方案与实现。";
            case VERIFICATION -> "当前公开材料未覆盖验证过程。";
            case STATUS -> "当前公开材料未覆盖最终状态。";
            case BOUNDARY -> "当前公开材料未覆盖限制与边界。";
            case GENERAL_PRINCIPLE, PORTFOLIO_EXAMPLE, RELATION, REJECTED ->
                    throw new IllegalArgumentException(
                            "section is not part of the legacy portfolio answer mapping");
        };
    }

    public static AnswerSectionType sectionTypeFor(AnswerClaimCategory category) {
        return switch (category) {
            case BACKGROUND -> AnswerSectionType.BACKGROUND;
            case RESPONSIBILITY -> AnswerSectionType.RESPONSIBILITY;
            case TECHNICAL_DECISION, IMPLEMENTATION -> AnswerSectionType.SOLUTION;
            case VERIFICATION -> AnswerSectionType.VERIFICATION;
            case OUTCOME -> AnswerSectionType.STATUS;
            case LIMITATION, LEARNING, REFLECTION -> AnswerSectionType.BOUNDARY;
        };
    }

    public static List<AnswerClaimCategory> preferredCategoriesFor(
            AnswerSectionType sectionType) {
        if (sectionType == null) {
            return List.of();
        }
        return switch (sectionType) {
            case BACKGROUND -> List.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> List.of(AnswerClaimCategory.RESPONSIBILITY);
            case SOLUTION -> List.of(
                    AnswerClaimCategory.TECHNICAL_DECISION,
                    AnswerClaimCategory.IMPLEMENTATION);
            case VERIFICATION -> List.of(AnswerClaimCategory.VERIFICATION);
            case STATUS -> List.of(
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.LIMITATION);
            case BOUNDARY, GENERAL_PRINCIPLE, PORTFOLIO_EXAMPLE, RELATION, REJECTED -> List.of();
        };
    }
}
