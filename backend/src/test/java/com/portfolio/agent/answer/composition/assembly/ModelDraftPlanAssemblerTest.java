package com.portfolio.agent.answer.composition.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatementContractTest;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelDraftPlanAssemblerTest {
    @Test void aggregatesSentencesBySectionAndAppendsServerBoundaryWithinBudget() {
        com.portfolio.agent.answer.composition.domain.GroundedStatement statement =
                GroundedStatementContractTest.statement();
        ExpressionStatement entry = new ExpressionStatement(statement, PresentationRole.REQUIRED,
                AnswerSectionType.VERIFICATION, 0);
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题", new SubjectReference("公开项目"),
                FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(entry), OrderingPolicy.STABLE)), SummaryPolicy.FORBIDDEN,
                List.of("仅覆盖公开验证"), List.of("生产指标"));
        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        aliases.addSubject("P01", material.getSubject()); aliases.addStatement("S001", entry);
        FactExpressionDraft draft = new FactExpressionDraft("portfolio-expression-draft.v1", null,
                List.of(new FactExpressionDraft.FactDraftSection(AnswerSectionType.VERIFICATION,
                        List.of(new DraftSentence("计划在 2026 年阶段性协作完成 API 原型 42%", List.of("S001"))))));
        com.portfolio.agent.answer.domain.PortfolioAnswerPlan plan =
                new ModelDraftPlanAssembler().assemble(material, draft, aliases, 1000);
        assertThat(plan.getSections()).hasSize(2);
        assertThat(plan.getSections().get(0).getEvidenceIds()).isEmpty();
        assertThat(plan.getSections().get(0).getSourceReferences())
                .extracting(com.portfolio.agent.answer.domain.PublicSourceReferenceValue::getReferenceKey)
                .containsExactly("REF-SECRET");
        assertThat(plan.getSections().get(1).getContent()).contains("仅覆盖公开验证", "生产指标");
        assertThatThrownBy(() -> new ModelDraftPlanAssembler().assemble(material, draft, aliases, 10))
                .isInstanceOf(PlanAssemblyException.class);
    }

    @Test void deduplicatesSharedPublicReferenceByReferenceKey() {
        com.portfolio.agent.answer.composition.domain.GroundedStatement first =
                GroundedStatementContractTest.statement();
        com.portfolio.agent.answer.composition.domain.GroundedStatement second =
                new com.portfolio.agent.answer.composition.domain.GroundedStatement(
                        first.getStatementType(), first.getSubjectReferences(),
                        first.getControlledPredicate(), "另一条公开陈述", null,
                        first.getClaimCategory(), first.getAchievementStatus(),
                        first.getContributionType(), first.getVerificationBasis(),
                        first.getMateriality(), first.getSupportTarget(),
                        List.of(new com.portfolio.agent.answer.domain.PublicSourceReferenceValue(
                                "REF-SECRET", "另一个展示标签", "v2", "CASE",
                                "/projects/public", "/evidence/public")));
        ExpressionStatement firstEntry = new ExpressionStatement(first, PresentationRole.REQUIRED,
                AnswerSectionType.VERIFICATION, 0);
        ExpressionStatement secondEntry = new ExpressionStatement(second, PresentationRole.OPTIONAL,
                AnswerSectionType.VERIFICATION, 1);
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(firstEntry, secondEntry), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of(), List.of());
        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        aliases.addSubject("P01", material.getSubject());
        aliases.addStatement("S001", firstEntry);
        aliases.addStatement("S002", secondEntry);
        FactExpressionDraft draft = new FactExpressionDraft("portfolio-expression-draft.v1", null,
                List.of(new FactExpressionDraft.FactDraftSection(AnswerSectionType.VERIFICATION,
                        List.of(new DraftSentence("共享公开来源", List.of("S001", "S002"))))));

        com.portfolio.agent.answer.domain.PortfolioAnswerPlan plan =
                new ModelDraftPlanAssembler().assemble(material, draft, aliases, 1000);

        assertThat(plan.getSections().getFirst().getSourceReferences())
                .extracting(com.portfolio.agent.answer.domain.PublicSourceReferenceValue::getReferenceKey)
                .containsExactly("REF-SECRET");
    }

    @Test void countsSummaryInsideModelContentBudget() {
        com.portfolio.agent.answer.composition.domain.GroundedStatement statement =
                GroundedStatementContractTest.statement();
        ExpressionStatement entry = new ExpressionStatement(statement, PresentationRole.REQUIRED,
                AnswerSectionType.VERIFICATION, 0);
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题",
                new SubjectReference("公开项目"), FocusMode.OVERVIEW,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(entry), OrderingPolicy.STABLE)), SummaryPolicy.REQUIRED,
                List.of(), List.of());
        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        aliases.addSubject("P01", material.getSubject()); aliases.addStatement("S001", entry);
        String summaryText = "概".repeat(300);
        FactExpressionDraft draft = new FactExpressionDraft("portfolio-expression-draft.v1",
                new com.portfolio.agent.answer.composition.domain.draft.DraftText(
                        summaryText, List.of("S001")),
                List.of(new FactExpressionDraft.FactDraftSection(AnswerSectionType.VERIFICATION,
                        List.of(
                                new DraftSentence("甲".repeat(550), List.of("S001")),
                                new DraftSentence("乙".repeat(550), List.of("S001")),
                                new DraftSentence("丙".repeat(550), List.of("S001")),
                                new DraftSentence("丁".repeat(550), List.of("S001"))))));

        assertThatThrownBy(() -> new ModelDraftPlanAssembler().assemble(
                material, draft, aliases, 10_000))
                .isInstanceOf(PlanAssemblyException.class)
                .hasMessageContaining("MODEL_CONTENT_LIMIT");
    }
}
