package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.ActiveRelease;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.JdbcPostgresSelectionQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresHybridCandidateRetriever;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.RetrievalMode;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionCandidate;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL 公开检索查询；输入只接受最终 Turn 调用与检索策略。
 *
 * <p>两条检索路径：EXACT 主体范围走精确标识查询（段落按 facet/维度类别过滤并截断到 50），
 * 其余走混合候选检索；带推荐约束且首轮命中不足或候选不满足约束时，再发起一次放宽
 * （无赛道/能力过滤）的补充检索并按主体合并。检索文本由固定受控词表与约束前缀拼装，
 * 访问者原文不进入 SQL（隐私边界）。
 */
public final class JdbcPostgresKnowledgeQuery implements PostgresKnowledgeQuery {

    /** 固定受众角色，标识查询来自检索管线而非访问者输入。 */
    private static final String FIXED_AUDIENCE_ROLE = "PORTFOLIO_RETRIEVAL";
    /** 受控检索词基串：检索词表固定，避免把访问者自由文本下发到 SQL。 */
    private static final String CONTROLLED_QUERY = "portfolio-profile-v1";
    /** 混合路径的候选主体数上限。 */
    private static final int MAX_SUBJECTS = 50;

    private final PostgresSelectionQuery selectionQuery;
    private final PostgresHybridCandidateRetriever candidateRetriever;
    private final PostgresFactPassageQuery passageQuery;

    public JdbcPostgresKnowledgeQuery(JdbcTemplate jdbcTemplate, LocalEmbeddingPort embeddingPort) {
        this(
                new JdbcPostgresSelectionQuery(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate")),
                embeddingPort,
                new JdbcPostgresFactPassageQuery(jdbcTemplate));
    }

    public JdbcPostgresKnowledgeQuery(
            PostgresSelectionQuery selectionQuery,
            LocalEmbeddingPort embeddingPort,
            PostgresFactPassageQuery passageQuery) {
        this.selectionQuery = Objects.requireNonNull(selectionQuery, "selectionQuery");
        this.candidateRetriever = new PostgresHybridCandidateRetriever(
                selectionQuery,
                Objects.requireNonNull(embeddingPort, "embeddingPort"));
        this.passageQuery = Objects.requireNonNull(passageQuery, "passageQuery");
    }

    /**
     * 执行一次公开知识检索：锁定活跃发布后按主体范围选择精确或混合路径，
     * 返回候选结果与命中的事实段落。
     *
     * @param invocation 当前 Evidence 调用（主体范围、推荐约束与规模）
     * @param request    检索请求（策略：KEYWORD 或 HYBRID）
     * @return 候选与段落的组合结果
     */
    @Override
    public PostgresKnowledgeQueryResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(request, "request");
        SelectionTarget target = new SelectionTarget(
                invocation.getRecommendationCareerTrack(), FIXED_AUDIENCE_ROLE,
                invocation.getRecommendationCapabilityCodes(),
                controlledQuery(invocation),
                invocation.getRequestedSize() == 0 ? 1 : invocation.getRequestedSize());
        ActiveRelease release = selectionQuery.activeRelease();
        if (invocation.getSubjectScope().getMode() == AuthorizedSubjectScope.Mode.EXACT) {
            return retrieveExact(release, invocation, target);
        }
        CandidateRetrievalResult candidates = candidateRetriever.retrieve(
                release, target, MAX_SUBJECTS, request.getStrategy());
        if (!invocation.getRecommendationConstraints().isEmpty()
                && (candidates.getCandidates().size() < invocation.getRequestedSize()
                || candidates.getCandidates().stream().anyMatch(value ->
                !matchesAll(value, invocation)))) {
            SelectionTarget broadTarget = new SelectionTarget(
                    null, FIXED_AUDIENCE_ROLE, Set.of(), controlledQuery(invocation),
                    invocation.getRequestedSize());
            CandidateRetrievalResult broad = candidateRetriever.retrieve(
                    release, broadTarget, MAX_SUBJECTS, request.getStrategy());
            java.util.LinkedHashMap<String, SelectionCandidate> merged =
                    new java.util.LinkedHashMap<>();
            candidates.getCandidates().forEach(value ->
                    merged.put(value.getSubjectId(), value));
            broad.getCandidates().forEach(value ->
                    merged.putIfAbsent(value.getSubjectId(), value));
            candidates = new CandidateRetrievalResult(
                    release.getReleaseVersion(), candidates.getRetrievalMode(),
                    List.copyOf(merged.values()));
        }
        List<String> subjectIds = candidates.getCandidates().stream()
                .map(SelectionCandidate::getSubjectId)
                .toList();
        return new PostgresKnowledgeQueryResult(
                candidates,
                passageQuery.findPassages(release.getReleaseId(), subjectIds));
    }

