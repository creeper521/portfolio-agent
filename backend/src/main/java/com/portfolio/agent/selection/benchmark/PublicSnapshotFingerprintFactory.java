package com.portfolio.agent.selection.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a public-only semantic fingerprint from a fully loaded runtime snapshot.
 * Transient load timestamps, private governance state, and raw vectors are excluded.
 */
public final class PublicSnapshotFingerprintFactory {
    private final ObjectMapper mapper;

    public PublicSnapshotFingerprintFactory(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public PublicSnapshotFingerprint create(RuntimeContentSnapshot snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("PROJECT", snapshot.getProjects().size());
        counts.put("CASE", snapshot.getCases().size());
        counts.put("COLLECTION", snapshot.getCollections().size());
        counts.put("CLAIM", snapshot.getClaims().size());
        counts.put("EVIDENCE", snapshot.getApprovedEvidence().size());
        counts.put("CLAIM_EVIDENCE_LINK", snapshot.getClaimEvidenceLinks().size());
        counts.put("QUESTION", snapshot.getQuestions().size());
        counts.put("TIMELINE_EVENT", snapshot.getTimeline().size());
        counts.put("RETRIEVAL_DOCUMENT", snapshot.getRetrievalContent()
                .map(value -> value.getDocuments().size()).orElse(0));

        Set<String> relationships = new TreeSet<>();
        for (Claim claim : snapshot.getClaims()) {
            relationships.add("claim-subject:" + claim.getId() + ":" + claim.getSubjectId());
        }
        for (ClaimEvidenceLink link : snapshot.getClaimEvidenceLinks()) {
            relationships.add("claim-evidence:" + link.getClaimId() + ":" + link.getEvidenceId());
        }
        for (CaseStudy caseStudy : snapshot.getCases()) {
            if (caseStudy.getProjectId() != null) {
                relationships.add("case-project:" + caseStudy.getId() + ":" + caseStudy.getProjectId());
            }
            for (String collectionId : caseStudy.getCollectionIds()) {
                relationships.add("case-collection:" + caseStudy.getId() + ":" + collectionId);
            }
        }
        for (ProjectProfile project : snapshot.getProjects()) {
            for (String caseId : project.getFeaturedCaseIds()) {
                relationships.add("project-featured-case:" + project.getId() + ":" + caseId);
            }
        }
        snapshot.getRetrievalContent().ifPresent(retrieval -> {
            for (RagDocument document : retrieval.getDocuments()) {
                for (String claimId : document.getClaimIds()) {
                    relationships.add("document-claim:" + document.getChunkId() + ":" + claimId);
                }
            }
        });

        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("owner", hash(snapshot.getOwner()));
        hashes.put("publishedAt", hash(snapshot.getPublishedAt()));
        hashes.put("runtimeBundleHash", hash(snapshot.getRuntimeBundleHash()));
        hashes.put("projects", hash(sorted(snapshot.getProjects(), ProjectProfile::getId)));
        hashes.put("cases", hash(sorted(snapshot.getCases(), CaseStudy::getId)));
        hashes.put("collections", hash(snapshot.getCollections().stream()
                .sorted(Comparator.comparing(value -> value.getId())).toList()));
        hashes.put("claims", hash(sorted(snapshot.getClaims(), Claim::getId)));
        hashes.put("evidence", hash(snapshot.getApprovedEvidence().stream()
                .sorted(Comparator.comparing(value -> value.getId())).toList()));
        hashes.put("claimEvidenceLinks", hash(snapshot.getClaimEvidenceLinks().stream()
                .sorted(Comparator.comparing(ClaimEvidenceLink::getId)).toList()));
        hashes.put("questions", hash(snapshot.getQuestions().stream()
                .sorted(Comparator.comparing(value -> value.getId())).toList()));
        hashes.put("timeline", hash(snapshot.getTimeline().stream()
                .sorted(Comparator.comparing(value -> value.getId())).toList()));
        hashes.put("relationships", hash(new ArrayList<>(relationships)));
        snapshot.getRetrievalContent().ifPresent(retrieval ->
                hashes.put("retrievalDocuments", hash(retrieval.getDocuments().stream()
                        .sorted(Comparator.comparing(RagDocument::getChunkId)).toList())));
        return new PublicSnapshotFingerprint(
                snapshot.getSchemaVersion(), snapshot.getContentVersion(), counts, relationships, hashes);
    }

    private <T> List<T> sorted(List<T> values, java.util.function.Function<T, String> id) {
        return values.stream().sorted(Comparator.comparing(id)).toList();
    }

    private String hash(Object value) {
        try {
            byte[] canonical = mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("unable to canonicalize public snapshot", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
