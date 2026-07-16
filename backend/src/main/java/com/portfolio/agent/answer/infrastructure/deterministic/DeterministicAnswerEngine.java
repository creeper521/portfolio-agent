package com.portfolio.agent.answer.infrastructure.deterministic;

import com.portfolio.agent.answer.application.AnswerEngine;
import com.portfolio.agent.answer.application.QuestionNormalizer;
import com.portfolio.agent.answer.domain.model.AnswerMode;
import com.portfolio.agent.answer.domain.model.AnswerResult;
import com.portfolio.agent.answer.domain.model.AnswerSection;
import com.portfolio.agent.answer.domain.model.AnswerSectionType;
import com.portfolio.agent.portfolio.domain.model.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.model.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.model.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.model.ProjectProfile;
import com.portfolio.agent.portfolio.domain.model.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DeterministicAnswerEngine implements AnswerEngine {

    private static final String BOUNDARY_MESSAGE =
            "当前版本只稳定支持项目完整介绍问题。你可以使用下方推荐问题了解项目背景、我的职责、技术方案、验证过程和最终状态。";

    private final PublicPortfolioRepository repository;
    private final QuestionNormalizer normalizer;

    public DeterministicAnswerEngine(
            PublicPortfolioRepository repository,
            QuestionNormalizer normalizer
    ) {
        this.repository = repository;
        this.normalizer = normalizer;
    }

    @Override
    public AnswerResult answer(String projectSlug, String question) {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = snapshot.getProjects().stream()
                .filter(candidate -> candidate.getSlug().equals(projectSlug))
                .findFirst()
                .orElse(null);

        if (project == null) {
            return boundaryResult("", List.of());
        }

        List<QuestionDefinition> projectQuestions = snapshot.getQuestions().stream()
                .filter(candidate -> project.getId().equals(candidate.getProjectId()))
                .filter(candidate -> project.getQuestionIds().contains(candidate.getId()))
                .toList();

        String normalizedQuestion = normalizer.normalize(question);
        boolean matched = projectQuestions.stream()
                .anyMatch(candidate -> matches(candidate, normalizedQuestion));

        List<String> suggestions = projectQuestions.stream()
                .map(QuestionDefinition::getSuggestion)
                .toList();

        if (!matched) {
            return boundaryResult(project.getTitle(), suggestions);
        }

        Set<String> evidenceIds = Set.copyOf(project.getEvidenceIds());
        List<EvidenceRecord> publicEvidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> !candidate.getRawContentPublic())
                .toList();

        return new AnswerResult(
                AnswerMode.DETERMINISTIC,
                true,
                false,
                project.getTitle(),
                buildSections(project),
                publicEvidence,
                suggestions
        );
    }

    private boolean matches(QuestionDefinition definition, String normalizedQuestion) {
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

    private List<AnswerSection> buildSections(ProjectProfile project) {
        return List.of(
                new AnswerSection(AnswerSectionType.BACKGROUND, project.getBackground()),
                new AnswerSection(AnswerSectionType.RESPONSIBILITY,
                        joinSentences(project.getResponsibilities())),
                new AnswerSection(AnswerSectionType.SOLUTION,
                        project.getSolution() + " 关键决策包括：" + joinSentences(project.getKeyDecisions())),
                new AnswerSection(AnswerSectionType.VERIFICATION,
                        joinSentences(project.getVerification())),
                new AnswerSection(AnswerSectionType.STATUS,
                        project.getOutcome() + " " + project.getHandoff())
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
                List.<EvidenceRecord>of(),
                suggestions
        );
    }
}
