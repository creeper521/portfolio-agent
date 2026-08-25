package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;

import java.util.Objects;

/**
 * Atomic candidate: a verified claim and its complete approved evidence descriptor.
 *
 * <p>原子候选（不可变值对象）：一条已验证 claim 与其完整的已批准 Evidence 描述符。
 * 构造期不变量：claim 投影完整且验证状态为 VERIFIED、Evidence 的 publicStatus 必须为
 * APPROVED、claim 的直证列表必须包含该 Evidence（claim 与 Evidence 关联完整），
 * 任一不满足抛出 IllegalArgumentException。toString 只输出布尔标记，不泄露内容。
 */
public final class ClaimEvidenceCandidate {

    private final String subjectId;
    private final AnswerClaimProjection claim;
    private final PublicEvidenceDescriptor evidence;
    private final String retrievalTarget;

    public ClaimEvidenceCandidate(
            String subjectId, AnswerClaimProjection claim,
            PublicEvidenceDescriptor evidence, String retrievalTarget) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.retrievalTarget = requireText(retrievalTarget, "retrievalTarget");
        if (claim.getId() == null || claim.getId().isBlank()
                || claim.getCategory() == null || claim.getStatement().isBlank()
                || claim.getDetail().isBlank()) {
            throw new IllegalArgumentException("claim projection is incomplete");
        }
        if (claim.getVerificationStatus() != com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("candidate claim must be verified");
        }
        if (!"APPROVED".equals(evidence.getPublicStatus())) {
            throw new IllegalArgumentException("candidate evidence must be approved");
        }
        if (!claim.getDirectEvidenceIds().contains(evidence.getEvidenceId())) {
            throw new IllegalArgumentException("claim and evidence link is incomplete");
        }
    }

    public String getSubjectId() { return subjectId; }
    public AnswerClaimProjection getClaim() { return claim; }
    public PublicEvidenceDescriptor getEvidence() { return evidence; }
    public String getRetrievalTarget() { return retrievalTarget; }
    public String getClaimId() { return claim.getId(); }
    public String getEvidenceCode() { return evidence.getEvidenceCode(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClaimEvidenceCandidate that)) return false;
        return subjectId.equals(that.subjectId) && claim.equals(that.claim)
                && evidence.equals(that.evidence) && retrievalTarget.equals(that.retrievalTarget);
    }

    @Override
    public int hashCode() { return Objects.hash(subjectId, claim, evidence, retrievalTarget); }

    @Override
    public String toString() {
        return "ClaimEvidenceCandidate{hasClaim=true, hasEvidence=true}";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

