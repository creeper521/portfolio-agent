package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 语义结果工厂：依据 SemanticTask 类型把已验证 Evidence 束组装为对应的 PortfolioSemanticResult。
 *
 * <p>事实与对比直接委托 {@link PortfolioSupportEvaluator} 评估支撑并映射为相应子类；
 * 推荐按约束匹配数、证据多样性与主体标识的固定次序排序并截断到 requestedSize，
 * 未满足的约束与规模缺口显式记入结果（PARTIAL）。非作品集任务类型抛出
 * IllegalArgumentException，不产生半成品结果。
 */
public final class PortfolioSemanticResultFactory {
    private final PortfolioSupportEvaluator evaluator;
    public PortfolioSemanticResultFactory(PortfolioSupportEvaluator evaluator) {
        this.evaluator = java.util.Objects.requireNonNull(evaluator, "evaluator");
    }

    /**
     * 按任务类型创建语义结果。
     *
     * @param task       规划阶段产出的语义任务
     * @param invocation 当前 Evidence 调用上下文
     * @param bundle     已通过晋级校验的 Evidence 束
     * @return 对应形态的语义结果；支撑为空时返回 empty（由上层判定任务失败）
     * @throws IllegalArgumentException 任务类型不属于作品集事实/对比/推荐时
     */
    public Optional<PortfolioSemanticResult> create(
            SemanticTask task,
            PortfolioEvidenceInvocation invocation,
            ValidatedEvidenceBundle bundle) {
        return switch (task.getType()) {
            case PORTFOLIO_FACT -> fact(invocation, bundle);
            case PORTFOLIO_COMPARE -> comparison(invocation, bundle);
            case PORTFOLIO_RECOMMEND ->
                    recommendation(invocation, bundle);
            default -> throw new IllegalArgumentException("unsupported portfolio task");
        };
    }

    private Optional<PortfolioSemanticResult> fact(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.fact(invocation, bundle);
        return support.hasSupport() ? Optional.of(new PortfolioSemanticResult.Fact(
                support.coverage(), invocation.getSubjectScope(),
                support.getSelectedUnits(), support.getOmissions(),
                invocation.getDepth())) : Optional.empty();
    }

    private Optional<PortfolioSemanticResult> comparison(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.comparison(invocation, bundle);
        return support.hasSupport() ? Optional.of(new PortfolioSemanticResult.Comparison(
                support.coverage(), invocation.getSubjectScope(),
                support.getSelectedUnits(), support.getOmissions(),
                invocation.getDimensions().stream().map(value ->
                        UserGoalProposal.PortfolioComparisonDimension.valueOf(value)).toList()))
                : Optional.empty();
    }

    /**
     * 组装推荐结果：按主体聚合证据、排序截断到 requestedSize，并汇总未满足约束。
     * 集齐规模且无未满足约束才记 FULL，否则 PARTIAL 并以 REQUESTED_SIZE 声明规模缺口。
     */
    private Optional<PortfolioSemanticResult> recommendation(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        if (bundle.getUnits().isEmpty()) return Optional.empty();
        int requestedSize = invocation.getRequestedSize();
        Set<String> constraints = invocation.getRecommendationConstraints();
        Map<String, List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>>
                bySubject = bundle.getUnits().stream().collect(Collectors.groupingBy(
                value -> value.getSubjectId(), java.util.LinkedHashMap::new, Collectors.toList()));
        // 固定排序：先约束命中数降序，再证据类别多样性降序，最后按主体标识保证确定性
        List<RankedSubject> ranked = bySubject.entrySet().stream()
                .map(entry -> rank(entry.getKey(), entry.getValue(), constraints))
                .sorted(Comparator.comparingInt(RankedSubject::matchedConstraintCount).reversed()
                        .thenComparing(Comparator.comparingInt(RankedSubject::evidenceScore).reversed())
                        .thenComparing(RankedSubject::subjectId))
                .limit(requestedSize).toList();
        List<String> selectedSubjects = ranked.stream().map(RankedSubject::subjectId).toList();
        Set<String> unsatisfied = new LinkedHashSet<>();
        ranked.forEach(value -> unsatisfied.addAll(value.unsatisfiedConstraints()));
        PortfolioSemanticResult.Coverage coverage = selectedSubjects.size() == requestedSize
                && unsatisfied.isEmpty() ? PortfolioSemanticResult.Coverage.FULL
                : PortfolioSemanticResult.Coverage.PARTIAL;
        List<String> omissions = selectedSubjects.size() == requestedSize
                ? List.of() : List.of("REQUESTED_SIZE");
        List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>
                selectedUnits = selectedSubjects.stream().flatMap(value -> bySubject.get(value).stream())
                .toList();
        return Optional.of(new PortfolioSemanticResult.Recommendation(
                coverage, invocation.getSubjectScope(), selectedUnits,
                omissions, requestedSize,
                ranked.stream().map(value -> new PortfolioSemanticResult.Recommendation
                        .RecommendationItem(value.subjectId(), value.reasonCodes())).toList(),
                List.copyOf(unsatisfied)));
    }

