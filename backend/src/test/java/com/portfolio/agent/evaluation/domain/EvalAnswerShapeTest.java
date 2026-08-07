package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
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
        assertThat(shape.getSemanticSectionCount()).isEqualTo(2);
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
    void countsOnlyNonEmptySemanticSections() {
        ConversationAnswerBlock empty = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                "   ",
                List.of(),
                List.of());
        ConversationAnswerBlock real = new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                "有效回答",
                List.of("claim-a"),
                List.of());

        EvalAnswerShape shape = EvalAnswerShape.from(List.of(empty, real));

        assertThat(shape.getSemanticSectionCount()).isEqualTo(1);
        assertThat(shape.getBlockCount()).isEqualTo(2);
    }
}
