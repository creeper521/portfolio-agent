package com.portfolio.agent.answer.engine.deterministic;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.engine.AnswerEngine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DeterministicAnswerEngine implements AnswerEngine {

    @Override
    public GeneratedAnswer answer(ResolvedAnswerContext context) {
        AnswerKnowledge knowledge = context.getProject();
        Set<String> approvedEvidenceIds = context.getApprovedEvidence().stream()
                .map(com.portfolio.agent.answer.domain.AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        List<AnswerSection> sections = List.of(
                section(knowledge, approvedEvidenceIds, AnswerSectionType.BACKGROUND,
                        "项目背景", knowledge.getBackground(), Set.of("BACKGROUND")),
                section(AnswerSectionType.RESPONSIBILITY, "我的职责",
                        joinSentences(knowledge.getResponsibilities()), knowledge,
                        approvedEvidenceIds, Set.of("RESPONSIBILITY")),
                section(AnswerSectionType.SOLUTION, "技术方案",
                        knowledge.getSolution() + " 关键决策包括："
                                + joinSentences(knowledge.getKeyDecisions()), knowledge,
                        approvedEvidenceIds, Set.of("TECHNICAL_DECISION", "IMPLEMENTATION")),
                section(AnswerSectionType.VERIFICATION, "验证过程",
                        joinSentences(knowledge.getVerification()), knowledge,
                        approvedEvidenceIds, Set.of("VERIFICATION")),
                section(AnswerSectionType.STATUS, "最终状态",
                        knowledge.getOutcome() + " " + knowledge.getHandoff(), knowledge,
                        approvedEvidenceIds, Set.of("OUTCOME", "LIMITATION"))
        );
        return new GeneratedAnswer(knowledge.getTitle(), knowledge.getSummary(), sections);
    }

    private AnswerSection section(AnswerKnowledge knowledge, Set<String> approvedEvidenceIds,
            AnswerSectionType type,
            String title,
            String content,
            Set<String> categories
    ) {
        List<AnswerClaimProjection> claims = knowledge.getClaims().stream()
                .filter(claim -> categories.contains(claim.getCategory().name()))
                .toList();
        List<String> claimIds = claims.stream().map(AnswerClaimProjection::getId).toList();
        List<String> evidenceIds = claims.stream()
                .flatMap(claim -> claim.getDirectEvidenceIds().stream())
                .filter(approvedEvidenceIds::contains)
                .distinct()
                .toList();
        return new AnswerSection(type, title, content, evidenceIds, claimIds);
    }

    private AnswerSection section(AnswerSectionType type, String title, String content,
            AnswerKnowledge knowledge, Set<String> approvedEvidenceIds, Set<String> categories) {
        return section(knowledge, approvedEvidenceIds, type, title, content, categories);
    }

    private String joinSentences(List<String> values) {
        return values.stream()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }
}
