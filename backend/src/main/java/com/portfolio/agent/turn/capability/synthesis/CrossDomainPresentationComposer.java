package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 跨域综合展示组装器：把 {@link CrossDomainSemanticResult} 组装为固定三段的
 * {@link CrossDomainPresentation}——通用原理（通用陈述拼接，caveats 非空时
 * 追加"适用边界"）、项目实例（项目陈述拼接并附公开来源）、概念与实例的关系
 * （模板化生成的验证段）。来源引用按 referenceKey 去重后只挂在后两段上。
 */
public final class CrossDomainPresentationComposer {
    /** 组装固定三段展示：概念锚作为标题，来源列表挂在项目实例与关系两段。 */
    public CrossDomainPresentation compose(CrossDomainSemanticResult result) {
        String general = result.getGeneralStatements().stream()
                .map(value -> value.getText()).collect(java.util.stream.Collectors.joining("\n"));
        if (!result.getCaveats().isEmpty()) {
            general += "\n适用边界：" + String.join("；", result.getCaveats());
        }
        String portfolio = result.getPortfolioStatements().stream()
                .map(CrossDomainSemanticResult.GroundedPortfolioStatement::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        String relation = relation(result);
        List<PublicSourceReferenceValue> sources = sources(result);
        return new CrossDomainPresentation(
                result.getConceptAnchor(),
                List.of(
                        new CrossDomainPresentation.Section(
                                AnswerSectionType.BACKGROUND, "通用原理", general, List.of()),
                        new CrossDomainPresentation.Section(
                                AnswerSectionType.SOLUTION, "项目实例", portfolio, sources),
                        new CrossDomainPresentation.Section(
                                AnswerSectionType.VERIFICATION, "概念与实例的关系",
                                relation,
                                sources)));
    }

    /**
     * 生成"概念与实例的关系"验证段：优先取 MECHANISM 陈述作机制句（缺失时
     * 退回首条陈述），再按主张类别给每条项目陈述配固定关系标签，串成
     * "机制 → 项目证据逐条对应"的一段文本。句末标点先剥离再统一收尾。
     */
    private String relation(CrossDomainSemanticResult result) {
        String mechanism = result.getGeneralStatements().stream()
                .filter(value -> value.getRole()
                        == com.portfolio.agent.turn.capability.general.GeneralSemanticResult.Role.MECHANISM)
                .map(value -> value.getText()).collect(java.util.stream.Collectors.joining("；"));
        if (mechanism.isBlank()) {
            mechanism = result.getGeneralStatements().getFirst().getText();
        }
        StringBuilder relationship = new StringBuilder()
                .append("“").append(result.getConceptAnchor()).append("”的机制是：")
                .append(trimSentenceEnd(mechanism)).append("。在项目证据中，");
        for (int index = 0; index < result.getPortfolioStatements().size(); index++) {
            CrossDomainSemanticResult.GroundedPortfolioStatement statement =
                    result.getPortfolioStatements().get(index);
            if (index > 0) relationship.append("；");
            relationship.append(relationLabel(statement.category()))
                    .append("：").append(trimSentenceEnd(statement.text()));
        }
        return relationship.append("。").toString();
    }

    /** 剥离句末的中文/英文终止标点，便于模板统一补句号。 */
    private String trimSentenceEnd(String value) {
        return value.replaceFirst("[。！？；]+$", "");
    }

    /** 主张类别到关系措辞的封闭映射：每类证据以固定句式说明它如何支撑机制。 */
    private String relationLabel(
            com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory category) {
        return switch (category) {
            case BACKGROUND -> "背景事实给出该机制面对的具体条件";
            case RESPONSIBILITY -> "职责事实界定该机制的实施责任";
            case TECHNICAL_DECISION -> "技术决策把该机制落到方案选择";
            case IMPLEMENTATION -> "实现事实展示该机制的落地方式";
            case VERIFICATION -> "验证事实展示该机制的检验方式";
            case OUTCOME -> "结果事实展示该机制的可观察结果";
            case LIMITATION -> "限制事实界定该机制的适用边界";
            case LEARNING, REFLECTION -> "复盘事实说明该机制带来的后续认识";
        };
    }

    /** 汇总项目陈述的公开来源并按 referenceKey 去重，保持首次出现顺序。 */
    private List<PublicSourceReferenceValue> sources(CrossDomainSemanticResult result) {
        LinkedHashMap<String, PublicSourceReferenceValue> values = new LinkedHashMap<>();
        for (CrossDomainSemanticResult.GroundedPortfolioStatement statement
                : result.getPortfolioStatements()) {
            PublicSourceReferenceValue source = statement.sourceReference();
            values.putIfAbsent(source.getReferenceKey(), new PublicSourceReferenceValue(
                    source.getReferenceKey(), source.getLabel(), source.getPublishedVersion(),
                    source.getSourceType(), source.getSubjectRoute(), source.getEvidenceRoute()));
        }
        return List.copyOf(values.values());
    }
}