    /** 构造受控检索词：固定基串 + 排序后的约束前缀；无约束时仅基串。 */
    private String controlledQuery(PortfolioEvidenceInvocation invocation) {
        if (invocation.getRecommendationConstraints().isEmpty()) {
            return CONTROLLED_QUERY;
        }
        return CONTROLLED_QUERY + " " + String.join(" ",
                invocation.getRecommendationConstraints().stream().sorted().toList());
    }

    /** 判断候选是否满足全部推荐约束（职业赛道相等且能力码全包含）；无约束维度恒通过。 */
    private boolean matchesAll(
            SelectionCandidate candidate, PortfolioEvidenceInvocation invocation) {
        return (invocation.getRecommendationCareerTrack() == null
                || invocation.getRecommendationCareerTrack().equals(candidate.getCareerTrack()))
                && candidate.getCapabilityCodes().containsAll(
                invocation.getRecommendationCapabilityCodes());
    }

    /**
     * EXACT 范围专用路径：按获准主体标识精确查询候选，段落再按调用 facet/维度
     * 对应的 claim 类别过滤并截断；范围主体为空时返回空结果。
     */
    private PostgresKnowledgeQueryResult retrieveExact(
            ActiveRelease release,
            PortfolioEvidenceInvocation invocation,
            SelectionTarget target) {
        List<String> subjectIds = invocation.getSubjectScope().getSubjects().stream()
                .map(reference -> reference.getReference())
                .toList();
        if (subjectIds.isEmpty()) {
            return new PostgresKnowledgeQueryResult(
                    new CandidateRetrievalResult(
                            release.getReleaseVersion(), RetrievalMode.FTS_ONLY, List.of()),
                    List.of());
        }
        List<SelectionCandidate> candidates = selectionQuery.findByIds(
                        release.getReleaseId(), subjectIds, target).stream()
                .map(this::exactCandidate)
                .toList();
        List<AnswerClaimCategory> categories = categories(invocation);
        List<PostgresKnowledgePassageRow> passages = passageQuery
                .findPassages(release.getReleaseId(), subjectIds).stream()
                .filter(row -> categories.isEmpty() || categories.contains(row.getClaimCategory()))
                .limit(50)
                .toList();
        return new PostgresKnowledgeQueryResult(
                new CandidateRetrievalResult(
                        release.getReleaseVersion(), RetrievalMode.FTS_ONLY, candidates),
                passages);
    }

    /** 精确查询行转候选：targetFit 固定 1.0（精确命中即满分），冲突惩罚为 0。 */
    private SelectionCandidate exactCandidate(PostgresSelectionRow row) {
        return new SelectionCandidate(
                row.getSubjectId(), row.getSubjectKind(), row.getTitle(), row.getSummary(),
                row.getRoute(), row.getCareerTrack(), row.getCapabilityCodes(),
                row.getEvidenceReferences(), 1.0d, row.getEvidenceQuality(), 0.0d);
    }

    /** 把调用的 facet 与对比维度映射为 claim 类别集合；未知维度抛出异常（fail-closed）。 */
    private List<AnswerClaimCategory> categories(PortfolioEvidenceInvocation invocation) {
        LinkedHashSet<AnswerClaimCategory> categories = new LinkedHashSet<>();
        invocation.getFacets().forEach(facet -> categories.addAll(switch (facet) {
            case BACKGROUND -> List.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> List.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPLEMENTATION -> List.of(AnswerClaimCategory.IMPLEMENTATION);
            case TECHNICAL_DECISION -> List.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case VERIFICATION -> List.of(AnswerClaimCategory.VERIFICATION);
            case OUTCOME -> List.of(AnswerClaimCategory.OUTCOME);
            case LIMITATION -> List.of(AnswerClaimCategory.LIMITATION);
            case RECOMMENDATION -> List.of(
                    AnswerClaimCategory.BACKGROUND,
                    AnswerClaimCategory.IMPLEMENTATION,
                    AnswerClaimCategory.VERIFICATION,
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.TECHNICAL_DECISION);
        }));
        invocation.getDimensions().forEach(dimension -> categories.add(switch (dimension) {
            case "ARCHITECTURE" -> AnswerClaimCategory.TECHNICAL_DECISION;
            case "IMPLEMENTATION" -> AnswerClaimCategory.IMPLEMENTATION;
            case "OUTCOME" -> AnswerClaimCategory.OUTCOME;
            case "RISKS" -> AnswerClaimCategory.LIMITATION;
            case "VERIFICATION" -> AnswerClaimCategory.VERIFICATION;
            default -> throw new IllegalArgumentException(
                    "unsupported portfolio comparison dimension");
        }));
        return List.copyOf(categories);
    }
}
