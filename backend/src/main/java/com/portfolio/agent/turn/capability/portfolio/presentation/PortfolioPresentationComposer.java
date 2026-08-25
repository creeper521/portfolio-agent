package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 呈现组装器（Presenter）：把语义结果组装为受 {@link PresentationPolicy} 预算约束的公开呈现。
 *
 * <p>事实/推荐按 claim 类别分组为段落，对比结果按对比维度逐段展开并按主体分组陈述；
 * 段落数受策略与回答深度（CONCISE 至多 2 段）双重限制，字符数超预算即停止追加段落。
 * 每个段落只引用已验证 Evidence 的公开来源；组装结果为空（无对齐支撑或超预算）时
 * 抛出 IllegalArgumentException，由上层判定任务失败（fail-closed）。
 */
public final class PortfolioPresentationComposer {
    private final PresentationPolicy policy;
    public PortfolioPresentationComposer(PresentationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * 组装语义结果的公开呈现：对比型走专用路径，其余按 claim 类别分组为段落。
     *
     * @param result 已通过支撑评估的语义结果
     * @return 受预算约束的呈现对象
     * @throws IllegalArgumentException 没有任何段落能在预算内产出时
     */
    public PortfolioPresentation compose(PortfolioSemanticResult result) {
        if (result instanceof PortfolioSemanticResult.Comparison comparison) {
            return comparison(comparison);
        }
        List<PortfolioPresentation.Section> sections = new ArrayList<>();
        int characters = 0;
        int maximumSections = maximumSections(result);
        Map<AnswerSectionType, List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>>
                grouped = new LinkedHashMap<>();
        for (com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit
                : result.getUnits()) {
            grouped.computeIfAbsent(section(unit.getClaim().getCategory()), ignored ->
                    new ArrayList<>()).add(unit);
        }
        for (Map.Entry<AnswerSectionType,
                List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>>
                entry : grouped.entrySet()) {
            // 预算截断：段落数与累计字符数任一超限即停止，宁可少给也不超发
            if (sections.size() >= maximumSections) break;
            String content = sectionContent(result, entry.getKey(), entry.getValue());
            int next = characters + content.length();
            if (next > policy.getMaximumCharacters()) break;
            sections.add(new PortfolioPresentation.Section(
                    entry.getKey(), label(entry.getKey()), content,
                    entry.getValue().stream().map(unit -> source(unit.getSourceReference()))
                            .distinct().toList()));
            characters = next;
        }
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("supported semantic result exceeds presentation bounds");
        }
        return new PortfolioPresentation("回答", sections);
    }

    /** 段落正文：单条陈述直接使用；多条时以"类别标签 + 共同表明"句式拼接。 */
    private String sectionContent(
            PortfolioSemanticResult result,
            AnswerSectionType sectionType,
            List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit> units) {
        List<String> statements = units.stream().map(unit -> content(result, unit)).toList();
        if (statements.size() == 1) {
            return statements.getFirst();
        }
        return label(sectionType) + "方面，公开证据共同表明："
                + String.join("；\n", statements);
    }

