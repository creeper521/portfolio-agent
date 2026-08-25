package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 候选选择查询的 JDBC 实现：面向公开 PostgreSQL 投影的三条只读 SQL
 * （活跃发布、全文检索、向量检索、精确标识查询）。
 *
 * <p>隐私/质量边界（全部 SQL 共用）：Evidence 必须 public_status='APPROVED'、
 * claim 必须 verification_status='VERIFIED'，且所有查询都锁定单一 release_id，
 * 禁止跨快照混合。检索词以参数化 tsquery 下发，数组/向量字面量在拼装前统一转义，
 * 防止注入。结果排序确定性：评分降序 + display_order + 稳定标识。
 */
public final class JdbcPostgresSelectionQuery implements PostgresSelectionQuery {

    /** 查询唯一单例 active_release 指向且状态为 VERIFIED/PUBLISHED 的内容发布。 */
    private static final String ACTIVE_RELEASE_SQL = """
            SELECT CAST(r.release_id AS text), r.release_version
            FROM active_release a
            JOIN content_release r ON r.release_id = a.release_id
            WHERE a.singleton = true
              AND r.status IN ('VERIFIED', 'PUBLISHED')
            """;

    /**
     * 全文检索 SQL：eligible CTE 先按职业赛道/能力码过滤并要求存在已验证 claim + APPROVED
     * Evidence（无证据的主体不可入选），ranked CTE 取 ts_rank_cd 最大值排序，
     * 外层按主体聚合能力码并展开 claim×evidence 明细行。案例在归属项目缺职业赛道时，
     * 若自身能力码命中请求则按"中立案例"放行。
     */
    private static final String FTS_SQL = """
            WITH eligible AS (
                SELECT ps.release_id,
                       ps.stable_id,
                       ps.display_order,
                       COALESCE(owner.career_track, ps.career_track) AS effective_career_track
                FROM portfolio_subject ps
                LEFT JOIN case_study cs
                  ON cs.release_id = ps.release_id
                 AND cs.stable_id = ps.stable_id
                 AND ps.subject_kind = 'CASE'
                LEFT JOIN portfolio_subject owner
                  ON owner.release_id = ps.release_id
                 AND owner.stable_id = cs.project_stable_id
                 AND owner.subject_kind = 'PROJECT'
                WHERE ps.release_id = CAST(? AS uuid)
                  AND (
                      CAST(? AS text) IS NULL
                      OR COALESCE(owner.career_track, ps.career_track) = ?
                      OR (
                          ps.subject_kind = 'CASE'
                          AND cs.project_stable_id IS NULL
                          AND COALESCE(owner.career_track, ps.career_track) IS NULL
                          AND CAST(? AS text[]) IS NOT NULL
                          AND EXISTS (
                              SELECT 1
                              FROM subject_capability neutral_capability
                              WHERE neutral_capability.release_id = ps.release_id
                                AND neutral_capability.subject_stable_id = ps.stable_id
                                AND neutral_capability.capability_code = ANY(CAST(? AS text[]))
                          )
                      )
                  )
                  AND (CAST(? AS text[]) IS NULL OR EXISTS (
                      SELECT 1
                      FROM subject_capability sc
                      WHERE sc.release_id = ps.release_id
                        AND sc.subject_stable_id = ps.stable_id
                        AND sc.capability_code = ANY(CAST(? AS text[]))
                  ))
                  AND EXISTS (
                      SELECT 1
                      FROM claim c
                      JOIN claim_evidence_link cel
                        ON cel.release_id = c.release_id
                       AND cel.claim_stable_id = c.stable_id
                      JOIN evidence e
                        ON e.release_id = c.release_id
                       AND e.stable_id = cel.evidence_stable_id
                       AND e.public_status = 'APPROVED'
                      WHERE c.release_id = ps.release_id
                        AND c.subject_stable_id = ps.stable_id
                        AND c.verification_status = 'VERIFIED'
                  )
            ),
            query_spec AS (
                SELECT CAST(NULLIF(?, '') AS tsquery) AS query
            ),
            ranked AS (
                SELECT eligible.stable_id,
                       eligible.display_order,
                       eligible.effective_career_track,
                       COALESCE(
                           max(ts_rank_cd(rd.search_vector, query_spec.query))
                               FILTER (
                                   WHERE query_spec.query IS NOT NULL
                                     AND rd.search_vector @@ query_spec.query
                               ),
                           0
                       ) AS rank_score
                FROM eligible
                CROSS JOIN query_spec
                LEFT JOIN retrieval_document rd
                  ON rd.release_id = eligible.release_id
                 AND rd.subject_stable_id = eligible.stable_id
                GROUP BY eligible.stable_id, eligible.display_order, eligible.effective_career_track
                ORDER BY rank_score DESC, eligible.display_order, eligible.stable_id
                LIMIT ?
            )
            SELECT ps.stable_id,
                   ps.subject_kind,
                   ps.title,
                   ps.summary,
                   ps.public_route,
                   r.effective_career_track AS career_track,
                   caps.capabilities,
                   c.stable_id AS claim_id,
                   e.stable_id AS evidence_id,
                   e.label AS evidence_label,
                   e.public_status AS evidence_public_status
            FROM ranked r
            JOIN portfolio_subject ps
              ON ps.release_id = CAST(? AS uuid)
             AND ps.stable_id = r.stable_id
            LEFT JOIN LATERAL (
                SELECT array_agg(sc.capability_code ORDER BY sc.capability_code) AS capabilities
                FROM subject_capability sc
                WHERE sc.release_id = ps.release_id
                  AND sc.subject_stable_id = ps.stable_id
            ) caps ON true
            JOIN claim c
              ON c.release_id = ps.release_id
             AND c.subject_stable_id = ps.stable_id
             AND c.verification_status = 'VERIFIED'
            JOIN claim_evidence_link cel
              ON cel.release_id = ps.release_id
             AND cel.claim_stable_id = c.stable_id
            JOIN evidence e
              ON e.release_id = ps.release_id
             AND e.stable_id = cel.evidence_stable_id
             AND e.public_status = 'APPROVED'
            ORDER BY r.rank_score DESC, r.display_order, ps.stable_id, c.stable_id, e.stable_id
            """;

