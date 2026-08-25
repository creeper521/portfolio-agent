package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 事实段落查询的 JDBC 实现：从公开 PostgreSQL 投影装配"主体-claim-APPROVED Evidence"段落行。
 *
 * <p>SQL 固定三条隐私/质量边界：claim 必须为 VERIFIED、Evidence 必须为 public_status
 * ='APPROVED'、仅取 DIRECT 支撑链接；claim 的陈述/明细等投影字段任一为空即整行剔除，
 * 保证产出的 {@link PostgresKnowledgePassageRow} 始终完整可引用。结果按传入主体顺序
 * 与 claim 稳定标识排序，保证同输入同输出。
 */
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
                   array_agg(e.public_code ORDER BY e.stable_id) AS evidence_codes,
                   array_agg(e.label ORDER BY e.stable_id) AS evidence_labels,
                   array_agg(e.evidence_type ORDER BY e.stable_id) AS evidence_types,
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

    /**
     * 查询指定发布下若干主体的全部事实段落。
     *
     * @param releaseId  内容发布 UUID 字符串，锁定检索快照
     * @param subjectIds 主体稳定标识列表；为空直接返回空列表，不发起 SQL
     * @return 按主体输入顺序与 claim 标识排序的段落列表
     */
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

    /** 逐行装配：把 claim 列与按 stable_id 聚合的 Evidence 数组列合成段落行。 */
    private List<PostgresKnowledgePassageRow> mapRows(ResultSet resultSet) throws SQLException {
        java.util.ArrayList<PostgresKnowledgePassageRow> rows = new java.util.ArrayList<>();
        while (resultSet.next()) {
            List<String> evidenceIds = readStrings(resultSet.getArray("evidence_ids"));
            List<String> evidenceCodes = readStrings(resultSet.getArray("evidence_codes"));
            List<String> evidenceLabels = readStrings(resultSet.getArray("evidence_labels"));
            List<String> evidenceTypes = readStrings(resultSet.getArray("evidence_types"));
            List<String> evidenceStatuses = readStrings(resultSet.getArray("evidence_statuses"));
            AnswerClaimProjection claim = claimProjection(resultSet, evidenceIds);
            rows.add(new PostgresKnowledgePassageRow(
                    resultSet.getString("subject_id"),
                    resultSet.getString("content"),
                    claim,
                    evidenceReferences(
                            claim.getId(),
                            evidenceIds,
                            evidenceCodes,
                            evidenceLabels,
                            evidenceTypes,
                            evidenceStatuses)));
        }
        return List.copyOf(rows);
    }

    /** 把 claim 各列转换为枚举化的 AnswerClaimProjection；直证列表来自聚合的 evidence_ids。 */
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

    /**
     * 把五组同序聚合列（id/编码/标签/类型/状态）逐位组装为 Evidence 引用列表。
     *
     * @throws IllegalStateException 任一组长度不一致（投影列错位，数据完整性故障）时
     */
    private List<com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference> evidenceReferences(
            String claimId,
            List<String> evidenceIds,
            List<String> evidenceCodes,
            List<String> labels,
            List<String> evidenceTypes,
            List<String> statuses) {
        if (evidenceIds.size() != evidenceCodes.size()
                || evidenceIds.size() != labels.size()
                || evidenceIds.size() != evidenceTypes.size()
                || evidenceIds.size() != statuses.size()) {
            throw new IllegalStateException("PostgreSQL evidence projection columns are misaligned");
        }
        java.util.ArrayList<com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference> references =
                new java.util.ArrayList<>();
        for (int index = 0; index < evidenceIds.size(); index++) {
            references.add(new com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference(
                    claimId, evidenceIds.get(index), evidenceCodes.get(index), labels.get(index),
                    evidenceTypes.get(index), statuses.get(index)));
        }
        return List.copyOf(references);
    }

    /** 读取 SQL 数组列为字符串列表；NULL 数组返回空列表。 */
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

    /** 把标识列表编为 PostgreSQL 数组字面量，转义反斜杠与双引号防止注入。 */
    private String arrayLiteral(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
}
