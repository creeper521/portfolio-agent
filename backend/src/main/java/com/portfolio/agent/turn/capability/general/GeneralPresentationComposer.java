package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.AnswerSectionType;

import java.util.ArrayList;
import java.util.List;

public final class GeneralPresentationComposer {
    public GeneralPresentation compose(GeneralSemanticResult result) {
        List<GeneralPresentation.Section> sections = new ArrayList<>();
        for (GeneralSemanticResult.Statement statement : result.getStatements()) {
            sections.add(new GeneralPresentation.Section(
                    sectionType(statement.getRole()), title(statement), statement.getText()));
        }
        if (!result.getCaveats().isEmpty()) {
            sections.add(new GeneralPresentation.Section(
                    AnswerSectionType.BOUNDARY, "适用边界", String.join("\n", result.getCaveats())));
        }
        return new GeneralPresentation(result.getTopic(), sections);
    }

    private AnswerSectionType sectionType(GeneralSemanticResult.Role role) {
        return role == GeneralSemanticResult.Role.DEFINITION
                ? AnswerSectionType.BACKGROUND : AnswerSectionType.SOLUTION;
    }

    private String title(GeneralSemanticResult.Statement statement) {
        return switch (statement.getRole()) {
            case DEFINITION -> "概念";
            case MECHANISM -> "机制";
            case COMPARISON -> statement.getSubject() + " · " + statement.getDimension();
        };
    }
}
