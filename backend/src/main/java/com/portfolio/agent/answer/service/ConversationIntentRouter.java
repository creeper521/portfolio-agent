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
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

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
    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public ConversationIntentRouter(
            ConversationalModelPort modelPort,
            double minimumConfidence,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.modelPort = modelPort;
        this.minimumConfidence = minimumConfidence;
        this.diagnosticEventPublisher = diagnosticEventPublisher;
    }

    public ConversationRoute route(
            RuntimeAnswerContent content,
            ConversationWindow window,
            ConversationAnswerRequest request
    ) {
        long startedAt = System.nanoTime();
        String question = request.getQuestion().strip();
        String normalized = question.toLowerCase(Locale.ROOT);
        ConversationRoute boundary = routeBoundary(content, window, request, false);
        if (boundary != null) {
            return boundary;
        }
        if (isConversation(normalized)) {
            return decided(deterministic(
                    ConversationIntent.CONVERSATION,
                    ConversationAnswerScope.CONVERSATION), "DETERMINISTIC", startedAt);
        }

        ConversationRoute hinted = routeHint(content, request.getContext());
        if (hinted != null) {
            return decided(hinted, "DETERMINISTIC", startedAt);
        }

        List<ConversationSubjectOption> subjects = publicSubjects(content);
        ConversationModelResult<ConversationRoute> classified =
                modelPort.classify(question, window, subjects);
        if (classified == null || !classified.isSuccessful()) {
            return decided(clarificationRoute(), "DETERMINISTIC", startedAt);
        }
        ConversationRoute candidate = classified.getValue();
        if (candidate.getConfidence() < minimumConfidence
                || !subjectIsValid(candidate, content)) {
            return decided(clarificationRoute(), "DETERMINISTIC", startedAt);
        }
        return decided(candidate, "MODEL", startedAt);
    }

    public ConversationRoute routeBoundary(String question) {
        long startedAt = System.nanoTime();
        String normalized = question.strip().toLowerCase(Locale.ROOT);
        if (isUnsafe(normalized)) {
            return decided(deterministic(
                    ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                    ConversationAnswerScope.CONVERSATION), "DETERMINISTIC", startedAt);
        }
        if (isTimeSensitive(normalized)) {
            return decided(deterministic(
                    ConversationIntent.TIME_SENSITIVE,
                    ConversationAnswerScope.GENERAL), "DETERMINISTIC", startedAt);
        }
        return null;
    }

    public ConversationRoute routeBoundary(
            RuntimeAnswerContent content,
            ConversationWindow window,
            ConversationAnswerRequest request,
            boolean allowModelBoundary
    ) {
        ConversationRoute deterministicBoundary = routeBoundary(request.getQuestion());
        if (deterministicBoundary != null || !allowModelBoundary) {
            return deterministicBoundary;
        }

        long startedAt = System.nanoTime();
        ConversationModelResult<ConversationRoute> classified;
        try {
            classified = modelPort.classify(
                    request.getQuestion().strip(), window, publicSubjects(content));
        } catch (RuntimeException exception) {
            return null;
        }
        if (classified == null || !classified.isSuccessful()) {
            return null;
        }
        ConversationRoute candidate = classified.getValue();
        if (candidate == null || candidate.getConfidence() < minimumConfidence) {
            return null;
        }
        if (candidate.getIntent() == ConversationIntent.UNSUPPORTED_OR_UNSAFE) {
            return decided(deterministic(
                    ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                    ConversationAnswerScope.CONVERSATION), "MODEL_BOUNDARY", startedAt);
        }
        if (candidate.getIntent() == ConversationIntent.TIME_SENSITIVE) {
            return decided(deterministic(
                    ConversationIntent.TIME_SENSITIVE,
                    ConversationAnswerScope.GENERAL), "MODEL_BOUNDARY", startedAt);
        }
        return null;
    }

    private ConversationRoute decided(
            ConversationRoute route,
            String routeSource,
            long startedAt
    ) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "agent.route.decided", DiagnosticLevel.DEBUG)
                    .field("conversation.intent", route.getIntent())
                    .field("answer.scope", route.getAnswerScope())
                    .field("route.source", routeSource)
                    .field("duration.bucket", DurationBuckets.fromElapsedMillis(elapsedMillis))
                    .build());
        } catch (RuntimeException ignored) {
            // Observability is passive and must never change route selection.
        }
        return route;
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
