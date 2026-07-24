package com.portfolio.agent.answer.adapter.tool;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerTimelineEvent;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPublicKnowledgeToolsTest {

    private final LocalPublicKnowledgeTools tools = new LocalPublicKnowledgeTools();
    private final RuntimeAnswerContent content = content();

    @Test
    void executesAllSixClosedToolsAgainstPublicRuntimeContent() {
        assertThat(execute(ToolKind.GET_PROJECT, List.of("sql-audit"), List.of())
                .getProjects()).extracting(AnswerKnowledge::getSlug)
                .containsExactly("sql-audit");
        assertThat(execute(ToolKind.GET_CLAIMS, List.of("sql-audit"), List.of("claim-sql"))
                .getClaims()).extracting(AnswerClaimProjection::getId)
                .containsExactly("claim-sql");
        assertThat(execute(
                ToolKind.GET_EVIDENCE_FOR_CLAIMS,
                List.of("sql-audit"),
                List.of("claim-sql"))
                .getEvidence()).extracting(AnswerEvidence::getId)
                .containsExactly("evidence-sql");
        assertThat(execute(ToolKind.GET_TIMELINE, List.of("sql-audit"), List.of("claim-sql"))
                .getTimeline()).extracting(AnswerTimelineEvent::getId)
                .containsExactly("timeline-sql");
        assertThat(execute(ToolKind.SEARCH_PUBLIC_CONTENT, List.of("sql-audit"), List.of())
                .getQuestions()).extracting(AnswerQuestion::getId)
                .containsExactly("question-sql");
        assertThat(execute(
                ToolKind.COMPARE_PROJECTS,
                List.of("sql-audit", "portfolio-agent"),
                List.of()).getProjects())
                .extracting(AnswerKnowledge::getSlug)
                .containsExactly("sql-audit", "portfolio-agent");
    }

    @Test
    void returnsInsufficientInsteadOfGuessingUnknownOrSingleProjectComparisons() {
        PublicToolResult unknown = execute(
                ToolKind.GET_PROJECT, List.of("missing-project"), List.of());
        PublicToolResult oneProject = execute(
                ToolKind.COMPARE_PROJECTS, List.of("sql-audit"), List.of());

        assertThat(unknown.getStatus()).isEqualTo(PublicToolResultStatus.INSUFFICIENT);
        assertThat(oneProject.getStatus()).isEqualTo(PublicToolResultStatus.INSUFFICIENT);
        assertThat(oneProject.getProjects()).isEmpty();
    }

    @Test
    void neverExpandsEvidenceOutsideTheSelectedClaims() {
        PublicToolResult result = execute(
                ToolKind.GET_EVIDENCE_FOR_CLAIMS,
                List.of("sql-audit"),
                List.of("claim-sql"));

        assertThat(result.getEvidence()).extracting(AnswerEvidence::getId)
                .containsExactly("evidence-sql")
                .doesNotContain("evidence-agent");
    }

    @Test
    void reportsInsufficientWhenASelectedClaimHasNoApprovedEvidence() {
        AnswerClaimProjection unsupported = new AnswerClaimProjection(
                "claim-without-evidence",
                AnswerClaimCategory.OUTCOME,
                "statement",
                "detail",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.SELF_DECLARED,
                AnswerClaimVerificationStatus.UNVERIFIED,
                AnswerMateriality.KEY,
                List.of("DELIVERY_STATUS"),
                List.of());
        RuntimeAnswerContent unsupportedContent = new RuntimeAnswerContent(
                "2026-07-21.1",
                "sha256:runtime",
                List.of(new AnswerKnowledge(
                        "sql-audit", "sql-audit", "summary", "background",
                        List.of("responsibility"), "solution", List.of("decision"),
                        List.of("verified"), "outcome", "handoff", "DELIVERED",
                        List.of(), List.of(), List.of(unsupported))));

        PublicToolResult result = tools.execute(unsupportedContent, new ToolCall(
                ToolKind.GET_EVIDENCE_FOR_CLAIMS,
                List.of("sql-audit"),
                List.of("claim-without-evidence"),
                AnswerSectionType.STATUS));

        assertThat(result.getStatus()).isEqualTo(PublicToolResultStatus.INSUFFICIENT);
        assertThat(result.getEvidence()).isEmpty();
    }

    @Test
    void getsCaseAndItsBoundedClaimsAndEvidence() {
        AnswerEvidence caseEvidence = evidence("evidence-case");
        AnswerClaimProjection caseClaim = claim("claim-case", "evidence-case");
        AnswerKnowledge caseStudy = new AnswerKnowledge(
                AnswerSubjectType.CASE,
                "codegraph-evaluation", "CodeGraph", "summary", "problem",
                List.of("action"), "solution", List.of("decision"),
                List.of("verified"), "outcome", "limitation", "PROTOTYPE",
                List.of(new AnswerQuestion(
                        "question-case", "Case overview", List.of(), "Case overview")),
                List.of(caseEvidence), List.of(caseClaim));
        RuntimeAnswerContent caseContent = new RuntimeAnswerContent(
                "2026-07-23.1", "sha256:runtime", List.of(), List.of(caseStudy),
                null, List.of());

        PublicToolResult result = tools.execute(caseContent, new ToolCall(
                ToolKind.GET_CASE,
                List.of(),
                List.of("codegraph-evaluation"),
                List.of("claim-case"),
                AnswerSectionType.STATUS));

        assertThat(result.getCases()).extracting(AnswerKnowledge::getSlug)
                .containsExactly("codegraph-evaluation");
    }

    private PublicToolResult execute(
            ToolKind kind,
            List<String> projectSlugs,
            List<String> claimIds
    ) {
        return tools.execute(content, new ToolCall(
                kind, projectSlugs, claimIds, AnswerSectionType.STATUS));
    }

    private RuntimeAnswerContent content() {
        AnswerEvidence sqlEvidence = evidence("evidence-sql");
        AnswerEvidence agentEvidence = evidence("evidence-agent");
        AnswerClaimProjection sqlClaim = claim("claim-sql", "evidence-sql");
        AnswerClaimProjection agentClaim = claim("claim-agent", "evidence-agent");
        AnswerKnowledge sql = project(
                "sql-audit", "question-sql", sqlEvidence, sqlClaim);
        AnswerKnowledge agent = project(
                "portfolio-agent", "question-agent", agentEvidence, agentClaim);
        AnswerTimelineEvent timeline = new AnswerTimelineEvent(
                "timeline-sql", "2026-07", "SQL Audit", "problem", "action", "impact",
                List.of("sql-audit"), List.of("claim-sql"), List.of("evidence-sql"));
        return new RuntimeAnswerContent(
                "2026-07-21.1",
                "sha256:runtime",
                List.of(sql, agent),
                null,
                List.of(timeline));
    }

    private AnswerKnowledge project(
            String slug,
            String questionId,
            AnswerEvidence evidence,
            AnswerClaimProjection claim
    ) {
        return new AnswerKnowledge(
                slug, slug, "summary", "background", List.of("responsibility"),
                "solution", List.of("decision"), List.of("verified"), "outcome",
                "handoff", "DELIVERED",
                List.of(new AnswerQuestion(
                        questionId, "Canonical " + questionId, List.of(), "Suggested")),
                List.of(evidence), List.of(claim));
    }

    private AnswerClaimProjection claim(String id, String evidenceId) {
        return new AnswerClaimProjection(
                id,
                AnswerClaimCategory.OUTCOME,
                "statement",
                "detail",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("DELIVERY_STATUS"),
                List.of(evidenceId));
    }

    private AnswerEvidence evidence(String id) {
        return new AnswerEvidence(
                id, id, "DOCUMENT", LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-21"), 1, "summary", "APPROVED", false);
    }
}