    /**
     * 对单个主体打标：以首个单元代表主体判定约束满足情况，并按证据类别
     * （实现/验证/结果）与约束前缀（CAREER_TRACK_/CAPABILITY_）推导推荐理由码，
     * 无任何匹配理由时兜底为 VERIFIED_PUBLIC_EVIDENCE。
     */
    private RankedSubject rank(
            String subjectId,
            List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit> units,
            Set<String> constraints) {
        com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit first =
                units.getFirst();
        Set<String> unsatisfied = constraints.stream()
                .filter(value -> !matches(first, value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PortfolioSemanticResult.Recommendation.RecommendationReasonCode> reasons =
                new ArrayList<>();
        if (constraints.stream().anyMatch(value -> value.startsWith("CAREER_TRACK_")
                && matches(first, value))) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .CAREER_TRACK_MATCH);
        }
        if (constraints.stream().anyMatch(value -> value.startsWith("CAPABILITY_")
                && matches(first, value))) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .CAPABILITY_MATCH);
        }
        if (units.stream().anyMatch(value -> value.getClaim().getCategory()
                == com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory.IMPLEMENTATION)) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_IMPLEMENTATION);
        }
        if (units.stream().anyMatch(value -> value.getClaim().getCategory()
                == com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory.VERIFICATION)) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_VERIFICATION);
        }
        if (units.stream().anyMatch(value -> value.getClaim().getCategory()
                == com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory.OUTCOME)) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_OUTCOME);
        }
        if (reasons.isEmpty()) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_PUBLIC_EVIDENCE);
        }
        return new RankedSubject(subjectId, constraints.size() - unsatisfied.size(),
                units.stream().map(value -> value.getClaim().getCategory()).distinct().toList().size(),
                List.copyOf(unsatisfied), List.copyOf(reasons));
    }

    /** 约束前缀匹配：CAREER_TRACK_ 前缀比职业赛道，CAPABILITY_ 前缀查能力码集合，其余恒不匹配。 */
    private boolean matches(
            com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit,
            String constraint) {
        if (constraint.startsWith("CAREER_TRACK_")) {
            return constraint.substring("CAREER_TRACK_".length()).equals(unit.getCareerTrack());
        }
        if (constraint.startsWith("CAPABILITY_")) {
            return unit.getCapabilityCodes().contains(
                    constraint.substring("CAPABILITY_".length()));
        }
        return false;
    }

    /** 推荐排序的中间载体（record）：主体标识、约束命中数、证据多样性分、未满足约束与理由码。 */
    private record RankedSubject(
            String subjectId, int matchedConstraintCount, int evidenceScore,
            List<String> unsatisfiedConstraints,
            List<PortfolioSemanticResult.Recommendation.RecommendationReasonCode> reasonCodes) { }
}
