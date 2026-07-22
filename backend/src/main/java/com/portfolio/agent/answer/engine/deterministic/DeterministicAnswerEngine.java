package com.portfolio.agent.answer.engine.deterministic;

import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanSection;
import com.portfolio.agent.answer.engine.AnswerEngine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeterministicAnswerEngine implements AnswerEngine {

    @Override
    public GeneratedAnswer answer(AnswerPlan plan) {
        List<AnswerSection> sections = plan.getRequiredSections().stream()
                .map(this::renderSection)
                .toList();
        return new GeneratedAnswer(plan.getProjectTitle(), plan.getProjectSummary(), sections);
    }

    private AnswerSection renderSection(AnswerPlanSection section) {
        return new AnswerSection(
                section.getType(),
                section.getCanonicalTitle(),
                section.getCanonicalContent(),
                section.getAllowedEvidenceIds(),
                section.getAllowedClaimIds()
        );
    }
}
