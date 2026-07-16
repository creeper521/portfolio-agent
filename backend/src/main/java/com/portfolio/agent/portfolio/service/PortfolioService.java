package com.portfolio.agent.portfolio.service;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.exception.ProjectNotFoundException;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class PortfolioService {

    private final PublicPortfolioRepository repository;

    public PortfolioService(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    public PortfolioOverview getPortfolio() {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        return new PortfolioOverview(
                snapshot.getContentVersion(),
                snapshot.getPublishedAt(),
                snapshot.getOwner(),
                snapshot.getProjects()
        );
    }

    public ProjectDetails getProject(String slug) {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = findProject(snapshot, slug);
        Set<String> evidenceIds = Set.copyOf(project.getEvidenceIds());

        List<EvidenceRecord> evidence = snapshot.getEvidence().stream()
                .filter(item -> evidenceIds.contains(item.getId()))
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> Boolean.FALSE.equals(item.getRawContentPublic()))
                .toList();

        List<String> suggestedQuestions = snapshot.getQuestions().stream()
                .filter(question -> project.getId().equals(question.getProjectId()))
                .filter(question -> project.getQuestionIds().contains(question.getId()))
                .map(QuestionDefinition::getSuggestion)
                .toList();

        return new ProjectDetails(project, evidence, suggestedQuestions);
    }

    private ProjectProfile findProject(PortfolioSnapshot snapshot, String slug) {
        return snapshot.getProjects().stream()
                .filter(project -> project.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ProjectNotFoundException(slug));
    }
}
