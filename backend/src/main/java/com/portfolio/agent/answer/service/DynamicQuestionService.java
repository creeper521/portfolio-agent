package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DynamicQuestionService {

    private final ConversationalModelPort modelPort;
    private final PortfolioGroundingAssembler groundingAssembler;
    private final int maxQuestions;

    public DynamicQuestionService(
            ConversationalModelPort modelPort,
            PortfolioGroundingAssembler groundingAssembler,
            int maxQuestions
    ) {
        this.modelPort = modelPort;
        this.groundingAssembler = groundingAssembler;
        this.maxQuestions = maxQuestions;
    }

    public List<ConversationSuggestedQuestion> generate(
            RuntimeAnswerContent content,
            ConversationRoute route,
            ConversationWindow window,
            List<ConversationAnswerBlock> acceptedBlocks
    ) {
        return generate(
                content,
                route,
                window,
                acceptedBlocks,
                new ConversationProgress(
                        List.of(),
                        ConversationGuidanceStage.OPENING),
                "");
    }

    public List<ConversationSuggestedQuestion> generate(
            RuntimeAnswerContent content,
            ConversationRoute route,
            ConversationWindow window,
            List<ConversationAnswerBlock> acceptedBlocks,
            ConversationProgress progress,
            String currentQuestion
    ) {
        return generate(
                content,
                route,
                window,
                acceptedBlocks,
                progress,
                currentQuestion,
                true);
    }

    public List<ConversationSuggestedQuestion> generate(
            RuntimeAnswerContent content,
            ConversationRoute route,
            ConversationWindow window,
            List<ConversationAnswerBlock> acceptedBlocks,
            ConversationProgress progress,
            String currentQuestion,
            boolean allowModelSuggestions
    ) {
        List<ConversationSubjectOption> subjects = subjectOptions(content);
        List<ConversationSuggestedQuestion> candidates = new ArrayList<>();
        if (allowModelSuggestions) {
            ConversationModelResult<List<ConversationSuggestedQuestion>> generated =
                    modelPort.suggest(route, window, acceptedBlocks, subjects);
            if (generated != null && generated.isSuccessful()
                    && generated.getValue() != null) {
                candidates.addAll(generated.getValue().stream()
                        .limit(maxQuestions)
                        .toList());
            }
        }
        candidates.addAll(deterministicCandidates(content, route));

        Set<String> excluded = excludedQuestions(window, currentQuestion);
        Map<String, ConversationSuggestedQuestion> distinct =
                new LinkedHashMap<>();
        for (ConversationSuggestedQuestion candidate : candidates) {
            ConversationSuggestedQuestion validated =
                    validateCandidate(content, candidate, excluded);
            if (validated == null) {
                continue;
            }
            distinct.putIfAbsent(normalize(validated.getText()), validated);
        }

        int currentSlots = currentSlots(progress.getStage());
        List<ConversationSuggestedQuestion> current = distinct.values().stream()
                .filter(candidate -> isCurrentSubject(candidate, route))
                .toList();
        List<ConversationSuggestedQuestion> other = distinct.values().stream()
                .filter(candidate -> isOtherProject(candidate, route))
                .toList();

        List<ConversationSuggestedQuestion> result = new ArrayList<>(maxQuestions);
        addFirst(result, current, currentSlots);
        addDistinctProjects(
                result,
                other,
                maxQuestions - currentSlots);
        if (result.size() != maxQuestions) {
            throw new IllegalStateException(
                    "public suggestion pool cannot supply three grounded questions");
        }
        return List.copyOf(result);
    }

    private ConversationSuggestedQuestion validateCandidate(
            RuntimeAnswerContent content,
            ConversationSuggestedQuestion candidate,
            Set<String> excluded
    ) {
        if (candidate == null || candidate.getText() == null) {
            return null;
        }
        String text = candidate.getText().strip();
        String normalized = normalize(text);
        if (text.length() < 5 || text.length() > 120
                || normalized.isEmpty()
                || excluded.contains(normalized)
                || !subjectExists(content, candidate)
                || !groundingAssembler.canAnswer(content, candidate)) {
            return null;
        }
        return new ConversationSuggestedQuestion(
                text,
                candidate.getProjectSlug(),
                candidate.getCaseSlug(),
                candidate.getFacet());
    }

    private Set<String> excludedQuestions(
            ConversationWindow window,
            String currentQuestion
    ) {
        LinkedHashSet<String> excluded = new LinkedHashSet<>();
        excluded.add(normalize(currentQuestion == null ? "" : currentQuestion));
        List<ConversationMessage> userMessages = window.getRecentMessages().stream()
                .filter(message ->
                        message.getRole() == ConversationMessageRole.USER)
                .toList();
        int start = Math.max(0, userMessages.size() - 6);
        for (ConversationMessage message :
                userMessages.subList(start, userMessages.size())) {
            excluded.add(normalize(message.getContent()));
        }
        return Set.copyOf(excluded);
    }

    private int currentSlots(ConversationGuidanceStage stage) {
        return switch (stage) {
            case OPENING -> 3;
            case DEEPENING -> 2;
            case WRAP_UP -> 1;
            case EXPLORE_OTHERS -> 0;
        };
    }

    private void addFirst(
            List<ConversationSuggestedQuestion> result,
            List<ConversationSuggestedQuestion> candidates,
            int count
    ) {
        for (ConversationSuggestedQuestion candidate : candidates) {
            if (result.size() == count) {
                return;
            }
            result.add(candidate);
        }
    }

    private void addDistinctProjects(
            List<ConversationSuggestedQuestion> result,
            List<ConversationSuggestedQuestion> candidates,
            int count
    ) {
        int targetSize = result.size() + count;
        Set<String> selectedProjects = new HashSet<>();
        for (ConversationSuggestedQuestion candidate : candidates) {
            if (result.size() == targetSize) {
                return;
            }
            if (selectedProjects.add(candidate.getProjectSlug())) {
                result.add(candidate);
            }
        }
        for (ConversationSuggestedQuestion candidate : candidates) {
            if (result.size() == targetSize) {
                return;
            }
            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }
    }

    private boolean isCurrentSubject(
            ConversationSuggestedQuestion candidate,
            ConversationRoute route
    ) {
        if (route.getProjectSlug() != null) {
            return route.getProjectSlug().equals(candidate.getProjectSlug())
                    && candidate.getCaseSlug() == null;
        }
        if (route.getCaseSlug() != null) {
            return route.getCaseSlug().equals(candidate.getCaseSlug())
                    && candidate.getProjectSlug() == null;
        }
        return false;
    }

    private boolean isOtherProject(
            ConversationSuggestedQuestion candidate,
            ConversationRoute route
    ) {
        return candidate.getProjectSlug() != null
                && !candidate.getProjectSlug().equals(route.getProjectSlug());
    }

    private List<ConversationSuggestedQuestion> deterministicCandidates(
            RuntimeAnswerContent content,
            ConversationRoute route
    ) {
        List<ConversationSuggestedQuestion> candidates = new ArrayList<>();
        AnswerKnowledge current = currentSubject(content, route);
        if (current != null) {
            candidates.addAll(knowledgeCandidates(current));
        }

        List<AnswerKnowledge> otherProjects = content.getProjects().stream()
                .filter(project -> current == null
                        || project != current)
                .toList();
        List<List<ConversationSuggestedQuestion>> questionsByProject =
                otherProjects.stream()
                        .map(this::knowledgeCandidates)
                        .toList();
        for (List<ConversationSuggestedQuestion> projectQuestions :
                questionsByProject) {
            if (!projectQuestions.isEmpty()) {
                candidates.add(projectQuestions.getFirst());
            }
        }
        for (List<ConversationSuggestedQuestion> projectQuestions :
                questionsByProject) {
            if (projectQuestions.size() > 1) {
                candidates.addAll(projectQuestions.subList(
                        1,
                        projectQuestions.size()));
            }
        }
        return candidates;
    }

    private List<ConversationSuggestedQuestion> knowledgeCandidates(
            AnswerKnowledge knowledge
    ) {
        List<ConversationSuggestedQuestion> candidates = new ArrayList<>();
        for (AnswerQuestion question : knowledge.getQuestions()) {
            addQuestion(candidates, knowledge, question);
        }
        String title = knowledge.getTitle();
        addSynthetic(
                candidates,
                knowledge,
                title + "的背景和目标是什么？",
                PortfolioKnowledgeFacet.OVERVIEW);
        addSynthetic(
                candidates,
                knowledge,
                "我在" + title + "中承担了哪些职责？",
                PortfolioKnowledgeFacet.RESPONSIBILITY);
        addSynthetic(
                candidates,
                knowledge,
                title + "的核心方案是如何实现的？",
                PortfolioKnowledgeFacet.IMPLEMENTATION);
        addSynthetic(
                candidates,
                knowledge,
                title + "有哪些关键技术取舍？",
                PortfolioKnowledgeFacet.DECISION);
        addSynthetic(
                candidates,
                knowledge,
                title + "遇到过哪些困难，如何排查？",
                PortfolioKnowledgeFacet.INCIDENT);
        addSynthetic(
                candidates,
                knowledge,
                title + "最终如何验证结果？",
                PortfolioKnowledgeFacet.VERIFICATION);
        addSynthetic(
                candidates,
                knowledge,
                title + "最终取得了什么结果，还有哪些局限？",
                PortfolioKnowledgeFacet.OUTCOME);
        addSynthetic(
                candidates,
                knowledge,
                title + "有哪些已经公开的信息？",
                PortfolioKnowledgeFacet.OVERVIEW);
        addSynthetic(
                candidates,
                knowledge,
                title + "的公开方案与结果是什么？",
                PortfolioKnowledgeFacet.OVERVIEW);
        addSynthetic(
                candidates,
                knowledge,
                title + "有哪些可核验的公开证据？",
                PortfolioKnowledgeFacet.OVERVIEW);
        return candidates;
    }

    private void addSynthetic(
            List<ConversationSuggestedQuestion> candidates,
            AnswerKnowledge knowledge,
            String text,
            PortfolioKnowledgeFacet facet
    ) {
        candidates.add(new ConversationSuggestedQuestion(
                text,
                knowledge.getSubjectType() == AnswerSubjectType.PROJECT
                        ? knowledge.getSlug()
                        : null,
                knowledge.getSubjectType() == AnswerSubjectType.CASE
                        ? knowledge.getSlug()
                        : null,
                facet));
    }

    private void addQuestion(
            List<ConversationSuggestedQuestion> candidates,
            AnswerKnowledge knowledge,
            AnswerQuestion question
    ) {
        candidates.add(new ConversationSuggestedQuestion(
                question.getCanonicalQuestion(),
                knowledge.getSubjectType() == AnswerSubjectType.PROJECT
                        ? knowledge.getSlug()
                        : null,
                knowledge.getSubjectType() == AnswerSubjectType.CASE
                        ? knowledge.getSlug()
                        : null,
                facetFor(question)));
    }

    private PortfolioKnowledgeFacet facetFor(AnswerQuestion question) {
        if (question.getPreferredClaimCategories().isEmpty()) {
            return PortfolioKnowledgeFacet.OVERVIEW;
        }
        AnswerClaimCategory category =
                question.getPreferredClaimCategories().getFirst();
        return switch (category) {
            case BACKGROUND -> PortfolioKnowledgeFacet.OVERVIEW;
            case RESPONSIBILITY -> PortfolioKnowledgeFacet.RESPONSIBILITY;
            case TECHNICAL_DECISION -> PortfolioKnowledgeFacet.DECISION;
            case IMPLEMENTATION -> PortfolioKnowledgeFacet.IMPLEMENTATION;
            case VERIFICATION -> PortfolioKnowledgeFacet.VERIFICATION;
            case OUTCOME -> PortfolioKnowledgeFacet.OUTCOME;
            case LIMITATION -> PortfolioKnowledgeFacet.LIMITATION;
            case LEARNING, REFLECTION -> PortfolioKnowledgeFacet.LEARNING;
        };
    }

    private AnswerKnowledge currentSubject(
            RuntimeAnswerContent content,
            ConversationRoute route
    ) {
        if (route.getProjectSlug() != null) {
            return content.getProjects().stream()
                    .filter(item ->
                            route.getProjectSlug().equals(item.getSlug()))
                    .findFirst()
                    .orElse(null);
        }
        if (route.getCaseSlug() != null) {
            return content.getCases().stream()
                    .filter(item -> route.getCaseSlug().equals(item.getSlug()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private boolean subjectExists(
            RuntimeAnswerContent content,
            ConversationSuggestedQuestion question
    ) {
        if (question.getProjectSlug() != null
                && question.getCaseSlug() != null) {
            return false;
        }
        if (question.getProjectSlug() != null) {
            return content.getProjects().stream()
                    .anyMatch(item -> question.getProjectSlug().equals(
                            item.getSlug()));
        }
        if (question.getCaseSlug() != null) {
            return content.getCases().stream()
                    .anyMatch(item -> question.getCaseSlug().equals(
                            item.getSlug()));
        }
        return false;
    }

    private List<ConversationSubjectOption> subjectOptions(
            RuntimeAnswerContent content
    ) {
        return java.util.stream.Stream.concat(
                        content.getProjects().stream(),
                        content.getCases().stream())
                .map(item -> new ConversationSubjectOption(
                        item.getSubjectType(),
                        item.getSlug(),
                        item.getTitle(),
                        item.getSummary()))
                .toList();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s？?！!。,.，]", "");
    }
}
