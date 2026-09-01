package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一次作品集 Evidence 检索的不可变调用参数（value object）。
 *
 * <p>由 {@link PortfolioInvocationFactory} 从 SemanticTask 装配，完整描述：任务类型、
 * 获准主体范围、可参与本次 operation 的主体类型、检索切面（facets）或对比维度
 * （dimensions）、回答深度、推荐数量与约束、内容发布 ID，以及主/fallback 检索的
 * backend 与 strategy 组合。
 *
 * <p>关键不变量在构造期校验；普通参数错误抛 IllegalArgumentException，主体类型
 * 合同错误抛 typed capability integrity failure：
 * requestedSize 仅对 PORTFOLIO_RECOMMEND 有效且限定 1..5，其余任务必须为 0 且不带
 * 推荐约束；推荐约束必须是 CAREER_TRACK_ 或 CAPABILITY_ 前缀的受控编码；对比维度
 * 必须是受支持的枚举名；facets 与 dimensions 至少有一项；contentReleaseId 必须与
 * 主体范围一致；allowedSubjectKinds 非空，Recommendation 必须严格为 PROJECT，EXACT
 * 引用必须与允许类型相容且不得含未解析 RESULT；fallback 的 backend 与 strategy
 * 必须成对出现或同时为空。
 */
public final class PortfolioEvidenceInvocation {
    private final SemanticTask.Type taskType;
    private final AuthorizedSubjectScope subjectScope;
    private final Set<PortfolioSubjectKind> allowedSubjectKinds;
    private final List<FacetProfile> facets;
    private final List<String> dimensions;
    private final UserGoalProposal.Depth depth;
    private final int requestedSize;
    private final Set<String> recommendationConstraints;
    private final String contentReleaseId;
    private final CorpusBackend primaryBackend;
    private final SearchStrategy primaryStrategy;
    private final CorpusBackend fallbackBackend;
    private final SearchStrategy fallbackStrategy;

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            Set<PortfolioSubjectKind> allowedSubjectKinds,
            List<FacetProfile> facets, List<String> dimensions,
            UserGoalProposal.Depth depth,
            int requestedSize, Set<String> recommendationConstraints,
            String contentReleaseId, CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy, CorpusBackend fallbackBackend,
            SearchStrategy fallbackStrategy) {
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.subjectScope = Objects.requireNonNull(subjectScope, "subjectScope");
        this.allowedSubjectKinds = allowedSubjectKinds(allowedSubjectKinds);
        validateSubjectKinds();
        this.facets = List.copyOf(Objects.requireNonNull(facets, "facets"));
        this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        for (String dimension : this.dimensions) {
            try {
                UserGoalProposal.PortfolioComparisonDimension.valueOf(dimension);
            } catch (IllegalArgumentException | NullPointerException unsupported) {
                throw new IllegalArgumentException("unsupported portfolio comparison dimension");
            }
        }
        this.depth = Objects.requireNonNull(depth, "depth");
        if (requestedSize < 0 || requestedSize > 5
                || taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND && requestedSize < 1) {
            throw new IllegalArgumentException("recommendation requestedSize is invalid");
        }
        this.requestedSize = requestedSize;
        this.recommendationConstraints = Set.copyOf(Objects.requireNonNull(
                recommendationConstraints, "recommendationConstraints"));
        if (this.recommendationConstraints.stream().anyMatch(value ->
                !value.matches("(?:CAREER_TRACK|CAPABILITY)_[A-Z0-9_]{1,64}"))) {
            throw new IllegalArgumentException("recommendation constraint is invalid");
        }
        if (taskType != SemanticTask.Type.PORTFOLIO_RECOMMEND
                && (!this.recommendationConstraints.isEmpty() || requestedSize != 0)) {
            throw new IllegalArgumentException("recommendation inputs require recommendation task");
        }
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.primaryBackend = Objects.requireNonNull(primaryBackend, "primaryBackend");
        this.primaryStrategy = Objects.requireNonNull(primaryStrategy, "primaryStrategy");
        this.fallbackBackend = fallbackBackend;
        this.fallbackStrategy = fallbackStrategy;
        if (!contentReleaseId.equals(subjectScope.getContentReleaseId())) {
            throw new IllegalArgumentException("subject scope release mismatch");
        }
        if (facets.isEmpty() && dimensions.isEmpty()) {
            throw new IllegalArgumentException("at least one retrieval profile is required");
        }
        if ((fallbackBackend == null) != (fallbackStrategy == null)) {
            throw new IllegalArgumentException("fallback backend and strategy must be paired");
        }
    }

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            Set<PortfolioSubjectKind> allowedSubjectKinds,
            List<FacetProfile> facets, List<String> dimensions,
            String contentReleaseId, CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy, CorpusBackend fallbackBackend,
            SearchStrategy fallbackStrategy) {
        this(taskType, subjectScope, allowedSubjectKinds, facets, dimensions,
                UserGoalProposal.Depth.STANDARD,
                taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND ? 3 : 0,
                Set.of(), contentReleaseId,
                primaryBackend, primaryStrategy, fallbackBackend, fallbackStrategy);
    }

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            Set<PortfolioSubjectKind> allowedSubjectKinds,
            List<FacetProfile> facets, List<String> dimensions,
            UserGoalProposal.Depth depth,
            String contentReleaseId, CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy, CorpusBackend fallbackBackend,
            SearchStrategy fallbackStrategy) {
        this(taskType, subjectScope, allowedSubjectKinds, facets, dimensions, depth,
                taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND ? 3 : 0,
                Set.of(),
                contentReleaseId, primaryBackend, primaryStrategy,
                fallbackBackend, fallbackStrategy);
    }

    public SemanticTask.Type getTaskType() { return taskType; }
    public AuthorizedSubjectScope getSubjectScope() { return subjectScope; }
    public Set<PortfolioSubjectKind> getAllowedSubjectKinds() { return allowedSubjectKinds; }
    public List<FacetProfile> getFacets() { return facets; }
    public List<String> getDimensions() { return dimensions; }
    public UserGoalProposal.Depth getDepth() { return depth; }
    public int getRequestedSize() { return requestedSize; }
    public Set<String> getRecommendationConstraints() { return recommendationConstraints; }
    /** 从推荐约束中提取首个职业轨道（剥去 CAREER_TRACK_ 前缀），不存在时返回 null。 */
    public String getRecommendationCareerTrack() {
        return recommendationConstraints.stream()
                .filter(value -> value.startsWith("CAREER_TRACK_"))
                .map(value -> value.substring("CAREER_TRACK_".length()))
                .findFirst().orElse(null);
    }
    /** 提取全部能力编码约束（剥去 CAPABILITY_ 前缀），返回不可修改集合。 */
    public Set<String> getRecommendationCapabilityCodes() {
        return recommendationConstraints.stream()
                .filter(value -> value.startsWith("CAPABILITY_"))
                .map(value -> value.substring("CAPABILITY_".length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    /** 按回答深度返回每个主体最多保留的 Evidence 单元数（截断上限：简洁 2 / 标准 6 / 详细 12）。 */
    public int getMaximumEvidenceUnitsPerSubject() {
        return switch (depth) {
            case CONCISE -> 2;
            case STANDARD -> 6;
            case DETAILED -> 12;
        };
    }
    public String getContentReleaseId() { return contentReleaseId; }
    public CorpusBackend getPrimaryBackend() { return primaryBackend; }
    public SearchStrategy getPrimaryStrategy() { return primaryStrategy; }
    public CorpusBackend getFallbackBackend() { return fallbackBackend; }
    public SearchStrategy getFallbackStrategy() { return fallbackStrategy; }

    private Set<PortfolioSubjectKind> allowedSubjectKinds(
            Set<PortfolioSubjectKind> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw integrityFailure(
                    PortfolioEvidenceCapability.IntegrityReason.SUBJECT_KIND_NOT_ALLOWED);
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private void validateSubjectKinds() {
        if (taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND
                && !allowedSubjectKinds.equals(Set.of(PortfolioSubjectKind.PROJECT))) {
            throw integrityFailure(PortfolioEvidenceCapability.IntegrityReason
                    .RECOMMENDATION_SUBJECT_KIND_CONTRACT_VIOLATION);
        }
        if (subjectScope.getMode() != AuthorizedSubjectScope.Mode.EXACT) return;
        for (AuthorizedSubjectScope.Subject subject : subjectScope.getSubjects()) {
            PortfolioSubjectKind kind = toPortfolioSubjectKind(subject.getKind());
            if (!allowedSubjectKinds.contains(kind)) {
                throw integrityFailure(PortfolioEvidenceCapability.IntegrityReason
                        .EXACT_SCOPE_SUBJECT_KIND_MISMATCH);
            }
        }
    }

    private PortfolioSubjectKind toPortfolioSubjectKind(
            com.portfolio.agent.turn.planning.GoalSubjectReference.Kind kind) {
        return switch (kind) {
            case PROJECT -> PortfolioSubjectKind.PROJECT;
            case CASE -> PortfolioSubjectKind.CASE;
            case RESULT -> throw integrityFailure(PortfolioEvidenceCapability.IntegrityReason
                    .UNRESOLVED_RESULT_SUBJECT);
        };
    }

    private PortfolioEvidenceCapability.PortfolioCapabilityException integrityFailure(
            PortfolioEvidenceCapability.IntegrityReason reason) {
        return new PortfolioEvidenceCapability.PortfolioCapabilityException(reason);
    }

    /** 检索切面：决定 Evidence 取材的内容侧面（背景、职责、实现、技术决策、验证、结果、局限、推荐）。 */
    public enum FacetProfile {
        BACKGROUND, RESPONSIBILITY, IMPLEMENTATION, TECHNICAL_DECISION,
        VERIFICATION, OUTCOME, LIMITATION, RECOMMENDATION
    }
}
