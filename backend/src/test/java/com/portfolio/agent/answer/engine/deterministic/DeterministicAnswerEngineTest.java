package com.portfolio.agent.answer.engine.deterministic;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnswerEngineTest {

    private DeterministicAnswerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeterministicAnswerEngine();
    }

    @Test
    void buildsFiveStructuredSectionsFromResolvedContext() {
        GeneratedAnswer result = engine.answer(context());

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
    void emitsOnlyEvidenceApprovedByTheResolvedContext() {
        ResolvedAnswerContext context = new ResolvedAnswerContext(
                null, knowledge(), knowledge().getQuestions().getFirst(),
                List.of(evidence("evidence-1"))
        );

        GeneratedAnswer result = engine.answer(context);

        assertThat(result.getSections())
                .allSatisfy(section -> assertThat(section.getEvidenceIds())
                        .doesNotContain("evidence-2"));
    }

    private ResolvedAnswerContext context() {
        AnswerKnowledge knowledge = knowledge();
        return new ResolvedAnswerContext(
                null, knowledge, knowledge.getQuestions().getFirst(), knowledge.getEvidence()
        );
    }

    private AnswerKnowledge knowledge() {
        return new AnswerKnowledge(
                "sql-audit",
                "SQL audit tool",
                "A public project summary",
                "Project background",
                List.of("Responsibility one.", "Responsibility two."),
                "Technical solution.",
                List.of("Decision one.", "Decision two."),
                List.of("Verification one.", "Verification two."),
                "Delivered.",
                "Handed over.",
                "DELIVERED",
                List.of(new AnswerQuestion(
                        "project-overview",
                        "Describe this project",
                        List.of("What did you build?"),
                        "Project overview"
                )),
                List.of(evidence("evidence-2"), evidence("evidence-1")),
                List.of(
                        claim("claim-background", AnswerClaimCategory.BACKGROUND, "evidence-2"),
                        claim("claim-responsibility", AnswerClaimCategory.RESPONSIBILITY, "evidence-1"),
                        claim("claim-solution", AnswerClaimCategory.TECHNICAL_DECISION, "evidence-1"),
                        claim("claim-verification", AnswerClaimCategory.VERIFICATION, "evidence-2"),
                        claim("claim-outcome", AnswerClaimCategory.OUTCOME, "evidence-1")
                )
        );
    }

    private AnswerClaimProjection claim(String id, AnswerClaimCategory category, String evidenceId) {
        return new AnswerClaimProjection(id, category, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of(evidenceId));
    }

    private AnswerEvidence evidence(String id) {
        return new AnswerEvidence(
                id,
                "Evidence " + id,
                "DOCUMENT",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 14),
                2,
                "Summary " + id,
                "APPROVED",
                false
        );
    }
}
