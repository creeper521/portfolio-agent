package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.retrieval.ClaimEvidenceCandidate;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PublicEvidenceDescriptor;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioCandidateSet;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 原始检索候选到已验证 Evidence 的唯一“全有或全无”晋级边界（Validator）。
 *
 * <p>在检索成功后、语义结果组装前执行：校验候选集与每个候选的 contentReleaseId
 * 一致、Evidence 的 publicStatus 必须为 APPROVED 且未过 validUntil 有效期，
 * 并对 claimId + evidenceCode 做整批去重。任何一条不满足都抛出
 * IllegalArgumentException，整批候选作废（fail-closed），绝不部分放行。
 */
public final class EvidencePromotionValidator {
    private final Clock clock;
    public EvidencePromotionValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 将候选集整批晋级为 {@link ValidatedEvidenceBundle}。
     *
     * @param candidateSet             检索产出的原始候选集
     * @param expectedContentReleaseId 本次调用获准的内容发布 ID
     * @return 与候选集同范围、同发布 ID 的已验证 Evidence 捆绑包
     * @throws IllegalArgumentException 候选集发布 ID 不一致（CONTENT_RELEASE_MISMATCH），
     *         或存在未批准、已过期或重复的候选（INTEGRITY_FAILURE）时抛出，整批作废
     */
    public ValidatedEvidenceBundle promote(
            PortfolioCandidateSet candidateSet, String expectedContentReleaseId) {
        Objects.requireNonNull(candidateSet, "candidateSet");
        if (!expectedContentReleaseId.equals(candidateSet.getContentReleaseId())) {
            throw new IllegalArgumentException("CONTENT_RELEASE_MISMATCH");
        }
        List<ValidatedEvidenceUnit> units = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        candidateSet.getSubjects().forEach(subject -> subject.getCandidates().forEach(candidate -> {
            validate(candidate, expectedContentReleaseId);
            PublicEvidenceDescriptor evidence = candidate.getEvidence();
            // 用 NUL 分隔符拼去重键，避免 claimId 与 evidenceCode 直接拼接产生歧义碰撞
            String identity = candidate.getClaimId() + "\u0000" + evidence.getEvidenceCode();
            if (!identities.add(identity)) throw new IllegalArgumentException("INTEGRITY_FAILURE");
            units.add(new ValidatedEvidenceUnit(
                    subject.getSubjectId(), subject.getTitle(), subject.getCareerTrack(),
                    subject.getCapabilityCodes(), candidate.getClaim(),
                    new PublicSourceReferenceValue(
                            evidence.getEvidenceCode(), evidence.getLabel(),
                            evidence.getContentVersion(), evidence.getSourceType().name(),
                            evidence.getSubjectRoute(), evidence.getEvidenceRoute())));
        }));
        return new ValidatedEvidenceBundle(
                candidateSet.getExecutedScope(), expectedContentReleaseId, units);
    }

    /** 单候选完整性校验：发布 ID 一致、publicStatus=APPROVED、未过 validUntil 有效期，任一失败即判 INTEGRITY_FAILURE。 */
    private void validate(ClaimEvidenceCandidate candidate, String releaseId) {
        PublicEvidenceDescriptor evidence = candidate.getEvidence();
        if (!releaseId.equals(evidence.getContentVersion())
                || !"APPROVED".equals(evidence.getPublicStatus())
                || evidence.getValidUntil() != null
                && evidence.getValidUntil().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("INTEGRITY_FAILURE");
        }
    }
}
