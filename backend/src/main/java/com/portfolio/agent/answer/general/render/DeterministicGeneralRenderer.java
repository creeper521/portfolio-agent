package com.portfolio.agent.answer.general.render;

import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;
import com.portfolio.agent.answer.general.domain.GeneralStatement;
import com.portfolio.agent.turn.execution.SectionedTaskPresentation;

import java.util.ArrayList;
import java.util.List;

public final class DeterministicGeneralRenderer {
    public SectionedTaskPresentation render(GeneralAnswerMaterial material) {
        List<SectionedTaskPresentation.Section> sections = new ArrayList<>();
        for (GeneralStatement statement : material.getStatements()) {
            sections.add(new SectionedTaskPresentation.Section(
                    sectionType(statement), statement.getRole().name(), statement.getText(), List.of()));
        }
        for (com.portfolio.agent.answer.general.domain.MaterialCaveat caveat : material.getCaveats()) {
            sections.add(new SectionedTaskPresentation.Section(
                    com.portfolio.agent.answer.domain.AnswerSectionType.BOUNDARY,
                    caveat.getAlias(), caveat.getText(), List.of()));
        }
        return new SectionedTaskPresentation(material.getTopic(), sections);
    }
    private com.portfolio.agent.answer.domain.AnswerSectionType sectionType(GeneralStatement statement) {
        return switch (statement.getRole()) {
            case DEFINITION, MECHANISM -> com.portfolio.agent.answer.domain.AnswerSectionType.BACKGROUND;
            case ADVANTAGE, LIMITATION, CONTRAST -> com.portfolio.agent.answer.domain.AnswerSectionType.VERIFICATION;
            case USE_CASE, PRACTICE -> com.portfolio.agent.answer.domain.AnswerSectionType.SOLUTION;
            case CAUTION -> com.portfolio.agent.answer.domain.AnswerSectionType.BOUNDARY;
        };
    }
}