    /**
     * 向量检索 SQL：eligible 过滤与全文版完全一致，ranked CTE 按 retrieval_document
     * 的 pgvector 余弦距离（&lt;=&gt;）取每个主体的最小距离排序；无 embedding 的主体被
     * INNER JOIN 自然排除。外层明细展开与全文版相同。
     */
    private static final String VECTOR_SQL = """
            WITH eligible AS (
                SELECT ps.release_id,
                       ps.stable_id,
                       ps.display_order,
                       COALESCE(owner.career_track, ps.career_track) AS effective_career_track
                FROM portfolio_subject ps
                LEFT JOIN case_study cs
                  ON cs.release_id = ps.release_id
                 AND cs.stable_id = ps.stable_id
                 AND ps.subject_kind = 'CASE'
                LEFT JOIN portfolio_subject owner
                  ON owner.release_id = ps.release_id
                 AND owner.stable_id = cs.project_stable_id
                 AND owner.subject_kind = 'PROJECT'
                WHERE ps.release_id = CAST(? AS uuid)
                  AND (
                      CAST(? AS text) IS NULL
                      OR COALESCE(owner.career_track, ps.career_track) = ?
                      OR (
                          ps.subject_kind = 'CASE'
                          AND cs.project_stable_id IS NULL
                          AND COALESCE(owner.career_track, ps.career_track) IS NULL
                          AND CAST(? AS text[]) IS NOT NULL
                          AND EXISTS (
                              SELECT 1
                              FROM subject_capability neutral_capability
                              WHERE neutral_capability.release_id = ps.release_id
                                AND neutral_capability.subject_stable_id = ps.stable_id
                                AND neutral_capability.capability_code = ANY(CAST(? AS text[]))
                          )
                      )
                  )
                  AND (CAST(? AS text[]) IS NULL OR EXISTS (
                      SELECT 1
                      FROM subject_capability sc
                      WHERE sc.release_id = ps.release_id
                        AND sc.subject_stable_id = ps.stable_id
                        AND sc.capability_code = ANY(CAST(? AS text[]))
                  ))
                  AND EXISTS (
                      SELECT 1
                      FROM claim c
                      JOIN claim_evidence_link cel
                        ON cel.release_id = c.release_id
                       AND cel.claim_stable_id = c.stable_id
                      JOIN evidence e
                        ON e.release_id = c.release_id
                       AND e.stable_id = cel.evidence_stable_id
                       AND e.public_status = 'APPROVED'
                      WHERE c.release_id = ps.release_id
                        AND c.subject_stable_id = ps.stable_id
                        AND c.verification_status = 'VERIFIED'
                  )
            ),
            ranked AS (
                SELECT eligible.stable_id,
                       eligible.display_order,
                       eligible.effective_career_track,
                       min(rd.embedding <=> CAST(? AS vector)) AS distance
                FROM eligible
                JOIN retrieval_document rd
                  ON rd.release_id = eligible.release_id
                 AND rd.subject_stable_id = eligible.stable_id
                 AND rd.embedding IS NOT NULL
                GROUP BY eligible.stable_id, eligible.display_order, eligible.effective_career_track
                ORDER BY distance, eligible.display_order, eligible.stable_id
                LIMIT ?
            )
            SELECT ps.stable_id,
                   ps.subject_kind,
                   ps.title,
                   ps.summary,
                   ps.public_route,
                   r.effective_career_track AS career_track,
                   caps.capabilities,
                   c.stable_id AS claim_id,
                   e.stable_id AS evidence_id,
                   e.label AS evidence_label,
                   e.public_status AS evidence_public_status
            FROM ranked r
            JOIN portfolio_subject ps
              ON ps.release_id = CAST(? AS uuid)
             AND ps.stable_id = r.stable_id
            LEFT JOIN LATERAL (
                SELECT array_agg(sc.capability_code ORDER BY sc.capability_code) AS capabilities
                FROM subject_capability sc
                WHERE sc.release_id = ps.release_id
                  AND sc.subject_stable_id = ps.stable_id
            ) caps ON true
            JOIN claim c
              ON c.release_id = ps.release_id
             AND c.subject_stable_id = ps.stable_id
             AND c.verification_status = 'VERIFIED'
            JOIN claim_evidence_link cel
              ON cel.release_id = ps.release_id
             AND cel.claim_stable_id = c.stable_id
            JOIN evidence e
              ON e.release_id = ps.release_id
             AND e.stable_id = cel.evidence_stable_id
             AND e.public_status = 'APPROVED'
            ORDER BY r.distance, r.display_order, ps.stable_id, c.stable_id, e.stable_id
            """;

