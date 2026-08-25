package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionCandidate;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgePassageRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgeQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgeQueryResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresRetrievalFailureClassifier;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PostgreSQL 查询结果到最终候选模型的唯一适配器。
 *
 * <p>关键不变量：执行前后检查 TurnDeadline（超时归类 CANCELLED）、候选发布版本必须与
 * invocation 的 contentReleaseId 一致（否则 INTEGRITY_FAILURE），仅保留带 APPROVED
 * Evidence 引用且有知识段落的主体；异常经 {@link PostgresRetrievalFailureClassifier}
 * 分类后以失败结果返回，不向上抛出。
 */
public final class PostgresPortfolioRetrieverAdapter implements PortfolioRetrieverPort {

    private final PostgresKnowledgeQuery knowledgeQuery;

    public PostgresPortfolioRetrieverAdapter(PostgresKnowledgeQuery knowledgeQuery) {
        this.knowledgeQuery = Objects.requireNonNull(knowledgeQuery, "knowledgeQuery");
    }

    /**
     * 执行一次 PostgreSQL 检索并装配候选集。
     *
     * @param invocation 当前 Evidence 调用（含获准范围与单主体证据上限）
     * @param request    检索请求
     * @param deadline   Turn 截止时间；进入前或查询后已过期均返回 CANCELLED 失败
     * @return 成功携带候选集；发布版本不一致返回 INTEGRITY_FAILURE，
     *         其余异常按分类器归入对应失败
     */
    @Override
    public RetrievalAttemptResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(deadline, "deadline");
        if (deadline.isExpired()) {
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
        }
        try {
            PostgresKnowledgeQueryResult result = knowledgeQuery.retrieve(invocation, request);
            if (deadline.isExpired()) {
                return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
            }
            if (!invocation.getContentReleaseId().equals(
                    result.getCandidates().getReleaseVersion())) {
                return RetrievalAttemptResult.failure(RetrievalAttemptFailure.INTEGRITY_FAILURE);
            }
            return RetrievalAttemptResult.success(candidateSet(invocation, result));
        } catch (RuntimeException failure) {
            return RetrievalAttemptResult.failure(
                    PostgresRetrievalFailureClassifier.classify(failure));
        }
    }

    /** 装配候选集：只保留存在 APPROVED 引用且命中知识段落的主体，再按主体转换。 */
    private PortfolioCandidateSet candidateSet(
            PortfolioEvidenceInvocation invocation,
            PostgresKnowledgeQueryResult result) {
        Map<String, List<PostgresKnowledgePassageRow>> passagesBySubject = result.getPassages().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PostgresKnowledgePassageRow::getSubjectId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<CandidateSubject> subjects = result.getCandidates().getCandidates().stream()
                .filter(candidate -> candidate.getEvidenceReferences().stream()
                        .anyMatch(EvidenceReference::isApproved))
                .filter(candidate -> passagesBySubject.containsKey(candidate.getSubjectId()))
                .map(candidate -> toSubject(
                        result.getCandidates().getReleaseVersion(), candidate,
                        passagesBySubject.get(candidate.getSubjectId()),
                        invocation.getMaximumEvidenceUnitsPerSubject()))
                .toList();
        return new PortfolioCandidateSet(
                result.getCandidates().getReleaseVersion(),
                invocation.getSubjectScope(),
                subjects);
    }

    /**
     * 把单个主体的段落×引用展开为原子候选：跳过未批准或 claimId+evidenceId 重复的组合，
     * 并在达到单主体证据上限后停止；Evidence 路由使用公开查询端点，有效期设为长期上限。
     */
    private CandidateSubject toSubject(
            String contentVersion,
            SelectionCandidate subject,
            List<PostgresKnowledgePassageRow> passages,
            int maximumEvidenceUnits) {
        List<ClaimEvidenceCandidate> candidates = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        for (PostgresKnowledgePassageRow passage : passages) {
            for (EvidenceReference evidence : passage.getEvidenceReferences()) {
                if (candidates.size() >= maximumEvidenceUnits) {
                    break;
                }
                String identity = passage.getClaimId() + "\u0000" + evidence.getEvidenceId();
                if (!evidence.isApproved() || !identities.add(identity)) {
                    continue;
                }
                PublicEvidenceDescriptor descriptor = new PublicEvidenceDescriptor(
                        evidence.getEvidenceId(), evidence.getEvidenceCode(), evidence.getLabel(),
                        contentVersion, evidence.getPublicStatus(),
                        PublicEvidenceDescriptor.SourceType.valueOf(evidence.getEvidenceType()),
                        subject.getRoute(), "/evidence?evidence=" + evidence.getEvidenceId(),
                        LocalDate.of(9999, 12, 31));
                candidates.add(new ClaimEvidenceCandidate(
                        subject.getSubjectId(), passage.getClaim(), descriptor,
                        passage.getClaimCategory().name()));
            }
            if (candidates.size() >= maximumEvidenceUnits) {
                break;
            }
        }
        return new CandidateSubject(
                subject.getSubjectId(), subject.getRoute(), subject.getTitle(),
                contentVersion, subject.getCareerTrack(),
                subject.getCapabilityCodes(), candidates);
    }
}
