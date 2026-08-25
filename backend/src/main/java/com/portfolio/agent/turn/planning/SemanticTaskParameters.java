package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;

/**
 * 语义任务参数：任务执行所需的源目标类别、类型化参数、主体引用与受众画像。
 */
public final class SemanticTaskParameters {
    private final GoalKind sourceGoalKind;
    private final UserGoalProposal.GoalParameters parameters;
    private final List<GoalSubjectReference> subjects;
    private final AudienceProfile audienceProfile;

    public SemanticTaskParameters(
            GoalKind sourceGoalKind,
            UserGoalProposal.GoalParameters parameters,
            List<GoalSubjectReference> subjects) {
        this(sourceGoalKind, parameters, subjects, AudienceProfile.GUEST);
    }

    public SemanticTaskParameters(
            GoalKind sourceGoalKind,
            UserGoalProposal.GoalParameters parameters,
            List<GoalSubjectReference> subjects,
            AudienceProfile audienceProfile) {
        this.sourceGoalKind = Objects.requireNonNull(sourceGoalKind, "sourceGoalKind");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.audienceProfile = Objects.requireNonNull(audienceProfile, "audienceProfile");
    }

    public GoalKind getSourceGoalKind() { return sourceGoalKind; }
    public UserGoalProposal.GoalParameters getParameters() { return parameters; }
    public List<GoalSubjectReference> getSubjects() { return subjects; }
    public AudienceProfile getAudienceProfile() { return audienceProfile; }

    /** 受众画像：面试官/导师/HR/访客，控制表达的语气与侧重。 */
    public enum AudienceProfile { INTERVIEWER, MENTOR, HR, GUEST }
}
