package com.portfolio.agent.answer.general.render;

import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;
import com.portfolio.agent.answer.general.domain.GeneralStatement;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;

import java.util.ArrayList;
import java.util.List;

public final class DeterministicGeneralRenderer {
    public TaskResultPayload.SectionResultPayload render(GeneralAnswerMaterial material) {
        List<TaskResultPayload.SectionBlock> sections = new ArrayList<>();
        for (GeneralStatement statement : material.getStatements()) {
            sections.add(new TaskResultPayload.SectionBlock(
                    sectionType(statement), statement.getRole().name(), statement.getText(), List.of(), List.of()));
        }
        for (com.portfolio.agent.answer.general.domain.MaterialCaveat caveat : material.getCaveats()) {
            sections.add(new TaskResultPayload.SectionBlock(
                    com.portfolio.agent.answer.domain.AnswerSectionType.BOUNDARY, caveat.getAlias(), caveat.getText(), List.of(), List.of()));
        }
        return TaskResultPayload.SectionResultPayload.fromSections(sections, material.getTopic());
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
