package com.portfolio.agent.portfolio.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeVectorIndex;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

class PublicBundleDatabaseImporterTest {

    @Test
    void rejectsUnapprovedEvidenceBeforeStartingATransaction() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(jdbcTemplate, transactions);

        RuntimeContentSnapshot snapshot = snapshot("PENDING");

        assertThatThrownBy(() -> importer.importBundle(snapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED");

        verifyNoInteractions(jdbcTemplate, transactions);
    }

    @Test
    void importsAValidatedBundleUnderOneReleaseIdAndMarksItVerified() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(jdbcTemplate, transactions);

        PublicBundleImportResult result = importer.importBundle(snapshot("APPROVED"));

        assertThat(result.getReleaseId()).isNotBlank();
        assertThat(result.getReleaseStatus()).isEqualTo("VERIFIED");
        verify(transactions).execute(any());
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void returnsTheExistingReleaseWithoutWritingDuplicateRows() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionOperations transactions = immediateTransactions();
        when(jdbcTemplate.queryForObject(
                anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class)))
                .thenReturn(new PublicBundleImportResult(
                        "1ebddf24-991a-340a-a7c5-4457a146afc7",
                        "2026-07-30.1",
                        "VERIFIED"));
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(
                jdbcTemplate, transactions);

        PublicBundleImportResult result = importer.importBundle(snapshot("APPROVED"));

        assertThat(result.getReleaseStatus()).isEqualTo("VERIFIED");
        verify(jdbcTemplate).queryForObject(
                anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void storesTheCompatibilityPayloadAndItsChecksumInsideTheImportTransaction() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(
                jdbcTemplate, immediateTransactions(),
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules()));

