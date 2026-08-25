package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户目标提案：Goal 解析产出的 1..6 条已校验目标集合。
 *
 * <p>既是 {@link GoalInterpretationPort} 的模型提案类型，也是
 * {@link SemanticPlanCompiler} 的编译输入。全部字段为封闭枚举、受限字符串
 * 或带 {@link InputAnchor} 的原文锚点；构造时即校验目标键唯一性、类型化参数
 * 与 goalKind 的一致性、requestedOutputs 与参数的派生关系。</p>
 */
public final class UserGoalProposal {

    private final List<ProposedGoal> goals;

    public UserGoalProposal(List<ProposedGoal> goals) {
        List<ProposedGoal> copied = List.copyOf(Objects.requireNonNull(goals, "goals"));
        if (copied.isEmpty() || copied.size() > 6) {
            throw new IllegalArgumentException("goals must contain between one and six items");
        }
        if (copied.stream().map(ProposedGoal::getGoalKey).distinct().count() != copied.size()) {
            throw new IllegalArgumentException("goal keys must be unique");
        }
        this.goals = copied;
    }

    public List<ProposedGoal> getGoals() {
        return goals;
    }

    /**
     * 单条提案目标。
     *
     * <p>goalKey 必须是局部闭合键（小写字母数字开头，总长 2..64）；
     * parameters 必须与 goalKind 匹配；requestedOutputs 强制等于类型化参数
     * 的派生结果，防止模型随意声明输出形状。</p>
     */
    public static final class ProposedGoal {
        private final String goalKey;
        private final GoalKind goalKind;
        private final InputAnchor inputAnchor;
        private final List<GoalSubjectReference> subjectCandidates;
        private final GoalKnowledgeRequirement knowledgeRequirement;
        private final GoalParameters parameters;

        public ProposedGoal(
                String goalKey,
                GoalKind goalKind,
                InputAnchor inputAnchor,
                List<GoalSubjectReference> subjectCandidates,
                Set<GoalRequestedOutput> requestedOutputs,
                GoalKnowledgeRequirement knowledgeRequirement,
                GoalParameters parameters) {
            if (goalKey == null || !goalKey.matches("[a-z0-9][a-z0-9-]{1,63}")) {
                throw new IllegalArgumentException("goalKey must be a local key");
            }
            this.goalKey = goalKey;
            this.goalKind = Objects.requireNonNull(goalKind, "goalKind");
            this.inputAnchor = Objects.requireNonNull(inputAnchor, "inputAnchor");
            this.subjectCandidates = List.copyOf(
                    Objects.requireNonNull(subjectCandidates, "subjectCandidates"));
            Set<GoalRequestedOutput> suppliedOutputs = Set.copyOf(
                    Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
            this.knowledgeRequirement = Objects.requireNonNull(
                    knowledgeRequirement, "knowledgeRequirement");
            this.parameters = Objects.requireNonNull(parameters, "parameters");
            if (parameters.getGoalKind() != goalKind) {
                throw new IllegalArgumentException("goal parameters must match goalKind");
            }
            if (!suppliedOutputs.equals(requestedOutputs(parameters))) {
                throw new IllegalArgumentException(
                        "requestedOutputs must match the typed goal parameters");
            }
        }

        public String getGoalKey() { return goalKey; }
        public GoalKind getGoalKind() { return goalKind; }
        public InputAnchor getInputAnchor() { return inputAnchor; }
        public List<GoalSubjectReference> getSubjectCandidates() { return subjectCandidates; }
        public Set<GoalRequestedOutput> getRequestedOutputs() {
            return requestedOutputs(parameters);
        }
        public GoalKnowledgeRequirement getKnowledgeRequirement() { return knowledgeRequirement; }
        public GoalParameters getParameters() { return parameters; }
    }

    /** 目标参数封闭接口：每个实现对应一种 GoalKind 并携带其类型化参数。 */
    public interface GoalParameters {
        GoalKind getGoalKind();
    }

    /** PORTFOLIO_FACT 参数：查询的作品集侧面集合（非空）与内容深度。 */
    public static final class PortfolioFactParameters implements GoalParameters {
        private final Set<Facet> facets;
        private final Depth depth;

