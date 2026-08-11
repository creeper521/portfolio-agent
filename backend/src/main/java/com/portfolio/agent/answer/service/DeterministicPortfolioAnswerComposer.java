package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerSectionMapping;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.answer.exception.PortfolioAnswerCompositionException;
import com.portfolio.agent.answer.intelligence.domain.AnswerFocus;
import com.portfolio.agent.answer.intelligence.domain.AnswerFocusMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 唯一的 P1 Composer：检索选择事实，Composer 组织事实。
 * 不读取用户原文，不调用模型、网络、数据库或工具。
 */
public final class DeterministicPortfolioAnswerComposer {

    private static final int OVERVIEW_FACTS_PER_SECTION = 3;
    private static final int FOCUSED_FACTS_PER_SECTION = 6;

    public PortfolioAnswerPlan compose(PortfolioIntelligenceResult result) {
        Objects.requireNonNull(result, "result");
        validate(result);
        List<PortfolioRetrievedPassage> selected = deduplicateAndBudget(result);
        AnswerFocus focus = result.getAnswerFocus();
        Map<AnswerSectionType, List<PortfolioRetrievedPassage>> grouped =
                groupBySection(selected, focus);
        if (focus.getMode() == AnswerFocusMode.FOCUSED
                && grouped.values().stream().allMatch(List::isEmpty)) {
            throw new PortfolioAnswerCompositionException(
                    "requested focus has no matching evidence");
        }
        List<PortfolioAnswerSection> sections = orderedSections(grouped, focus);
        String summary = focus.getMode() == AnswerFocusMode.OVERVIEW
                ? summary(result, selected)
                : null;
        String title = result.getSubjects().getFirst().getTitle();
        PortfolioAnswerPlan plan = new PortfolioAnswerPlan(title, summary, sections);
        verifyIdsAreInScope(plan, result);
        return plan;
    }

    private void verifyIdsAreInScope(
            PortfolioAnswerPlan plan,
            PortfolioIntelligenceResult result) {
        Set<String> claimIds = new LinkedHashSet<>();
        Set<String> evidenceIds = new LinkedHashSet<>();
        for (PortfolioRetrievedPassage passage : result.getEvidence()) {
            claimIds.add(passage.getClaimId());
            evidenceIds.addAll(passage.getEvidenceIds());
        }
        for (PortfolioAnswerSection section : plan.getSections()) {
            for (String claimId : section.getClaimIds()) {
                if (!claimIds.contains(claimId)) {
                    throw new PortfolioAnswerCompositionException(
                            "answer plan references an unknown claim");
                }
            }
            for (String evidenceId : section.getEvidenceIds()) {
                if (!evidenceIds.contains(evidenceId)) {
                    throw new PortfolioAnswerCompositionException(
                            "answer plan references an unknown evidence");
                }
            }
        }
    }

    private void validate(PortfolioIntelligenceResult result) {
        if (result.getResolvedIntent() != PortfolioTaskMode.FACT_LOOKUP) {
            throw new PortfolioAnswerCompositionException(
                    "answer composition requires FACT_LOOKUP");
        }
        if (result.getSubjects().size() != 1) {
            throw new PortfolioAnswerCompositionException(
                    "answer composition requires exactly one subject");
        }
        if (result.getEvidence().isEmpty()) {
            throw new PortfolioAnswerCompositionException(
                    "answer composition requires a non-empty fact collection");
        }
        String subjectId = result.getSubjects().getFirst().getSubjectId();
        for (PortfolioRetrievedPassage passage : result.getEvidence()) {
            if (!subjectId.equals(passage.getSubjectId())) {
                throw new PortfolioAnswerCompositionException(
                        "passage subject does not match the composed subject");
            }
        }
    }

    private List<PortfolioRetrievedPassage> deduplicateAndBudget(
            PortfolioIntelligenceResult result) {
        Map<String, PortfolioRetrievedPassage> byClaimId = new LinkedHashMap<>();
        for (PortfolioRetrievedPassage passage : result.getEvidence()) {
            byClaimId.putIfAbsent(passage.getClaimId(), passage);
        }
        return List.copyOf(byClaimId.values());
    }

