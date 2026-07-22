package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicBundleLoaderTest {

    private final PublicBundleLoader loader = new PublicBundleLoader(
            new ObjectMapper().findAndRegisterModules(),
            new PortfolioSnapshotValidator(),
            Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void loadsClosedBundleAndDerivesRuntimeHashWithoutCycles() {
        Map<String, byte[]> bundle = validBundle();

        RuntimeContentSnapshot snapshot = loader.load(bundle);

        assertThat(snapshot.getContentVersion()).isEqualTo("2026-07-21.1");
        assertThat(snapshot.getPublishedAt().toInstant())
                .isEqualTo("2026-07-21T08:30:00Z");
        assertThat(snapshot.getRuntimeBundleHash()).startsWith("sha256:");
        assertThat(snapshot.getLoadedAt()).isEqualTo("2026-07-21T09:00:00Z");
        assertThat(snapshot.getRetrievalContent()).isEmpty();
    }

    @Test
    void loadsCompleteRetrievalBundleIntoTheSameRuntimeSnapshot() {
        Map<String, byte[]> bundle = validRetrievalBundle();

        RuntimeContentSnapshot snapshot = loader.load(bundle);

        assertThat(snapshot.getRetrievalContent()).isPresent();
        assertThat(snapshot.getRetrievalContent().orElseThrow().getDocuments())
                .extracting("chunkId")
                .containsExactly("chunk-sql-audit-delivery");
        assertThat(snapshot.getRetrievalContent().orElseThrow().getManifest()
                .getEmbeddingModelId()).isEqualTo("BAAI/bge-small-zh-v1.5");
        assertThat(snapshot.getRetrievalContent().orElseThrow().getKeywordIndex()
                .getDocumentCount()).isEqualTo(1);
        assertThat(snapshot.getRetrievalContent().orElseThrow().getVectorIndex()
                .getVectors()).containsKey("chunk-sql-audit-delivery");
    }

    @Test
    void rejectsPartialRetrievalBundle() {
        Map<String, byte[]> bundle = validRetrievalBundle();
        bundle.remove("vector-index.bin");

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("file set");
    }

    @Test
    void rejectsRetrievalChecksumMutation() {
        Map<String, byte[]> bundle = validRetrievalBundle();
        bundle.put("rag-documents.jsonl", new String(
                bundle.get("rag-documents.jsonl"), StandardCharsets.UTF_8)
                .replace("Delivered", "Changed")
                .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void rejectsPayloadByteMutation() {
        Map<String, byte[]> bundle = validBundle();
        bundle.put("portfolio.json", new String(bundle.get("portfolio.json"), StandardCharsets.UTF_8)
                .replace("SQL Audit", "SQL Audit changed")
                .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void rejectsUnknownFile() {
        Map<String, byte[]> bundle = validBundle();
        bundle.put("keyword-index.json", "{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("file set");
    }

    @Test
    void rejectsCountMismatch() {
        Map<String, byte[]> bundle = validBundle();
        replaceAndRehashManifest(bundle, "\"projects\":1", "\"projects\":2");

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("counts");
    }

    @Test
    void rejectsCrossFileVersionMismatch() {
        Map<String, byte[]> bundle = validBundle();
        byte[] presentation = new String(bundle.get("presentation.json"), StandardCharsets.UTF_8)
                .replace("2026-07-21.1", "2026-07-22.1")
                .getBytes(StandardCharsets.UTF_8);
        bundle.put("presentation.json", presentation);
        rebuildChecksumsAndManifest(bundle);

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("contentVersion");
    }

    @Test
    void rejectsBundleRequiringNewerApplication() {
        Map<String, byte[]> bundle = validBundle();
        replaceAndRehashManifest(bundle,
                "\"minimumApplicationVersion\":\"0.1.0\"",
                "\"minimumApplicationVersion\":\"9.0.0\"");

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("minimumApplicationVersion");
    }

    @Test
    void rejectsTraversalInDeclaredFilename() {
        Map<String, byte[]> bundle = validBundle();
        replaceAndRehashManifest(bundle, "\"factsFile\":\"portfolio.json\"",
                "\"factsFile\":\"../portfolio.json\"");

        assertThatThrownBy(() -> loader.load(bundle))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("factsFile");
    }

    @Test
    void rejectsMissingChecksumAndUnknownManifestField() {
        Map<String, byte[]> missing = validBundle();
        missing.put("checksums.json", new String(missing.get("checksums.json"), StandardCharsets.UTF_8)
                .replaceAll(",\\\"presentation\\.json\\\":\\\"sha256:[^\\\"]+\\\"", "")
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> loader.load(missing)).isInstanceOf(InvalidPortfolioSnapshotException.class);

        Map<String, byte[]> unknown = validBundle();
        replaceAndRehashManifest(unknown, "\"counts\":{", "\"unknown\":true,\"counts\":{");
        assertThatThrownBy(() -> loader.load(unknown))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("Unrecognized field");
    }

    @Test
    void rejectsLegacyOrManifestOnlyFieldsInCanonicalPortfolioPayload() {
        Map<String, byte[]> publishedAt = validBundle();
        publishedAt.put("portfolio.json", new String(
                publishedAt.get("portfolio.json"), StandardCharsets.UTF_8)
                .replace("\"owner\":", "\"publishedAt\":\"2026-07-21T16:30:00+08:00\",\"owner\":")
                .getBytes(StandardCharsets.UTF_8));
        rebuildChecksumsAndManifest(publishedAt);

        assertThatThrownBy(() -> loader.load(publishedAt))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("portfolio.json field set");

        Map<String, byte[]> legacyAlias = validBundle();
        legacyAlias.put("portfolio.json", new String(
                legacyAlias.get("portfolio.json"), StandardCharsets.UTF_8)
                .replace("\"questionPresets\":", "\"questions\":")
                .getBytes(StandardCharsets.UTF_8));
        rebuildChecksumsAndManifest(legacyAlias);

        assertThatThrownBy(() -> loader.load(legacyAlias))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("portfolio.json field set");
    }

    @Test
    void derivesSameRuntimeHashFromSameExactBytes() {
        Map<String, byte[]> bundle = validBundle();
        assertThat(loader.load(bundle).getRuntimeBundleHash())
                .isEqualTo(loader.load(new LinkedHashMap<>(bundle)).getRuntimeBundleHash());
    }

    private Map<String, byte[]> validBundle() {
        Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("portfolio.json", portfolio().getBytes(StandardCharsets.UTF_8));
        bundle.put("presentation.json", presentation().getBytes(StandardCharsets.UTF_8));
        rebuildChecksumsAndManifest(bundle);
        return bundle;
    }

    private Map<String, byte[]> validRetrievalBundle() {
        Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("portfolio.json", portfolio().getBytes(StandardCharsets.UTF_8));
        bundle.put("presentation.json", presentation().getBytes(StandardCharsets.UTF_8));
        bundle.put("rag-documents.jsonl", ("{\"chunkId\":\"chunk-sql-audit-delivery\","
                + "\"contentVersion\":\"2026-07-21.1\",\"projectSlugs\":[\"sql-audit\"],"
                + "\"claimIds\":[\"claim-1\"],\"text\":\"Delivered\","
                + "\"topics\":[\"DELIVERY_STATUS\"],\"validFrom\":\"2026-07-01\","
                + "\"validUntil\":null,\"contentHash\":\"sha256:chunk\"}\n")
                .getBytes(StandardCharsets.UTF_8));
        bundle.put("keyword-index.json", keywordIndexBytes());
        float[] vector = new float[512];
        vector[0] = 1.0f;
        bundle.put("vector-index.bin", new VectorIndexCodec().encode(
                Map.of("chunk-sql-audit-delivery", vector), 512));
        rebuildChecksumsAndManifest(bundle);
        return bundle;
    }

    private byte[] keywordIndexBytes() {
        try {
            KeywordIndexFile index = new KeywordIndexFile(
                    "keyword-index-v1", "nfkc-bigram-v1", 1, 1.0,
                    java.util.List.of(new KeywordIndexFile.DocumentEntry(
                            "chunk-sql-audit-delivery", 1, Map.of("交付", 1))),
                    Map.of("交付", 1));
            return new ObjectMapper().writeValueAsBytes(index);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void rebuildChecksumsAndManifest(Map<String, byte[]> bundle) {
        String portfolioHash = BundleHashCalculator.sha256(bundle.get("portfolio.json"));
        String presentationHash = BundleHashCalculator.sha256(bundle.get("presentation.json"));
        String retrievalChecksums = "";
        if (bundle.containsKey("rag-documents.jsonl")) {
            retrievalChecksums = ",\"rag-documents.jsonl\":\""
                    + BundleHashCalculator.sha256(bundle.get("rag-documents.jsonl"))
                    + "\",\"keyword-index.json\":\""
                    + BundleHashCalculator.sha256(bundle.get("keyword-index.json"))
                    + "\",\"vector-index.bin\":\""
                    + BundleHashCalculator.sha256(bundle.get("vector-index.bin")) + "\"";
        }
        String checksums = "{\"schemaVersion\":\"2.0\",\"contentVersion\":\"2026-07-21.1\","
                + "\"files\":{\"portfolio.json\":\"" + portfolioHash + "\","
                + "\"presentation.json\":\"" + presentationHash + "\""
                + retrievalChecksums + "}}";
        bundle.put("checksums.json", checksums.getBytes(StandardCharsets.UTF_8));
        String candidateHash = BundleHashCalculator.candidatePayloadHash(bundle);
        String manifest = "{\"schemaVersion\":\"2.0\",\"contentVersion\":\"2026-07-21.1\","
                + "\"publishedAt\":\"2026-07-21T16:30:00+08:00\","
                + "\"builtAt\":\"2026-07-21T16:25:00+08:00\","
                + "\"minimumApplicationVersion\":\"0.1.0\","
                + "\"factsFile\":\"portfolio.json\",\"presentationFile\":\"presentation.json\","
                + "\"approvalId\":\"APR-2026-07-21-001\",\"approvalDigest\":\"sha256:approved\","
                + "\"candidatePayloadHash\":\"" + candidateHash + "\","
                + "\"checksumsFile\":\"checksums.json\",\"counts\":{"
                + "\"projects\":1,\"claims\":1,\"evidence\":1,\"claimEvidenceLinks\":1,"
                + "\"timelineEvents\":1,\"questionPresets\":1}"
                + retrievalManifest(bundle) + "}";
        bundle.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
    }

    private String retrievalManifest(Map<String, byte[]> bundle) {
        if (!bundle.containsKey("rag-documents.jsonl")) {
            return "";
        }
        return ",\"retrieval\":{\"strategyVersion\":\"hybrid-rag-v1\","
                + "\"normalizationVersion\":\"nfkc-bigram-v1\","
                + "\"retrievalPolicyVersion\":\"retrieval-policy-v1\","
                + "\"embeddingModelId\":\"BAAI/bge-small-zh-v1.5\","
                + "\"embeddingArtifactSha256\":\"sha256:model\",\"dimension\":512,"
                + "\"documentMaxTokens\":256,\"vectorNormalization\":\"L2\","
                + "\"similarity\":\"COSINE\",\"chunkCount\":1,\"chunkSetHash\":\""
                + BundleHashCalculator.sha256(bundle.get("rag-documents.jsonl")) + "\","
                + "\"keywordIndexFormatVersion\":\"keyword-index-v1\","
                + "\"vectorIndexFormatVersion\":\"vector-index-v1\"}";
    }

    private void replaceAndRehashManifest(Map<String, byte[]> bundle, String before, String after) {
        bundle.put("manifest.json", new String(bundle.get("manifest.json"), StandardCharsets.UTF_8)
                .replace(before, after).getBytes(StandardCharsets.UTF_8));
    }

    private String portfolio() {
        return """
                {"schemaVersion":"2.0","contentVersion":"2026-07-21.1",
                "owner":{"name":"","role":"Java backend intern","summary":"Reviewed public work","githubUrl":null,"email":null,"resumeUrl":null},
                "projects":[{"id":"project-1","code":"P-01","slug":"sql-audit","title":"SQL Audit","summary":"Summary","background":"Background","responsibilities":["Owner"],"solution":"Solution","keyDecisions":["Decision"],"technologies":["Java"],"verification":["Tested"],"outcome":"Delivered","handoff":"Handoff","status":"DELIVERED","contributionType":"PRIMARY","claimIds":["claim-1"],"evidenceIds":["evidence-1"],"timelineEventIds":["timeline-1"]}],
                "claims":[{"id":"claim-1","subjectType":"PROJECT","subjectId":"project-1","category":"OUTCOME","statement":"Delivered","detail":"Reviewed","achievementStatus":"DELIVERED","contributionType":"PRIMARY","verificationBasis":"EVIDENCE_SUPPORTED","verificationStatus":"VERIFIED","materiality":"KEY","topics":["DELIVERY"],"audiencePriorities":{"INTERVIEWER":100}}],
                "evidence":[{"id":"evidence-1","code":"E-01","title":"Delivery evidence","type":"DOCUMENT","periodStart":"2026-07-01","periodEnd":"2026-07-20","sourceCount":1,"summary":"Reviewed evidence","publicStatus":"APPROVED","rawContentPublic":false}],
                "claimEvidenceLinks":[{"id":"link-1","claimId":"claim-1","evidenceId":"evidence-1","supportType":"DIRECT","scope":"Delivery only","reviewStatus":"APPROVED"}],
                "timelineEvents":[{"id":"timeline-1","dateLabel":"2026.07","title":"Delivery","problem":"Problem","action":"Action","impact":"Impact","projectIds":["project-1"],"claimIds":["claim-1"],"evidenceIds":["evidence-1"]}],
                "questionPresets":[{"id":"question-1","text":"Introduce SQL Audit","aliases":["SQL Audit"],"audiences":["INTERVIEWER"],"projectIds":["project-1"],"topics":["OVERVIEW"],"preferredClaimCategories":["OUTCOME"],"placements":["HOME"],"deterministicEntry":true,"displayOrder":10}]}
                """;
    }

    private String presentation() {
        return """
                {"schemaVersion":"2.0","contentVersion":"2026-07-21.1","audiences":[],"homeSections":[],"metrics":[],"explorePortals":[],"footer":{}}
                """;
    }
}
