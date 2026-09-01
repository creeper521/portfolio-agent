package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceCapability;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Raw retrieval output. Attempt metadata is deliberately not business data.
 *
 * <p>检索候选集（不可变值对象）：一次成功检索产出的原始主体与候选 Evidence 集合。
 * 构造期强制不变量：候选集与执行范围的 contentReleaseId 必须一致（禁止跨快照混合）、
 * 主体数不超过 64、Evidence 单元总数不超过 128、主体标识两两不同、且每个主体都落在
 * 获准的 AuthorizedSubjectScope 内（EXACT 模式要求主体标识与正式类型成对匹配）。
 * 违反普通结构不变量抛出 IllegalArgumentException；主体类型合同失败使用 typed
 * capability integrity failure。
 */
public final class PortfolioCandidateSet {
    private final String contentReleaseId;
    private final AuthorizedSubjectScope executedScope;
    private final List<CandidateSubject> subjects;

    public PortfolioCandidateSet(
            String contentReleaseId,
            AuthorizedSubjectScope executedScope,
            List<CandidateSubject> subjects) {
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.executedScope = Objects.requireNonNull(executedScope, "executedScope");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        if (!contentReleaseId.equals(executedScope.getContentReleaseId())) {
            throw new IllegalArgumentException("candidate release conflicts with scope");
        }
        if (subjects.size() > 64) throw new IllegalArgumentException("too many subjects");
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        int units = 0;
        for (CandidateSubject subject : subjects) {
            if (!contentReleaseId.equals(subject.getContentVersion())
                    || !identities.add(subject.getSubjectId())) {
                throw new IllegalArgumentException("candidate subjects are inconsistent");
            }
            if (!authorized(subject)) {
                throw new IllegalArgumentException("candidate subject is outside authorized scope");
            }
            units += subject.getCandidates().size();
        }
        if (units > 128) throw new IllegalArgumentException("too many evidence units");
    }

    /** 判断候选主体是否在获准范围内：ALL_PUBLISHED 放行全部，EXACT 需标识与正式类型同时匹配。 */
    private boolean authorized(CandidateSubject subject) {
        if (executedScope.getMode() == AuthorizedSubjectScope.Mode.ALL_PUBLISHED) return true;
        boolean referenceExists = executedScope.getSubjects().stream().anyMatch(value ->
                value.getReference().equals(subject.getSubjectId()));
        if (!referenceExists) return false;
        boolean pairMatches = executedScope.getSubjects().stream().anyMatch(value ->
                value.getReference().equals(subject.getSubjectId())
                        && scopeKind(value) == subject.getSubjectKind());
        if (!pairMatches) {
            throw new PortfolioEvidenceCapability.PortfolioCapabilityException(
                    PortfolioEvidenceCapability.IntegrityReason
                            .EXACT_SCOPE_SUBJECT_KIND_MISMATCH);
        }
        return true;
    }

    private PortfolioSubjectKind scopeKind(AuthorizedSubjectScope.Subject subject) {
        return switch (subject.getKind()) {
            case PROJECT -> PortfolioSubjectKind.PROJECT;
            case CASE -> PortfolioSubjectKind.CASE;
            case RESULT -> throw new PortfolioEvidenceCapability.PortfolioCapabilityException(
                    PortfolioEvidenceCapability.IntegrityReason.UNRESOLVED_RESULT_SUBJECT);
        };
    }

    public String getContentReleaseId() { return contentReleaseId; }
    public AuthorizedSubjectScope getExecutedScope() { return executedScope; }
    public List<CandidateSubject> getSubjects() { return subjects; }
    public int getEvidenceUnitCount() {
        return subjects.stream().mapToInt(value -> value.getCandidates().size()).sum();
    }
}
