package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioContractTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.common.text.StableQuestionNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PortfolioPresetResolver {

    public PortfolioPresetResolution resolve(
            PortfolioTurn turn,
            RuntimeAnswerContent content
    ) {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(content, "content");
        List<SubjectQuestion> available = questions(content, turn);
        if (turn.getQuestionPresetId() != null) {
            List<SubjectQuestion> globalMatches = allQuestions(content).stream()
                    .filter(candidate -> candidate.question.getId()
                            .equals(turn.getQuestionPresetId()))
                    .toList();
            if (globalMatches.size() != 1
                    || !matchesHint(globalMatches.getFirst().subject, turn)) {
                return PortfolioPresetResolution.invalid();
            }
            SubjectQuestion match = globalMatches.getFirst();
            if (!match.question.isActiveContract()) {
                return PortfolioPresetResolution.unavailable(match.question.getId());
            }
            if (!matchesText(match.question, turn.getQuestion())) {
                return PortfolioPresetResolution.invalid();
            }
            if (turn.getContractVersion() != null
                    && !turn.getContractVersion().equals(match.question.getContractVersion())) {
                return PortfolioPresetResolution.stale(
                        match.question.getId(), match.question.getContractVersion());
            }
            return matched(turn, match);
        }
        String normalizedQuestion = normalize(turn.getQuestion());
        List<SubjectQuestion> matches = available.stream()
                .filter(candidate -> candidate.question.isActiveContract())
                .filter(candidate -> matchesNormalized(candidate.question, normalizedQuestion))
                .toList();
        if (matches.isEmpty()) {
            return PortfolioPresetResolution.noMatch();
        }
        if (matches.size() != 1) {
            return PortfolioPresetResolution.invalid();
        }
        return matched(turn, matches.getFirst());
    }

    private PortfolioPresetResolution matched(
            PortfolioTurn turn,
            SubjectQuestion match
    ) {
        if (!match.question.getRequiredClaimIds().isEmpty()) {
            if (!match.subject.getStableId()
                    .equals(match.question.getContractSubjectId())) {
                return PortfolioPresetResolution.invalid();
            }
            return PortfolioPresetResolution.matchedContract(new PortfolioContractTask(
                    match.question.getId(),
                    match.question.getContractVersion(),
                    match.question.getCanonicalQuestion(),
                    match.question.getContractSubjectId(),
                    match.question.getRequiredClaimIds(),
                    match.question.getSupportingClaimIds(),
                    match.question.getMinimumApprovedEvidencePerRequiredClaim()));
        }
        PortfolioTask task = new PortfolioTask(
                turn.getTurnId(),
                match.question.getCanonicalQuestion(),
                PortfolioTaskMode.FACT_LOOKUP,
                1.0d,
                PortfolioConditions.empty(),
                turn.getRecommendationContext(),
                null,
                match.subject.getStableId(),
                match.question.getPreferredClaimCategories());
        return PortfolioPresetResolution.matched(task, match.question.getId());
    }

    private boolean matchesText(AnswerQuestion question, String text) {
        return matchesNormalized(question, normalize(text));
    }

    private boolean matchesNormalized(AnswerQuestion question, String normalized) {
        if (normalized.isBlank()) {
            return false;
        }
        if (normalize(question.getCanonicalQuestion()).equals(normalized)) {
            return true;
        }
        return question.getAliases().stream()
                .map(PortfolioPresetResolver::normalize)
                .anyMatch(normalized::equals);
    }

    private static String normalize(String question) {
        return StableQuestionNormalizer.normalize(question);
    }

    private List<SubjectQuestion> questions(
            RuntimeAnswerContent content,
            PortfolioTurn turn
    ) {
        return allQuestions(content).stream()
                .filter(candidate -> matchesHint(candidate.subject, turn))
                .toList();
    }

    private List<SubjectQuestion> allQuestions(RuntimeAnswerContent content) {
        List<SubjectQuestion> questions = new ArrayList<>();
        addQuestions(questions, content.getProjects());
        addQuestions(questions, content.getCases());
        return List.copyOf(questions);
    }

    private void addQuestions(
            List<SubjectQuestion> target,
            List<AnswerKnowledge> subjects
    ) {
        for (AnswerKnowledge subject : subjects) {
            for (AnswerQuestion question : subject.getQuestions()) {
                target.add(new SubjectQuestion(subject, question));
            }
        }
    }

    private boolean matchesHint(AnswerKnowledge subject, PortfolioTurn turn) {
        if (turn.getProjectSlug() != null) {
            return subject.getSubjectType() == AnswerSubjectType.PROJECT
                    && turn.getProjectSlug().equals(subject.getSlug());
        }
        if (turn.getCaseSlug() != null) {
            return subject.getSubjectType() == AnswerSubjectType.CASE
                    && turn.getCaseSlug().equals(subject.getSlug());
        }
        return true;
    }

    private static final class SubjectQuestion {

        private final AnswerKnowledge subject;
        private final AnswerQuestion question;

        private SubjectQuestion(AnswerKnowledge subject, AnswerQuestion question) {
            this.subject = subject;
            this.question = question;
        }
    }
}
