package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.CaseStudy;
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
        Map<String, CaseStudy> casesById = snapshot.getCases().stream()
                .collect(Collectors.toUnmodifiableMap(CaseStudy::getId, Function.identity()));
        return snapshot.getClaims().stream()
                .sorted(java.util.Comparator.comparing(Claim::getId))
                .map(claim -> document(
                        snapshot, projectsById, casesById, claim, validFrom))
                .toList();
    }

    private RagDocument document(
            PortfolioSnapshot snapshot,
            Map<String, ProjectProfile> projectsById,
            Map<String, CaseStudy> casesById,
            Claim claim,
            LocalDate validFrom
    ) {
        ProjectProfile project = claim.getSubjectType() == ClaimSubjectType.PROJECT
                ? projectsById.get(claim.getSubjectId())
                : null;
        CaseStudy caseStudy = claim.getSubjectType() == ClaimSubjectType.CASE
                ? casesById.get(claim.getSubjectId())
                : null;
        if (project == null && caseStudy == null) {
            throw new InvalidPortfolioSnapshotException(
                    "claim owner does not exist: " + claim.getId());
        }
        List<String> projectSlugs = project == null
                ? List.of()
                : List.of(project.getSlug());
        List<String> caseSlugs = caseStudy == null
                ? List.of()
                : List.of(caseStudy.getSlug());
        String ownerTitle = project == null ? caseStudy.getTitle() : project.getTitle();
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
                projectSlugs,
                caseSlugs,
                List.of(claim.getId()),
                ownerTitle.strip() + "：" + fact,
                topics,
                validFrom,
                null,
                "unsigned");
        return new RagDocument(
                unsigned.getChunkId(),
                unsigned.getContentVersion(),
                unsigned.getProjectSlugs(),
                unsigned.getCaseSlugs(),
                unsigned.getClaimIds(),
                unsigned.getText(),
                unsigned.getTopics(),
                unsigned.getValidFrom(),
                unsigned.getValidUntil(),
                RagDocumentHashCalculator.contentHash(unsigned));
    }
}
