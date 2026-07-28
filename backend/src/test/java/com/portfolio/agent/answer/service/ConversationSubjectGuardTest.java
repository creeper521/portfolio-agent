package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSubjectGuardTest {

    private final ConversationSubjectGuard guard = new ConversationSubjectGuard();

    @Test
    void acceptsContextWithoutASubjectHint() {
        assertThat(guard.accepts(context(null, null), content())).isTrue();
    }

    @Test
    void acceptsPublishedProjectHint() {
        assertThat(guard.accepts(context("known-project", null), content())).isTrue();
    }

    @Test
    void acceptsPublishedCaseHint() {
        assertThat(guard.accepts(context(null, "known-case"), content())).isTrue();
    }

    @Test
    void rejectsUnknownProjectAndCaseHints() {
        assertThat(guard.accepts(context("missing-project", null), content())).isFalse();
        assertThat(guard.accepts(context(null, "missing-case"), content())).isFalse();
    }

    private RuntimeAnswerContent content() {
        return new RuntimeAnswerContent(
                "v1",
                "hash",
                List.of(knowledge(AnswerSubjectType.PROJECT, "known-project")),
                List.of(knowledge(AnswerSubjectType.CASE, "known-case")),
                null,
                List.of());
    }

    private ConversationAnswerContextRequest context(String projectSlug, String caseSlug) {
        return new ConversationAnswerContextRequest(
                projectSlug,
                caseSlug,
                AudienceRole.GUEST,
                AnswerRequestSource.AGENT_PAGE);
    }

    private AnswerKnowledge knowledge(AnswerSubjectType subjectType, String slug) {
        return new AnswerKnowledge(
                subjectType,
                slug,
                "Title",
                "Summary",
                "Background",
                List.of(),
                "Solution",
                List.of(),
                List.of(),
                "Outcome",
                "Handoff",
                "PUBLISHED",
                List.of(),
                List.of(),
                List.of());
    }
}
