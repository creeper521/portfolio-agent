package com.portfolio.agent.answer.engine.deterministic;

import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanSection;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.ExpressionPolicy;
import com.portfolio.agent.answer.domain.ExpressionTone;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnswerEngineTest {

    private DeterministicAnswerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeterministicAnswerEngine();
    }

    @Test
    void rendersTheFiveStructuredSectionsFromTheProvidedPlan() {
        GeneratedAnswer result = engine.answer(plan(true));

        assertThat(result.getTitle()).isEqualTo("SQL audit tool");
        assertThat(result.getSummary()).isEqualTo("A public project summary");
        assertThat(result.getSections()).extracting(AnswerSection::getType)
                .containsExactly(
                        AnswerSectionType.BACKGROUND,
                        AnswerSectionType.RESPONSIBILITY,
                        AnswerSectionType.SOLUTION,
                        AnswerSectionType.VERIFICATION,
                        AnswerSectionType.STATUS
                );
        assertThat(result.getSections()).allSatisfy(section -> {
            assertThat(section.getTitle()).isNotBlank();
            assertThat(section.getContent()).isNotBlank();
            assertThat(section.getClaimIds()).hasSize(1);
            assertThat(section.getEvidenceIds()).hasSize(1);
        });
        assertThat(result.getSections()).extracting(section -> section.getClaimIds().getFirst())
                .containsExactly("claim-background", "claim-responsibility", "claim-solution",
                        "claim-verification", "claim-outcome");
        assertThat(result.getSections()).extracting(AnswerSection::getContent)
                .containsExactly(
                        "Project background",
                        "Responsibility one. Responsibility two.",
                        "Technical solution. 关键决策包括：Decision one. Decision two.",
                        "Verification one. Verification two.",
                        "Delivered. Handed over."
                );
    }

    @Test
    void emitsOnlyEvidenceWhitelistedByThePlan() {
        GeneratedAnswer result = engine.answer(plan(false));

        assertThat(result.getSections())
                .allSatisfy(section -> assertThat(section.getEvidenceIds())
                        .doesNotContain("evidence-2"));
    }

    private AnswerPlan plan(boolean includeSecondEvidence) {
        List<String> firstEvidence = List.of("evidence-1");
        List<String> secondEvidence = includeSecondEvidence
                ? List.of("evidence-2")
                : firstEvidence;
        List<AnswerPlanSection> sections = List.of(
                section(AnswerSectionType.BACKGROUND, "背景", "Project background",
                        "claim-background", secondEvidence),
                section(AnswerSectionType.RESPONSIBILITY, "职责",
                        "Responsibility one. Responsibility two.",
                        "claim-responsibility", firstEvidence),
                section(AnswerSectionType.SOLUTION, "方案",
                        "Technical solution. 关键决策包括：Decision one. Decision two.",
                        "claim-solution", firstEvidence),
                section(AnswerSectionType.VERIFICATION, "验证",
                        "Verification one. Verification two.",
                        "claim-verification", secondEvidence),
                section(AnswerSectionType.STATUS, "状态", "Delivered. Handed over.",
                        "claim-outcome", firstEvidence)
        );
        return new AnswerPlan(
                "2026-07-22", "project-overview", "Describe this project", null,
                "SQL audit tool", "A public project summary", sections,
                List.of(), List.of(), new ExpressionPolicy(
                        ExpressionTone.CONCISE_TECHNICAL, 80, 240, 40, 800,
                        true, true, List.of())
        );
    }

    private AnswerPlanSection section(
            AnswerSectionType type,
            String title,
            String content,
            String claimId,
            List<String> evidenceIds
    ) {
        return new AnswerPlanSection(type, title, content, List.of(claimId), evidenceIds);
    }
}
