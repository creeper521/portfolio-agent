package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.portfolio.agent.infrastructure.retrieval.EmbeddingVector;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.RetrievalMode;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionCandidate;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalException;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 公开 PostgreSQL 混合候选检索器：全文检索与向量检索并行执行后按 RRF 融合排序。
 *
 * <p>降级策略：请求 KEYWORD 时只走全文（FTS_ONLY）；请求 HYBRID 时先取全文结果，
 * 向量侧失败时若目标不带能力码与职业赛道（无质量兜底信号）则整体失败（fail-closed），
 * 否则降级为 FTS_ONLY 继续返回全文候选。基础设施异常统一包装为
 * {@link CandidateRetrievalException} 交由上层分类降级。
 */
public final class PostgresHybridCandidateRetriever implements CandidateRetrievalPort {

    /** RRF 平滑常数：贡献 1/(K+排名)，K=60 为通用经验值，抑制单侧排名的支配效应。 */
    private static final double RRF_K = 60.0;

    private final PostgresSelectionQuery query;
    private final LocalEmbeddingPort embeddingPort;

    public PostgresHybridCandidateRetriever(
            PostgresSelectionQuery query,
            LocalEmbeddingPort embeddingPort) {
        this.query = Objects.requireNonNull(query, "query");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
    }

    /**
     * 检索入口：先查询当前生效发布，再以默认 HYBRID 策略检索。
     *
     * @throws CandidateRetrievalException 活跃发布查询失败时
     */
    @Override
    public CandidateRetrievalResult retrieve(SelectionTarget target, int limit) {
        ActiveRelease release;
        try {
            release = query.activeRelease();
        } catch (RuntimeException exception) {
            throw new CandidateRetrievalException(
                    "public candidate retrieval is unavailable",
                    exception);
        }
        return retrieve(release, target, limit);
    }

    /** 在指定发布上以默认 HYBRID 策略检索。 */
    public CandidateRetrievalResult retrieve(
            ActiveRelease release,
            SelectionTarget target,
            int limit) {
        return retrieve(release, target, limit, SearchStrategy.HYBRID);
    }

    /**
     * 在指定发布上按请求策略检索：全文结果始终先取，向量侧按策略执行或跳过，
     * 两路结果经 RRF 融合后截断到 limit。
     *
     * @param release           锁定的内容发布
     * @param target            选择目标
     * @param limit             候选数上限
     * @param requestedStrategy KEYWORD 仅全文，HYBRID 全文+向量
     * @return 含实际检索模式（HYBRID/FTS_ONLY）的候选结果
     * @throws CandidateRetrievalException 全文查询失败，或向量查询失败且目标缺乏降级兜底信号时
     */
    public CandidateRetrievalResult retrieve(
            ActiveRelease release,
            SelectionTarget target,
            int limit,
            SearchStrategy requestedStrategy) {
        List<PostgresSelectionRow> ftsRows;
        try {
            ftsRows = query.searchFts(release.getReleaseId(), target, limit);
        } catch (RuntimeException exception) {
            throw new CandidateRetrievalException(
                    "public candidate retrieval is unavailable",
                    exception);
        }
        List<PostgresSelectionRow> vectorRows;
        RetrievalMode retrievalMode;
        try {
            if (requestedStrategy == SearchStrategy.KEYWORD) {
                return new CandidateRetrievalResult(
                        release.getReleaseVersion(), RetrievalMode.FTS_ONLY,
                        ftsRows.stream().map(this::toCandidate).limit(limit).toList());
            }
            EmbeddingVector embedding = embeddingPort.embedQuery(queryText(target));
            vectorRows = query.searchVector(
                    release.getReleaseId(),
                    embedding.copyValues(),
                    target,
                    limit);
            retrievalMode = RetrievalMode.HYBRID;
        } catch (RuntimeException exception) {
            // 无能力码与职业赛道即没有质量兜底信号，静默降级会返回低质候选，故整体失败
            if (target.getCapabilityCodes().isEmpty() && target.getCareerTrack() == null) {
                throw new CandidateRetrievalException(
                        "public vector retrieval is unavailable",
                        exception);
            }
            // 携带兜底信号的降级路径：仅用全文结果继续
            vectorRows = List.of();
            retrievalMode = RetrievalMode.FTS_ONLY;
        }

        List<SelectionCandidate> candidates = fuse(ftsRows, vectorRows, limit);
        return new CandidateRetrievalResult(
                release.getReleaseVersion(),
                retrievalMode,
                candidates);
    }

