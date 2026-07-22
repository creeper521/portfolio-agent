package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ClaimRagDocumentBuilder {

    public List<RagDocument> build(PortfolioSnapshot snapshot) {
        if (snapshot == null || snapshot.getPublishedAt() == null) {
            throw new InvalidPortfolioSnapshotException(
                    "published portfolio snapshot is required");
        }
        return build(snapshot, snapshot.getPublishedAt().toLocalDate());
    }

    public List<RagDocument> build(PortfolioSnapshot snapshot, LocalDate validFrom) {
        if (snapshot == null || validFrom == null) {
            throw new InvalidPortfolioSnapshotException(
                    "portfolio snapshot and RAG validFrom are required");
        }
        Map<String, ProjectProfile> projectsById = snapshot.getProjects().stream()
                .collect(Collectors.toUnmodifiableMap(ProjectProfile::getId, Function.identity()));
        return snapshot.getClaims().stream()
                .filter(claim -> claim.getSubjectType() == ClaimSubjectType.PROJECT)
                .sorted(java.util.Comparator.comparing(Claim::getId))
                .map(claim -> document(snapshot, projectsById, claim, validFrom))
                .toList();
    }

    private RagDocument document(
            PortfolioSnapshot snapshot,
            Map<String, ProjectProfile> projectsById,
            Claim claim,
            LocalDate validFrom
    ) {
        ProjectProfile project = projectsById.get(claim.getSubjectId());
        if (project == null) {
            throw new InvalidPortfolioSnapshotException(
                    "project claim owner does not exist: " + claim.getId());
        }
        List<String> topics = claim.getTopics().stream()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        String fact = (claim.getStatement().strip() + " " + claim.getDetail().strip()).strip();
        RagDocument unsigned = new RagDocument(
                "chunk-" + claim.getId(),
                snapshot.getContentVersion(),
                List.of(project.getSlug()),
                List.of(claim.getId()),
                project.getTitle().strip() + "：" + fact,
                topics,
                validFrom,
                null,
                "unsigned");
        return new RagDocument(
                unsigned.getChunkId(),
                unsigned.getContentVersion(),
                unsigned.getProjectSlugs(),
                unsigned.getClaimIds(),
                unsigned.getText(),
                unsigned.getTopics(),
                unsigned.getValidFrom(),
                unsigned.getValidUntil(),
                RagDocumentHashCalculator.contentHash(unsigned));
    }
}
