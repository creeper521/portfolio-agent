package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.evaluation.domain.ConversationAnswerBlock;
import com.portfolio.agent.evaluation.domain.ConversationSourceScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalAnswerShapeTest {

    @Test
    void countsRepeatedContentClaimEvidenceAndSourceScopeWithoutStoringContent() {
        ConversationAnswerBlock first = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                "同一个正文",
                List.of("claim-a", "claim-b"),
                List.of("evidence-a"));
        ConversationAnswerBlock second = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                "同一个正文",
                List.of("claim-b"),
                List.of("evidence-a", "evidence-c"));

        EvalAnswerShape shape = EvalAnswerShape.from(List.of(first, second));

        assertThat(shape.getBlockCount()).isEqualTo(2);
        assertThat(shape.getCharacterCount()).isGreaterThan(0);
        assertThat(shape.getDistinctClaimCount()).isEqualTo(2);
        assertThat(shape.getDistinctEvidenceCount()).isEqualTo(2);
        assertThat(shape.getRepeatedClaimReferenceCount()).isEqualTo(1);
        assertThat(shape.getRepeatedEvidenceReferenceCount()).isEqualTo(1);
        assertThat(shape.getRepeatedContentCount()).isEqualTo(1);
        assertThat(shape.getRepeatedSourceScopeCount()).isEqualTo(1);
        assertThat(shape.getSemanticSectionCount()).isZero();
        assertThat(shape.getTypedSectionCount()).isZero();
        assertThat(shape.getUntypedBlockCount()).isEqualTo(2);
        assertThat(shape.isDirectAnswerPresent()).isTrue();
    }

    @Test
    void emptyShapeHasZeroCountsAndNoDirectAnswer() {
        EvalAnswerShape shape = EvalAnswerShape.empty();

        assertThat(shape.getBlockCount()).isZero();
        assertThat(shape.getCharacterCount()).isZero();
        assertThat(shape.getDistinctClaimCount()).isZero();
        assertThat(shape.getDistinctEvidenceCount()).isZero();
        assertThat(shape.getRepeatedClaimReferenceCount()).isZero();
        assertThat(shape.getRepeatedEvidenceReferenceCount()).isZero();
        assertThat(shape.getRepeatedContentCount()).isZero();
        assertThat(shape.getRepeatedSourceScopeCount()).isZero();
        assertThat(shape.getSemanticSectionCount()).isZero();
        assertThat(shape.isDirectAnswerPresent()).isFalse();
    }

    @Test
    void shapeNeverStoresAnswerContent() {
        ConversationAnswerBlock block = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                "敏感正文内容",
                List.of("claim-a"),
                List.of("evidence-a"));

        EvalAnswerShape shape = EvalAnswerShape.from(List.of(block));

        for (Field field : shape.getClass().getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(String.class);
        }
        assertThat(shape.toString()).doesNotContain("敏感正文内容");
    }

    @Test
    void countsOnlyNonEmptyTypedSemanticSections() {
        ConversationAnswerBlock empty = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                AnswerSectionType.BACKGROUND,
                "项目背景",
                "   ",
                List.of(),
                List.of());
        ConversationAnswerBlock real = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                AnswerSectionType.SOLUTION,
                "技术方案与实现",
                "有效回答",
                List.of("claim-a"),
                List.of());

        EvalAnswerShape shape = EvalAnswerShape.from(List.of(empty, real));

        assertThat(shape.getSemanticSectionCount()).isEqualTo(1);
        assertThat(shape.getTypedSectionCount()).isEqualTo(2);
        assertThat(shape.getUntypedBlockCount()).isZero();
        assertThat(shape.getBlockCount()).isEqualTo(2);
    }

    @Test
    void countsTypedSectionsAndSummaryWithoutStoringContent() {
        ConversationAnswerBlock background = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                AnswerSectionType.BACKGROUND,
                "项目背景",
                "背景正文",
                List.of("claim-bg"),
                List.of("evidence-bg"));
        ConversationAnswerBlock solution = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                AnswerSectionType.SOLUTION,
                "技术方案与实现",
                "方案正文",
                List.of("claim-sol"),
                List.of("evidence-sol"));

        EvalAnswerShape shape = EvalAnswerShape.from(
                List.of(background, solution), "直接摘要");

        assertThat(shape.getSemanticSectionCount()).isEqualTo(2);
        assertThat(shape.getTypedSectionCount()).isEqualTo(2);
        assertThat(shape.getUntypedBlockCount()).isZero();
        assertThat(shape.isSectionOrderValid()).isTrue();
        assertThat(shape.isSummaryPresent()).isTrue();
        assertThat(shape.isDirectAnswerPresent()).isTrue();
    }

    @Test
    void rejectsOutOfOrderTypedSectionsAndCountsUntypedBlocksSeparately() {
        ConversationAnswerBlock solution = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                AnswerSectionType.SOLUTION,
                "技术方案与实现",
                "方案正文",
                List.of("claim-sol"),
                List.of("evidence-sol"));
        ConversationAnswerBlock background = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                AnswerSectionType.BACKGROUND,
                "项目背景",
                "背景正文",
                List.of("claim-bg"),
                List.of("evidence-bg"));
        ConversationAnswerBlock untyped = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                "无类型兼容块",
                List.of("claim-legacy"),
                List.of());

        EvalAnswerShape shape = EvalAnswerShape.from(
                List.of(solution, background, untyped), null);

        assertThat(shape.getSemanticSectionCount()).isEqualTo(2);
        assertThat(shape.getTypedSectionCount()).isEqualTo(2);
        assertThat(shape.getUntypedBlockCount()).isEqualTo(1);
        assertThat(shape.isSectionOrderValid()).isFalse();
        assertThat(shape.isSummaryPresent()).isFalse();
        assertThat(shape.isDirectAnswerPresent()).isTrue();
    }

    @Test
    void summaryAloneMakesTheAnswerDirectlyPresent() {
        EvalAnswerShape shape = EvalAnswerShape.from(List.of(), "直接摘要");

        assertThat(shape.getBlockCount()).isZero();
        assertThat(shape.isSummaryPresent()).isTrue();
        assertThat(shape.isDirectAnswerPresent()).isTrue();
    }
}
