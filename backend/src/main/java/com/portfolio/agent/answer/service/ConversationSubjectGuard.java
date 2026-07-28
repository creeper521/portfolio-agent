package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;

public final class ConversationSubjectGuard {

    public boolean accepts(
            ConversationAnswerContextRequest context,
            RuntimeAnswerContent content
    ) {
        if (context == null) {
            return true;
        }
        boolean hasProjectSlug = hasText(context.getProjectSlug());
        boolean hasCaseSlug = hasText(context.getCaseSlug());
        if (!hasProjectSlug && !hasCaseSlug) {
            return true;
        }
        if (hasProjectSlug && hasCaseSlug) {
            return false;
        }
        if (hasProjectSlug) {
            return content.getProjects().stream()
                    .anyMatch(project -> context.getProjectSlug().equals(project.getSlug()));
        }
        return content.getCases().stream()
                .anyMatch(caseItem -> context.getCaseSlug().equals(caseItem.getSlug()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
