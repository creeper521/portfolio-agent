package com.portfolio.agent.selection.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class JdbcPostgresSelectionQueryTest {

    @Test
    void pinsFtsEnrichmentToReleaseAndFiltersForApprovedEvidenceInOneQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresSelectionQuery query = new JdbcPostgresSelectionQuery(jdbcTemplate);

        query.searchFts(
                "9d1bca16-1e9a-4d54-a692-b7f7c68dbc20",
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("JAVA"),
                        "public goal",
                        3),
                12);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                parameters.capture());
        assertThat(sql.getValue())
                .contains("ps.title")
                .contains("ps.summary")
                .contains("ps.public_route")
                .contains("e.public_status = 'APPROVED'")
                .contains("c.verification_status = 'VERIFIED'")
                .contains("c.release_id = ps.release_id")
                .contains("e.release_id = ps.release_id");
        assertThat(sql.getValue().indexOf("e.public_status = 'APPROVED'"))
                .isLessThan(sql.getValue().indexOf("LIMIT ?"));
        assertThat(parameters.getValue())
                .contains("9d1bca16-1e9a-4d54-a692-b7f7c68dbc20");
    }

    @Test
    void buildsFtsFromGoalAndReadableCapabilityTermsWithoutStructuredAudienceOrCareer() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresSelectionQuery query = new JdbcPostgresSelectionQuery(jdbcTemplate);

        query.searchFts(
                "9d1bca16-1e9a-4d54-a692-b7f7c68dbc20",
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("INCIDENT_ANALYSIS", "RAG"),
                        "故障定位 检索增强",
                        3),
                12);

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                anyString(),
                any(ResultSetExtractor.class),
                parameters.capture());
        String tsquery = java.util.Arrays.stream(parameters.getValue())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> value.contains("|"))
                .findFirst()
                .orElseThrow();
        assertThat(tsquery)
                .contains("|")
                .doesNotContain("INTERVIEWER")
                .doesNotContain("JAVA_BACKEND");
    }

    @Test
    void qualifiesVectorCandidatesBeforeLimitAsWell() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresSelectionQuery query = new JdbcPostgresSelectionQuery(jdbcTemplate);

        query.searchVector(
                "9d1bca16-1e9a-4d54-a692-b7f7c68dbc20",
                new float[]{0.1f, 0.2f},
                new SelectionTarget("JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 3),
                12);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                any(Object[].class));
        assertThat(sql.getValue().indexOf("e.public_status = 'APPROVED'"))
                .isLessThan(sql.getValue().indexOf("LIMIT ?"));
        assertThat(sql.getValue()).contains("sc.capability_code = ANY");
    }

    @Test
    void derivesCaseCareerFromItsOwningProjectWithinTheSameRelease() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresSelectionQuery query = new JdbcPostgresSelectionQuery(jdbcTemplate);

        query.searchFts(
                "9d1bca16-1e9a-4d54-a692-b7f7c68dbc20",
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("JAVA"),
                        "public goal",
                        3),
                12);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                any(Object[].class));
        assertThat(sql.getValue())
                .contains("owner.release_id = ps.release_id")
                .contains("owner.stable_id = cs.project_stable_id")
                .contains("COALESCE(owner.career_track, ps.career_track)")
                .contains("AS career_track");
    }

    @Test
    void permitsCareerNeutralStandaloneCasesOnlyWhenRequestedCapabilitiesMatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresSelectionQuery query = new JdbcPostgresSelectionQuery(jdbcTemplate);

        query.searchVector(
                "9d1bca16-1e9a-4d54-a692-b7f7c68dbc20",
                new float[]{0.1f, 0.2f},
                new SelectionTarget(
                        "JAVA_BACKEND",
                        "INTERVIEWER",
                        Set.of("JAVA"),
                        null,
                        3),
                12);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                any(Object[].class));
        assertThat(sql.getValue())
                .contains("cs.project_stable_id IS NULL")
                .contains("CAST(? AS text[]) IS NOT NULL")
                .contains("neutral_capability");
    }

    @Test
    void findsExactIdsWithinOneReleaseAndKeepsEvidenceGates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(ResultSetExtractor.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        JdbcPostgresSelectionQuery query = new JdbcPostgresSelectionQuery(jdbcTemplate);

        query.findByIds(
                "9d1bca16-1e9a-4d54-a692-b7f7c68dbc20",
                List.of("project-1", "case-2"),
                new SelectionTarget(
                        "JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                any(Object[].class));
        assertThat(sql.getValue())
                .contains("ps.release_id = CAST(? AS uuid)")
                .contains("ps.stable_id = ANY(CAST(? AS text[]))")
                .contains("c.verification_status = 'VERIFIED'")
                .contains("e.public_status = 'APPROVED'")
                .contains("COALESCE(owner.career_track, ps.career_track) = ?")
                .contains("sc.capability_code = ANY");
    }
}
