package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;

import java.util.LinkedHashMap;
import java.util.List;

public final class CrossDomainPresentationComposer {
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

    private String trimSentenceEnd(String value) {
        return value.replaceFirst("[。！？；]+$", "");
    }

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
