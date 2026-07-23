package com.portfolio.agent.portfolio.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import com.portfolio.agent.portfolio.repository.file.VectorIndexCodec;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalBundleCompilerTest {

    @Test
    void compilesCanonicalDocumentsAndDerivedIndexesByteForByteDeterministically()
            throws Exception {
        PortfolioSnapshot snapshot = snapshot();
        float[] unitVector = new float[512];
        unitVector[0] = 1.0f;
        RetrievalBundleCompiler compiler = new RetrievalBundleCompiler(
                ignored -> unitVector,
                "sha256:embedding-artifact",
                512);

        RetrievalCompilation first = compiler.compile(
                snapshot, LocalDate.of(2026, 7, 22));
        RetrievalCompilation second = compiler.compile(
                snapshot, LocalDate.of(2026, 7, 22));

        assertThat(first.getRagDocuments()).isEqualTo(second.getRagDocuments());
        assertThat(first.getKeywordIndex()).isEqualTo(second.getKeywordIndex());
        assertThat(first.getVectorIndex()).isEqualTo(second.getVectorIndex());
        assertThat(first.getManifest().getChunkCount()).isEqualTo(1);
        assertThat(first.getManifest().getChunkSetHash())
                .isEqualTo(BundleHashCalculator.sha256(first.getRagDocuments()));
        assertThat(new VectorIndexCodec().decode(first.getVectorIndex(), 512)
                .getVectors().keySet()).containsExactly("chunk-claim-1");
    }

    private PortfolioSnapshot snapshot() throws Exception {
        return new ObjectMapper().findAndRegisterModules().readValue("""
                {"schemaVersion":"2.0","contentVersion":"2026-07-21.1",
                "publishedAt":"2026-07-21T16:30:00+08:00",
                "owner":{"name":"","role":"Java backend intern","summary":"Public","githubUrl":null,"email":null,"resumeUrl":null},
                "projects":[{"id":"project-1","code":"P-01","slug":"sql-audit","title":"SQL Audit","summary":"Summary","background":"Background","responsibilities":["Owner"],"solution":"Solution","keyDecisions":["Decision"],"technologies":["Java"],"verification":["Tested"],"outcome":"Delivered","handoff":"Handoff","status":"DELIVERED","contributionType":"PRIMARY","claimIds":["claim-1"],"evidenceIds":["evidence-1"],"timelineEventIds":["timeline-1"]}],
                "cases":[],
                "claims":[{"id":"claim-1","subjectType":"PROJECT","subjectId":"project-1","category":"OUTCOME","statement":"Delivered","detail":"Reviewed","achievementStatus":"DELIVERED","contributionType":"PRIMARY","verificationBasis":"EVIDENCE_SUPPORTED","verificationStatus":"VERIFIED","materiality":"KEY","topics":["delivery","audit"],"audiencePriorities":{"INTERVIEWER":100}}],
                "evidence":[{"id":"evidence-1","code":"E-01","title":"Delivery evidence","type":"DOCUMENT","periodStart":"2026-07-01","periodEnd":"2026-07-20","sourceCount":1,"summary":"Reviewed evidence","publicStatus":"APPROVED","rawContentPublic":false}],
                "claimEvidenceLinks":[{"id":"link-1","claimId":"claim-1","evidenceId":"evidence-1","supportType":"DIRECT","scope":"Delivery only","reviewStatus":"APPROVED"}],
                "timelineEvents":[{"id":"timeline-1","dateLabel":"2026.07","title":"Delivery","problem":"Problem","action":"Action","impact":"Impact","projectIds":["project-1"],"caseIds":[],"claimIds":["claim-1"],"evidenceIds":["evidence-1"]}],
                "questionPresets":[{"id":"question-1","text":"Introduce SQL Audit","aliases":["SQL Audit"],"audiences":["INTERVIEWER"],"projectIds":["project-1"],"caseIds":[],"topics":["OVERVIEW"],"preferredClaimCategories":["OUTCOME"],"placements":["HOME"],"deterministicEntry":true,"displayOrder":10}]}
                """, PortfolioSnapshot.class);
    }
}
