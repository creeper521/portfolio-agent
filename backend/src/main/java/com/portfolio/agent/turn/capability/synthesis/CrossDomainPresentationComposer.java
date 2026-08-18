package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.evidence.PublicSourceReference;

import java.util.LinkedHashMap;
import java.util.List;

public final class CrossDomainPresentationComposer {
    public CrossDomainPresentation compose(CrossDomainSemanticResult result) {
        String general = result.getGeneralStatements().stream()
                .map(value -> value.getText()).collect(java.util.stream.Collectors.joining("\n"));
        String portfolio = result.getPortfolioStatements().stream()
                .map(CrossDomainSemanticResult.GroundedPortfolioStatement::text)
                .collect(java.util.stream.Collectors.joining("\n"));
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
                                "上述项目事实具体说明了“" + result.getConceptAnchor() + "”的应用。",
                                sources)));
    }

    private List<PublicSourceReferenceValue> sources(CrossDomainSemanticResult result) {
        LinkedHashMap<String, PublicSourceReferenceValue> values = new LinkedHashMap<>();
        for (CrossDomainSemanticResult.GroundedPortfolioStatement statement
                : result.getPortfolioStatements()) {
            PublicSourceReference source = statement.sourceReference();
            values.putIfAbsent(source.getReferenceKey(), new PublicSourceReferenceValue(
                    source.getReferenceKey(), source.getLabel(), source.getPublishedVersion(),
                    source.getSourceType(), source.getSubjectRoute(), source.getEvidenceRoute()));
        }
        return List.copyOf(values.values());
    }
}
