package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolution;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolutionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredSubjectResolverTest {

    private final StructuredSubjectResolver resolver = new StructuredSubjectResolver();

    @Test
    void knownProjectReturnsStableSubjectIdWithoutCreatingTask() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "question")
                        .projectSlug("project-a")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.MATCHED);
        assertThat(resolution.getSubjectId()).isEqualTo("project-a-id");
    }

    @Test
    void knownCaseReturnsStableSubjectIdWithoutCreatingTask() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-1", "question")
                        .caseSlug("case-a")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.MATCHED);
        assertThat(resolution.getSubjectId()).isEqualTo("case-a-id");
    }

    @Test
    void noSlugReturnsNone() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-none", "question").build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.NONE);
        assertThat(resolution.getSubjectId()).isNull();
    }

    @Test
    void unknownSlugReturnsInvalid() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-missing", "question")
                        .projectSlug("missing")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.INVALID);
        assertThat(resolution.getSubjectId()).isNull();
    }

    @Test
    void duplicateCaseSlugsReturnInvalid() {
        StructuredSubjectResolution resolution = resolver.resolve(
                PortfolioTurn.builder("turn-duplicate", "question")
                        .caseSlug("duplicate-slug")
                        .build(),
                content());

        assertThat(resolution.getType()).isEqualTo(StructuredSubjectResolutionType.INVALID);
    }

    private RuntimeAnswerContent content() {
        return new RuntimeAnswerContent(
                "public-1",
                "sha256:runtime",
                List.of(projectKnowledge("project-a")),
                List.of(
                        caseKnowledge("case-a"),
                        caseKnowledge("duplicate-slug"),
                        caseKnowledge("duplicate-slug")),
                null,
                List.of());
    }

    private AnswerKnowledge projectKnowledge(String slug) {
        return new AnswerKnowledge(
                AnswerSubjectType.PROJECT,
                slug + "-id",
                slug,
                "Title",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "ACTIVE",
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private AnswerKnowledge caseKnowledge(String slug) {
        return new AnswerKnowledge(
                AnswerSubjectType.CASE,
                slug + "-id",
                slug,
                "Title",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "ACTIVE",
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
