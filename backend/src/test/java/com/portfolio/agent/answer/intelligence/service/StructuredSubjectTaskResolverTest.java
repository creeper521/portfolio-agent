package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolution;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolutionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredSubjectTaskResolverTest {

    private final StructuredSubjectTaskResolver resolver =
            new StructuredSubjectTaskResolver();

    @Test
    void returnsNoneWithoutAnyStructuredSlug() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "任意问题").build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.NONE);
    }

    @Test
    void matchesKnownProjectSlug() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "测试角色重置工具的背景和目标是什么？")
                        .projectSlug("role-reset-tool")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.MATCHED);
        assertThat(resolution.getTask().getMode()).isEqualTo(PortfolioTaskMode.FACT_LOOKUP);
        assertThat(resolution.getTask().getSubjectId()).isEqualTo("role-reset-tool");
    }

    @Test
    void matchesKnownCaseSlug() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "这个案例如何验证？")
                        .caseSlug("multilingual-image-preservation")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.MATCHED);
        assertThat(resolution.getTask().getSubjectId())
                .isEqualTo("multilingual-image-preservation");
    }

    @Test
    void rejectsUnknownSlug() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "这个项目如何实现？")
                        .projectSlug("missing-project")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.INVALID);
    }

    @Test
    void rejectsDuplicateSlugs() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "这个项目如何实现？")
                        .projectSlug("duplicate-slug")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.INVALID);
    }

    private RuntimeAnswerContent content() {
        return new RuntimeAnswerContent(
                "public-1",
                "sha256:runtime",
                List.of(projectKnowledge("role-reset-tool")),
                List.of(
                        caseKnowledge("multilingual-image-preservation"),
                        caseKnowledge("duplicate-slug"),
                        caseKnowledge("duplicate-slug")),
                null,
                List.of());
    }

    private AnswerKnowledge projectKnowledge(String slug) {
        return new AnswerKnowledge(
                AnswerSubjectType.PROJECT,
                slug,
                slug,
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "ACTIVE",
                List.of(),
                List.of(),
                List.of());
    }

    private AnswerKnowledge caseKnowledge(String slug) {
        return new AnswerKnowledge(
                AnswerSubjectType.CASE,
                slug,
                slug,
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "ACTIVE",
                List.of(),
                List.of(),
                List.of());
    }
}
