package com.portfolio.agent.answer.intelligence.adapter.postgres;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcPostgresFactPassageQuery implements PostgresFactPassageQuery {

    private static final String FACT_PASSAGE_SQL = """
            SELECT ps.stable_id AS subject_id,
                   c.stable_id AS claim_id,
                   c.statement AS content,
                   array_agg(DISTINCT e.stable_id ORDER BY e.stable_id) AS evidence_ids
            FROM portfolio_subject ps
            JOIN claim c
              ON c.release_id = ps.release_id
             AND c.subject_stable_id = ps.stable_id
             AND c.verification_status = 'VERIFIED'
            JOIN claim_evidence_link cel
              ON cel.release_id = c.release_id
             AND cel.claim_stable_id = c.stable_id
             AND cel.support_type = 'DIRECT'
            JOIN evidence e
              ON e.release_id = c.release_id
             AND e.stable_id = cel.evidence_stable_id
             AND e.public_status = 'APPROVED'
            WHERE ps.release_id = CAST(? AS uuid)
              AND ps.stable_id = ANY(CAST(? AS text[]))
              AND NULLIF(BTRIM(c.statement), '') IS NOT NULL
            GROUP BY ps.stable_id, c.stable_id, c.statement
            ORDER BY array_position(CAST(? AS text[]), ps.stable_id), c.stable_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPostgresFactPassageQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<PostgresKnowledgePassageRow> findPassages(
            String releaseId,
            List<String> subjectIds) {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(subjectIds, "subjectIds");
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        String subjectArray = arrayLiteral(subjectIds);
        return jdbcTemplate.query(
                FACT_PASSAGE_SQL,
                this::mapRows,
                releaseId,
                subjectArray,
                subjectArray);
    }

    private List<PostgresKnowledgePassageRow> mapRows(ResultSet resultSet) throws SQLException {
        java.util.ArrayList<PostgresKnowledgePassageRow> rows = new java.util.ArrayList<>();
        while (resultSet.next()) {
            rows.add(new PostgresKnowledgePassageRow(
                    resultSet.getString("subject_id"),
                    resultSet.getString("claim_id"),
                    resultSet.getString("content"),
                    readStrings(resultSet.getArray("evidence_ids"))));
        }
        return List.copyOf(rows);
    }

    private List<String> readStrings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object values = array.getArray();
        if (values instanceof String[] strings) {
            return List.copyOf(Arrays.asList(strings));
        }
        return Arrays.stream((Object[]) values)
                .map(String::valueOf)
                .toList();
    }

    private String arrayLiteral(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
}
