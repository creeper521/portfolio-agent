package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;

public final class ClaimEvidenceLinkResponse {

    private final String id;
    private final String claimId;
    private final String evidenceId;
    private final String supportType;
    private final String scope;

    public ClaimEvidenceLinkResponse(ClaimEvidenceLink link) {
        this.id = link.getId();
        this.claimId = link.getClaimId();
        this.evidenceId = link.getEvidenceId();
        this.supportType = link.getSupportType().name();
        this.scope = link.getScope();
    }

    public String getId() { return id; }
    public String getClaimId() { return claimId; }
    public String getEvidenceId() { return evidenceId; }
    public String getSupportType() { return supportType; }
    public String getScope() { return scope; }
}