    /**
     * 对比型专用组装：按维度过滤支撑单元，按主体分组陈述并逐段检查字符预算。
     *
     * @throws IllegalArgumentException 任何维度都无法在预算内产出段落时
     */
    private PortfolioPresentation comparison(
            PortfolioSemanticResult.Comparison result) {
        List<PortfolioPresentation.Section> sections = new ArrayList<>();
        int characters = 0;
        for (com.portfolio.agent.turn.planning.UserGoalProposal.PortfolioComparisonDimension
                dimension : result.getDimensions()) {
            List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>
                    matching = result.getUnits().stream()
                    .filter(unit -> comparisonCategory(dimension, unit.getClaim().getCategory()))
                    .toList();
            if (matching.isEmpty() || sections.size() >= policy.getMaximumSections()) continue;
            String content = matching.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            value -> value.getSubjectTitle(), java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.mapping(
                                    value -> value.getClaim().getStatement(),
                                    java.util.stream.Collectors.toList())))
                    .entrySet().stream().map(entry -> entry.getKey() + "："
                            + String.join("；", entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (characters + content.length() > policy.getMaximumCharacters()) break;
            List<PublicSourceReferenceValue> sources = matching.stream()
                    .map(value -> source(value.getSourceReference())).distinct().toList();
            sections.add(new PortfolioPresentation.Section(
                    comparisonSection(dimension), comparisonLabel(dimension), content, sources));
            characters += content.length();
        }
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("comparison has no aligned public support");
        }
        return new PortfolioPresentation("对比", sections);
    }

    /** 对比维度到 claim 类别的映射（与支撑评估器同一张对照表）。 */
    private boolean comparisonCategory(
            com.portfolio.agent.turn.planning.UserGoalProposal.PortfolioComparisonDimension dimension,
            AnswerClaimCategory category) {
        return switch (dimension) {
            case ARCHITECTURE -> category == AnswerClaimCategory.TECHNICAL_DECISION;
            case IMPLEMENTATION -> category == AnswerClaimCategory.IMPLEMENTATION;
            case OUTCOME -> category == AnswerClaimCategory.OUTCOME;
            case RISKS -> category == AnswerClaimCategory.LIMITATION;
            case VERIFICATION -> category == AnswerClaimCategory.VERIFICATION;
        };
    }

    /** 对比维度到公开段落类型的映射。 */
    private AnswerSectionType comparisonSection(
            com.portfolio.agent.turn.planning.UserGoalProposal.PortfolioComparisonDimension dimension) {
        return switch (dimension) {
            case VERIFICATION -> AnswerSectionType.VERIFICATION;
            case OUTCOME -> AnswerSectionType.STATUS;
            case RISKS -> AnswerSectionType.BOUNDARY;
            case ARCHITECTURE, IMPLEMENTATION -> AnswerSectionType.SOLUTION;
        };
    }

    /** 对比维度的中文段落标题。 */
    private String comparisonLabel(
            com.portfolio.agent.turn.planning.UserGoalProposal.PortfolioComparisonDimension dimension) {
        return switch (dimension) {
            case ARCHITECTURE -> "架构对比";
            case IMPLEMENTATION -> "实现对比";
            case OUTCOME -> "结果对比";
            case RISKS -> "风险与边界对比";
            case VERIFICATION -> "验证对比";
        };
    }

    /** 段落上限：仅事实型受深度约束（CONCISE=2，其余=8），再与策略上限取较小值。 */
    private int maximumSections(PortfolioSemanticResult result) {
        if (!(result instanceof PortfolioSemanticResult.Fact fact)) {
            return policy.getMaximumSections();
        }
        int depthMaximum = switch (fact.getDepth()) {
            case CONCISE -> 2;
            case STANDARD, DETAILED -> 8;
        };
        return Math.min(policy.getMaximumSections(), depthMaximum);
    }

    /** 单元陈述正文：仅事实型 DETAILED 深度且明细非空、不与陈述重复时追加明细行。 */
    private String content(
            PortfolioSemanticResult result,
            com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit) {
        String statement = unit.getClaim().getStatement();
        if (!(result instanceof PortfolioSemanticResult.Fact fact)
                || fact.getDepth() != com.portfolio.agent.turn.planning.UserGoalProposal.Depth.DETAILED
                || unit.getClaim().getDetail() == null
                || unit.getClaim().getDetail().isBlank()
                || unit.getClaim().getDetail().equals(statement)) {
            return statement;
        }
        return statement + "\n" + unit.getClaim().getDetail();
    }

    /** claim 类别到公开段落类型的映射；学习/反思类归入边界段。 */
    private AnswerSectionType section(AnswerClaimCategory category) {
        return switch (category) {
            case BACKGROUND -> AnswerSectionType.BACKGROUND;
            case RESPONSIBILITY -> AnswerSectionType.RESPONSIBILITY;
            case VERIFICATION -> AnswerSectionType.VERIFICATION;
            case OUTCOME -> AnswerSectionType.STATUS;
            case LIMITATION, LEARNING, REFLECTION -> AnswerSectionType.BOUNDARY;
            default -> AnswerSectionType.SOLUTION;
        };
    }
    /** 公开段落类型的中文标题。 */
    private String label(AnswerSectionType sectionType) {
        return switch (sectionType) {
            case BACKGROUND -> "背景";
            case RESPONSIBILITY -> "职责";
            case VERIFICATION -> "验证";
            case STATUS -> "状态";
            case BOUNDARY -> "边界";
            default -> "方案";
        };
    }
    private PublicSourceReferenceValue source(
            com.portfolio.agent.turn.execution.PublicSourceReferenceValue value) {
        return new PublicSourceReferenceValue(
                value.getReferenceKey(), value.getLabel(), value.getPublishedVersion(),
                value.getSourceType(), value.getSubjectRoute(), value.getEvidenceRoute());
    }
}
