package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.engine.QuestionNormalizer;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPresetResolverTest {

    private final PortfolioPresetResolver resolver =
            new PortfolioPresetResolver(new QuestionNormalizer());

    @Test
    void explicitPresetIdSelectsTheStablePresetAndSubject() {
        RuntimeAnswerContent content = content();
        PortfolioTurn turn = PortfolioTurn.builder("turn-1", "How is async state restored?")
                .questionPresetId("preset-async")
                .projectSlug("project-a")
                .build();

        PortfolioPresetResolution resolution = resolver.resolve(turn, content);

        assertThat(resolution.getType()).isEqualTo(PortfolioPresetResolutionType.MATCHED);
        assertThat(resolution.getIntentSource()).isEqualTo(AnswerIntentSource.PRESET);
        assertThat(resolution.getTask().getSubjectId()).isEqualTo("project-a");
        assertThat(resolution.getQuestionPresetId()).isEqualTo("preset-async");
    }

    @Test
    void canonicalAndAliasTextConvergeToTheSameStableTask() {
        RuntimeAnswerContent content = content();

        PortfolioPresetResolution canonical = resolver.resolve(
                PortfolioTurn.builder("turn-1", "How is async state restored?").build(),
                content);
        PortfolioPresetResolution alias = resolver.resolve(
                PortfolioTurn.builder("turn-2", "How do tasks recover after refresh?").build(),
                content);

        assertThat(canonical.getQuestionPresetId()).isEqualTo("preset-async");
        assertThat(alias.getQuestionPresetId()).isEqualTo("preset-async");
        assertThat(canonical.getTask().getSubjectId())
                .isEqualTo(alias.getTask().getSubjectId());
        assertThat(canonical.getTask().getMode()).isEqualTo(alias.getTask().getMode());
    }

    @Test
    void pageSubjectAloneDoesNotCreateAPresetMatch() {
        PortfolioTurn turn = PortfolioTurn.builder("turn-1", "What is dependency injection?")
                .projectSlug("project-a")
                .build();

        PortfolioPresetResolution resolution = resolver.resolve(turn, content());

        assertThat(resolution.getType()).isEqualTo(PortfolioPresetResolutionType.NO_MATCH);
    }

    @Test
    void explicitPresetMustBelongToTheHintedSubject() {
        PortfolioTurn turn = PortfolioTurn.builder("turn-1", "How is async state restored?")
                .questionPresetId("preset-async")
                .projectSlug("project-b")
                .build();

        PortfolioPresetResolution resolution = resolver.resolve(turn, content());

        assertThat(resolution.getType()).isEqualTo(PortfolioPresetResolutionType.INVALID);
    }

    private RuntimeAnswerContent content() {
        AnswerQuestion preset = new AnswerQuestion(
                "preset-async",
                "How is async state restored?",
                List.of("How do tasks recover after refresh?"),
                "Async recovery");
        return new RuntimeAnswerContent(
                "public-1",
                "sha256:runtime",
                List.of(knowledge("project-a", List.of(preset)),
                        knowledge("project-b", List.of())));
    }

    private AnswerKnowledge knowledge(String slug, List<AnswerQuestion> questions) {
        return new AnswerKnowledge(
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
                questions,
                List.of(),
                List.of());
    }
}
