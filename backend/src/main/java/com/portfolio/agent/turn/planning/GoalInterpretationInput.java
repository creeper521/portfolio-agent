package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.continuation.ConversationSemanticState;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 目标解释输入：交给解释端口（模型或 fail-closed 模板路径）的封闭输入载体。
 *
 * <p>包含访客文本、最近会话消息摘要、公开主体目录、允许的目标类别与路由，
 * 以及讨论模式、锁定主体、路由候选和最近语义状态等会话延续信号。构造时
 * 校验各集合的封闭性与模式一致性，保证解释端口看到的输入永远在公开范围内。</p>
 */
public final class GoalInterpretationInput {

    private final String userText;
    private final List<String> recentMessages;
    private final List<PublicSubjectDescriptor> publicSubjects;
    private final Set<GoalKind> allowedGoalKinds;
    private final InterpretationMode interpretationMode;
    private final DiscussionState discussionState;
    private final PublicSubjectDescriptor lockedSubject;
    private final List<RouteCandidate> routeCandidates;
    private final Set<SemanticRouteProposal.Route> allowedRoutes;
    private final Set<String> allowedRecommendationConstraints;
    private final PublicSubjectDescriptor defaultSubject;
    private final SemanticTaskParameters.AudienceProfile audienceProfile;
    private final ConversationSemanticState recentSemanticState;

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                InterpretationMode.STANDARD, DiscussionState.NONE, null,
                List.of(), Set.of(
                        SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION), Set.of());
    }

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            InterpretationMode interpretationMode,
            DiscussionState discussionState,
            PublicSubjectDescriptor lockedSubject,
            List<RouteCandidate> routeCandidates,
            Set<SemanticRouteProposal.Route> allowedRoutes) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                interpretationMode, discussionState, lockedSubject,
                routeCandidates, allowedRoutes, Set.of());
    }

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            InterpretationMode interpretationMode,
            DiscussionState discussionState,
            PublicSubjectDescriptor lockedSubject,
            List<RouteCandidate> routeCandidates,
            Set<SemanticRouteProposal.Route> allowedRoutes,
            Set<String> allowedRecommendationConstraints) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                interpretationMode, discussionState, lockedSubject,
                routeCandidates, allowedRoutes, allowedRecommendationConstraints,
                null, SemanticTaskParameters.AudienceProfile.GUEST);
    }

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            InterpretationMode interpretationMode,
            DiscussionState discussionState,
            PublicSubjectDescriptor lockedSubject,
            List<RouteCandidate> routeCandidates,
            Set<SemanticRouteProposal.Route> allowedRoutes,
            Set<String> allowedRecommendationConstraints,
            PublicSubjectDescriptor defaultSubject,
            SemanticTaskParameters.AudienceProfile audienceProfile) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                interpretationMode, discussionState, lockedSubject,
                routeCandidates, allowedRoutes, allowedRecommendationConstraints,
                defaultSubject, audienceProfile, null);
    }

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            InterpretationMode interpretationMode,
            DiscussionState discussionState,
            PublicSubjectDescriptor lockedSubject,
            List<RouteCandidate> routeCandidates,
            Set<SemanticRouteProposal.Route> allowedRoutes,
            Set<String> allowedRecommendationConstraints,
            PublicSubjectDescriptor defaultSubject,
            SemanticTaskParameters.AudienceProfile audienceProfile,
            ConversationSemanticState recentSemanticState) {
        if (userText == null || userText.isBlank() || userText.length() > 2000) {
            throw new IllegalArgumentException("userText is required and bounded");
        }
        this.userText = userText;
        this.recentMessages = List.copyOf(
                Objects.requireNonNull(recentMessages, "recentMessages"));
        this.publicSubjects = List.copyOf(
                Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.allowedGoalKinds = Set.copyOf(
                Objects.requireNonNull(allowedGoalKinds, "allowedGoalKinds"));
        this.interpretationMode = Objects.requireNonNull(
                interpretationMode, "interpretationMode");
        this.discussionState = Objects.requireNonNull(
                discussionState, "discussionState");
        this.lockedSubject = lockedSubject;
        this.routeCandidates = List.copyOf(
                Objects.requireNonNull(routeCandidates, "routeCandidates"));
        this.allowedRoutes = Set.copyOf(
                Objects.requireNonNull(allowedRoutes, "allowedRoutes"));
        this.allowedRecommendationConstraints = Set.copyOf(Objects.requireNonNull(
                allowedRecommendationConstraints, "allowedRecommendationConstraints"));
        this.defaultSubject = defaultSubject;
        this.audienceProfile = Objects.requireNonNull(audienceProfile, "audienceProfile");
        this.recentSemanticState = recentSemanticState;
        if (this.allowedRecommendationConstraints.stream().anyMatch(value ->
                value == null || !value.matches(
                        "(?:CAREER_TRACK|CAPABILITY)_[A-Z0-9_]{1,64}"))) {
            throw new IllegalArgumentException(
                    "allowed recommendation constraints are invalid");
        }
        if (this.allowedRoutes.isEmpty()
                || this.routeCandidates.size() > 5
                || this.routeCandidates.stream()
                .map(RouteCandidate::getCandidateKey).distinct().count()
                != this.routeCandidates.size()) {
            throw new IllegalArgumentException(
                    "semantic interpretation scope is invalid");
        }
        if (interpretationMode == InterpretationMode.STANDARD
                && discussionState != DiscussionState.NONE
                || interpretationMode == InterpretationMode.DISCUSSION
                && discussionState == DiscussionState.NONE) {
            throw new IllegalArgumentException(
                    "interpretation mode and discussion state do not match");
        }
        if (defaultSubject != null && (interpretationMode != InterpretationMode.STANDARD
                || publicSubjects.stream().noneMatch(subject ->
                subject.getKind() == defaultSubject.getKind()
                        && subject.getReference().equals(defaultSubject.getReference())))) {
            throw new IllegalArgumentException("default subject is outside public scope");
        }
    }

    public String getUserText() { return userText; }
    public List<String> getRecentMessages() { return recentMessages; }
    public List<PublicSubjectDescriptor> getPublicSubjects() { return publicSubjects; }
    public Set<GoalKind> getAllowedGoalKinds() { return allowedGoalKinds; }
    public InterpretationMode getInterpretationMode() { return interpretationMode; }
    public DiscussionState getDiscussionState() { return discussionState; }
    public PublicSubjectDescriptor getLockedSubject() { return lockedSubject; }
    public List<RouteCandidate> getRouteCandidates() { return routeCandidates; }
    public Set<SemanticRouteProposal.Route> getAllowedRoutes() { return allowedRoutes; }
    public Set<String> getAllowedRecommendationConstraints() {
        return allowedRecommendationConstraints;
    }
    public PublicSubjectDescriptor getDefaultSubject() { return defaultSubject; }
    public SemanticTaskParameters.AudienceProfile getAudienceProfile() {
        return audienceProfile;
    }
    public ConversationSemanticState getRecentSemanticState() {
        return recentSemanticState;
    }
    /**
     * 从最近语义状态推断唯一的可续接作品集主体。
     *
     * <p>仅当最近状态中恰好存在一个"作品集续接安全"的目标时，返回该目标
     * 对应的公开主体描述符；否则返回 null（供无显式引用时的兜底绑定）。</p>
     */
    public PublicSubjectDescriptor recentPortfolioSubject() {
        if (recentSemanticState == null) return null;
        List<ConversationSemanticState.GoalSummary> safeGoals = recentSemanticState.goals().stream()
                .filter(ConversationSemanticState.GoalSummary::isPortfolioContinuationSafe)
                .toList();
        if (safeGoals.size() != 1) return null;
        return recentPortfolioSubject(safeGoals.getFirst().goalId(), null);
    }
    /**
     * 按 goalId（可选 sectionId）在最近语义状态中查找可续接的作品集主体。
     *
     * <p>目标必须作品集续接安全且恰好关联一个主体；指定 sectionId 时还必须
     * 存在该小节。查得的结果再与公开主体目录比对，不在目录内返回 null。</p>
     */
    public PublicSubjectDescriptor recentPortfolioSubject(
            String goalId, String sectionId) {
        if (recentSemanticState == null) return null;
        ConversationSemanticState.GoalSummary goal = recentSemanticState.goals().stream()
                .filter(candidate -> candidate.goalId().equals(goalId)
                        && candidate.isPortfolioContinuationSafe())
                .findFirst().orElse(null);
        if (goal == null || goal.subjects().size() != 1
                || sectionId != null && goal.sections().stream().noneMatch(
                section -> section.sectionId().equals(sectionId))) {
            return null;
        }
        ConversationSemanticState.Subject recent = goal.subjects().getFirst();
        return publicSubjects.stream().filter(subject ->
                subject.getKind() == recent.kind()
                        && subject.getReference().equals(recent.reference()))
                .findFirst().orElse(null);
    }
    /**
     * 把最近语义状态中的小节映射为作品集查询侧面。
     *
     * <p>用于校验 recentReference 指定的小节与请求侧面一致；无最近状态、
     * 未指定小节、小节不存在或其类型不对应任何侧面时返回 null。</p>
     */
    public UserGoalProposal.Facet recentSectionFacet(
            String goalId, String sectionId) {
        if (recentSemanticState == null || sectionId == null) return null;
        return recentSemanticState.goals().stream()
                .filter(goal -> goal.goalId().equals(goalId)
                        && goal.isPortfolioContinuationSafe())
                .flatMap(goal -> goal.sections().stream())
                .filter(section -> section.sectionId().equals(sectionId))
                .findFirst()
                .map(section -> switch (section.sectionKind()) {
                    case BACKGROUND -> UserGoalProposal.Facet.BACKGROUND;
                    case RESPONSIBILITY -> UserGoalProposal.Facet.RESPONSIBILITY;
                    case SOLUTION -> UserGoalProposal.Facet.SOLUTION;
                    case VERIFICATION -> UserGoalProposal.Facet.VERIFICATION;
                    case STATUS -> UserGoalProposal.Facet.STATUS;
                    case BOUNDARY, GENERAL_PRINCIPLE, PORTFOLIO_EXAMPLE,
                            RELATION, REJECTED -> null;
                })
                .orElse(null);
    }
    /**
     * 校验推荐约束全部落在公开目录内。
     *
     * @throws IllegalArgumentException 存在目录之外的约束
     */
    public void requireAllowedRecommendationConstraints(Set<String> values) {
        if (!allowedRecommendationConstraints.containsAll(values)) {
            throw new IllegalArgumentException(
                    "recommendation constraints are outside the public catalog");
        }
    }

    /** 判断指定类别与引用的公开主体是否在目录内。 */
    public boolean containsPublicSubject(
            GoalSubjectReference.Kind kind, String reference) {
        return publicSubjects.stream().anyMatch(subject ->
                subject.getKind() == kind
                        && subject.getReference().equals(reference));
    }

    /**
     * 公开主体描述符：公开目录中的一个项目或案例条目。
     *
     * <p>reviewedAliases 为已审核别名集合（默认含 reference 与 label），
     * 供主体提示匹配与澄清文本匹配使用。</p>
     */
    public static final class PublicSubjectDescriptor {
        private final GoalSubjectReference.Kind kind;
        private final String reference;
        private final String label;
        private final Set<String> reviewedAliases;

        public PublicSubjectDescriptor(
                GoalSubjectReference.Kind kind, String reference, String label) {
            this(kind, reference, label, Set.of(reference, label));
        }

        public PublicSubjectDescriptor(
                GoalSubjectReference.Kind kind,
                String reference,
                String label,
                Set<String> reviewedAliases) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.reference = requireText(reference, "reference", 128);
            this.label = requireText(label, "label", 200);
            this.reviewedAliases = Set.copyOf(
                    Objects.requireNonNull(reviewedAliases, "reviewedAliases"));
        }

        public GoalSubjectReference.Kind getKind() { return kind; }
        public String getReference() { return reference; }
        public String getLabel() { return label; }
        public Set<String> getReviewedAliases() { return reviewedAliases; }
        /** 精确匹配已审核别名（大小写敏感）；值为 null 时恒不匹配。 */
        public boolean matchesAlias(String value) {
            return value != null
                    && reviewedAliases.stream().anyMatch(value::equals);
        }
    }

    /** 路由候选：讨论模式下模型可选择的公开主体候选，candidateKey 形如 C1..C5。 */
    public static final class RouteCandidate {
        private final String candidateKey;
        private final GoalSubjectReference.Kind kind;
        private final String reference;
        private final String label;
        private final Set<String> reviewedAliases;

        public RouteCandidate(
                String candidateKey,
                GoalSubjectReference.Kind kind,
                String reference,
                String label,
                Set<String> reviewedAliases) {
            if (candidateKey == null || !candidateKey.matches("C[1-5]")) {
                throw new IllegalArgumentException("candidateKey is invalid");
            }
            this.candidateKey = candidateKey;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.reference = requireText(reference, "reference", 128);
            this.label = requireText(label, "label", 200);
            this.reviewedAliases = Set.copyOf(
                    Objects.requireNonNull(reviewedAliases, "reviewedAliases"));
        }

        public String getCandidateKey() { return candidateKey; }
        public GoalSubjectReference.Kind getKind() { return kind; }
        public String getReference() { return reference; }
        public String getLabel() { return label; }
        public Set<String> getReviewedAliases() { return reviewedAliases; }
    }

    /** 解释模式：标准模式或项目讨论模式（须与 DiscussionState 匹配）。 */
    public enum InterpretationMode { STANDARD, DISCUSSION }
    /** 讨论状态：无讨论、活跃讨论、已过期讨论。 */
    public enum DiscussionState { NONE, ACTIVE, EXPIRED }

    /** 校验文本必填且不超过上限。 */
    private static String requireText(
            String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(
                    name + " is required and bounded");
        }
        return value;
    }
}
