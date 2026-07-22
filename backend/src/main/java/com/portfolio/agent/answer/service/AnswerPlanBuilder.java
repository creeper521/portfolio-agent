package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanClaim;
import com.portfolio.agent.answer.domain.AnswerPlanEvidence;
import com.portfolio.agent.answer.domain.AnswerPlanSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.ExpressionPolicy;
import com.portfolio.agent.answer.domain.ExpressionTone;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class AnswerPlanBuilder {

    public AnswerPlan build(AnswerTurnSnapshot turn, ResolvedAnswerContext context) {
        AnswerKnowledge project = context.getProject();
        Set<String> approvedEvidenceIds = context.getApprovedEvidence().stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        List<AnswerPlanClaim> claims = context.getSelectedClaims().stream()
                .map(claim -> toPlanClaim(claim, approvedEvidenceIds))
                .toList();
        List<AnswerPlanEvidence> evidence = context.getApprovedEvidence().stream()
                .map(this::toPlanEvidence)
                .toList();
        List<AnswerPlanSection> sections = context.getAnswerSource()
                == com.portfolio.agent.answer.domain.AnswerSource.RETRIEVAL
                ? retrievalSections(context.getSelectedClaims(), approvedEvidenceIds)
                : List.of(
                section(project, approvedEvidenceIds, AnswerSectionType.BACKGROUND, "项目背景",
                        project.getBackground(), Set.of(AnswerClaimCategory.BACKGROUND)),
                section(project, approvedEvidenceIds, AnswerSectionType.RESPONSIBILITY, "我的职责",
                        joinSentences(project.getResponsibilities()),
                        Set.of(AnswerClaimCategory.RESPONSIBILITY)),
                section(project, approvedEvidenceIds, AnswerSectionType.SOLUTION, "技术方案",
                        project.getSolution() + " 关键决策包括："
                                + joinSentences(project.getKeyDecisions()),
                        Set.of(AnswerClaimCategory.TECHNICAL_DECISION,
                                AnswerClaimCategory.IMPLEMENTATION)),
                section(project, approvedEvidenceIds, AnswerSectionType.VERIFICATION, "验证过程",
                        joinSentences(project.getVerification()),
                        Set.of(AnswerClaimCategory.VERIFICATION)),
                section(project, approvedEvidenceIds, AnswerSectionType.STATUS, "最终状态",
                        project.getOutcome() + " " + project.getHandoff(),
                        Set.of(AnswerClaimCategory.OUTCOME, AnswerClaimCategory.LIMITATION))
        );
        return new AnswerPlan(
                turn.getContentVersion(),
                context.getQuestionPreset() == null ? null : context.getQuestionPreset().getId(),
                context.getCanonicalIntent(),
                turn.getAudienceRole(),
                project.getTitle(),
                context.getAnswerSource()
                        == com.portfolio.agent.answer.domain.AnswerSource.RETRIEVAL
                        ? selectedSummary(context.getSelectedClaims())
                        : project.getSummary(),
                sections,
                claims,
                evidence,
                expressionPolicy(turn.getAudienceRole())
        );
    }

    private List<AnswerPlanSection> retrievalSections(
            List<AnswerClaimProjection> selectedClaims,
            Set<String> approvedEvidenceIds
    ) {
        List<AnswerPlanSection> sections = new java.util.ArrayList<>();
        addRetrievalSection(sections, selectedClaims, approvedEvidenceIds,
                AnswerSectionType.BACKGROUND, "项目背景",
                Set.of(AnswerClaimCategory.BACKGROUND));
        addRetrievalSection(sections, selectedClaims, approvedEvidenceIds,
                AnswerSectionType.RESPONSIBILITY, "我的职责",
                Set.of(AnswerClaimCategory.RESPONSIBILITY));
        addRetrievalSection(sections, selectedClaims, approvedEvidenceIds,
                AnswerSectionType.SOLUTION, "技术方案",
                Set.of(AnswerClaimCategory.TECHNICAL_DECISION,
                        AnswerClaimCategory.IMPLEMENTATION));
        addRetrievalSection(sections, selectedClaims, approvedEvidenceIds,
                AnswerSectionType.VERIFICATION, "验证过程",
                Set.of(AnswerClaimCategory.VERIFICATION));
        addRetrievalSection(sections, selectedClaims, approvedEvidenceIds,
                AnswerSectionType.STATUS, "最终状态",
                Set.of(AnswerClaimCategory.OUTCOME, AnswerClaimCategory.LIMITATION));
        return List.copyOf(sections);
    }

    private void addRetrievalSection(
            List<AnswerPlanSection> sections,
            List<AnswerClaimProjection> selectedClaims,
            Set<String> approvedEvidenceIds,
            AnswerSectionType type,
            String title,
            Set<AnswerClaimCategory> categories
    ) {
        List<AnswerClaimProjection> matching = selectedClaims.stream()
                .filter(claim -> categories.contains(claim.getCategory()))
                .toList();
        if (matching.isEmpty()) {
            return;
        }
        List<String> claimIds = matching.stream()
                .map(AnswerClaimProjection::getId)
                .toList();
        List<String> evidenceIds = matching.stream()
                .flatMap(claim -> claim.getDirectEvidenceIds().stream())
                .filter(approvedEvidenceIds::contains)
                .distinct()
                .toList();
        sections.add(new AnswerPlanSection(
                type, title, selectedSummary(matching), claimIds, evidenceIds));
    }

    private String selectedSummary(List<AnswerClaimProjection> claims) {
        return claims.stream()
                .map(claim -> (claim.getStatement() + " " + claim.getDetail()).strip())
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private AnswerPlanClaim toPlanClaim(
            AnswerClaimProjection claim,
            Set<String> approvedEvidenceIds
    ) {
        return new AnswerPlanClaim(
                claim.getId(),
                claim.getCategory(),
                claim.getStatement(),
                claim.getDetail(),
                claim.getAchievementStatus(),
                claim.getContributionType(),
                claim.getVerificationBasis(),
                claim.getVerificationStatus(),
                claim.getMateriality(),
                claim.getDirectEvidenceIds().stream()
                        .filter(approvedEvidenceIds::contains)
                        .toList()
        );
    }

    private AnswerPlanEvidence toPlanEvidence(AnswerEvidence evidence) {
        return new AnswerPlanEvidence(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary()
        );
    }

    private AnswerPlanSection section(
            AnswerKnowledge project,
            Set<String> approvedEvidenceIds,
            AnswerSectionType type,
            String title,
            String content,
            Set<AnswerClaimCategory> categories
    ) {
        List<AnswerClaimProjection> claims = project.getClaims().stream()
                .filter(claim -> categories.contains(claim.getCategory()))
                .toList();
        List<String> claimIds = claims.stream()
                .map(AnswerClaimProjection::getId)
                .toList();
        List<String> evidenceIds = claims.stream()
                .flatMap(claim -> claim.getDirectEvidenceIds().stream())
                .filter(approvedEvidenceIds::contains)
                .distinct()
                .toList();
        return new AnswerPlanSection(type, title, content, claimIds, evidenceIds);
    }

    private ExpressionPolicy expressionPolicy(AudienceRole role) {
        if (role == AudienceRole.INTERVIEWER) {
            return new ExpressionPolicy(
                    ExpressionTone.CONCISE_TECHNICAL,
                    120, 250, 80, 1000, true, true,
                    List.of(
                            AnswerClaimCategory.TECHNICAL_DECISION,
                            AnswerClaimCategory.IMPLEMENTATION,
                            AnswerClaimCategory.VERIFICATION
                    )
            );
        }
        if (role == AudienceRole.MENTOR) {
            return new ExpressionPolicy(
                    ExpressionTone.REFLECTIVE_TECHNICAL,
                    120, 300, 80, 1200, true, true,
                    List.of(AnswerClaimCategory.RESPONSIBILITY,
                            AnswerClaimCategory.LIMITATION)
            );
        }
        if (role == AudienceRole.HR) {
            return new ExpressionPolicy(
                    ExpressionTone.CONCISE_OUTCOME,
                    120, 220, 80, 800, true, true,
                    List.of(AnswerClaimCategory.RESPONSIBILITY,
                            AnswerClaimCategory.OUTCOME)
            );
        }
        return new ExpressionPolicy(
                ExpressionTone.CLEAR_OVERVIEW,
                120, 250, 80, 900, true, true,
                List.of(AnswerClaimCategory.BACKGROUND, AnswerClaimCategory.OUTCOME)
        );
    }

    private String joinSentences(List<String> values) {
        return values.stream()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }
}
