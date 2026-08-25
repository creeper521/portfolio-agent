package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.Objects;

/**
 * Evidence 引用（不可变值对象）：claim 与一条公开 Evidence 的关联描述。
 *
 * <p>便捷构造器默认 publicStatus=APPROVED、类型 DOCUMENT；运行时仅允许
 * {@link #isApproved()} 为 true 的引用进入公开回答（隐私边界）。
 */
public final class EvidenceReference {

    private final String claimId;
    private final String evidenceId;
    private final String evidenceCode;
    private final String label;
    private final String evidenceType;
    private final String publicStatus;

    public EvidenceReference(String claimId, String evidenceId, String label) {
        this(claimId, evidenceId, label, "APPROVED");
    }

    public EvidenceReference(
            String claimId,
            String evidenceId,
            String label,
            String publicStatus) {
        this(claimId, evidenceId, evidenceId, label, "DOCUMENT", publicStatus);
    }

    public EvidenceReference(
            String claimId,
            String evidenceId,
            String evidenceCode,
            String label,
            String evidenceType,
            String publicStatus) {
        this.claimId = requireText(claimId, "claimId");
        this.evidenceId = requireText(evidenceId, "evidenceId");
        this.evidenceCode = requireText(evidenceCode, "evidenceCode");
        this.label = requireText(label, "label");
        this.evidenceType = requireText(evidenceType, "evidenceType");
        this.publicStatus = requireText(publicStatus, "publicStatus");
    }

    public String getClaimId() {
        return claimId;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getEvidenceCode() { return evidenceCode; }

    public String getLabel() {
        return label;
    }

    public String getEvidenceType() { return evidenceType; }

    public boolean isApproved() {
        return "APPROVED".equals(publicStatus);
    }

    public String getPublicStatus() {
        return publicStatus;
    }

    private String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