    /** 精确标识查询 SQL：按传入主体列表直接取行（仅 DIRECT 支撑链接），保持输入顺序。 */
    private static final String EXACT_IDS_SQL = """
            SELECT ps.stable_id,
                   ps.subject_kind,
                   ps.title,
                   ps.summary,
                   ps.public_route,
                   ps.career_track AS career_track,
                   caps.capabilities,
                   c.stable_id AS claim_id,
                   e.stable_id AS evidence_id,
                   e.label AS evidence_label,
                   e.public_status AS evidence_public_status
            FROM portfolio_subject ps
            LEFT JOIN LATERAL (
                SELECT array_agg(sc.capability_code ORDER BY sc.capability_code) AS capabilities
                FROM subject_capability sc
                WHERE sc.release_id = ps.release_id
                  AND sc.subject_stable_id = ps.stable_id
            ) caps ON true
            JOIN claim c
              ON c.release_id = ps.release_id
             AND c.subject_stable_id = ps.stable_id
             AND c.verification_status = 'VERIFIED'
            JOIN claim_evidence_link cel
              ON cel.release_id = ps.release_id
             AND cel.claim_stable_id = c.stable_id
             AND cel.support_type = 'DIRECT'
            JOIN evidence e
              ON e.release_id = ps.release_id
             AND e.stable_id = cel.evidence_stable_id
             AND e.public_status = 'APPROVED'
            WHERE ps.release_id = CAST(? AS uuid)
              AND ps.stable_id = ANY(CAST(? AS text[]))
              AND (
                  CAST(? AS text) IS NULL
                  OR ps.career_track = ?
              )
              AND (CAST(? AS text[]) IS NULL OR EXISTS (
                  SELECT 1
                  FROM subject_capability sc
                  WHERE sc.release_id = ps.release_id
                    AND sc.subject_stable_id = ps.stable_id
                    AND sc.capability_code = ANY(CAST(? AS text[]))
              ))
            ORDER BY array_position(CAST(? AS text[]), ps.stable_id), c.stable_id, e.stable_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPostgresSelectionQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查询当前生效的公开内容发布；无结果或多行时由 Spring 抛出数据访问异常。 */
    @Override
    public ActiveRelease activeRelease() {
        return jdbcTemplate.queryForObject(
                ACTIVE_RELEASE_SQL,
                (resultSet, rowNumber) -> new ActiveRelease(
                        resultSet.getString(1),
                        resultSet.getString(2)));
    }

    /**
     * 全文检索候选主体。参数按占位符出现顺序绑定：releaseId、careerTrack（两处）、
     * 能力码数组字面量（四处）、检索词、limit、releaseId（外层）。
     *
     * @param releaseId 内容发布 UUID 字符串
     * @param target    选择目标（职业赛道/能力码/目标文本转为 tsquery）
     * @param limit     主体数上限
     * @return 按文本相关性排序的主体行（含 claim×evidence 明细）
     */
    @Override
    public List<PostgresSelectionRow> searchFts(
            String releaseId,
            SelectionTarget target,
            int limit) {
        String queryText = queryText(target);
        String capabilityFilter = capabilityArrayLiteral(target);
        return jdbcTemplate.query(
                FTS_SQL,
                this::mapRows,
                releaseId,
                target.getCareerTrack(),
                target.getCareerTrack(),
                capabilityFilter,
                capabilityFilter,
                capabilityFilter,
                capabilityFilter,
                queryText,
                limit,
                releaseId);
    }

    /**
     * 向量相似度检索候选主体；参数绑定顺序与 {@link #searchFts} 一致，
     * 仅在 limit 前多一个向量字面量。
     *
     * @param embedding 查询向量
     */
    @Override
    public List<PostgresSelectionRow> searchVector(
            String releaseId,
            float[] embedding,
            SelectionTarget target,
            int limit) {
        String capabilityFilter = capabilityArrayLiteral(target);
        return jdbcTemplate.query(
                VECTOR_SQL,
                this::mapRows,
                releaseId,
                target.getCareerTrack(),
                target.getCareerTrack(),
                capabilityFilter,
                capabilityFilter,
                capabilityFilter,
                capabilityFilter,
                vectorLiteral(embedding),
                limit,
                releaseId);
    }

    /**
     * 按主体标识精确查询；subjectIds 为空直接返回空列表，不发起 SQL。
     * 结果按输入主体顺序排列。
     */
    @Override
    public List<PostgresSelectionRow> findByIds(
            String releaseId,
            List<String> subjectIds,
            SelectionTarget target) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        String subjectArray = arrayLiteral(subjectIds);
        String capabilityFilter = capabilityArrayLiteral(target);
        return jdbcTemplate.query(
                EXACT_IDS_SQL,
                this::mapRows,
                releaseId,
                subjectArray,
                target.getCareerTrack(),
                target.getCareerTrack(),
                capabilityFilter,
                capabilityFilter,
                subjectArray);
    }

    /** 行装配：SQL 按主体×claim×evidence 展开多行，这里按主体合并并把 Evidence 引用累积去重。 */
    private List<PostgresSelectionRow> mapRows(ResultSet resultSet) throws SQLException {
        Map<String, MutableRow> rows = new LinkedHashMap<>();
        while (resultSet.next()) {
            String subjectId = resultSet.getString("stable_id");
            MutableRow row = rows.get(subjectId);
            if (row == null) {
                row = new MutableRow(
                        subjectId,
                        PortfolioSubjectKind.valueOf(resultSet.getString("subject_kind")),
                        resultSet.getString("title"),
                        resultSet.getString("summary"),
                        resultSet.getString("public_route"),
                        resultSet.getString("career_track"),
                        readCapabilities(resultSet.getArray("capabilities")));
                rows.put(subjectId, row);
            }
            row.addEvidence(new EvidenceReference(
                    resultSet.getString("claim_id"),
                    resultSet.getString("evidence_id"),
                    resultSet.getString("evidence_label"),
                    resultSet.getString("evidence_public_status")));
        }
        return rows.values().stream().map(MutableRow::toRow).toList();
    }

    /** 读取 SQL 数组列为能力码集合；NULL 数组返回空集合。 */
    private Set<String> readCapabilities(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        Object values = array.getArray();
        if (values instanceof String[] strings) {
            return Set.copyOf(Arrays.asList(strings));
        }
        Object[] objects = (Object[]) values;
        return Arrays.stream(objects)
                .map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 构造全文检索的 tsquery：目标文本与能力码（下划线转空格）提取词元后以 OR 连接。 */
    private String queryText(SelectionTarget target) {
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        addSearchTerms(terms, target.getGoal());
        target.getCapabilityCodes().stream()
                .sorted()
                .forEach(code -> addSearchTerms(terms, code.replace('_', ' ')));
        return String.join(" | ", terms);
    }

    /** 提取小写字母/数字词元加入词集；文本为 null 时忽略。 */
    private void addSearchTerms(Set<String> terms, String text) {
        if (text == null) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[\\p{L}\\p{N}]+")
                .matcher(text.toLowerCase(java.util.Locale.ROOT));
        while (matcher.find()) {
            terms.add(matcher.group());
        }
    }

    /** 能力码集合编为排序后的 PostgreSQL 数组字面量并转义；空集合返回 null 表示不过滤。 */
    private String capabilityArrayLiteral(SelectionTarget target) {
        if (target.getCapabilityCodes().isEmpty()) {
            return null;
        }
        return target.getCapabilityCodes().stream()
                .sorted()
                .map(code -> "\"" + code.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    /** 把查询向量编为 pgvector 字面量 [v1,v2,...]。 */
    private String vectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(embedding[index]));
        }
        return builder.append(']').toString();
    }

    /** 标识列表编为 PostgreSQL 数组字面量，转义反斜杠与双引号防止注入。 */
    private String arrayLiteral(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    /** 行装配中间载体：主体元数据加按 claimId+evidenceId 去重的引用表，toRow 时固定质量分 1.0。 */
    private static final class MutableRow {

        private final String subjectId;
        private final PortfolioSubjectKind kind;
        private final String title;
        private final String summary;
        private final String route;
        private final String careerTrack;
        private final Set<String> capabilities;
        private final Map<String, EvidenceReference> evidence = new LinkedHashMap<>();

        private MutableRow(
                String subjectId,
                PortfolioSubjectKind kind,
                String title,
                String summary,
                String route,
                String careerTrack,
                Set<String> capabilities) {
            this.subjectId = subjectId;
            this.kind = kind;
            this.title = title;
            this.summary = summary;
            this.route = route;
            this.careerTrack = careerTrack;
            this.capabilities = capabilities;
        }

        private void addEvidence(EvidenceReference reference) {
            evidence.put(reference.getClaimId() + "\n" + reference.getEvidenceId(), reference);
        }

        private PostgresSelectionRow toRow() {
            return new PostgresSelectionRow(
                    subjectId,
                    kind,
                    title,
                    summary,
                    route,
                    careerTrack,
                    capabilities,
                    List.copyOf(evidence.values()),
                    1.0);
        }
    }
}
