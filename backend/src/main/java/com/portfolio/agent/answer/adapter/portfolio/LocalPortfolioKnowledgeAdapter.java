package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LocalPortfolioKnowledgeAdapter implements PortfolioKnowledgeGateway {

    private final PublicPortfolioRepository repository;

    public LocalPortfolioKnowledgeAdapter(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AnswerKnowledge> findBySlug(String projectSlug) {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        Optional<ProjectProfile> project = snapshot.getProjects().stream()
                .filter(candidate -> candidate.getSlug().equals(projectSlug))
                .findFirst();

        if (project.isEmpty()) {
            return Optional.empty();
        }

        ProjectProfile value = project.orElseThrow();
        Set<String> questionIds = Set.copyOf(value.getQuestionIds());
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());

        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> value.getId().equals(candidate.getProjectId()))
                .filter(candidate -> questionIds.contains(candidate.getId()))
                .map(this::toQuestion)
                .toList();

        List<AnswerEvidence> evidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> Boolean.FALSE.equals(candidate.getRawContentPublic()))
                .map(this::toEvidence)
                .toList();

        return Optional.of(new AnswerKnowledge(
                value.getSlug(),
                value.getTitle(),
                value.getBackground(),
                value.getResponsibilities(),
                value.getSolution(),
                value.getKeyDecisions(),
                value.getVerification(),
                value.getOutcome(),
                value.getHandoff(),
                value.getStatus().name(),
                questions,
                evidence
        ));
    }

    private AnswerQuestion toQuestion(QuestionDefinition question) {
        return new AnswerQuestion(
                question.getCanonicalQuestion(),
                question.getAliases(),
                question.getSuggestion()
        );
    }

    private AnswerEvidence toEvidence(EvidenceRecord evidence) {
        return new AnswerEvidence(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType().name(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                evidence.getPublicStatus().name(),
                false
        );
    }
}
