package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioFactExpressionCompilerAdversarialTest {
    private final PortfolioFactExpressionCompiler compiler =
            new PortfolioFactExpressionCompiler(PresentationPolicy.defaults());

    @Test
    void acceptsOnlyClosedSectionsBoundToCanonicalPublicSourcesAndCaveats() {
        PortfolioPresentation compiled = compiler.compile(result(), canonical(), """
                {"sections":[{"sectionType":"SOLUTION","title":"方案",
                "content":"受控表达文本","publicSourceKeys":["E-01"]}],
                "caveats":["LIMITATION"]}
                """);
        assertThat(compiled.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getContent()).isEqualTo("受控表达文本");
            assertThat(section.getSources()).extracting(PublicSourceReferenceValue::getReferenceKey)
                    .containsExactly("E-01");
        });
    }

    @Test
    void rejectsUnknownSourceUnknownFieldAndRemovedCaveatAtomically() {
        assertThatThrownBy(() -> compiler.compile(result(), canonical(), """
                {"sections":[{"sectionType":"SOLUTION","title":"方案",
                "content":"文本","publicSourceKeys":["UNKNOWN"]}],"caveats":["LIMITATION"]}
                """)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compiler.compile(result(), canonical(), """
                {"sections":[{"sectionType":"SOLUTION","title":"方案",
                "content":"文本","publicSourceKeys":["E-01"],"taskId":"internal"}],
                "caveats":["LIMITATION"]}
                """)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compiler.compile(result(), canonical(), """
                {"sections":[{"sectionType":"SOLUTION","title":"方案",
                "content":"文本","publicSourceKeys":["E-01"]}],"caveats":[]}
                """)).isInstanceOf(IllegalArgumentException.class);
    }

    private PortfolioSemanticResult.Fact result() {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.IMPLEMENTATION,
                "statement", "detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of("evidence-1"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit("project-a", claim,
                new PublicSourceReferenceValue(
                        "E-01", "Evidence", "public-1", "DOCUMENT",
                        "/projects/project-a", "/evidence/e-01"));
        return new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.PARTIAL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), List.of(unit), List.of("LIMITATION"));
    }

    private PortfolioPresentation canonical() {
        return new PortfolioPresentation("回答", List.of(new PortfolioPresentation.Section(
                AnswerSectionType.SOLUTION, "方案", "canonical",
                List.of(new PublicSourceReferenceValue(
                        "E-01", "Evidence", "public-1", "DOCUMENT",
                        "/projects/project-a", "/evidence/e-01")))));
    }
}
