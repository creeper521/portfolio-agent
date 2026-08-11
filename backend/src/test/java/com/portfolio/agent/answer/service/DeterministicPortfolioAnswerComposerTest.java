package com.portfolio.agent.answer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.answer.exception.PortfolioAnswerCompositionException;
import com.portfolio.agent.answer.intelligence.domain.AnswerFocus;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicPortfolioAnswerComposerTest {

    private final DeterministicPortfolioAnswerComposer composer =
            new DeterministicPortfolioAnswerComposer();

    @Test
    void overviewComposesAllSixSectionsInAuthoritativeOrderWithLeadSummary() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.overview(),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(
                        passage("claim-background", AnswerClaimCategory.BACKGROUND,
                                "项目背景事实。", "evidence-bg"),
                        passage("claim-responsibility", AnswerClaimCategory.RESPONSIBILITY,
                                "负责审计链路设计。", "evidence-resp"),
                        passage("claim-solution", AnswerClaimCategory.TECHNICAL_DECISION,
                                "采用受控路由替代硬编码。", "evidence-sol"),
                        passage("claim-verification", AnswerClaimCategory.VERIFICATION,
                                "已验证主要功能流程。", "evidence-ver"),
                        passage("claim-outcome", AnswerClaimCategory.OUTCOME,
                                "已交付并投入使用。", "evidence-out"),
                        passage("claim-limitation", AnswerClaimCategory.LIMITATION,
                                "当前公开材料不包含全部运营细节。", "evidence-lim")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getTitle()).isEqualTo("SQL 审计与故障排查工具");
        assertThat(plan.getSummary()).isEqualTo("公开项目摘要");
        assertThat(plan.getSections())
                .extracting(PortfolioAnswerSection::getSectionType)
                .containsExactly(
                        AnswerSectionType.BACKGROUND,
                        AnswerSectionType.RESPONSIBILITY,
                        AnswerSectionType.SOLUTION,
                        AnswerSectionType.VERIFICATION,
                        AnswerSectionType.STATUS,
                        AnswerSectionType.BOUNDARY);
    }

    @Test
    void focusedSkipsSummaryAndComposesOnlyRequestedSections() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.focused(List.of(AnswerClaimCategory.VERIFICATION)),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(
                        passage("claim-verification", AnswerClaimCategory.VERIFICATION,
                                "已验证主要功能流程。", "evidence-ver"),
                        passage("claim-background", AnswerClaimCategory.BACKGROUND,
                                "项目背景事实。", "evidence-bg")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSummary()).isNull();
        assertThat(plan.getSections())
                .extracting(PortfolioAnswerSection::getSectionType)
                .containsExactly(AnswerSectionType.VERIFICATION);
        assertThat(plan.getSections().getFirst().getContent())
                .contains("已验证主要功能流程。");
    }

    @Test
    void partialGapMergesMissingTargetsIntoUniqueBoundaryWithoutFabricatedEvidence() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.focused(List.of(
                        AnswerClaimCategory.VERIFICATION,
                        AnswerClaimCategory.OUTCOME)),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(passage(
                        "claim-verification", AnswerClaimCategory.VERIFICATION,
                        "已验证主要功能流程。", "evidence-ver")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSummary()).isNull();
        assertThat(plan.getSections())
                .extracting(PortfolioAnswerSection::getSectionType)
                .containsExactly(
                        AnswerSectionType.VERIFICATION,
                        AnswerSectionType.BOUNDARY);
        assertThat(plan.getSections().getLast().getContent())
                .contains("当前公开材料未覆盖最终状态。")
                .doesNotContain("已上线", "长期有效");
        assertThat(plan.getSections().getLast().getEvidenceIds())
                .doesNotContain("evidence-ver");
    }

    @Test
    void focusedStatusReportsMissingBoundaryTargetWithoutLosingOutcome() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.focused(List.of(
                        AnswerClaimCategory.OUTCOME,
                        AnswerClaimCategory.LIMITATION)),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(passage("claim-outcome", AnswerClaimCategory.OUTCOME,
                        "已完成公开范围内的交付。", "evidence-out")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSections())
                .extracting(PortfolioAnswerSection::getSectionType)
                .containsExactly(AnswerSectionType.STATUS, AnswerSectionType.BOUNDARY);
        assertThat(plan.getSections().getLast().getContent())
                .isEqualTo("当前公开材料未覆盖限制与边界。");
        assertThat(plan.getSections().getLast().getEvidenceIds()).isEmpty();
    }

    @Test
    void unrequestedBoundaryCategoryCannotSatisfyFocusedLimitation() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.focused(List.of(AnswerClaimCategory.LIMITATION)),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(passage("claim-learning", AnswerClaimCategory.LEARNING,
                        "从事故复盘中学到了分层监控。", "evidence-learn")));

        assertThatThrownBy(() -> composer.compose(result))
                .isInstanceOf(PortfolioAnswerCompositionException.class)
                .hasMessageContaining("requested focus");
    }

    @Test
    void overviewAndFocusedBoundaryFactsRespectThreeAndSixFactBudgets() {
        List<PortfolioRetrievedPassage> facts = List.of(
                passage("claim-limit-1", AnswerClaimCategory.LIMITATION, "限制事实一。", "evidence-limit-1"),
                passage("claim-limit-2", AnswerClaimCategory.LIMITATION, "限制事实二。", "evidence-limit-2"),
                passage("claim-limit-3", AnswerClaimCategory.LIMITATION, "限制事实三。", "evidence-limit-3"),
                passage("claim-limit-4", AnswerClaimCategory.LIMITATION, "限制事实四。", "evidence-limit-4"),
                passage("claim-limit-5", AnswerClaimCategory.LIMITATION, "限制事实五。", "evidence-limit-5"),
                passage("claim-limit-6", AnswerClaimCategory.LIMITATION, "限制事实六。", "evidence-limit-6"),
                passage("claim-limit-7", AnswerClaimCategory.LIMITATION, "限制事实七。", "evidence-limit-7"));

        PortfolioAnswerPlan overview = composer.compose(result(
                AnswerFocus.overview(), "project-1", "SQL 审计与故障排查工具", "公开项目摘要", facts));
        PortfolioAnswerPlan focused = composer.compose(result(
                AnswerFocus.focused(List.of(AnswerClaimCategory.LIMITATION)),
                "project-1", "SQL 审计与故障排查工具", "公开项目摘要", facts));

        assertThat(overview.getSections()).filteredOn(section ->
                section.getSectionType() == AnswerSectionType.BOUNDARY)
                .singleElement().satisfies(section ->
                        assertThat(section.getClaimIds()).hasSize(3));
        assertThat(focused.getSections()).filteredOn(section ->
                section.getSectionType() == AnswerSectionType.BOUNDARY)
                .singleElement().satisfies(section -> {
                    assertThat(section.getClaimIds()).hasSize(6);
                    assertThat(section.getContent()).contains("限制事实六。")
                            .doesNotContain("限制事实七。");
                });
    }

    @Test
    void boundarySectionKeepsEveryDistinctBoundaryFactWithoutDuplicatingBody() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.overview(),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(
                        passage("claim-lim-1", AnswerClaimCategory.LIMITATION,
                                "当前公开材料不包含全部运营细节。", "evidence-lim-1"),
                        passage("claim-lim-1", AnswerClaimCategory.LIMITATION,
                                "当前公开材料不包含全部运营细节。", "evidence-lim-1"),
                        passage("claim-learn", AnswerClaimCategory.LEARNING,
                                "从事故复盘中学到了分层监控。", "evidence-learn")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSections())
                .filteredOn(section -> section.getSectionType() == AnswerSectionType.BOUNDARY)
                .singleElement().satisfies(section -> {
                    assertThat(section.getContent())
                            .contains("当前公开材料不包含全部运营细节。")
                            .contains("从事故复盘中学到了分层监控。");
                    assertThat(section.getContent().indexOf("当前公开材料不包含全部运营细节。"))
                            .isEqualTo(section.getContent().lastIndexOf("当前公开材料不包含全部运营细节。"));
                    assertThat(section.getClaimIds()).containsExactly(
                            "claim-lim-1", "claim-learn");
                    assertThat(section.getEvidenceIds()).containsExactly(
                            "evidence-lim-1", "evidence-learn");
                });
    }

    @Test
    void overviewWithoutBoundaryFactsDoesNotInventASection() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.overview(),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(passage(
                        "claim-solution", AnswerClaimCategory.IMPLEMENTATION,
                        "采用受控路由替代硬编码。", "evidence-sol")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSections())
                .extracting(PortfolioAnswerSection::getSectionType)
                .containsExactly(AnswerSectionType.SOLUTION);
        assertThat(plan.getSections().getFirst().getContent())
                .contains("采用受控路由替代硬编码。");
    }

    @Test
    void deduplicatesSameClaimAndMergesSameNormalizedStatementDetail() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.overview(),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(
                        passage("claim-ver", AnswerClaimCategory.VERIFICATION,
                                "已验证主要功能流程。", "evidence-1"),
                        passage("claim-ver", AnswerClaimCategory.VERIFICATION,
                                "已验证主要功能流程。", "evidence-1"),
                        passage("claim-ver-dup", AnswerClaimCategory.VERIFICATION,
                                "已验证主要功能流程。", "evidence-2")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSections())
                .filteredOn(section -> section.getSectionType() == AnswerSectionType.VERIFICATION)
                .singleElement().satisfies(section -> {
                    assertThat(section.getClaimIds()).containsExactly(
                            "claim-ver", "claim-ver-dup");
                    assertThat(section.getEvidenceIds()).containsExactly(
                            "evidence-1", "evidence-2");
                });
    }

    @Test
    void preservesStatusContributionAndLimitingWords() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.overview(),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(passage(
                        "claim-outcome", AnswerClaimCategory.OUTCOME,
                        "项目尚未上线，处于长期维护阶段。",
                        "协作完成，非个人独立成果。",
                        "evidence-out")));

        PortfolioAnswerPlan plan = composer.compose(result);

        assertThat(plan.getSections())
                .filteredOn(section -> section.getSectionType() == AnswerSectionType.STATUS)
                .singleElement().satisfies(section ->
                        assertThat(section.getContent())
                                .contains("尚未")
                                .contains("长期维护")
                                .contains("协作完成")
                                .doesNotContain("已上线"));
    }

    @Test
    void rejectsMultiSubjectInput() {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(
                        subject("project-1", "Project one", "摘要一"),
                        subject("project-2", "Project two", "摘要二")),
                List.of(passage("claim-1", AnswerClaimCategory.VERIFICATION,
                        "已验证。", "evidence-1")),
                null, null, false, null);

        assertThatThrownBy(() -> composer.compose(result))
                .isInstanceOf(PortfolioAnswerCompositionException.class);
    }

    @Test
    void rejectsNonFactLookupInput() {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.COMPARISON,
                List.of(subject("project-1", "Project one", "摘要一")),
                List.of(passage("claim-1", AnswerClaimCategory.VERIFICATION,
                        "已验证。", "evidence-1")),
                null, null, false, null);

        assertThatThrownBy(() -> composer.compose(result))
                .isInstanceOf(PortfolioAnswerCompositionException.class);
    }

    @Test
    void rejectsPassageWhoseSubjectIsNotTheSingleSubject() {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1", "Project one", "摘要一")),
                List.of(passage(
                        "claim-1", AnswerClaimCategory.VERIFICATION, "已验证。",
                        "验证范围以公开证据为限。", "evidence-1", "project-other")),
                null, null, false, null);

        assertThatThrownBy(() -> composer.compose(result))
                .isInstanceOf(PortfolioAnswerCompositionException.class);
    }

    @Test
    void rejectsEmptyFactCollection() {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1", "Project one", "摘要一")),
                List.of(),
                null, null, false, null);

        assertThatThrownBy(() -> composer.compose(result))
                .isInstanceOf(PortfolioAnswerCompositionException.class);
    }

    @Test
    void identicalInputProducesEqualPlanOnRepeatedExecution() {
        PortfolioIntelligenceResult result = result(
                AnswerFocus.overview(),
                "project-1",
                "SQL 审计与故障排查工具",
                "公开项目摘要",
                List.of(
                        passage("claim-background", AnswerClaimCategory.BACKGROUND,
                                "项目背景事实。", "evidence-bg"),
                        passage("claim-solution", AnswerClaimCategory.IMPLEMENTATION,
                                "采用受控路由替代硬编码。", "evidence-sol")));

        assertThat(composer.compose(result)).isEqualTo(composer.compose(result));
    }

    private PortfolioIntelligenceResult result(
            AnswerFocus focus,
            String subjectId,
            String title,
            String summary,
            List<PortfolioRetrievedPassage> passages) {
        return new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject(subjectId, title, summary)),
                passages,
                null, null, false, null)
                .withAnswerFocus(focus);
    }

    private PortfolioRetrievedSubject subject(String id, String title, String summary) {
        return new PortfolioRetrievedSubject(
                id, "PROJECT", title, summary, "/projects/" + id,
                "BACKEND", Set.of("POSTGRESQL"), 1.0d, 1.0d, 0.0d);
    }

    private PortfolioRetrievedPassage passage(
            String claimId,
            AnswerClaimCategory category,
            String statement,
            String evidenceId) {
        return passage(claimId, category, statement, "验证范围以公开证据为限。",
                evidenceId, "project-1");
    }

    private PortfolioRetrievedPassage passage(
            String claimId,
            AnswerClaimCategory category,
            String statement,
            String detail,
            String evidenceId) {
        return passage(claimId, category, statement, detail, evidenceId, "project-1");
    }

    private PortfolioRetrievedPassage passage(
            String claimId,
            AnswerClaimCategory category,
            String statement,
            String detail,
            String evidenceId,
            String subjectId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId,
                category,
                statement,
                detail,
                AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("POSTGRESQL"),
                List.of(evidenceId));
        return new PortfolioRetrievedPassage(
                subjectId + "#" + claimId,
                subjectId,
                statement,
                claim,
                List.of(new PortfolioRetrievedEvidenceReference(
                        evidenceId, "公开证据", "APPROVED")));
    }
}
