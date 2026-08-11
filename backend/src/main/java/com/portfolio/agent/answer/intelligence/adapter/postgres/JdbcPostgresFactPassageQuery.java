package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
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
                   c.category AS claim_category,
                   c.statement AS content,
                   c.statement AS claim_statement,
                   c.detail AS claim_detail,
                   c.achievement_status AS claim_achievement_status,
                   c.contribution_type AS claim_contribution_type,
                   c.verification_basis AS claim_verification_basis,
                   c.verification_status AS claim_verification_status,
                   c.materiality AS claim_materiality,
                   ARRAY(SELECT jsonb_array_elements_text(c.topics)) AS claim_topics,
                   array_agg(e.stable_id ORDER BY e.stable_id) AS evidence_ids,
                   array_agg(e.label ORDER BY e.stable_id) AS evidence_labels,
                   array_agg(e.public_status ORDER BY e.stable_id) AS evidence_statuses
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
              AND c.detail IS NOT NULL
              AND c.achievement_status IS NOT NULL
              AND c.contribution_type IS NOT NULL
              AND c.verification_basis IS NOT NULL
              AND c.materiality IS NOT NULL
              AND c.topics IS NOT NULL
            GROUP BY ps.stable_id, c.stable_id, c.category, c.statement, c.detail,
                     c.achievement_status, c.contribution_type, c.verification_basis,
                     c.verification_status, c.materiality, c.topics
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
            List<String> evidenceIds = readStrings(resultSet.getArray("evidence_ids"));
            List<String> evidenceLabels = readStrings(resultSet.getArray("evidence_labels"));
            List<String> evidenceStatuses = readStrings(resultSet.getArray("evidence_statuses"));
            AnswerClaimProjection claim = claimProjection(resultSet, evidenceIds);
            rows.add(new PostgresKnowledgePassageRow(
                    resultSet.getString("subject_id"),
                    resultSet.getString("content"),
                    claim,
                    evidenceReferences(
                            claim.getId(),
                            evidenceIds,
                            evidenceLabels,
                            evidenceStatuses)));
        }
        return List.copyOf(rows);
    }

    private AnswerClaimProjection claimProjection(
            ResultSet resultSet,
            List<String> evidenceIds) throws SQLException {
        return new AnswerClaimProjection(
                resultSet.getString("claim_id"),
                AnswerClaimCategory.valueOf(resultSet.getString("claim_category")),
                resultSet.getString("claim_statement"),
                resultSet.getString("claim_detail"),
                AnswerAchievementStatus.valueOf(
                        resultSet.getString("claim_achievement_status")),
                AnswerContributionType.valueOf(
                        resultSet.getString("claim_contribution_type")),
                AnswerVerificationBasis.valueOf(
                        resultSet.getString("claim_verification_basis")),
                AnswerClaimVerificationStatus.valueOf(
                        resultSet.getString("claim_verification_status")),
                AnswerMateriality.valueOf(resultSet.getString("claim_materiality")),
                readStrings(resultSet.getArray("claim_topics")),
                evidenceIds);
    }

    private List<com.portfolio.agent.selection.domain.EvidenceReference> evidenceReferences(
            String claimId,
            List<String> evidenceIds,
            List<String> labels,
            List<String> statuses) {
        if (evidenceIds.size() != labels.size() || evidenceIds.size() != statuses.size()) {
            throw new IllegalStateException("PostgreSQL evidence projection columns are misaligned");
        }
        java.util.ArrayList<com.portfolio.agent.selection.domain.EvidenceReference> references =
                new java.util.ArrayList<>();
        for (int index = 0; index < evidenceIds.size(); index++) {
            references.add(new com.portfolio.agent.selection.domain.EvidenceReference(
                    claimId, evidenceIds.get(index), labels.get(index), statuses.get(index)));
        }
        return List.copyOf(references);
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