        public PortfolioFactParameters(Set<Facet> facets, Depth depth) {
            this.facets = Set.copyOf(Objects.requireNonNull(facets, "facets"));
            if (this.facets.isEmpty()) {
                throw new IllegalArgumentException("portfolio fact facets must not be empty");
            }
            this.depth = Objects.requireNonNull(depth, "depth");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.PORTFOLIO_FACT; }
        public Set<Facet> getFacets() { return facets; }
        public Depth getDepth() { return depth; }
    }

    /** PORTFOLIO_COMPARE 参数：比较维度集合（非空）。 */
    public static final class PortfolioCompareParameters implements GoalParameters {
        private final Set<PortfolioComparisonDimension> dimensions;

        public PortfolioCompareParameters(Set<PortfolioComparisonDimension> dimensions) {
            this.dimensions = Set.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
            if (this.dimensions.isEmpty()) {
                throw new IllegalArgumentException("dimensions must not be empty");
            }
        }

        @Override public GoalKind getGoalKind() { return GoalKind.PORTFOLIO_COMPARE; }
        public Set<PortfolioComparisonDimension> getDimensions() { return dimensions; }
    }

    /** PORTFOLIO_RECOMMEND 参数：推荐数量（1..5）与闭合约束（最多一条职业方向）。 */
    public static final class PortfolioRecommendationParameters implements GoalParameters {
        private final int requestedSize;
        private final Set<String> constraints;

        public PortfolioRecommendationParameters(int requestedSize, Set<String> constraints) {
            if (requestedSize < 1 || requestedSize > 5) {
                throw new IllegalArgumentException("requestedSize must be between one and five");
            }
            this.requestedSize = requestedSize;
            this.constraints = copyNamesAllowEmpty(constraints, "constraints");
            if (this.constraints.stream().anyMatch(value ->
                    !value.matches("(?:CAREER_TRACK|CAPABILITY)_[A-Z0-9_]{1,64}"))) {
                throw new IllegalArgumentException(
                        "recommendation constraints must use a typed prefix");
            }
            if (this.constraints.stream().filter(value ->
                    value.startsWith("CAREER_TRACK_")).count() > 1) {
                throw new IllegalArgumentException(
                        "recommendation accepts at most one career track");
            }
        }

        @Override public GoalKind getGoalKind() { return GoalKind.PORTFOLIO_RECOMMEND; }
        public int getRequestedSize() { return requestedSize; }
        public Set<String> getConstraints() { return constraints; }
    }

    /** GENERAL_EXPLANATION 参数：主题锚点与解释深度。 */
    public static final class GeneralExplanationParameters implements GoalParameters {
        private final InputAnchor topicAnchor;
        private final Depth depth;

        public GeneralExplanationParameters(InputAnchor topicAnchor, Depth depth) {
            this.topicAnchor = Objects.requireNonNull(topicAnchor, "topicAnchor");
            this.depth = Objects.requireNonNull(depth, "depth");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.GENERAL_EXPLANATION; }
        public InputAnchor getTopicAnchor() { return topicAnchor; }
        public Depth getDepth() { return depth; }
    }

    /** GENERAL_COMPARISON 参数：2..5 个比较对象锚点与非空闭合比较维度名。 */
    public static final class GeneralComparisonParameters implements GoalParameters {
        private final List<InputAnchor> subjectAnchors;
        private final Set<String> dimensions;

        public GeneralComparisonParameters(List<InputAnchor> subjectAnchors, Set<String> dimensions) {
            this.subjectAnchors = List.copyOf(Objects.requireNonNull(subjectAnchors, "subjectAnchors"));
            if (this.subjectAnchors.size() < 2 || this.subjectAnchors.size() > 5) {
                throw new IllegalArgumentException("general comparison requires two to five subjects");
            }
            this.dimensions = copyNames(dimensions, "dimensions");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.GENERAL_COMPARISON; }
        public List<InputAnchor> getSubjectAnchors() { return subjectAnchors; }
        public Set<String> getDimensions() { return dimensions; }
    }

