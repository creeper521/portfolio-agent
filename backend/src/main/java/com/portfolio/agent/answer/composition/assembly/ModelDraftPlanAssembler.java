package com.portfolio.agent.answer.composition.assembly;

import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.ModelExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Builds a complete plan atomically. Draft text never supplies titles, boundaries or references. */
public final class ModelDraftPlanAssembler {
    public PortfolioAnswerPlan assemble(PortfolioAnswerMaterial material, ModelExpressionDraft draft,
            ExpressionAliasRegistry aliases, int characterLimit) {
        if (!(material instanceof FactAnswerMaterial)
                || !(draft instanceof FactExpressionDraft factDraft)) {
            throw new PlanAssemblyException("UNSUPPORTED_MATERIAL_KIND");
        }
        if (characterLimit < 1) throw new PlanAssemblyException("CHARACTER_LIMIT");
        try {
            List<PortfolioAnswerSection> sections = new ArrayList<>();
            List<PublicSourceReferenceValue> summaryReferences = factDraft.getSummary() == null
                    ? List.of() : publicReferencesForAliases(
                            factDraft.getSummary().getSupports(), aliases);
            int bodyCharacters = factDraft.getSummary() == null
                    ? 0 : factDraft.getSummary().getText().length();
            for (FactExpressionDraft.FactDraftSection section : factDraft.getSections()) {
                List<PublicSourceReferenceValue> publicReferences =
                        publicReferences(section.getSentences(), aliases);
                String body = String.join("\n", section.getSentences().stream()
                        .map(DraftSentence::getText).toList());
                bodyCharacters += body.length();
                if (bodyCharacters > Math.min(2400, characterLimit)) {
                    throw new PlanAssemblyException("MODEL_CONTENT_LIMIT");
                }
                if (sections.isEmpty() && !summaryReferences.isEmpty()) {
                    publicReferences = mergeReferences(summaryReferences, publicReferences);
                }
                sections.add(PortfolioAnswerSection.grounded(section.getSectionType(),
                        sectionTitle(section.getSectionType()),
                        body,
                        publicReferences));
            }
            List<String> boundaryLines = new ArrayList<>(material.getFixedCaveats());
            if (!material.getOmittedTopicLabels().isEmpty()) {
                boundaryLines.add("未覆盖：" + String.join("、", material.getOmittedTopicLabels()));
            }
            if (!boundaryLines.isEmpty()) {
                sections.add(new PortfolioAnswerSection(AnswerSectionType.BOUNDARY, "边界",
                        String.join("；", boundaryLines), List.of(), List.of()));
            }
            String summary = factDraft.getSummary() == null ? null : factDraft.getSummary().getText();
            PortfolioAnswerPlan plan = new PortfolioAnswerPlan(material.getPublicTitle(), summary, sections);
            if (characterCount(plan) > characterLimit) {
                throw new PlanAssemblyException("CHARACTER_LIMIT");
            }
            return plan;
        } catch (PlanAssemblyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlanAssemblyException("PLAN_INVARIANT", exception);
        }
    }

    private static List<PublicSourceReferenceValue> publicReferences(List<DraftSentence> sentences,
            ExpressionAliasRegistry aliases) {
        LinkedHashMap<String, PublicSourceReferenceValue> values = new LinkedHashMap<>();
        for (DraftSentence sentence : sentences) {
            for (String alias : sentence.getSupports()) {
                if (!aliases.containsStatement(alias)) throw new PlanAssemblyException("ALIAS_SCOPE");
                aliases.statement(alias).getPublicSourceReferences().forEach(reference ->
                        values.putIfAbsent(reference.getReferenceKey(), reference));
            }
        }
        if (values.isEmpty()) throw new PlanAssemblyException("SOURCE_REQUIRED");
        return List.copyOf(values.values());
    }

    private static List<PublicSourceReferenceValue> publicReferencesForAliases(List<String> supports,
            ExpressionAliasRegistry aliases) {
        LinkedHashMap<String, PublicSourceReferenceValue> values = new LinkedHashMap<>();
        for (String alias : supports) {
            if (!aliases.containsStatement(alias)) throw new PlanAssemblyException("ALIAS_SCOPE");
            aliases.statement(alias).getPublicSourceReferences().forEach(reference ->
                    values.putIfAbsent(reference.getReferenceKey(), reference));
        }
        return List.copyOf(values.values());
    }

    private static List<PublicSourceReferenceValue> mergeReferences(
            List<PublicSourceReferenceValue> first, List<PublicSourceReferenceValue> second) {
        LinkedHashMap<String, PublicSourceReferenceValue> values = new LinkedHashMap<>();
        first.forEach(reference -> values.putIfAbsent(reference.getReferenceKey(), reference));
        second.forEach(reference -> values.putIfAbsent(reference.getReferenceKey(), reference));
        return List.copyOf(values.values());
    }

    private static int characterCount(PortfolioAnswerPlan plan) {
        int count = plan.getTitle().length() + (plan.getSummary() == null ? 0 : plan.getSummary().length());
        for (PortfolioAnswerSection section : plan.getSections()) {
            count += section.getTitle().length() + section.getContent().length();
        }
        return count;
    }

    private static String sectionTitle(AnswerSectionType type) {
        return switch (type) {
            case BACKGROUND -> "背景";
            case RESPONSIBILITY -> "职责";
            case SOLUTION -> "方案";
            case VERIFICATION -> "验证";
            case STATUS -> "状态";
            case BOUNDARY -> "边界";
            case REJECTED -> throw new PlanAssemblyException("SECTION_NOT_ALLOWED");
        };
    }
}
