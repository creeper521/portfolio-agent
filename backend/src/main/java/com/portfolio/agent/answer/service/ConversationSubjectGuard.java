package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

public final class ConversationSubjectGuard {

    public boolean accepts(
            AgentTurnCommand.SurfaceContext context,
            RuntimeAnswerContent content
    ) {
        if (context == null) {
            return true;
        }
        AgentTurnCommand.SubjectHint hint = context.getSubjectHint();
        if (hint == null) {
            return true;
        }
        if (hint.getKind() == AgentTurnCommand.SubjectHintKind.PROJECT) {
            return content.getProjects().stream()
                    .anyMatch(project -> hint.getSlug().equals(project.getSlug()));
        }
        return content.getCases().stream()
                .anyMatch(caseItem -> hint.getSlug().equals(caseItem.getSlug()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
