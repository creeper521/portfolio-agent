package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.Claim;

import java.util.List;
import java.util.Map;

public final class ClaimResponse {

    private final String id;
    private final String subjectType;
    private final String subjectId;
    private final String category;
    private final String statement;
    private final String detail;
    private final String achievementStatus;
    private final String contributionType;
    private final String verificationBasis;
    private final String verificationStatus;
    private final String materiality;
    private final List<String> topics;
    private final Map<String, Integer> audiencePriorities;

    public ClaimResponse(Claim claim) {
        this.id = claim.getId();
        this.subjectType = claim.getSubjectType().name();
        this.subjectId = claim.getSubjectId();
        this.category = claim.getCategory().name();
        this.statement = claim.getStatement();
        this.detail = claim.getDetail();
        this.achievementStatus = claim.getAchievementStatus().name();
        this.contributionType = claim.getContributionType().name();
        this.verificationBasis = claim.getVerificationBasis().name();
        this.verificationStatus = claim.getVerificationStatus().name();
        this.materiality = claim.getMateriality().name();
        this.topics = List.copyOf(claim.getTopics());
        this.audiencePriorities = Map.copyOf(claim.getAudiencePriorities());
    }

    public String getId() { return id; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public String getCategory() { return category; }
    public String getStatement() { return statement; }
    public String getDetail() { return detail; }
    public String getAchievementStatus() { return achievementStatus; }
    public String getContributionType() { return contributionType; }
    public String getVerificationBasis() { return verificationBasis; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getMateriality() { return materiality; }
    public List<String> getTopics() { return topics; }
    public Map<String, Integer> getAudiencePriorities() { return audiencePriorities; }
}
