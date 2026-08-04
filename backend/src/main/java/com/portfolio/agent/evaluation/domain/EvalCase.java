package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import java.util.List;

public final class EvalCase {

    private final String id;
    private final String title;
    private final EvalSplit split;
    private final EvalOrigin origin;
    private final EvalRiskLevel riskLevel;
    private final String reviewStatus;
    private final String reviewerId;
    private final String sourceCategory;
    private final String difficultyReason;
    private final String firstExposedDatasetVersion;
    private final List<String> tags;
    private final List<EvalMessage> inputMessages;
    private final List<EvalSubjectRef> expectedSubjects;
    private final List<AnswerResolution> allowedResolutions;
    private final List<ConversationAnswerScope> allowedAnswerScopes;
    private final List<String> requiredClaimIds;
    private final List<String> allowedEvidenceIds;
    private final List<String> forbiddenSubjectSlugs;
    private final List<String> forbiddenBehaviors;
    private final List<EvalLayer> layers;
    private final int providerTrials;
    private final List<EvalGraderRule> graders;
    private final List<EvalSubjectRef> maintenanceSubjects;
    private final boolean generatedFromBundle;
    private final Boolean generatedFromBundleValue;

    @JsonCreator
    public EvalCase(@JsonProperty("id") String id,
                    @JsonProperty("title") String title,
                    @JsonProperty("split") EvalSplit split,
                    @JsonProperty("origin") EvalOrigin origin,
                    @JsonProperty("riskLevel") EvalRiskLevel riskLevel,
                    @JsonProperty("reviewStatus") String reviewStatus,
                    @JsonProperty("reviewerId") String reviewerId,
                    @JsonProperty("sourceCategory") String sourceCategory,
                    @JsonProperty("difficultyReason") String difficultyReason,
                    @JsonProperty("firstExposedDatasetVersion") String firstExposedDatasetVersion,
                    @JsonProperty("tags") List<String> tags,
                    @JsonProperty("input") Input input,
                    @JsonProperty("oracle") Oracle oracle,
                    @JsonProperty("expectations") Expectations expectations,
                    @JsonProperty("execution") Execution execution,
                    @JsonProperty("graders") List<EvalGraderRule> graders,
                    @JsonProperty("maintenance") Maintenance maintenance) {
        this.id = id;
        this.title = title;
        this.split = split;
        this.origin = origin;
        this.riskLevel = riskLevel;
        this.reviewStatus = reviewStatus;
        this.reviewerId = reviewerId;
        this.sourceCategory = sourceCategory;
        this.difficultyReason = difficultyReason;
        this.firstExposedDatasetVersion = firstExposedDatasetVersion;
        this.tags = immutable(tags);
        this.inputMessages = input == null ? null : immutable(input.messages);
        this.expectedSubjects = oracle == null ? null : immutable(oracle.expectedSubjects);
        this.allowedResolutions = expectations == null ? null : immutable(expectations.resolution);
        this.allowedAnswerScopes = expectations == null ? null : immutable(expectations.answerScope);
        this.requiredClaimIds = expectations == null ? null : immutable(expectations.requiredClaimIds);
        this.allowedEvidenceIds = expectations == null ? null : immutable(expectations.allowedEvidenceIds);
        this.forbiddenSubjectSlugs = expectations == null ? null : immutable(expectations.forbiddenSubjectSlugs);
        this.forbiddenBehaviors = expectations == null ? null : immutable(expectations.forbiddenBehaviors);
        this.layers = execution == null ? null : immutable(execution.layers);
        this.providerTrials = execution == null ? 0 : execution.providerTrials;
        this.graders = immutable(graders);
        this.maintenanceSubjects = maintenance == null ? null : immutable(maintenance.subjectRefs);
        this.generatedFromBundle = maintenance != null
                && Boolean.TRUE.equals(maintenance.generatedFromBundle);
        this.generatedFromBundleValue = maintenance == null
                ? null : maintenance.generatedFromBundle;
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public EvalSplit getSplit() { return split; }
    public EvalOrigin getOrigin() { return origin; }
    public EvalRiskLevel getRiskLevel() { return riskLevel; }
    public String getReviewStatus() { return reviewStatus; }
    public String getReviewerId() { return reviewerId; }
    public String getSourceCategory() { return sourceCategory; }
    public String getDifficultyReason() { return difficultyReason; }
    public String getFirstExposedDatasetVersion() { return firstExposedDatasetVersion; }
    public List<String> getTags() { return tags; }
    public List<EvalMessage> getInputMessages() { return inputMessages; }
    public List<EvalSubjectRef> getExpectedSubjects() { return expectedSubjects; }
    public List<AnswerResolution> getAllowedResolutions() { return allowedResolutions; }
    public List<ConversationAnswerScope> getAllowedAnswerScopes() { return allowedAnswerScopes; }
    public List<String> getRequiredClaimIds() { return requiredClaimIds; }
    public List<String> getAllowedEvidenceIds() { return allowedEvidenceIds; }
    public List<String> getForbiddenSubjectSlugs() { return forbiddenSubjectSlugs; }
    public List<String> getForbiddenBehaviors() { return forbiddenBehaviors; }
    public List<EvalLayer> getLayers() { return layers; }
    public int getProviderTrials() { return providerTrials; }
    public List<EvalGraderRule> getGraders() { return graders; }
    public List<EvalSubjectRef> getMaintenanceSubjects() { return maintenanceSubjects; }
    public boolean isGeneratedFromBundle() { return generatedFromBundle; }
    public Boolean getGeneratedFromBundleValue() {
        return generatedFromBundleValue;
    }

    public static final class Input {
        private final List<EvalMessage> messages;

        @JsonCreator
        public Input(@JsonProperty("messages") List<EvalMessage> messages) {
            this.messages = messages;
        }
    }

    public static final class Oracle {
        private final List<EvalSubjectRef> expectedSubjects;

        @JsonCreator
        public Oracle(@JsonProperty("expectedSubjects") List<EvalSubjectRef> expectedSubjects) {
            this.expectedSubjects = expectedSubjects;
        }
    }

    public static final class Expectations {
        private final List<AnswerResolution> resolution;
        private final List<ConversationAnswerScope> answerScope;
        private final List<String> requiredClaimIds;
        private final List<String> allowedEvidenceIds;
        private final List<String> forbiddenSubjectSlugs;
        private final List<String> forbiddenBehaviors;

        @JsonCreator
        public Expectations(@JsonProperty("resolution") List<AnswerResolution> resolution,
                            @JsonProperty("answerScope") List<ConversationAnswerScope> answerScope,
                            @JsonProperty("requiredClaimIds") List<String> requiredClaimIds,
                            @JsonProperty("allowedEvidenceIds") List<String> allowedEvidenceIds,
                            @JsonProperty("forbiddenSubjectSlugs") List<String> forbiddenSubjectSlugs,
                            @JsonProperty("forbiddenBehaviors") List<String> forbiddenBehaviors) {
            this.resolution = resolution;
            this.answerScope = answerScope;
            this.requiredClaimIds = requiredClaimIds;
            this.allowedEvidenceIds = allowedEvidenceIds;
            this.forbiddenSubjectSlugs = forbiddenSubjectSlugs;
            this.forbiddenBehaviors = forbiddenBehaviors;
        }
    }

    public static final class Execution {
        private final List<EvalLayer> layers;
        private final int providerTrials;

        @JsonCreator
        public Execution(@JsonProperty("layers") List<EvalLayer> layers,
                         @JsonProperty("providerTrials") int providerTrials) {
            this.layers = layers;
            this.providerTrials = providerTrials;
        }
    }

    public static final class Maintenance {
        private final List<EvalSubjectRef> subjectRefs;
        private final Boolean generatedFromBundle;

        @JsonCreator
        public Maintenance(@JsonProperty("subjectRefs") List<EvalSubjectRef> subjectRefs,
                           @JsonProperty("generatedFromBundle") Boolean generatedFromBundle) {
            this.subjectRefs = subjectRefs;
            this.generatedFromBundle = generatedFromBundle;
        }
    }
}