    private Map<AnswerSectionType, List<PortfolioRetrievedPassage>> groupBySection(
            List<PortfolioRetrievedPassage> passages,
            AnswerFocus focus) {
        Set<AnswerSectionType> targetTypes = targetTypes(focus);
        Map<AnswerSectionType, List<PortfolioRetrievedPassage>> grouped =
                new LinkedHashMap<>();
        for (PortfolioRetrievedPassage passage : passages) {
            if (focus.getMode() == AnswerFocusMode.FOCUSED
                    && !focus.getRequestedClaimCategories().contains(
                            passage.getClaim().getCategory())) {
                continue;
            }
            AnswerSectionType type = sectionType(passage.getClaim().getCategory());
            if (!targetTypes.contains(type)) {
                continue;
            }
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>()).add(passage);
        }
        return grouped;
    }

    private Set<AnswerSectionType> targetTypes(AnswerFocus focus) {
        if (focus.getMode() == AnswerFocusMode.OVERVIEW) {
            return new LinkedHashSet<>(AnswerSectionMapping.authoritativeOrder());
        }
        Set<AnswerSectionType> types = new LinkedHashSet<>();
        for (AnswerClaimCategory category : focus.getRequestedClaimCategories()) {
            types.add(sectionType(category));
        }
        return types;
    }

    private boolean hasGapMessages(AnswerFocus focus) {
        return focus.getMode() == AnswerFocusMode.FOCUSED;
    }

    private AnswerSectionType sectionType(AnswerClaimCategory category) {
        return AnswerSectionMapping.sectionTypeFor(category);
    }

    private List<PortfolioAnswerSection> orderedSections(
            Map<AnswerSectionType, List<PortfolioRetrievedPassage>> grouped,
            AnswerFocus focus) {
        List<PortfolioAnswerSection> sections = new ArrayList<>();
        Set<AnswerSectionType> targetTypes = targetTypes(focus);
        for (AnswerSectionType type : AnswerSectionMapping.authoritativeOrder()) {
            if (!targetTypes.contains(type)) {
                continue;
            }
            if (type == AnswerSectionType.BOUNDARY) {
                continue;
            }
            List<PortfolioRetrievedPassage> passages = grouped.get(type);
            if (passages != null && !passages.isEmpty()) {
                sections.add(factSection(type, passages, budget(focus, type)));
            }
        }
        PortfolioAnswerSection boundary = boundarySection(grouped, focus);
        if (boundary != null) {
            sections.add(boundary);
        }
        return List.copyOf(sections);
    }

    private int budget(AnswerFocus focus, AnswerSectionType type) {
        if (focus.getMode() == AnswerFocusMode.FOCUSED
                && focus.getRequestedClaimCategories().stream()
                        .map(this::sectionType)
                        .anyMatch(categoryType -> categoryType == type)) {
            return FOCUSED_FACTS_PER_SECTION;
        }
        return OVERVIEW_FACTS_PER_SECTION;
    }

    private PortfolioAnswerSection factSection(
            AnswerSectionType type,
            List<PortfolioRetrievedPassage> passages,
            int budget) {
        List<ClaimFacts> merged = mergeByNormalizedBody(passages);
        ClaimFacts selected = new ClaimFacts();
        int used = 0;
        for (ClaimFacts candidate : merged) {
            if (used >= budget) {
                break;
            }
            selected.claimIds.addAll(candidate.claimIds);
            selected.evidenceIds.addAll(candidate.evidenceIds);
            selected.bodies.add(candidate.bodies.getFirst());
            used++;
        }
        return new PortfolioAnswerSection(
                type,
                AnswerSectionMapping.titleFor(type),
                String.join("\n", selected.bodies),
                selected.claimIds,
                selected.evidenceIds);
    }

    private List<ClaimFacts> mergeByNormalizedBody(List<PortfolioRetrievedPassage> passages) {
        Map<String, ClaimFacts> byBody = new LinkedHashMap<>();
        for (PortfolioRetrievedPassage passage : passages) {
            String statement = passage.getClaim().getStatement().trim();
            String detail = passage.getClaim().getDetail() == null
                    ? "" : passage.getClaim().getDetail().trim();
            String body = body(statement, detail);
            String normalized = normalize(body);
            ClaimFacts facts = byBody.computeIfAbsent(normalized, ignored -> new ClaimFacts());
            facts.claimIds.add(passage.getClaimId());
            facts.evidenceIds.addAll(passage.getEvidenceIds());
            facts.bodies.add(body);
        }
        return List.copyOf(byBody.values());
    }

    private String body(String statement, String detail) {
        if (detail.isEmpty() || detail.equals(statement)) {
            return statement;
        }
        return statement + "。" + detail;
    }

    private String normalize(String body) {
        return body.replaceAll("[\\s，。；：、,.!?；]", "").trim();
    }

    private PortfolioAnswerSection boundarySection(
            Map<AnswerSectionType, List<PortfolioRetrievedPassage>> grouped,
            AnswerFocus focus) {
        List<String> contentLines = new ArrayList<>();
        List<String> claimIds = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        List<PortfolioRetrievedPassage> boundaryFacts = grouped.get(AnswerSectionType.BOUNDARY);
        if (boundaryFacts != null && !boundaryFacts.isEmpty()) {
            List<ClaimFacts> mergedFacts = mergeByNormalizedBody(boundaryFacts);
            int limit = Math.min(budget(focus, AnswerSectionType.BOUNDARY), mergedFacts.size());
            for (int index = 0; index < limit; index++) {
                ClaimFacts merged = mergedFacts.get(index);
                contentLines.add(merged.bodies.getFirst());
                claimIds.addAll(merged.claimIds);
                evidenceIds.addAll(merged.evidenceIds);
            }
        }
        Set<AnswerSectionType> targetTypes = targetTypes(focus);
        if (hasGapMessages(focus)) {
            for (AnswerSectionType type : AnswerSectionMapping.authoritativeOrder()) {
                if (!targetTypes.contains(type)) {
                    continue;
                }
                List<PortfolioRetrievedPassage> passages = grouped.get(type);
                if (passages == null || passages.isEmpty()) {
                    contentLines.add(AnswerSectionMapping.gapMessageFor(type));
                }
            }
        }
        if (contentLines.isEmpty()) {
            return null;
        }
        return new PortfolioAnswerSection(
                AnswerSectionType.BOUNDARY,
                AnswerSectionMapping.titleFor(AnswerSectionType.BOUNDARY),
                String.join("\n", contentLines),
                claimIds,
                evidenceIds);
    }

    private String summary(
            PortfolioIntelligenceResult result,
            List<PortfolioRetrievedPassage> selected) {
        PortfolioRetrievedSubject subject = result.getSubjects().getFirst();
        if (subject.getSummary() != null && !subject.getSummary().isBlank()) {
            return subject.getSummary().trim();
        }
        return selected.getFirst().getClaim().getStatement().trim();
    }

    private static final class ClaimFacts {
        private final List<String> claimIds = new ArrayList<>();
        private final List<String> evidenceIds = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();
    }
}