    /** RRF 融合：按主体合并两路结果，RRF 分降序、主体标识稳定排序后截断。 */
    private List<SelectionCandidate> fuse(
            List<PostgresSelectionRow> ftsRows,
            List<PostgresSelectionRow> vectorRows,
            int limit) {
        Map<String, FusedCandidate> fused = new LinkedHashMap<>();
        addRanked(fused, ftsRows);
        addRanked(fused, vectorRows);
        return fused.values().stream()
                .sorted(Comparator
                        .comparingDouble(FusedCandidate::rrfScore)
                        .reversed()
                        .thenComparing(value -> value.row().getSubjectId()))
                .limit(limit)
                .map(this::toCandidate)
                .toList();
    }

    /** 单路结果按排名累加 RRF 贡献 1/(K+排名+1)；同一主体重复出现时合并行并累加分数。 */
    private void addRanked(
            Map<String, FusedCandidate> fused,
            List<PostgresSelectionRow> rows) {
        for (int index = 0; index < rows.size(); index++) {
            PostgresSelectionRow row = rows.get(index);
            double contribution = 1.0 / (RRF_K + index + 1);
            FusedCandidate current = fused.get(row.getSubjectId());
            if (current == null) {
                fused.put(row.getSubjectId(), new FusedCandidate(row, contribution));
            } else {
                fused.put(row.getSubjectId(), new FusedCandidate(
                        mergeRows(current.row(), row),
                        current.rrfScore() + contribution));
            }
        }
    }

    /** 合并同一主体的两路行：能力码并集、claim+evidence 键去重的引用并集、质量分取较大值。 */
    private PostgresSelectionRow mergeRows(
            PostgresSelectionRow left,
            PostgresSelectionRow right) {
        List<String> allCapabilities = new ArrayList<>(left.getCapabilityCodes());
        allCapabilities.addAll(right.getCapabilityCodes());
        Map<String, EvidenceReference> evidence = new LinkedHashMap<>();
        left.getEvidenceReferences().forEach(reference ->
                evidence.put(reference.getClaimId() + "\n" + reference.getEvidenceId(), reference));
        right.getEvidenceReferences().forEach(reference ->
                evidence.put(reference.getClaimId() + "\n" + reference.getEvidenceId(), reference));
        return new PostgresSelectionRow(
                left.getSubjectId(),
                left.getSubjectKind(),
                left.getTitle(),
                left.getSummary(),
                left.getRoute(),
                left.getCareerTrack(),
                Set.copyOf(allCapabilities),
                List.copyOf(evidence.values()),
                Math.max(left.getEvidenceQuality(), right.getEvidenceQuality()));
    }

    private SelectionCandidate toCandidate(PostgresSelectionRow row) {
        return new SelectionCandidate(
                row.getSubjectId(), row.getSubjectKind(), row.getTitle(), row.getSummary(),
                row.getRoute(), row.getCareerTrack(), row.getCapabilityCodes(),
                row.getEvidenceReferences(), 1.0d, row.getEvidenceQuality(), 0.0d);
    }

    /** 融合结果转候选：RRF 分放大 30 倍并封顶 1.0 作为 targetFit，conflictPenalty 恒为 0。 */
    private SelectionCandidate toCandidate(FusedCandidate fused) {
        PostgresSelectionRow row = fused.row();
        return new SelectionCandidate(
                row.getSubjectId(),
                row.getSubjectKind(),
                row.getTitle(),
                row.getSummary(),
                row.getRoute(),
                row.getCareerTrack(),
                row.getCapabilityCodes(),
                row.getEvidenceReferences(),
                Math.min(1.0, fused.rrfScore() * 30.0),
                row.getEvidenceQuality(),
                0.0);
    }

    /** 拼接向量检索的查询文本：职业赛道 + 受众角色 + 排序后的能力码 + 目标描述。 */
    private String queryText(SelectionTarget target) {
        List<String> parts = new ArrayList<>();
        if (target.getCareerTrack() != null) {
            parts.add(target.getCareerTrack());
        }
        parts.add(target.getAudienceRole());
        parts.addAll(target.getCapabilityCodes().stream().sorted().toList());
        if (target.getGoal() != null) {
            parts.add(target.getGoal());
        }
        return String.join(" ", parts);
    }

    /** 融合中间载体（不可变）：主体行与其累计 RRF 分。 */
    private static final class FusedCandidate {

        private final PostgresSelectionRow row;
        private final double rrfScore;

        private FusedCandidate(PostgresSelectionRow row, double rrfScore) {
            this.row = row;
            this.rrfScore = rrfScore;
        }

        private PostgresSelectionRow row() {
            return row;
        }

        private double rrfScore() {
            return rrfScore;
        }
    }
}
