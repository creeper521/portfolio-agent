package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Claim {

    private final String id;
    private final ClaimSubjectType subjectType;
    private final String subjectId;
    private final ClaimCategory category;
    private final String statement;
    private final String detail;
    private final AchievementStatus achievementStatus;
    private final ContributionType contributionType;
    private final VerificationBasis verificationBasis;
    private final ClaimVerificationStatus verificationStatus;
    private final Materiality materiality;
    private final List<String> topics;
    private final Map<String, Integer> audiencePriorities;

    @JsonCreator
    public Claim(
            @JsonProperty("id") String id,
            @JsonProperty("subjectType") ClaimSubjectType subjectType,
            @JsonProperty("subjectId") String subjectId,
            @JsonProperty("category") ClaimCategory category,
            @JsonProperty("statement") String statement,
            @JsonProperty("detail") String detail,
            @JsonProperty("achievementStatus") AchievementStatus achievementStatus,
            @JsonProperty("contributionType") ContributionType contributionType,
            @JsonProperty("verificationBasis") VerificationBasis verificationBasis,
            @JsonProperty("verificationStatus") ClaimVerificationStatus verificationStatus,
            @JsonProperty("materiality") Materiality materiality,
            @JsonProperty("topics") List<String> topics,
            @JsonProperty("audiencePriorities") Map<String, Integer> audiencePriorities
    ) {
        this.id = id;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.category = category;
        this.statement = statement;
        this.detail = detail;
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
        this.verificationBasis = verificationBasis;
        this.verificationStatus = verificationStatus;
        this.materiality = materiality;
        this.topics = List.copyOf(topics);
        this.audiencePriorities = Map.copyOf(audiencePriorities);
    }

    public String getId() { return id; }
    public ClaimSubjectType getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public ClaimCategory getCategory() { return category; }
    public String getStatement() { return statement; }
    public String getDetail() { return detail; }
    public AchievementStatus getAchievementStatus() { return achievementStatus; }
    public ContributionType getContributionType() { return contributionType; }
    public VerificationBasis getVerificationBasis() { return verificationBasis; }
    public ClaimVerificationStatus getVerificationStatus() { return verificationStatus; }
    public Materiality getMateriality() { return materiality; }
    public List<String> getTopics() { return topics; }
    public Map<String, Integer> getAudiencePriorities() { return audiencePriorities; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Claim that)) return false;
        return Objects.equals(id, that.id)
                && subjectType == that.subjectType
                && Objects.equals(subjectId, that.subjectId)
                && category == that.category
                && Objects.equals(statement, that.statement)
                && Objects.equals(detail, that.detail)
                && achievementStatus == that.achievementStatus
                && contributionType == that.contributionType
                && verificationBasis == that.verificationBasis
                && verificationStatus == that.verificationStatus
                && materiality == that.materiality
                && Objects.equals(topics, that.topics)
                && Objects.equals(audiencePriorities, that.audiencePriorities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subjectType, subjectId, category, statement, detail,
                achievementStatus, contributionType, verificationBasis, verificationStatus,
                materiality, topics, audiencePriorities);
    }
}
