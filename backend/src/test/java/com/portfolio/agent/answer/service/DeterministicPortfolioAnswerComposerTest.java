package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.GroundedStatement;
import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicPortfolioAnswerComposerTest {

    @Test
    void composesOnlyValidatedP3MaterialAndPublicReferences() {
        PortfolioAnswerMaterial material = new PortfolioAnswerMaterial(
                PortfolioAnswerMaterial.MaterialKind.FACT,
                "SQL Audit",
                List.of(new GroundedStatement("Built a bounded audit flow", List.of("REF-1"))),
                List.of("Only published evidence is included"),
                List.of());

        PortfolioAnswerPlan plan = new DeterministicPortfolioAnswerComposer().compose(material);

        assertThat(plan.getTitle()).isEqualTo("SQL Audit");
        assertThat(plan.getSections()).hasSize(1);
        assertThat(plan.getSections().getFirst().getContent())
                .isEqualTo("Built a bounded audit flow");
        assertThat(plan.getSections().getFirst().getEvidenceIds())
                .containsExactly("REF-1");
    }
}
