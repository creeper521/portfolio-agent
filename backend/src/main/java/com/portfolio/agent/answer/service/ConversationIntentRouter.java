package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConversationIntentRouter {

    private static final List<String> UNSAFE_MARKERS = List.of(
            "密码", "口令", "token", "api key", "密钥", "私有资料",
            "未公开资料", "内部文档", "系统提示词", "忽略安全规则");
    private static final List<String> TIME_MARKERS = List.of(
            "最新", "今天", "现在", "实时", "当前版本", "刚刚");

    private final ConversationalModelPort modelPort;
    private final double minimumConfidence;

    public ConversationIntentRouter(
            ConversationalModelPort modelPort,
            double minimumConfidence
    ) {
        this.modelPort = modelPort;
        this.minimumConfidence = minimumConfidence;
    }

    public ConversationRoute route(
            RuntimeAnswerContent content,
            ConversationWindow window,
            ConversationAnswerRequest request
    ) {
        String question = request.getQuestion().strip();
        String normalized = question.toLowerCase(Locale.ROOT);
        if (isUnsafe(normalized)) {
            return deterministic(
                    ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                    ConversationAnswerScope.CONVERSATION);
        }
        if (isTimeSensitive(normalized)) {
            return deterministic(
                    ConversationIntent.TIME_SENSITIVE,
                    ConversationAnswerScope.GENERAL);
        }
        if (isConversation(normalized)) {
            return deterministic(
                    ConversationIntent.CONVERSATION,
                    ConversationAnswerScope.CONVERSATION);
        }

        ConversationRoute hinted = routeHint(content, request.getContext());
        if (hinted != null) {
            return hinted;
        }

        List<ConversationSubjectOption> subjects = publicSubjects(content);
        ConversationModelResult<ConversationRoute> classified =
                modelPort.classify(question, window, subjects);
        if (classified == null || !classified.isSuccessful()) {
            return clarificationRoute();
        }
        ConversationRoute candidate = classified.getValue();
        if (candidate.getConfidence() < minimumConfidence
                || !subjectIsValid(candidate, content)) {
            return clarificationRoute();
        }
        return candidate;
    }

    private ConversationRoute routeHint(
            RuntimeAnswerContent content,
            ConversationAnswerContextRequest context
    ) {
        if (context == null) {
            return null;
        }
        if (hasText(context.getProjectSlug())) {
            return content.getProjects().stream()
                    .filter(project -> context.getProjectSlug().equals(project.getSlug()))
                    .findFirst()
                    .map(project -> portfolioRoute(project.getSlug(), null))
                    .orElseGet(this::clarificationRoute);
        }
        if (hasText(context.getCaseSlug())) {
            return content.getCases().stream()
                    .filter(caseItem -> context.getCaseSlug().equals(caseItem.getSlug()))
                    .findFirst()
                    .map(caseItem -> portfolioRoute(null, caseItem.getSlug()))
                    .orElseGet(this::clarificationRoute);
        }
        return null;
    }

    private ConversationRoute portfolioRoute(String projectSlug, String caseSlug) {
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                1.0,
                projectSlug,
                caseSlug,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private boolean subjectIsValid(
            ConversationRoute route,
            RuntimeAnswerContent content
    ) {
        if (hasText(route.getProjectSlug()) && hasText(route.getCaseSlug())) {
            return false;
        }
        if (hasText(route.getProjectSlug())) {
            return content.getProjects().stream()
                    .anyMatch(project -> route.getProjectSlug().equals(project.getSlug()));
        }
        if (hasText(route.getCaseSlug())) {
            return content.getCases().stream()
                    .anyMatch(caseItem -> route.getCaseSlug().equals(caseItem.getSlug()));
        }
        return route.getIntent() != ConversationIntent.PORTFOLIO_GROUNDED
                && route.getIntent() != ConversationIntent.HYBRID;
    }

    private List<ConversationSubjectOption> publicSubjects(RuntimeAnswerContent content) {
        List<ConversationSubjectOption> subjects = new ArrayList<>();
        for (AnswerKnowledge project : content.getProjects()) {
            subjects.add(new ConversationSubjectOption(
                    project.getSubjectType(),
                    project.getSlug(),
                    project.getTitle(),
                    project.getSummary()));
        }
        for (AnswerKnowledge caseItem : content.getCases()) {
            subjects.add(new ConversationSubjectOption(
                    caseItem.getSubjectType(),
                    caseItem.getSlug(),
                    caseItem.getTitle(),
                    caseItem.getSummary()));
        }
        return List.copyOf(subjects);
    }

    private ConversationRoute deterministic(
            ConversationIntent intent,
            ConversationAnswerScope scope
    ) {
        return new ConversationRoute(
                intent,
                scope,
                1.0,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private ConversationRoute clarificationRoute() {
        return new ConversationRoute(
                ConversationIntent.GENERAL_KNOWLEDGE,
                ConversationAnswerScope.GENERAL,
                0.0,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                true);
    }

    private boolean isUnsafe(String question) {
        return UNSAFE_MARKERS.stream().anyMatch(question::contains);
    }

    private boolean isTimeSensitive(String question) {
        return TIME_MARKERS.stream().anyMatch(question::contains);
    }

    private boolean isConversation(String question) {
        String compact = question.replaceAll("[\\s！!。,.，?？]", "");
        return compact.matches(
                "(你好|您好|嗨|哈喽|hello|hi|谢谢|感谢|再见|拜拜|你是谁|怎么使用)");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
