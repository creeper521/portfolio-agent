package com.portfolio.agent.answer.engine.deterministic;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.engine.QuestionNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DeterministicAnswerEngine implements AnswerEngine {

    private static final String BOUNDARY_MESSAGE =
            "当前版本只稳定支持项目完整介绍问题。你可以使用下方推荐问题了解项目背景、我的职责、技术方案、验证过程和最终状态。";

    private final QuestionNormalizer normalizer;

    public DeterministicAnswerEngine(QuestionNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public AnswerResult answer(AnswerKnowledge knowledge, String question) {
        String normalizedQuestion = normalizer.normalize(question);
        boolean matched = knowledge.getQuestions().stream()
                .anyMatch(candidate -> matches(candidate, normalizedQuestion));

        List<String> suggestions = knowledge.getQuestions().stream()
                .map(AnswerQuestion::getSuggestion)
                .toList();

        if (!matched) {
            return boundaryResult(knowledge.getTitle(), suggestions);
        }

        return new AnswerResult(
                AnswerMode.DETERMINISTIC,
                true,
                false,
                knowledge.getTitle(),
                buildSections(knowledge),
                knowledge.getEvidence(),
                suggestions
        );
    }

    private boolean matches(AnswerQuestion definition, String normalizedQuestion) {
        if (normalizedQuestion.isBlank()) {
            return false;
        }
        if (normalizer.normalize(definition.getCanonicalQuestion()).equals(normalizedQuestion)) {
            return true;
        }
        return definition.getAliases().stream()
                .map(normalizer::normalize)
                .anyMatch(normalizedQuestion::equals);
    }

    private List<AnswerSection> buildSections(AnswerKnowledge knowledge) {
        return List.of(
                new AnswerSection(AnswerSectionType.BACKGROUND, knowledge.getBackground()),
                new AnswerSection(
                        AnswerSectionType.RESPONSIBILITY,
                        joinSentences(knowledge.getResponsibilities())
                ),
                new AnswerSection(
                        AnswerSectionType.SOLUTION,
                        knowledge.getSolution()
                                + " 关键决策包括："
                                + joinSentences(knowledge.getKeyDecisions())
                ),
                new AnswerSection(
                        AnswerSectionType.VERIFICATION,
                        joinSentences(knowledge.getVerification())
                ),
                new AnswerSection(
                        AnswerSectionType.STATUS,
                        knowledge.getOutcome() + " " + knowledge.getHandoff()
                )
        );
    }

    private String joinSentences(List<String> values) {
        return values.stream()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private AnswerResult boundaryResult(String title, List<String> suggestions) {
        return new AnswerResult(
                AnswerMode.DETERMINISTIC,
                false,
                false,
                title,
                List.of(new AnswerSection(AnswerSectionType.BOUNDARY, BOUNDARY_MESSAGE)),
                List.of(),
                suggestions
        );
    }
}
