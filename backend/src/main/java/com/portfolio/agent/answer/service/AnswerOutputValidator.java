package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanClaim;
import com.portfolio.agent.answer.domain.AnswerPlanSection;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.ModelAnswerDraft;
import com.portfolio.agent.answer.domain.ModelDraftSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class AnswerOutputValidator {

    private static final String SELF_DECLARED_LABEL = "【个人陈述】";
    private static final String INFERENCE_LABEL = "【推断】";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:\\.\\d+)?%?");

    public AnswerValidationResult validate(AnswerPlan plan, ModelAnswerDraft draft) {
        if (draft == null || isBlank(draft.getTitle()) || isBlank(draft.getSummary())) {
            return AnswerValidationResult.rejected(AnswerValidationFailureCode.MISSING_FIELD);
        }
        if (exceedsLimits(plan, draft)) {
            return AnswerValidationResult.rejected(AnswerValidationFailureCode.LENGTH_EXCEEDED);
        }
        if (containsForbiddenContent(draft)) {
            return AnswerValidationResult.rejected(AnswerValidationFailureCode.FORBIDDEN_CONTENT);
        }
        if (containsInventedNumber(plan, draft)) {
            return AnswerValidationResult.rejected(AnswerValidationFailureCode.INVENTED_NUMBER);
        }
        List<AnswerSectionType> requiredTypes = plan.getRequiredSections().stream()
                .map(AnswerPlanSection::getType)
                .toList();
        List<AnswerSectionType> actualTypes = draft.getSections().stream()
                .map(ModelDraftSection::getType)
                .toList();
        if (!requiredTypes.equals(actualTypes) || new HashSet<>(actualTypes).size() != actualTypes.size()) {
            return AnswerValidationResult.rejected(AnswerValidationFailureCode.INVALID_STRUCTURE);
        }

        Map<String, AnswerPlanClaim> claimsById = new HashMap<>();
        for (AnswerPlanClaim claim : plan.getClaims()) {
            claimsById.put(claim.getClaimId(), claim);
        }
        Set<String> referencedClaimIds = new HashSet<>();
        List<AnswerSection> acceptedSections = new ArrayList<>();
        for (int index = 0; index < draft.getSections().size(); index++) {
            AnswerPlanSection required = plan.getRequiredSections().get(index);
            ModelDraftSection candidate = draft.getSections().get(index);
            AnswerValidationFailureCode referenceFailure = validateReferences(
                    required, candidate, claimsById, referencedClaimIds);
            if (referenceFailure != null) {
                return AnswerValidationResult.rejected(referenceFailure);
            }
            acceptedSections.add(new AnswerSection(
                    candidate.getType(),
                    candidate.getTitle(),
                    candidate.getContent(),
                    candidate.getEvidenceIds(),
                    candidate.getClaimIds()
            ));
        }
        boolean missingKeyClaim = plan.getClaims().stream()
                .filter(claim -> claim.getMateriality() == AnswerMateriality.KEY)
                .map(AnswerPlanClaim::getClaimId)
                .anyMatch(claimId -> !referencedClaimIds.contains(claimId));
        if (missingKeyClaim) {
            return AnswerValidationResult.rejected(AnswerValidationFailureCode.MISSING_KEY_CLAIM);
        }
        return AnswerValidationResult.accepted(new GeneratedAnswer(
                draft.getTitle(), draft.getSummary(), acceptedSections));
    }

    private AnswerValidationFailureCode validateReferences(
            AnswerPlanSection required,
            ModelDraftSection candidate,
            Map<String, AnswerPlanClaim> claimsById,
            Set<String> referencedClaimIds
    ) {
        if (isBlank(candidate.getTitle()) || isBlank(candidate.getContent())) {
            return AnswerValidationFailureCode.MISSING_FIELD;
        }
        if (hasDuplicates(candidate.getClaimIds()) || hasDuplicates(candidate.getEvidenceIds())) {
            return AnswerValidationFailureCode.INVALID_REFERENCE;
        }
        if (!required.getAllowedClaimIds().containsAll(candidate.getClaimIds())
                || !required.getAllowedEvidenceIds().containsAll(candidate.getEvidenceIds())) {
            return AnswerValidationFailureCode.INVALID_REFERENCE;
        }
        Set<String> evidenceAllowedByReferencedClaims = new HashSet<>();
        for (String claimId : candidate.getClaimIds()) {
            AnswerPlanClaim claim = claimsById.get(claimId);
            if (claim == null) {
                return AnswerValidationFailureCode.INVALID_REFERENCE;
            }
            evidenceAllowedByReferencedClaims.addAll(claim.getAllowedEvidenceIds());
            if (!candidate.getEvidenceIds().containsAll(claim.getAllowedEvidenceIds())) {
                return AnswerValidationFailureCode.INVALID_REFERENCE;
            }
            if (claim.getVerificationBasis() == AnswerVerificationBasis.SELF_DECLARED
                    && !candidate.getContent().contains(SELF_DECLARED_LABEL)) {
                return AnswerValidationFailureCode.INVALID_GOVERNANCE_LABEL;
            }
            if (claim.getVerificationBasis() == AnswerVerificationBasis.INFERRED
                    && !candidate.getContent().contains(INFERENCE_LABEL)) {
                return AnswerValidationFailureCode.INVALID_GOVERNANCE_LABEL;
            }
            referencedClaimIds.add(claimId);
        }
        if (!evidenceAllowedByReferencedClaims.containsAll(candidate.getEvidenceIds())) {
            return AnswerValidationFailureCode.INVALID_REFERENCE;
        }
        return null;
    }

    private boolean exceedsLimits(AnswerPlan plan, ModelAnswerDraft draft) {
        if (draft.getTitle().length() > plan.getExpressionPolicy().getMaxTitleLength()
                || draft.getSummary().length() > plan.getExpressionPolicy().getMaxSummaryLength()) {
            return true;
        }
        return draft.getSections().stream().anyMatch(section ->
                section.getTitle() == null
                        || section.getContent() == null
                        || section.getTitle().length()
                        > plan.getExpressionPolicy().getMaxSectionTitleLength()
                        || section.getContent().length()
                        > plan.getExpressionPolicy().getMaxSectionContentLength());
    }

    private boolean containsForbiddenContent(ModelAnswerDraft draft) {
        List<String> values = new ArrayList<>();
        values.add(draft.getTitle());
        values.add(draft.getSummary());
        for (ModelDraftSection section : draft.getSections()) {
            values.add(section.getTitle());
            values.add(section.getContent());
        }
        return values.stream().anyMatch(this::isForbidden);
    }

    private boolean isForbidden(String value) {
        if (value == null) {
            return true;
        }
        String lowered = value.toLowerCase(java.util.Locale.ROOT);
        if (lowered.contains("http://") || lowered.contains("https://")
                || lowered.contains("tool_call") || lowered.contains("function_call")
                || value.contains("<") || value.contains(">")
                || value.contains("```") || value.contains("{{") || value.contains("}}")
                || value.contains("](")) {
            return true;
        }
        return value.chars().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\r' && character != '\t');
    }

    private boolean containsInventedNumber(AnswerPlan plan, ModelAnswerDraft draft) {
        Set<String> allowedNumbers = numberTokens(allowedText(plan));
        return !allowedNumbers.containsAll(numberTokens(draftText(draft)));
    }

    private String allowedText(AnswerPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append(plan.getContentVersion()).append(' ')
                .append(plan.getProjectTitle()).append(' ')
                .append(plan.getProjectSummary()).append(' ')
                .append(plan.getCanonicalIntent()).append(' ');
        for (AnswerPlanClaim claim : plan.getClaims()) {
            builder.append(claim.getStatement()).append(' ')
                    .append(claim.getDetail()).append(' ');
        }
        plan.getEvidence().forEach(evidence -> builder
                .append(evidence.getTitle()).append(' ')
                .append(evidence.getSummary()).append(' ')
                .append(evidence.getPeriodStart()).append(' ')
                .append(evidence.getPeriodEnd()).append(' ')
                .append(evidence.getSourceCount()).append(' '));
        plan.getRequiredSections().forEach(section -> builder
                .append(section.getCanonicalTitle()).append(' ')
                .append(section.getCanonicalContent()).append(' '));
        return builder.toString();
    }

    private String draftText(ModelAnswerDraft draft) {
        StringBuilder builder = new StringBuilder();
        builder.append(draft.getTitle()).append(' ').append(draft.getSummary()).append(' ');
        draft.getSections().forEach(section -> builder
                .append(section.getTitle()).append(' ')
                .append(section.getContent()).append(' '));
        return builder.toString();
    }

    private Set<String> numberTokens(String value) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private boolean hasDuplicates(List<String> values) {
        return new HashSet<>(values).size() != values.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
