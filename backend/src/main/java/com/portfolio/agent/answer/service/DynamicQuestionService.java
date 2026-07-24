package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        List<ConversationSubjectOption> subjects = subjectOptions(content);
        ConversationModelResult<List<ConversationSuggestedQuestion>> generated =
                modelPort.suggest(route, window, acceptedBlocks, subjects);
        List<ConversationSuggestedQuestion> candidates =
                generated != null && generated.isSuccessful()
                        ? generated.getValue().stream().limit(6).toList()
                        : fallbackCandidates(content, route);
        Map<String, ConversationSuggestedQuestion> distinct = new LinkedHashMap<>();
        for (ConversationSuggestedQuestion candidate : candidates) {
            if (candidate == null || candidate.getText() == null) {
                continue;
            }
            String text = candidate.getText().strip();
            String normalized = normalize(text);
            if (text.length() < 5 || text.length() > 120
                    || distinct.containsKey(normalized)
                    || !subjectExists(content, candidate)
                    || !groundingAssembler.canAnswer(content, candidate)) {
                continue;
            }
            distinct.put(normalized, candidate);
            if (distinct.size() == maxQuestions) {
                break;
            }
        }
        return List.copyOf(distinct.values());
    }

    private List<ConversationSuggestedQuestion> fallbackCandidates(
            RuntimeAnswerContent content,
            ConversationRoute route
    ) {
        List<ConversationSuggestedQuestion> candidates = new ArrayList<>();
        for (AnswerKnowledge knowledge : selectedSubjects(content, route)) {
            for (AnswerQuestion question : knowledge.getQuestions()) {
                candidates.add(new ConversationSuggestedQuestion(
                        question.getCanonicalQuestion(),
                        knowledge.getSubjectType() == AnswerSubjectType.PROJECT
                                ? knowledge.getSlug() : null,
                        knowledge.getSubjectType() == AnswerSubjectType.CASE
                                ? knowledge.getSlug() : null,
                        PortfolioKnowledgeFacet.OVERVIEW));
            }
        }
        return candidates;
    }

    private List<AnswerKnowledge> selectedSubjects(
            RuntimeAnswerContent content,
            ConversationRoute route
    ) {
        if (route.getProjectSlug() != null) {
            return content.getProjects().stream()
                    .filter(item -> route.getProjectSlug().equals(item.getSlug()))
                    .toList();
        }
        if (route.getCaseSlug() != null) {
            return content.getCases().stream()
                    .filter(item -> route.getCaseSlug().equals(item.getSlug()))
                    .toList();
        }
        return java.util.stream.Stream.concat(
                        content.getProjects().stream(),
                        content.getCases().stream())
                .toList();
    }

    private boolean subjectExists(
            RuntimeAnswerContent content,
            ConversationSuggestedQuestion question
    ) {
        if (question.getProjectSlug() != null && question.getCaseSlug() != null) {
            return false;
        }
        if (question.getProjectSlug() != null) {
            return content.getProjects().stream()
                    .anyMatch(item -> question.getProjectSlug().equals(item.getSlug()));
        }
        if (question.getCaseSlug() != null) {
            return content.getCases().stream()
                    .anyMatch(item -> question.getCaseSlug().equals(item.getSlug()));
        }
        return false;
    }

    private List<ConversationSubjectOption> subjectOptions(RuntimeAnswerContent content) {
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
