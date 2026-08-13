package com.portfolio.agent.portfolio.repository.postgres;

import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ClaimVerificationStatus;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.ReviewStatus;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.support.TransactionOperations;

public final class PublicBundleDatabaseImporter {

    private static final String INSERT_RELEASE_SQL = """
            INSERT INTO content_release
                (release_id, release_version, schema_version, content_hash, status, verified_at)
            VALUES (CAST(? AS uuid), ?, ?, ?, 'VERIFIED', now())
            """;
    private static final String INSERT_SUBJECT_SQL = """
            INSERT INTO portfolio_subject
                (release_id, stable_id, subject_kind, slug, title, summary, career_track,
                 contribution_type, achievement_status, verification_status, public_route, display_order)
            VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_PROJECT_SQL = """
            INSERT INTO project_profile
                (release_id, stable_id, project_status, project_nature, display_tier, featured_case_ids)
            VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS jsonb))
            """;
    private static final String INSERT_CASE_SQL = """
            INSERT INTO case_study
                (release_id, stable_id, project_stable_id, case_type, collection_ids)
            VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS jsonb))
            """;
    private static final String INSERT_CLAIM_SQL = """
            INSERT INTO claim
                (release_id, stable_id, subject_stable_id, subject_kind, category,
                 statement, detail, achievement_status, contribution_type,
                 verification_basis, verification_status, materiality, topics, display_order)
            VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            """;
    private static final String INSERT_EVIDENCE_SQL = """
            INSERT INTO evidence
                (release_id, stable_id, public_code, evidence_type, label, description,
                 public_url, public_status)
            VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?, NULL, 'APPROVED')
            """;
    private static final String INSERT_LINK_SQL = """
            INSERT INTO claim_evidence_link
                (release_id, claim_stable_id, evidence_stable_id, support_type)
            VALUES (CAST(? AS uuid), ?, ?, ?)
            """;
    private static final String INSERT_CAPABILITY_SQL = """
            INSERT INTO subject_capability
                (release_id, subject_stable_id, capability_code, supporting_claim_stable_id)
            VALUES (CAST(? AS uuid), ?, ?, ?)
            ON CONFLICT (release_id, subject_stable_id, capability_code) DO NOTHING
            """;
    private static final String INSERT_DOCUMENT_SQL = """
            INSERT INTO retrieval_document
                (release_id, stable_id, subject_stable_id, claim_stable_id, search_text,
                 embedding, embedding_model, content_hash)
            VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS vector), ?, ?)
            """;
    private static final String INSERT_RUNTIME_SNAPSHOT_SQL = """
            INSERT INTO release_runtime_snapshot
                (release_id, payload, payload_checksum)
            VALUES (CAST(? AS uuid), CAST(? AS jsonb), ?)
            """;
    private static final String FIND_RELEASE_SQL = """
            SELECT release_id::text, release_version, status
            FROM content_release
            WHERE release_id = CAST(? AS uuid)
              AND release_version = ?
              AND content_hash = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations transactions;
    private final PublicRuntimeSnapshotCodec snapshotCodec;

    public PublicBundleDatabaseImporter(JdbcTemplate jdbcTemplate, TransactionOperations transactions) {
        this(jdbcTemplate, transactions,
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules()));
    }

    public PublicBundleDatabaseImporter(
            JdbcTemplate jdbcTemplate,
            TransactionOperations transactions,
            PublicRuntimeSnapshotCodec snapshotCodec) {
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = java.util.Objects.requireNonNull(transactions, "transactions");
        this.snapshotCodec = java.util.Objects.requireNonNull(snapshotCodec, "snapshotCodec");
    }

    public PublicBundleImportResult importBundle(RuntimeContentSnapshot snapshot) {
        ValidatedBundle bundle = validate(snapshot);
        String releaseId = UUID.nameUUIDFromBytes((snapshot.getSchemaVersion() + "\n"
                + snapshot.getContentVersion() + "\n" + snapshot.getRuntimeBundleHash())
                .getBytes(StandardCharsets.UTF_8)).toString();
        return transactions.execute(status -> importInTransaction(snapshot, bundle, releaseId));
    }

    private PublicBundleImportResult importInTransaction(
            RuntimeContentSnapshot snapshot, ValidatedBundle bundle, String releaseId) {
        PublicBundleImportResult existing = findExistingRelease(snapshot, releaseId);
        if (existing != null) {
            return existing;
        }
        jdbcTemplate.update(INSERT_RELEASE_SQL, releaseId, snapshot.getContentVersion(),
                snapshot.getSchemaVersion(), canonicalHash(snapshot.getRuntimeBundleHash(), "runtime bundle hash"));
        insertSubjects(snapshot, releaseId);
        insertClaims(snapshot.getClaims(), releaseId);
        insertEvidence(snapshot.getApprovedEvidence(), releaseId);
        insertLinks(snapshot.getClaimEvidenceLinks(), releaseId);
        insertCapabilities(snapshot.getClaims(), releaseId);
        insertDocuments(
                bundle.getRetrievalContent().getDocuments(),
                bundle.getVectors(),
                bundle.getDocumentSubjectIds(),
                bundle.getRetrievalContent().getManifest().getEmbeddingModelId(),
                releaseId);
        EncodedRuntimeSnapshot encoded = snapshotCodec.encode(snapshot);
        jdbcTemplate.update(
                INSERT_RUNTIME_SNAPSHOT_SQL,
                releaseId,
                encoded.getPayload(),
                encoded.getChecksum());
        return new PublicBundleImportResult(releaseId, snapshot.getContentVersion(), "VERIFIED");
    }

    private PublicBundleImportResult findExistingRelease(
            RuntimeContentSnapshot snapshot, String releaseId) {
        try {
            return jdbcTemplate.queryForObject(
                    FIND_RELEASE_SQL,
                    (resultSet, rowNumber) -> new PublicBundleImportResult(
                            resultSet.getString(1),
                            resultSet.getString(2),
                            resultSet.getString(3)),
                    releaseId,
                    snapshot.getContentVersion(),
                    canonicalHash(snapshot.getRuntimeBundleHash(), "runtime bundle hash"));
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void insertSubjects(RuntimeContentSnapshot snapshot, String releaseId) {
        int displayOrder = 0;
        for (ProjectProfile project : snapshot.getProjects()) {
            displayOrder++;
            jdbcTemplate.update(INSERT_SUBJECT_SQL, releaseId, project.getId(), "PROJECT", project.getSlug(),
                    project.getTitle(), project.getSummary(), project.getCareerTrack().name(),
                    project.getContributionType().name(), null, null, "/projects/" + project.getSlug(), displayOrder);
            jdbcTemplate.update(INSERT_PROJECT_SQL, releaseId, project.getId(), project.getStatus().name(),
                    project.getProjectNature().name(), project.getDisplayTier().name(), jsonArray(project.getFeaturedCaseIds()));
        }
        for (CaseStudy caseStudy : snapshot.getCases()) {
            displayOrder++;
            jdbcTemplate.update(INSERT_SUBJECT_SQL, releaseId, caseStudy.getId(), "CASE", caseStudy.getSlug(),
                    caseStudy.getTitle(), caseStudy.getSummary(), null, caseStudy.getContributionType().name(),
                    caseStudy.getAchievementStatus().name(), null, "/cases/" + caseStudy.getSlug(), displayOrder);
            jdbcTemplate.update(INSERT_CASE_SQL, releaseId, caseStudy.getId(), caseStudy.getProjectId(),
                    caseStudy.getType().name(), jsonArray(caseStudy.getCollectionIds()));
        }
    }

    private void insertClaims(List<Claim> claims, String releaseId) {
        int displayOrder = 0;
        for (Claim claim : claims) {
            displayOrder++;
            jdbcTemplate.update(INSERT_CLAIM_SQL, releaseId, claim.getId(), claim.getSubjectId(),
                    claim.getSubjectType().name(), claim.getCategory().name(), claim.getStatement(),
                    claim.getDetail(), claim.getAchievementStatus().name(),
                    claim.getContributionType().name(), claim.getVerificationBasis().name(),
                    claim.getVerificationStatus().name(), claim.getMateriality().name(),
                    jsonArray(claim.getTopics()), displayOrder);
        }
    }

    private void insertEvidence(List<EvidenceRecord> evidence, String releaseId) {
        for (EvidenceRecord item : evidence) {
            jdbcTemplate.update(INSERT_EVIDENCE_SQL, releaseId, item.getId(), item.getCode(),
                    item.getType().name(), item.getTitle(), item.getSummary());
        }
    }

    private void insertLinks(List<ClaimEvidenceLink> links, String releaseId) {
        for (ClaimEvidenceLink link : links) {
            jdbcTemplate.update(INSERT_LINK_SQL, releaseId, link.getClaimId(), link.getEvidenceId(),
                    link.getSupportType().name());
        }
    }

    private void insertCapabilities(List<Claim> claims, String releaseId) {
        for (Claim claim : claims) {
            if (claim.getVerificationStatus() != ClaimVerificationStatus.VERIFIED) {
                continue;
            }
            for (String topic : claim.getTopics()) {
                String capabilityCode = topic == null
                        ? "" : topic.trim().toUpperCase(Locale.ROOT);
                if (!capabilityCode.isEmpty()) {
                    jdbcTemplate.update(
                            INSERT_CAPABILITY_SQL,
                            releaseId,
                            claim.getSubjectId(),
                            capabilityCode,
                            claim.getId());
                }
            }
        }
    }

    private void insertDocuments(
            List<RagDocument> documents, Map<String, float[]> vectors, Map<String, String> documentSubjectIds,
            String embeddingModel, String releaseId) {
        for (RagDocument document : documents) {
            jdbcTemplate.update(INSERT_DOCUMENT_SQL, releaseId, document.getChunkId(), documentSubjectIds.get(document.getChunkId()),
                    document.getClaimIds().getFirst(), document.getText(), vectorLiteral(vectors.get(document.getChunkId())),
                    embeddingModel, canonicalHash(document.getContentHash(), "retrieval document content hash"));
        }
    }

    private ValidatedBundle validate(RuntimeContentSnapshot snapshot) {
        require(snapshot != null, "runtime content snapshot is required");
        requireStableValue(snapshot.getSchemaVersion(), "schema version");
        requireStableValue(snapshot.getContentVersion(), "content version");
        requireStableValue(snapshot.getRuntimeBundleHash(), "runtime bundle hash");
        Set<String> subjectIds = new HashSet<>();
        Map<String, String> subjectKinds = new HashMap<>();
        Map<String, String> projectBySlug = new HashMap<>();
        Map<String, String> caseBySlug = new HashMap<>();
        Map<String, CaseStudy> casesById = new HashMap<>();
        Set<String> projectSlugs = new HashSet<>();
        Set<String> caseSlugs = new HashSet<>();
        Set<String> collectionIds = new HashSet<>();
        for (ProjectProfile project : snapshot.getProjects()) {
            addUnique(subjectIds, project.getId(), "subject");
            subjectKinds.put(project.getId(), "PROJECT");
            addUnique(projectSlugs, project.getSlug(), "project slug");
            projectBySlug.put(project.getSlug(), project.getId());
        }
        for (CaseStudy caseStudy : snapshot.getCases()) {
            addUnique(subjectIds, caseStudy.getId(), "subject");
            subjectKinds.put(caseStudy.getId(), "CASE");
            addUnique(caseSlugs, caseStudy.getSlug(), "case slug");
            caseBySlug.put(caseStudy.getSlug(), caseStudy.getId());
            casesById.put(caseStudy.getId(), caseStudy);
            require(caseStudy.getProjectId() == null || "PROJECT".equals(subjectKinds.get(caseStudy.getProjectId())),
                    "case project relation is dangling");
        }
        for (CaseCollection collection : snapshot.getCollections()) {
            addUnique(collectionIds, collection.getId(), "case collection");
        }
        validateFeaturedCases(snapshot.getProjects(), casesById);
        validateCaseCollections(snapshot.getCases(), collectionIds);
        Map<String, Claim> claims = uniqueClaims(snapshot.getClaims(), subjectKinds);
        Set<String> evidenceIds = uniqueEvidence(snapshot.getApprovedEvidence());
        validateDeclaredRelations(snapshot, claims.keySet(), evidenceIds);
        validateLinks(snapshot.getClaimEvidenceLinks(), claims.keySet(), evidenceIds);
        RuntimeRetrievalContent retrieval = snapshot.getRetrievalContent()
                .orElseThrow(() -> new IllegalArgumentException("retrieval content is required"));
        require(retrieval.getManifest().getDimension() == 512 && retrieval.getVectorIndex().getDimension() == 512,
                "retrieval vector dimension must be 512");
        Map<String, float[]> vectors = retrieval.getVectorIndex().getVectors();
        Map<String, String> documentSubjectIds = validateDocuments(
                retrieval.getDocuments(), vectors, claims, projectBySlug, caseBySlug);
        return new ValidatedBundle(retrieval, vectors, documentSubjectIds);
    }

    private Map<String, Claim> uniqueClaims(List<Claim> values, Map<String, String> subjectKinds) {
        Map<String, Claim> claims = new HashMap<>();
        for (Claim claim : values) {
            requireStableValue(claim.getId(), "claim");
            require(claims.putIfAbsent(claim.getId(), claim) == null, "duplicate claim stable ID");
            require(claim.getSubjectType() == ClaimSubjectType.PROJECT || claim.getSubjectType() == ClaimSubjectType.CASE,
                    "claim subject must be PROJECT or CASE");
            require(subjectKinds.containsKey(claim.getSubjectId()), "claim subject relation is dangling");
            require(subjectKinds.get(claim.getSubjectId()).equals(claim.getSubjectType().name()),
                    "claim subject kind does not match subject");
        }
        return claims;
    }

    private Set<String> uniqueEvidence(List<EvidenceRecord> values) {
        Set<String> ids = new HashSet<>();
        for (EvidenceRecord evidence : values) {
            addUnique(ids, evidence.getId(), "evidence");
            require(evidence.getPublicStatus() == EvidenceStatus.APPROVED,
                    "only APPROVED evidence may be imported");
        }
        return ids;
    }

    private void validateDeclaredRelations(
            RuntimeContentSnapshot snapshot, Set<String> claimIds, Set<String> evidenceIds) {
        for (ProjectProfile project : snapshot.getProjects()) {
            validateIds(project.getClaimIds(), claimIds, "project claim relation is dangling");
            validateIds(project.getEvidenceIds(), evidenceIds, "project evidence relation is dangling");
        }
        for (CaseStudy caseStudy : snapshot.getCases()) {
            validateIds(caseStudy.getClaimIds(), claimIds, "case claim relation is dangling");
            validateIds(caseStudy.getEvidenceIds(), evidenceIds, "case evidence relation is dangling");
        }
    }

    private void validateLinks(List<ClaimEvidenceLink> links, Set<String> claims, Set<String> evidence) {
        Set<String> linkIds = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (ClaimEvidenceLink link : links) {
            addUnique(linkIds, link.getId(), "claim evidence link");
            require(claims.contains(link.getClaimId()), "claim evidence claim relation is dangling");
            require(evidence.contains(link.getEvidenceId()), "claim evidence evidence relation is dangling");
            require(pairs.add(link.getClaimId() + "\u0000" + link.getEvidenceId()),
                    "duplicate claim evidence relation");
            require(link.getReviewStatus() == ReviewStatus.APPROVED,
                    "only APPROVED claim evidence links may be imported");
        }
    }

    private void validateFeaturedCases(List<ProjectProfile> projects, Map<String, CaseStudy> casesById) {
        for (ProjectProfile project : projects) {
            for (String featuredCaseId : project.getFeaturedCaseIds()) {
                CaseStudy caseStudy = casesById.get(featuredCaseId);
                require(caseStudy != null, "featured case relation is dangling");
                require(project.getId().equals(caseStudy.getProjectId()),
                        "featured case does not belong to project");
            }
        }
    }

    private void validateCaseCollections(List<CaseStudy> cases, Set<String> collectionIds) {
        for (CaseStudy caseStudy : cases) {
            validateIds(caseStudy.getCollectionIds(), collectionIds, "case collection relation is dangling");
        }
    }

    private Map<String, String> validateDocuments(
            List<RagDocument> documents, Map<String, float[]> vectors, Map<String, Claim> claims,
            Map<String, String> projectBySlug, Map<String, String> caseBySlug) {
        Set<String> ids = new HashSet<>();
        Map<String, String> subjectIds = new HashMap<>();
        for (RagDocument document : documents) {
            addUnique(ids, document.getChunkId(), "retrieval document");
            requireStableValue(document.getContentVersion(), "retrieval document content version");
            requireStableValue(document.getContentHash(), "retrieval document content hash");
            require(document.getClaimIds().size() == 1, "retrieval document must reference exactly one claim");
            String claimId = document.getClaimIds().getFirst();
            Claim claim = claims.get(claimId);
            require(claim != null, "retrieval document claim relation is dangling");
            String subjectId = subjectId(document, projectBySlug, caseBySlug);
            require(subjectId.equals(claim.getSubjectId()), "retrieval document subject does not match claim");
            subjectIds.put(document.getChunkId(), subjectId);
            float[] vector = vectors.get(document.getChunkId());
            require(vector != null && vector.length == 512, "retrieval vector dimension must be 512");
        }
        require(vectors.keySet().equals(ids), "retrieval vectors must match retrieval document stable IDs");
        return subjectIds;
    }

    private String subjectId(RagDocument document, Map<String, String> projectBySlug, Map<String, String> caseBySlug) {
        require(document.getProjectSlugs().size() + document.getCaseSlugs().size() == 1,
                "retrieval document must reference exactly one subject");
        if (!document.getProjectSlugs().isEmpty()) {
            String subjectId = projectBySlug.get(document.getProjectSlugs().getFirst());
            require(subjectId != null, "retrieval document project relation is dangling");
            return subjectId;
        }
        String subjectId = caseBySlug.get(document.getCaseSlugs().getFirst());
        require(subjectId != null, "retrieval document case relation is dangling");
        return subjectId;
    }

    private void validateIds(List<String> ids, Set<String> knownIds, String message) {
        for (String id : ids) {
            require(knownIds.contains(id), message);
        }
    }

    private void addUnique(Set<String> values, String value, String label) {
        requireStableValue(value, label);
        require(values.add(value), "duplicate " + label + " stable ID");
    }

    private void requireStableValue(String value, String label) {
        require(value != null && !value.isBlank(), label + " stable ID is required");
    }

    private void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[index]));
        }
        return builder.append(']').toString();
    }

    private String jsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('"').append(values.get(index).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return builder.append(']').toString();
    }

    private String canonicalHash(String value, String label) {
        String normalized = value != null && value.startsWith("sha256:") ? value.substring(7) : value;
        require(normalized != null && normalized.matches("[0-9a-fA-F]{64}"), label + " must be a SHA-256 hash");
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static final class ValidatedBundle {
        private final RuntimeRetrievalContent retrievalContent;
        private final Map<String, float[]> vectors;
        private final Map<String, String> documentSubjectIds;

        private ValidatedBundle(
                RuntimeRetrievalContent retrievalContent, Map<String, float[]> vectors,
                Map<String, String> documentSubjectIds) {
            this.retrievalContent = java.util.Objects.requireNonNull(
                    retrievalContent, "retrievalContent");
            this.vectors = copyVectors(vectors);
            this.documentSubjectIds = Map.copyOf(documentSubjectIds);
        }

        private RuntimeRetrievalContent getRetrievalContent() {
            return retrievalContent;
        }

        private Map<String, float[]> getVectors() {
            return copyVectors(vectors);
        }

        private Map<String, String> getDocumentSubjectIds() {
            return documentSubjectIds;
        }

        private static Map<String, float[]> copyVectors(Map<String, float[]> source) {
            Map<String, float[]> copied = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, float[]> entry : source.entrySet()) {
                copied.put(entry.getKey(), entry.getValue().clone());
            }
            return java.util.Collections.unmodifiableMap(copied);
        }
    }
}
