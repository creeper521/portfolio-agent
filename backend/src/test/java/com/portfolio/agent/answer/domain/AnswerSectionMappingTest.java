package com.portfolio.agent.answer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerSectionMappingTest {

    @Test
    void exposesAuthoritativeOrderTitlesAndControlledGapMessages() {
        assertThat(AnswerSectionMapping.authoritativeOrder()).containsExactly(
                AnswerSectionType.BACKGROUND,
                AnswerSectionType.RESPONSIBILITY,
                AnswerSectionType.SOLUTION,
                AnswerSectionType.VERIFICATION,
                AnswerSectionType.STATUS,
                AnswerSectionType.BOUNDARY);
        assertThat(AnswerSectionMapping.titleFor(AnswerSectionType.STATUS))
                .isEqualTo("结果与当前状态");
        assertThat(AnswerSectionMapping.gapMessageFor(AnswerSectionType.STATUS))
                .isEqualTo("当前公开材料未覆盖最终状态。");
        assertThat(AnswerSectionMapping.gapMessageFor(AnswerSectionType.BOUNDARY))
                .isEqualTo("当前公开材料未覆盖限制与边界。");
        assertThatThrownBy(() -> AnswerSectionMapping.titleFor(AnswerSectionType.REJECTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsEveryClaimCategoryToAnAuthoritativeSection() {
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.BACKGROUND))
                .isEqualTo(AnswerSectionType.BACKGROUND);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.RESPONSIBILITY))
                .isEqualTo(AnswerSectionType.RESPONSIBILITY);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.TECHNICAL_DECISION))
                .isEqualTo(AnswerSectionType.SOLUTION);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.IMPLEMENTATION))
                .isEqualTo(AnswerSectionType.SOLUTION);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.VERIFICATION))
                .isEqualTo(AnswerSectionType.VERIFICATION);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.OUTCOME))
                .isEqualTo(AnswerSectionType.STATUS);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.LIMITATION))
                .isEqualTo(AnswerSectionType.BOUNDARY);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.LEARNING))
                .isEqualTo(AnswerSectionType.BOUNDARY);
        assertThat(AnswerSectionMapping.sectionTypeFor(AnswerClaimCategory.REFLECTION))
                .isEqualTo(AnswerSectionType.BOUNDARY);
    }

    @Test
    void exposesRetrievalCandidateCategoriesPerSection() {
        assertThat(AnswerSectionMapping.preferredCategoriesFor(null)).isEmpty();
        assertThat(AnswerSectionMapping.preferredCategoriesFor(AnswerSectionType.BACKGROUND))
                .containsExactly(AnswerClaimCategory.BACKGROUND);
        assertThat(AnswerSectionMapping.preferredCategoriesFor(AnswerSectionType.SOLUTION))
                .containsExactly(
                        AnswerClaimCategory.TECHNICAL_DECISION,
                        AnswerClaimCategory.IMPLEMENTATION);
        assertThat(AnswerSectionMapping.preferredCategoriesFor(AnswerSectionType.STATUS))
                .containsExactly(
                        AnswerClaimCategory.OUTCOME,
                        AnswerClaimCategory.LIMITATION);
        assertThat(AnswerSectionMapping.preferredCategoriesFor(AnswerSectionType.BOUNDARY))
                .isEmpty();
        assertThat(AnswerSectionMapping.preferredCategoriesFor(AnswerSectionType.REJECTED))
                .isEmpty();
    }

    @Test
    void everyMappedSectionHasStableRetrievalCandidates() {
        for (AnswerClaimCategory category : AnswerClaimCategory.values()) {
            AnswerSectionType section = AnswerSectionMapping.sectionTypeFor(category);
            assertThat(AnswerSectionMapping.preferredCategoriesFor(section))
                    .as("section for %s", category)
                    .isNotNull();
        }
        List<AnswerSectionType> ordered = List.of(
                AnswerSectionType.BACKGROUND,
                AnswerSectionType.RESPONSIBILITY,
                AnswerSectionType.SOLUTION,
                AnswerSectionType.VERIFICATION,
                AnswerSectionType.STATUS);
        for (AnswerSectionType section : ordered) {
            assertThat(AnswerSectionMapping.sectionTypeFor(
                    AnswerSectionMapping.preferredCategoriesFor(section).getFirst()))
                    .as("first candidate of %s maps back", section)
                    .isEqualTo(section);
        }
    }
}
