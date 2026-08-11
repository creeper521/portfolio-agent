package com.portfolio.agent.answer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.exception.PortfolioAnswerCompositionException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioAnswerPlanTest {

    @Test
    void defensivelyCopiesCollectionsAndPreservesValueSemantics() {
        List<String> claimIds = new ArrayList<>(List.of("claim-1"));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
        PortfolioAnswerSection section = new PortfolioAnswerSection(
                AnswerSectionType.SOLUTION, "技术方案与实现", "采用受控路由。",
                claimIds, evidenceIds);
        List<PortfolioAnswerSection> sections = new ArrayList<>(List.of(section));
        PortfolioAnswerPlan plan = new PortfolioAnswerPlan("SQL 审计工具", null, sections);

        claimIds.add("claim-mutated");
        evidenceIds.add("evidence-mutated");
        sections.clear();

        assertThat(section.getClaimIds()).containsExactly("claim-1");
        assertThat(section.getEvidenceIds()).containsExactly("evidence-1");
        assertThat(plan.getSections()).containsExactly(section);
        assertThatThrownBy(() -> section.getClaimIds().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.getSections().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        PortfolioAnswerSection equalSection = new PortfolioAnswerSection(
                AnswerSectionType.SOLUTION, "技术方案与实现", "采用受控路由。",
                List.of("claim-1"), List.of("evidence-1"));
        PortfolioAnswerPlan equalPlan = new PortfolioAnswerPlan(
                "SQL 审计工具", null, List.of(equalSection));
        assertThat(section).isEqualTo(equalSection).hasSameHashCodeAs(equalSection);
        assertThat(plan).isEqualTo(equalPlan).hasSameHashCodeAs(equalPlan);
    }

    @Test
    void deduplicatesClaimAndEvidenceIdsWhileKeepingFirstOccurrenceOrder() {
        PortfolioAnswerPlan plan = new PortfolioAnswerPlan(
                "SQL 审计工具",
                "公开项目摘要",
                List.of(new PortfolioAnswerSection(
                        AnswerSectionType.SOLUTION,
                        "技术方案与实现",
                        "使用受控路由替代硬编码。",
                        List.of("claim-1", "claim-1"),
                        List.of("evidence-1", "evidence-1"))));

        assertThat(plan.getTitle()).isEqualTo("SQL 审计工具");
        assertThat(plan.getSummary()).isEqualTo("公开项目摘要");
        assertThat(plan.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getSectionType()).isEqualTo(AnswerSectionType.SOLUTION);
            assertThat(section.getTitle()).isEqualTo("技术方案与实现");
            assertThat(section.getContent()).isEqualTo("使用受控路由替代硬编码。");
            assertThat(section.getClaimIds()).containsExactly("claim-1");
            assertThat(section.getEvidenceIds()).containsExactly("evidence-1");
        });
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new PortfolioAnswerPlan(
                "  ", "summary",
                List.of(new PortfolioAnswerSection(
                        AnswerSectionType.SOLUTION, "方案", "正文",
                        List.of(), List.of("evidence-1")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void rejectsEmptySectionList() {
        assertThatThrownBy(() -> new PortfolioAnswerPlan(
                "title", null, List.of()))
                .isInstanceOf(PortfolioAnswerCompositionException.class)
                .hasMessageContaining("sections");
    }

    @Test
    void rejectsFactSectionWithoutEvidence() {
        assertThatThrownBy(() -> new PortfolioAnswerPlan(
                "title", null,
                List.of(new PortfolioAnswerSection(
                        AnswerSectionType.SOLUTION, "方案", "正文",
                        List.of("claim-1"), List.of()))))
                .isInstanceOf(PortfolioAnswerCompositionException.class)
                .hasMessageContaining("at least one evidence");
    }

    @Test
    void rejectsDuplicateSectionType() {
        assertThatThrownBy(() -> new PortfolioAnswerPlan(
                "title", null,
                List.of(
                        new PortfolioAnswerSection(
                                AnswerSectionType.BACKGROUND, "背景", "正文一",
                                List.of(), List.of("evidence-1")),
                        new PortfolioAnswerSection(
                                AnswerSectionType.BACKGROUND, "背景", "正文二",
                                List.of(), List.of("evidence-2")))))
                .isInstanceOf(PortfolioAnswerCompositionException.class)
                .hasMessageContaining("duplicate section type");
    }

    @Test
    void rejectsBlankSectionFields() {
        assertThatThrownBy(() -> new PortfolioAnswerSection(
                AnswerSectionType.SOLUTION, "  ", "正文",
                List.of("claim-1"), List.of("evidence-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> new PortfolioAnswerSection(
                AnswerSectionType.SOLUTION, "方案", "  ",
                List.of("claim-1"), List.of("evidence-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }
}