        PublicBundleImportResult result = importer.importBundle(snapshot("APPROVED"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), parameters.capture());
        int payloadWrite = java.util.stream.IntStream.range(0, sql.getAllValues().size())
                .filter(index -> sql.getAllValues().get(index).contains("INSERT INTO release_runtime_snapshot"))
                .findFirst()
                .orElseThrow();
        Object[] values = parameters.getAllValues().get(payloadWrite);
        assertThat(values[0]).isEqualTo(result.getReleaseId());
        assertThat(values[1].toString()).contains("\"runtimeBundleHash\"");
        assertThat(values[2].toString()).matches("[0-9a-f]{64}");
    }

    @Test
    void rollsBackNormalizedRowsWhenCompatibilityPayloadInsertFails() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("INSERT INTO release_runtime_snapshot")) {
                throw new DataAccessResourceFailureException("payload insert failed");
            }
            return 1;
        });
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(
                jdbcTemplate, new TransactionTemplate(transactionManager),
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules()));

        assertThatThrownBy(() -> importer.importBundle(snapshot("APPROVED")))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void importsTheActualFiveFortyNineSeventyNineFiftyNineSeventyNineBundleUnderOneReleaseId()
            throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(
                jdbcTemplate, immediateTransactions());
        RuntimeContentSnapshot snapshot = actualBundleSnapshot();

        PublicBundleImportResult result = importer.importBundle(snapshot);

        assertThat(snapshot.getProjects()).hasSize(5);
        assertThat(snapshot.getCases()).hasSize(49);
        assertThat(snapshot.getClaims()).hasSize(79);
        assertThat(snapshot.getApprovedEvidence()).hasSize(59);
        assertThat(snapshot.getClaimEvidenceLinks()).hasSize(79);
        assertThat(snapshot.getRetrievalContent().orElseThrow().getDocuments()).hasSize(79);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeast(1)).update(sql.capture(), parameters.capture());
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO portfolio_subject"))
                .hasSize(54);
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO project_profile"))
                .hasSize(5);
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO case_study"))
                .hasSize(49);
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO claim\n"))
                .hasSize(79);
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO evidence\n"))
                .hasSize(59);
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO claim_evidence_link"))
                .hasSize(79);
        assertThat(sql.getAllValues()).filteredOn(value -> value.contains("INSERT INTO retrieval_document"))
                .hasSize(79);
        assertThat(parameters.getAllValues())
                .allSatisfy(values -> assertThat(values[0]).isEqualTo(result.getReleaseId()));
    }

    @Test
    void rejectsDanglingFeaturedCaseBeforeStartingATransaction() throws Exception {
        RuntimeContentSnapshot source = actualBundleSnapshot();
        ProjectProfile project = source.getProjects().getFirst();
        ProjectProfile altered = new ProjectProfile(project.getId(), project.getCode(), project.getSlug(),
                project.getTitle(), project.getSummary(), project.getBackground(), project.getResponsibilities(),
                project.getSolution(), project.getKeyDecisions(), project.getTechnologies(), project.getVerification(),
                project.getOutcome(), project.getHandoff(), project.getStatus(), project.getContributionType(),
                project.getCareerTrack(), project.getProjectNature(), project.getDisplayTier(), List.of("case-missing"),
                project.getClaimIds(), project.getEvidenceIds(), project.getTimelineEventIds());
        RuntimeContentSnapshot alteredSnapshot = replace(source, replaceFirst(source.getProjects(), altered), source.getCases(),
                source.getCollections(), source.getClaimEvidenceLinks(), source.getRetrievalContent().orElseThrow());

        assertRejectedWithoutTransaction(alteredSnapshot, "featured case");
    }

    @Test
    void rejectsDanglingCaseCollectionBeforeStartingATransaction() throws Exception {
        RuntimeContentSnapshot source = actualBundleSnapshot();
        CaseStudy caseStudy = source.getCases().getFirst();
        CaseStudy altered = new CaseStudy(caseStudy.getId(), caseStudy.getCode(), caseStudy.getSlug(),
                caseStudy.getType(), caseStudy.getTitle(), caseStudy.getSummary(), caseStudy.getProblem(),
                caseStudy.getActions(), caseStudy.getDecisions(), caseStudy.getVerification(), caseStudy.getOutcome(),
                caseStudy.getLimitations(), caseStudy.getAchievementStatus(), caseStudy.getContributionType(),
                caseStudy.getProjectId(), List.of("collection-missing"), caseStudy.getClaimIds(),
                caseStudy.getEvidenceIds(), caseStudy.getTimelineEventIds(), caseStudy.getQuestionPresetIds());
        RuntimeContentSnapshot alteredSnapshot = replace(source, source.getProjects(), replaceFirst(source.getCases(), altered),
                source.getCollections(), source.getClaimEvidenceLinks(), source.getRetrievalContent().orElseThrow());

        assertRejectedWithoutTransaction(alteredSnapshot, "case collection");
    }

    @Test
    void rejectsUnapprovedClaimEvidenceLinkBeforeStartingATransaction() throws Exception {
        RuntimeContentSnapshot source = actualBundleSnapshot();
        ClaimEvidenceLink link = source.getClaimEvidenceLinks().getFirst();
        ClaimEvidenceLink altered = new ClaimEvidenceLink(link.getId(), link.getClaimId(), link.getEvidenceId(),
                link.getSupportType(), link.getScope(), com.portfolio.agent.portfolio.domain.ReviewStatus.REJECTED);
        RuntimeContentSnapshot alteredSnapshot = replace(source, source.getProjects(), source.getCases(),
                source.getCollections(), replaceFirst(source.getClaimEvidenceLinks(), altered),
                source.getRetrievalContent().orElseThrow());

        assertRejectedWithoutTransaction(alteredSnapshot, "APPROVED");
    }

    @Test
    void rejectsDuplicateStableIdAndWrongVectorBeforeStartingATransaction() throws Exception {
        RuntimeContentSnapshot source = actualBundleSnapshot();
        RuntimeContentSnapshot duplicateProject = replace(source,
                List.of(source.getProjects().getFirst(), source.getProjects().getFirst()), source.getCases(),
                source.getCollections(), source.getClaimEvidenceLinks(), source.getRetrievalContent().orElseThrow());
        assertRejectedWithoutTransaction(duplicateProject, "duplicate subject stable ID");

        RuntimeRetrievalContent retrieval = source.getRetrievalContent().orElseThrow();
        Map<String, float[]> vectors = new java.util.LinkedHashMap<>(retrieval.getVectorIndex().getVectors());
        vectors.put(retrieval.getDocuments().getFirst().getChunkId(), new float[511]);
        RuntimeRetrievalContent wrongVector = new RuntimeRetrievalContent(retrieval.getManifest(), retrieval.getDocuments(),
                retrieval.getKeywordIndex(), new RuntimeVectorIndex(512, vectors));
        assertRejectedWithoutTransaction(replace(source, source.getProjects(), source.getCases(),
                source.getCollections(), source.getClaimEvidenceLinks(), wrongVector), "dimension must be 512");
    }

    @Test
    void rollsBackTheWholeImportWhenDatabaseWriteFails() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1)
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(
                jdbcTemplate, new TransactionTemplate(transactionManager));

        assertThatThrownBy(() -> importer.importBundle(snapshot("APPROVED")))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(transactionManager).rollback(transactionStatus);
    }

    private RuntimeContentSnapshot snapshot(String evidenceStatus) throws Exception {
        PortfolioSnapshot content = new ObjectMapper().findAndRegisterModules().readValue("""
                {"schemaVersion":"2.0","contentVersion":"2026-07-30.1",
                "publishedAt":"2026-07-30T12:00:00+08:00",
                "owner":{"name":"","role":"Java backend intern","summary":"Public","githubUrl":null,"email":null,"resumeUrl":null},
                "projects":[{"id":"project-1","code":"P-01","slug":"sql-audit","title":"SQL Audit","summary":"Summary","background":"Background","responsibilities":["Owner"],"solution":"Solution","keyDecisions":["Decision"],"technologies":["Java"],"verification":["Tested"],"outcome":"Delivered","handoff":"Handoff","status":"DELIVERED","contributionType":"PRIMARY","claimIds":["claim-1"],"evidenceIds":["evidence-1"],"timelineEventIds":[]}],
                "cases":[],
                "claims":[{"id":"claim-1","subjectType":"PROJECT","subjectId":"project-1","category":"OUTCOME","statement":"Delivered","detail":"Reviewed","achievementStatus":"DELIVERED","contributionType":"PRIMARY","verificationBasis":"EVIDENCE_SUPPORTED","verificationStatus":"VERIFIED","materiality":"KEY","topics":["delivery"],"audiencePriorities":{"INTERVIEWER":100}}],
                "evidence":[{"id":"evidence-1","code":"E-01","title":"Delivery evidence","type":"DOCUMENT","periodStart":"2026-07-01","periodEnd":"2026-07-20","sourceCount":1,"summary":"Reviewed evidence","publicStatus":"%s","rawContentPublic":false}],
                "claimEvidenceLinks":[{"id":"link-1","claimId":"claim-1","evidenceId":"evidence-1","supportType":"DIRECT","scope":"Delivery only","reviewStatus":"APPROVED"}],
                "timelineEvents":[],"questionPresets":[]}
                """.formatted(evidenceStatus), PortfolioSnapshot.class);
        RagDocument document = new RagDocument("chunk-claim-1", "2026-07-30.1", List.of("sql-audit"),
                List.of(), List.of("claim-1"), "Public retrieval content", List.of("delivery"),
                null, null, "a".repeat(64));
        float[] embedding = new float[512];
        embedding[0] = 1.0F;
        RetrievalManifest manifest = new RetrievalManifest("strategy", "normalization", "policy",
                "model", "artifact", 512, 100, "unit", "cosine", 1, "b".repeat(64), "keyword", "vector");
        RuntimeRetrievalContent retrieval = new RuntimeRetrievalContent(manifest, List.of(document),
                new RuntimeKeywordIndex(1, 1.0, List.of(), Map.of()),
                new RuntimeVectorIndex(512, Map.of(document.getChunkId(), embedding)));
        return new RuntimeContentSnapshot(content, "c".repeat(64), Instant.parse("2026-07-30T04:00:00Z"), retrieval);
    }

    private TransactionOperations immediateTransactions() {
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }

    private void assertRejectedWithoutTransaction(RuntimeContentSnapshot snapshot, String message) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        PublicBundleDatabaseImporter importer = new PublicBundleDatabaseImporter(jdbcTemplate, transactions);

        assertThatThrownBy(() -> importer.importBundle(snapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);

        verifyNoInteractions(jdbcTemplate, transactions);
    }

    private RuntimeContentSnapshot actualBundleSnapshot() throws Exception {
        PublicBundleLoader loader = new PublicBundleLoader(new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(), Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        for (String name : List.of("manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        return loader.load(files);
    }

    private byte[] readResource(String name) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertThat(stream).as("resource %s", name).isNotNull();
        try (InputStream input = stream) {
            return input.readAllBytes();
        }
    }

    private RuntimeContentSnapshot replace(
            RuntimeContentSnapshot source, List<ProjectProfile> projects, List<CaseStudy> cases,
            List<CaseCollection> collections, List<ClaimEvidenceLink> links, RuntimeRetrievalContent retrieval) {
        PortfolioSnapshot content = new PortfolioSnapshot(source.getSchemaVersion(), source.getContentVersion(),
                source.getPublishedAt(), source.getOwner(), projects, cases, collections, source.getClaims(), links,
                source.getQuestions(), source.getApprovedEvidence(), source.getTimeline());
        return new RuntimeContentSnapshot(content, source.getRuntimeBundleHash(), source.getLoadedAt(), retrieval);
    }

    private <T> List<T> replaceFirst(List<T> values, T replacement) {
        List<T> copied = new ArrayList<>(values);
        copied.set(0, replacement);
        return copied;
    }
}
