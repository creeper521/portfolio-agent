package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPostgresSelectionQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");

    private static final String ACTIVE_RELEASE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_RELEASE_ID = "22222222-2222-2222-2222-222222222222";

    private JdbcTemplate jdbcTemplate;
    private JdbcPostgresSelectionQuery query;

    @BeforeEach
    void setUp() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/public")
                .table("flyway_schema_history_public")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        query = new JdbcPostgresSelectionQuery(jdbcTemplate);
        seed();
    }

    @Test
    void actualFtsQualifiesBeforeTopNAndAggregatesOnlyTheActiveRelease() {
        List<PostgresSelectionRow> rows = query.searchFts(
                ACTIVE_RELEASE_ID,
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("JAVA"),
                        "needle",
                        3),
                3);

        assertThat(rows).extracting(PostgresSelectionRow::getSubjectId)
                .containsExactly("ELIGIBLE-01", "ELIGIBLE-02", "ELIGIBLE-03");
        assertThat(rows).allSatisfy(row -> assertThat(row.getTitle()).doesNotContain("other release"));
        assertThat(rows.getFirst().getEvidenceReferences()).hasSize(2);
    }

    @Test
    void emptyFtsQueryStillReturnsQualifiedCandidatesInStablePublicOrder() {
        List<PostgresSelectionRow> rows = query.searchFts(
                ACTIVE_RELEASE_ID,
                new SelectionTarget("JAVA_BACKEND", "INTERVIEWER", Set.of(), null, 3),
                3);

        assertThat(rows).extracting(PostgresSelectionRow::getSubjectId)
                .containsExactly("ELIGIBLE-01", "ELIGIBLE-02", "ELIGIBLE-03");
    }

    @Test
    void actualPgvectorAlsoQualifiesBeforeTopNAndPinsTheActiveRelease() {
        ActiveRelease activeRelease = query.activeRelease();
        List<PostgresSelectionRow> rows = query.searchVector(
                activeRelease.getReleaseId(),
                vector(1.0f, 0.0f),
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("JAVA"),
                        null,
                        3),
                3);

        assertThat(activeRelease.getReleaseId()).isEqualTo(ACTIVE_RELEASE_ID);
        assertThat(rows).extracting(PostgresSelectionRow::getSubjectId)
                .containsExactly("ELIGIBLE-01", "ELIGIBLE-02", "ELIGIBLE-03");
        assertThat(rows).allSatisfy(row -> assertThat(row.getTitle()).doesNotContain("other release"));
    }

    @Test
    void javaCareerRetrievesOwnedCasesAndProjectsWithoutCrossCareerOrCrossReleaseLeakage() {
        insertCase(ACTIVE_RELEASE_ID, "JAVA-CASE", "ELIGIBLE-01", "JAVA", "java case");
        insertProject(ACTIVE_RELEASE_ID, "OTHER-CAREER-PROJECT", "DATA_AI", "other owner");
        insertCase(ACTIVE_RELEASE_ID, "OTHER-CAREER-CASE", "OTHER-CAREER-PROJECT", "JAVA", "other career case");
        insertCase(OTHER_RELEASE_ID, "JAVA-CASE", "ELIGIBLE-01", "JAVA", "cross release case");

        List<PostgresSelectionRow> rows = query.searchFts(
                ACTIVE_RELEASE_ID,
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("JAVA"),
                        "java case",
                        5),
                10);

        assertThat(rows).extracting(PostgresSelectionRow::getSubjectId)
                .contains("ELIGIBLE-01", "JAVA-CASE")
                .doesNotContain("OTHER-CAREER-CASE");
        assertThat(rows).filteredOn(row -> row.getSubjectId().equals("JAVA-CASE"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCareerTrack()).isEqualTo("JAVA_BACKEND");
                    assertThat(row.getTitle()).isEqualTo("java case");
                });
    }

    @Test
    void standaloneCareerNeutralCaseRequiresAnExplicitMatchingCapability() {
        insertCase(ACTIVE_RELEASE_ID, "STANDALONE-JAVA", null, "JAVA", "standalone java");
        insertCase(ACTIVE_RELEASE_ID, "STANDALONE-RAG", null, "RAG", "standalone rag");

        List<PostgresSelectionRow> withCapability = query.searchFts(
                ACTIVE_RELEASE_ID,
                new SelectionTarget("JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 5),
                10);
        List<PostgresSelectionRow> withoutCapability = query.searchFts(
                ACTIVE_RELEASE_ID,
                new SelectionTarget("JAVA_BACKEND", "INTERVIEWER", Set.of(), null, 5),
                10);

        assertThat(withCapability).extracting(PostgresSelectionRow::getSubjectId)
                .contains("STANDALONE-JAVA")
                .doesNotContain("STANDALONE-RAG");
        assertThat(withCapability).filteredOn(row -> row.getSubjectId().equals("STANDALONE-JAVA"))
                .singleElement()
                .satisfies(row -> assertThat(row.getCareerTrack()).isNull());
        assertThat(withoutCapability).extracting(PostgresSelectionRow::getSubjectId)
                .doesNotContain("STANDALONE-JAVA", "STANDALONE-RAG");
    }

    private void seed() {
        insertRelease(ACTIVE_RELEASE_ID, "2026-07-30.1");
        insertRelease(OTHER_RELEASE_ID, "2026-07-30.2");
        for (int index = 1; index <= 13; index++) {
            insertSubject(
                    ACTIVE_RELEASE_ID,
                    "INELIGIBLE-" + String.format("%02d", index),
                    index,
                    "PENDING",
                    "needle needle needle",
                    vector(1.0f, 0.0f),
                    "ineligible");
        }
        for (int index = 1; index <= 3; index++) {
            insertSubject(
                    ACTIVE_RELEASE_ID,
                    "ELIGIBLE-" + String.format("%02d", index),
                    100 + index,
                    "VERIFIED",
                    "needle",
                    vector(0.5f, 0.5f + index * 0.01f),
                    "eligible");
        }
        insertAdditionalEvidence(ACTIVE_RELEASE_ID, "ELIGIBLE-01");
        insertSubject(
                OTHER_RELEASE_ID,
                "ELIGIBLE-01",
                1,
                "VERIFIED",
                "needle needle needle needle",
                vector(1.0f, 0.0f),
                "other release");
        jdbcTemplate.update("""
                INSERT INTO active_release (singleton, release_id)
                VALUES (true, CAST(? AS uuid))
                """, ACTIVE_RELEASE_ID);
    }

    private void insertRelease(String releaseId, String version) {
        jdbcTemplate.update("""
                INSERT INTO content_release
                    (release_id, release_version, schema_version, content_hash, status)
                VALUES (CAST(? AS uuid), ?, '4.0', ?, 'VERIFIED')
                """, releaseId, version, "a".repeat(64));
    }

    private void insertSubject(
            String releaseId,
            String subjectId,
            int displayOrder,
            String verificationStatus,
            String searchText,
            float[] embedding,
            String title) {
        String claimId = "CLAIM-" + subjectId;
        String evidenceId = "EVIDENCE-" + subjectId;
        jdbcTemplate.update("""
                INSERT INTO portfolio_subject
                    (release_id, stable_id, subject_kind, slug, title, summary,
                     career_track, public_route, display_order)
                VALUES (CAST(? AS uuid), ?, 'PROJECT', ?, ?, ?, 'JAVA_BACKEND', ?, ?)
                """,
                releaseId,
                subjectId,
                subjectId.toLowerCase(),
                title,
                "summary " + subjectId,
                "/projects/" + subjectId.toLowerCase(),
                displayOrder);
        jdbcTemplate.update("""
                INSERT INTO claim
                    (release_id, stable_id, subject_stable_id, subject_kind, category,
                     statement, verification_status, display_order)
                VALUES (CAST(? AS uuid), ?, ?, 'PROJECT', 'OUTCOME', 'statement', ?, 1)
                """, releaseId, claimId, subjectId, verificationStatus);
        jdbcTemplate.update("""
                INSERT INTO evidence
                    (release_id, stable_id, public_code, evidence_type, label,
                     description, public_status)
                VALUES (CAST(? AS uuid), ?, ?, 'DOCUMENT', ?, 'public', 'APPROVED')
                """, releaseId, evidenceId, "E-" + subjectId, "Evidence " + subjectId);
        jdbcTemplate.update("""
                INSERT INTO claim_evidence_link
                    (release_id, claim_stable_id, evidence_stable_id, support_type)
                VALUES (CAST(? AS uuid), ?, ?, 'DIRECT')
                """, releaseId, claimId, evidenceId);
        jdbcTemplate.update("""
                INSERT INTO subject_capability
                    (release_id, subject_stable_id, capability_code, supporting_claim_stable_id)
                VALUES (CAST(? AS uuid), ?, 'JAVA', ?)
                """, releaseId, subjectId, claimId);
        jdbcTemplate.update("""
                INSERT INTO retrieval_document
                    (release_id, stable_id, subject_stable_id, claim_stable_id,
                     search_text, embedding, embedding_model, content_hash)
                VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS vector), 'test', ?)
                """,
                releaseId,
                "DOC-" + subjectId,
                subjectId,
                claimId,
                searchText,
                vectorLiteral(embedding),
                "b".repeat(64));
    }

    private void insertAdditionalEvidence(String releaseId, String subjectId) {
        String claimId = "CLAIM-EXTRA-" + subjectId;
        String evidenceId = "EVIDENCE-EXTRA-" + subjectId;
        jdbcTemplate.update("""
                INSERT INTO claim
                    (release_id, stable_id, subject_stable_id, subject_kind, category,
                     statement, verification_status, display_order)
                VALUES (CAST(? AS uuid), ?, ?, 'PROJECT', 'OUTCOME', 'extra', 'VERIFIED', 2)
                """, releaseId, claimId, subjectId);
        jdbcTemplate.update("""
                INSERT INTO evidence
                    (release_id, stable_id, public_code, evidence_type, label,
                     description, public_status)
                VALUES (CAST(? AS uuid), ?, ?, 'DOCUMENT', 'Extra evidence',
                        'public', 'APPROVED')
                """, releaseId, evidenceId, "E-EXTRA-" + subjectId);
        jdbcTemplate.update("""
                INSERT INTO claim_evidence_link
                    (release_id, claim_stable_id, evidence_stable_id, support_type)
                VALUES (CAST(? AS uuid), ?, ?, 'DIRECT')
                """, releaseId, claimId, evidenceId);
    }

    private void insertProject(
            String releaseId, String subjectId, String careerTrack, String title) {
        insertSubject(
                releaseId,
                subjectId,
                300,
                "VERIFIED",
                title,
                vector(0.2f, 0.8f),
                title);
        jdbcTemplate.update("""
                UPDATE portfolio_subject
                SET career_track = ?
                WHERE release_id = CAST(? AS uuid) AND stable_id = ?
                """, careerTrack, releaseId, subjectId);
    }

    private void insertCase(
            String releaseId, String subjectId, String projectId, String capability, String title) {
        String claimId = "CLAIM-" + subjectId;
        String evidenceId = "EVIDENCE-" + subjectId;
        jdbcTemplate.update("""
                INSERT INTO portfolio_subject
                    (release_id, stable_id, subject_kind, slug, title, summary,
                     career_track, public_route, display_order)
                VALUES (CAST(? AS uuid), ?, 'CASE', ?, ?, ?, NULL, ?, 400)
                """,
                releaseId,
                subjectId,
                subjectId.toLowerCase(),
                title,
                "summary " + subjectId,
                "/cases/" + subjectId.toLowerCase());
        jdbcTemplate.update("""
                INSERT INTO case_study
                    (release_id, stable_id, project_stable_id, case_type)
                VALUES (CAST(? AS uuid), ?, ?, 'DEEP_DIVE')
                """, releaseId, subjectId, projectId);
        jdbcTemplate.update("""
                INSERT INTO claim
                    (release_id, stable_id, subject_stable_id, subject_kind, category,
                     statement, verification_status, display_order)
                VALUES (CAST(? AS uuid), ?, ?, 'CASE', 'OUTCOME', 'statement', 'VERIFIED', 1)
                """, releaseId, claimId, subjectId);
        jdbcTemplate.update("""
                INSERT INTO evidence
                    (release_id, stable_id, public_code, evidence_type, label,
                     description, public_status)
                VALUES (CAST(? AS uuid), ?, ?, 'DOCUMENT', ?, 'public', 'APPROVED')
                """, releaseId, evidenceId, "E-" + subjectId, "Evidence " + subjectId);
        jdbcTemplate.update("""
                INSERT INTO claim_evidence_link
                    (release_id, claim_stable_id, evidence_stable_id, support_type)
                VALUES (CAST(? AS uuid), ?, ?, 'DIRECT')
                """, releaseId, claimId, evidenceId);
        jdbcTemplate.update("""
                INSERT INTO subject_capability
                    (release_id, subject_stable_id, capability_code, supporting_claim_stable_id)
                VALUES (CAST(? AS uuid), ?, ?, ?)
                """, releaseId, subjectId, capability, claimId);
        jdbcTemplate.update("""
                INSERT INTO retrieval_document
                    (release_id, stable_id, subject_stable_id, claim_stable_id,
                     search_text, embedding, embedding_model, content_hash)
                VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS vector), 'test', ?)
                """,
                releaseId,
                "DOC-" + subjectId,
                subjectId,
                claimId,
                title,
                vectorLiteral(vector(0.3f, 0.7f)),
                "c".repeat(64));
    }

    private float[] vector(float first, float second) {
        float[] values = new float[512];
        values[0] = first;
        values[1] = second;
        return values;
    }

    private String vectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values[index]);
        }
        return builder.append(']').toString();
    }
}