    /** APPLY_GENERAL_CONCEPT_TO_PORTFOLIO 参数：概念锚点、关联的作品集侧面与深度。 */
    public static final class ApplyConceptParameters implements GoalParameters {
        private final InputAnchor conceptAnchor;
        private final Facet portfolioFacet;
        private final Depth depth;

        public ApplyConceptParameters(
                InputAnchor conceptAnchor, Facet portfolioFacet, Depth depth) {
            this.conceptAnchor = Objects.requireNonNull(conceptAnchor, "conceptAnchor");
            this.portfolioFacet = Objects.requireNonNull(portfolioFacet, "portfolioFacet");
            this.depth = Objects.requireNonNull(depth, "depth");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO; }
        public InputAnchor getConceptAnchor() { return conceptAnchor; }
        public Facet getPortfolioFacet() { return portfolioFacet; }
        public Depth getDepth() { return depth; }
    }

    /**
     * 输入锚点：指向访客原文某个片段的 (text, start) 定位。
     *
     * <p>必须与原文精确匹配（{@link #requireMatches}），用于防止模型编造
     * 不存在的原文引用。值相等性同时比较文本与起始位置。</p>
     */
    public static final class InputAnchor {
        private final String text;
        private final int start;

        public InputAnchor(String text, int start) {
            if (text == null || text.isBlank() || text.length() > 256 || start < 0) {
                throw new IllegalArgumentException("input anchor is invalid");
            }
            this.text = text;
            this.start = start;
        }

        public String getText() { return text; }
        public int getStart() { return start; }

        public void requireMatches(String input) {
            if (start + text.length() > input.length()
                    || !input.regionMatches(start, text, 0, text.length())) {
                throw new IllegalArgumentException("anchor does not match original input");
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InputAnchor that)) return false;
            return start == that.start && text.equals(that.text);
        }

        @Override
        public int hashCode() { return Objects.hash(text, start); }
    }

    /** 作品集查询侧面：概览/背景/职责/方案/验证/状态。 */
    public enum Facet { OVERVIEW, BACKGROUND, RESPONSIBILITY, SOLUTION, VERIFICATION, STATUS }
    /** 内容深度：简明/标准/详细。 */
    public enum Depth { CONCISE, STANDARD, DETAILED }
    /** 作品集比较维度：架构/实现/结果/风险/验证。 */
    public enum PortfolioComparisonDimension {
        ARCHITECTURE, IMPLEMENTATION, OUTCOME, RISKS, VERIFICATION
    }

    /** 按类型化参数派生请求输出：PORTFOLIO_FACT 由侧面派生，其余目标为单一固定输出。 */
    private static Set<GoalRequestedOutput> requestedOutputs(GoalParameters parameters) {
        if (parameters instanceof PortfolioFactParameters fact) {
            return fact.getFacets().stream()
                    .map(value -> GoalRequestedOutput.valueOf(value.name()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return switch (parameters.getGoalKind()) {
            case PORTFOLIO_COMPARE, GENERAL_COMPARISON ->
                    Set.of(GoalRequestedOutput.COMPARISON);
            case PORTFOLIO_RECOMMEND -> Set.of(GoalRequestedOutput.RECOMMENDATION);
            case GENERAL_EXPLANATION -> Set.of(GoalRequestedOutput.EXPLANATION);
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> Set.of(GoalRequestedOutput.RELATION);
            case PORTFOLIO_FACT -> throw new IllegalArgumentException(
                    "portfolio fact parameters are required");
        };
    }

    /** 复制并校验闭合命名集合（大写字母与下划线，1..64 字符），不允许为空。 */
    private static Set<String> copyNames(Set<String> values, String name) {
        Set<String> copied = copyNamesAllowEmpty(values, name);
        if (copied.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return copied;
    }

    /** 复制并校验闭合命名集合，允许为空。 */
    private static Set<String> copyNamesAllowEmpty(Set<String> values, String name) {
        Set<String> copied = Set.copyOf(Objects.requireNonNull(values, name));
        for (String value : copied) {
            if (value == null || !value.matches("[A-Z_]{1,64}")) {
                throw new IllegalArgumentException(name + " must contain closed names");
            }
        }
        return copied;
    }
}
