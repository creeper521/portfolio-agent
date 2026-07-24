package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class RagDocumentValidator {

    private static final Pattern EXTERNAL_URL = Pattern.compile("(?i)(https?://|www\\.)");
    private static final Pattern INSTRUCTION_MARKER = Pattern.compile(
            "(?i)(ignore previous|system prompt|tool[_ ]?call|\\{\\{|\\$\\{)");

    public void validate(
            PortfolioSnapshot snapshot,
            List<RagDocument> documents,
            LocalDate currentDate
    ) {
        require(snapshot != null, "portfolio snapshot is required");
        require(documents != null, "RAG documents are required");
        require(currentDate != null, "validation date is required");

        Map<String, ProjectProfile> projectsBySlug = new HashMap<>();
        Map<String, ProjectProfile> projectsById = new HashMap<>();
        for (ProjectProfile project : snapshot.getProjects()) {
            projectsBySlug.put(project.getSlug(), project);
            projectsById.put(project.getId(), project);
        }
        Map<String, Claim> claimsById = new HashMap<>();
        for (Claim claim : snapshot.getClaims()) {
            claimsById.put(claim.getId(), claim);
        }
        Map<String, CaseStudy> casesBySlug = new HashMap<>();
        Map<String, CaseStudy> casesById = new HashMap<>();
        for (CaseStudy caseStudy : snapshot.getCases()) {
            casesBySlug.put(caseStudy.getSlug(), caseStudy);
            casesById.put(caseStudy.getId(), caseStudy);
        }

        Set<String> chunkIds = new HashSet<>();
        Set<String> contentHashes = new HashSet<>();
        for (RagDocument document : documents) {
            require(document != null, "RAG document is required");
            require(hasText(document.getChunkId()), "chunkId is required");
            require(chunkIds.add(document.getChunkId()),
                    "duplicate chunkId: " + document.getChunkId());
            require(snapshot.getContentVersion().equals(document.getContentVersion()),
                    "RAG document contentVersion mismatch: " + document.getChunkId());
            require(document.getProjectSlugs().isEmpty() != document.getCaseSlugs().isEmpty(),
                    "RAG document must belong to exactly one subject type");
            requireNonBlank(document.getClaimIds(), "claimIds");
            requireNonBlank(document.getTopics(), "topics");
            require(hasText(document.getText()), "RAG document text is required");
            require(document.getValidFrom() != null, "validFrom is required");
            require(!document.getValidFrom().isAfter(currentDate),
                    "RAG document is not active: " + document.getChunkId());
            require(document.getValidUntil() == null
                            || !document.getValidUntil().isBefore(currentDate),
                    "RAG document is expired: " + document.getChunkId());
            require(!containsUnsafeText(document.getText()),
                    "RAG document contains unsafe text: " + document.getChunkId());
            require(RagDocumentHashCalculator.contentHash(document)
                            .equals(document.getContentHash()),
                    "RAG document contentHash mismatch: " + document.getChunkId());
            require(contentHashes.add(document.getContentHash()),
                    "duplicate canonical RAG document content");

            for (String slug : document.getProjectSlugs()) {
                require(projectsBySlug.containsKey(slug),
                        "RAG document project does not exist: " + slug);
            }
            for (String slug : document.getCaseSlugs()) {
                require(casesBySlug.containsKey(slug),
                        "RAG document case does not exist: " + slug);
            }
            for (String claimId : document.getClaimIds()) {
                Claim claim = claimsById.get(claimId);
                require(claim != null, "RAG document claim does not exist: " + claimId);
                if (claim.getSubjectType() == ClaimSubjectType.PROJECT) {
                    ProjectProfile owner = projectsById.get(claim.getSubjectId());
                    require(owner != null
                                    && document.getProjectSlugs().contains(owner.getSlug()),
                            "RAG document claim belongs to a different project: " + claimId);
                } else {
                    CaseStudy owner = casesById.get(claim.getSubjectId());
                    require(owner != null && document.getCaseSlugs().contains(owner.getSlug()),
                            "RAG document claim belongs to a different case: " + claimId);
                }
            }
        }
    }

    private boolean containsUnsafeText(String text) {
        if (EXTERNAL_URL.matcher(text).find() || INSTRUCTION_MARKER.matcher(text).find()) {
            return true;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (type == Character.FORMAT || type == Character.CONTROL) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private void requireNonBlank(List<String> values, String field) {
        require(values != null && !values.isEmpty(), field + " must not be empty");
        for (String value : values) {
            require(hasText(value), field + " must not contain blank values");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPortfolioSnapshotException(message);
        }
    }
}
