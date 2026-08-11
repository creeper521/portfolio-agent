package com.portfolio.agent.answer.intelligence.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import java.sql.Array;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class JdbcPostgresFactPassageQueryTest {

    @Test
    void usesFixedParameterizedSqlForVerifiedClaimsAndApprovedEvidenceInOneRelease() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresFactPassageQuery query = new JdbcPostgresFactPassageQuery(jdbcTemplate);

        query.findPassages("release-id", List.of("project-1", "case-2"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                parameters.capture());
        assertThat(sql.getValue())
                .contains("c.statement")
                .contains("e.label")
                .contains("e.public_status")
                .contains("c.verification_status = 'VERIFIED'")
                .contains("e.public_status = 'APPROVED'")
                .contains("cel.support_type = 'DIRECT'")
                .contains("ps.release_id = CAST(? AS uuid)")
                .contains("ps.stable_id = ANY(CAST(? AS text[]))")
                .doesNotContain("Public summary");
        assertThat(parameters.getValue()).contains("release-id");
    }

    @Test
    void mapsFullClaimProjectionAndApprovedReferencesFromOneRow() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(1);
                    return extractor.extractData(projectionResultSet());
                });
        JdbcPostgresFactPassageQuery query = new JdbcPostgresFactPassageQuery(jdbcTemplate);

        List<PostgresKnowledgePassageRow> rows =
                query.findPassages("release-id", List.of("project-1"));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getSubjectId()).isEqualTo("project-1");
            assertThat(row.getClaimId()).isEqualTo("claim-1");
            assertThat(row.getClaimCategory()).isEqualTo(AnswerClaimCategory.VERIFICATION);
            assertThat(row.getContent()).isEqualTo("已验证主要功能流程。");
            assertThat(row.getClaim()).satisfies(claim -> {
                assertThat(claim.getId()).isEqualTo("claim-1");
                assertThat(claim.getCategory()).isEqualTo(AnswerClaimCategory.VERIFICATION);
                assertThat(claim.getStatement()).isEqualTo("已验证主要功能流程。");
                assertThat(claim.getDetail()).isEqualTo("验证范围以公开证据为限。");
                assertThat(claim.getAchievementStatus())
                        .isEqualTo(AnswerAchievementStatus.IMPLEMENTED_TESTED);
                assertThat(claim.getContributionType())
                        .isEqualTo(AnswerContributionType.PRIMARY);
                assertThat(claim.getVerificationBasis())
                        .isEqualTo(AnswerVerificationBasis.EVIDENCE_SUPPORTED);
                assertThat(claim.getVerificationStatus())
                        .isEqualTo(AnswerClaimVerificationStatus.VERIFIED);
                assertThat(claim.getMateriality()).isEqualTo(AnswerMateriality.KEY);
                assertThat(claim.getTopics()).containsExactly("POSTGRESQL");
                assertThat(claim.getDirectEvidenceIds()).containsExactly("evidence-1");
            });
            assertThat(row.getEvidenceIds()).containsExactly("evidence-1");
            assertThat(row.getEvidenceReferences()).singleElement().satisfies(reference -> {
                assertThat(reference.getEvidenceId()).isEqualTo("evidence-1");
                assertThat(reference.getPublicStatus()).isEqualTo("APPROVED");
            });
        });
    }

    private ResultSet projectionResultSet() throws Exception {
        Array topics = mock(Array.class);
        when(topics.getArray()).thenReturn(new String[]{"POSTGRESQL"});
        Array evidenceIds = mock(Array.class);
        when(evidenceIds.getArray()).thenReturn(new String[]{"evidence-1"});
        Array evidenceLabels = mock(Array.class);
        when(evidenceLabels.getArray()).thenReturn(new String[]{"验证证据"});
        Array evidenceStatuses = mock(Array.class);
        when(evidenceStatuses.getArray()).thenReturn(new String[]{"APPROVED"});
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("subject_id")).thenReturn("project-1");
        when(resultSet.getString("claim_id")).thenReturn("claim-1");
        when(resultSet.getString("claim_category")).thenReturn("VERIFICATION");
        when(resultSet.getString("content")).thenReturn("已验证主要功能流程。");
        when(resultSet.getString("claim_statement")).thenReturn("已验证主要功能流程。");
        when(resultSet.getString("claim_detail")).thenReturn("验证范围以公开证据为限。");
        when(resultSet.getString("claim_achievement_status")).thenReturn("IMPLEMENTED_TESTED");
        when(resultSet.getString("claim_contribution_type")).thenReturn("PRIMARY");
        when(resultSet.getString("claim_verification_basis")).thenReturn("EVIDENCE_SUPPORTED");
        when(resultSet.getString("claim_verification_status")).thenReturn("VERIFIED");
        when(resultSet.getString("claim_materiality")).thenReturn("KEY");
        when(resultSet.getArray("claim_topics")).thenReturn(topics);
        when(resultSet.getArray("evidence_ids")).thenReturn(evidenceIds);
        when(resultSet.getArray("evidence_labels")).thenReturn(evidenceLabels);
        when(resultSet.getArray("evidence_statuses")).thenReturn(evidenceStatuses);
        return resultSet;
    }
}
