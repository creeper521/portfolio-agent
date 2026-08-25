package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.util.List;
import java.util.Objects;

/**
 * 预设问题的回答层投影（不可变值对象）。
 *
 * <p>携带规范问法、别名与建议答案文本，以及预设契约信息：契约版本、必需/支撑
 * claim 列表、每个必需 claim 的最少 APPROVED Evidence 数（构造期强制至少为 1）、
 * 契约是否激活及契约主体 ID；早期便捷构造器以 legacy 标识兜底。
 */
public final class AnswerQuestion {

    private final String id;
    private final String canonicalQuestion;
    private final List<String> aliases;
    private final String suggestion;
    private final List<AnswerClaimCategory> preferredClaimCategories;
    private final String contractVersion;
    private final List<String> requiredClaimIds;
    private final List<String> supportingClaimIds;
    private final int minimumApprovedEvidencePerRequiredClaim;
    private final boolean activeContract;
    private final String contractSubjectId;

    public AnswerQuestion(
            String id,
            String canonicalQuestion,
            List<String> aliases,
            String suggestion,
            List<AnswerClaimCategory> preferredClaimCategories,
            String contractVersion,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds,
            int minimumApprovedEvidencePerRequiredClaim,
            boolean activeContract,
            String contractSubjectId
    ) {
        this.id = id;
        this.canonicalQuestion = canonicalQuestion;
        this.aliases = List.copyOf(aliases);
        this.suggestion = suggestion;
        this.preferredClaimCategories =
                List.copyOf(preferredClaimCategories);
        this.contractVersion = contractVersion;
        this.requiredClaimIds = List.copyOf(requiredClaimIds);
        this.supportingClaimIds = List.copyOf(supportingClaimIds);
        if (minimumApprovedEvidencePerRequiredClaim < 1) {
            throw new IllegalArgumentException(
                    "minimumApprovedEvidencePerRequiredClaim must be at least 1");
        }
        this.minimumApprovedEvidencePerRequiredClaim = minimumApprovedEvidencePerRequiredClaim;
        this.activeContract = activeContract;
        this.contractSubjectId = contractSubjectId;
    }

    public AnswerQuestion(
            String id,
            String canonicalQuestion,
            List<String> aliases,
            String suggestion,
            List<AnswerClaimCategory> preferredClaimCategories,
            String contractVersion,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds,
            int minimumApprovedEvidencePerRequiredClaim,
            boolean activeContract
    ) {
        this(id, canonicalQuestion, aliases, suggestion, preferredClaimCategories,
                contractVersion, requiredClaimIds, supportingClaimIds,
                minimumApprovedEvidencePerRequiredClaim, activeContract, null);
    }

    public AnswerQuestion(
            String id,
            String canonicalQuestion,
            List<String> aliases,
            String suggestion,
            List<AnswerClaimCategory> preferredClaimCategories
    ) {
        this(id, canonicalQuestion, aliases, suggestion, preferredClaimCategories,
                "legacy-contract", List.of(), List.of(), 1, true, null);
    }

    public AnswerQuestion(
            String id,
            String canonicalQuestion,
            List<String> aliases,
            String suggestion
    ) {
        this(id, canonicalQuestion, aliases, suggestion, List.of());
    }

    public AnswerQuestion(String canonicalQuestion, List<String> aliases, String suggestion) {
        this("legacy-preset", canonicalQuestion, aliases, suggestion, List.of());
    }

    public String getId() {
        return id;
    }

    public String getCanonicalQuestion() {
        return canonicalQuestion;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public List<AnswerClaimCategory> getPreferredClaimCategories() {
        return preferredClaimCategories;
    }

    public String getContractVersion() { return contractVersion; }
    public List<String> getRequiredClaimIds() { return requiredClaimIds; }
    public List<String> getSupportingClaimIds() { return supportingClaimIds; }
    public int getMinimumApprovedEvidencePerRequiredClaim() {
        return minimumApprovedEvidencePerRequiredClaim;
    }
    public boolean isActiveContract() { return activeContract; }
    public String getContractSubjectId() { return contractSubjectId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerQuestion that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(canonicalQuestion, that.canonicalQuestion)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(suggestion, that.suggestion)
                && Objects.equals(
                        preferredClaimCategories,
                        that.preferredClaimCategories)
                && Objects.equals(contractVersion, that.contractVersion)
                && Objects.equals(requiredClaimIds, that.requiredClaimIds)
                && Objects.equals(supportingClaimIds, that.supportingClaimIds)
                && minimumApprovedEvidencePerRequiredClaim
                        == that.minimumApprovedEvidencePerRequiredClaim
                && activeContract == that.activeContract
                && Objects.equals(contractSubjectId, that.contractSubjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                canonicalQuestion,
                aliases,
                suggestion,
                preferredClaimCategories,
                contractVersion,
                requiredClaimIds,
                supportingClaimIds,
                minimumApprovedEvidencePerRequiredClaim,
                activeContract,
                contractSubjectId);
    }

    @Override
    public String toString() {
        return "AnswerQuestion{" +
                "id='" + id + '\'' +
                ", canonicalQuestion='" + canonicalQuestion + '\'' +
                ", aliases=" + aliases +
                ", suggestion='" + suggestion + '\'' +
                ", preferredClaimCategories=" + preferredClaimCategories +
                '}';
    }
}
