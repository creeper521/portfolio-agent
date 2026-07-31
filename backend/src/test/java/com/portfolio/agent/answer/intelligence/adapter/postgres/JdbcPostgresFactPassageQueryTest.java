package com.portfolio.agent.answer.intelligence.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .contains("c.verification_status = 'VERIFIED'")
                .contains("e.public_status = 'APPROVED'")
                .contains("cel.support_type = 'DIRECT'")
                .contains("ps.release_id = CAST(? AS uuid)")
                .contains("ps.stable_id = ANY(CAST(? AS text[]))")
                .doesNotContain("Public summary");
        assertThat(parameters.getValue()).contains("release-id");
    }
}
