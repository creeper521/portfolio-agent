package com.portfolio.agent.evaluation.grading;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-rule grader that derives deterministic grades from an eval case oracle
 * and a sanitized observation. Hard error reason codes are fixed and always
 * blocking; structural quality is scored and never fails phase 0 alone.
 */
public final class DeterministicEvalGrader implements EvalGrader {

    @Override
    public List<EvalGrade> grade(EvalCase evalCase, EvalObservation observation) {
        Objects.requireNonNull(evalCase, "evalCase");
        Objects.requireNonNull(observation, "observation");
        if (observation.getStatus() == EvalObservationStatus.SKIPPED) {
            return List.of();
        }
        if (observation.getStatus() == EvalObservationStatus.ERROR) {
            return List.of(grade(evalCase, observation,
                    "EXECUTOR", EvalSeverity.BLOCKING, false,
                    EvalReasonCode.EXECUTOR_ERROR, 0L, 1L));
        }
        List<EvalGrade> grades = new ArrayList<>();
        for (EvalGraderRule rule : evalCase.getGraders()) {
            grades.add(dispatch(evalCase, observation, rule));
        }
        if (isFalseSufficient(evalCase, observation)) {
            grades.add(grade(evalCase, observation,
                    "RESOLUTION", EvalSeverity.BLOCKING, false,
                    EvalReasonCode.FALSE_SUFFICIENT, 0L, 1L));
        }
        return List.copyOf(grades);
    }

    private EvalGrade dispatch(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        switch (rule.getType()) {
            case "SUBJECT_MATCH":
                return subjectMatch(evalCase, observation, rule);
            case "REFERENCE_INTEGRITY":
                return referenceIntegrity(evalCase, observation, rule);
            case "RESOLUTION":
                return resolution(evalCase, observation, rule);
            case "ANSWER_SCOPE":
                return answerScope(evalCase, observation, rule);
            case "REQUIRED_CLAIMS":
                return requiredClaims(evalCase, observation, rule);
            case "GROUNDING":
                return grounding(evalCase, observation, rule);
            case "FORBIDDEN_SUBJECT":
                return forbiddenSubject(evalCase, observation, rule);
            case "API_CONTRACT":
                return apiContract(evalCase, observation, rule);
            case "ANSWER_QUALITY":
                return answerQuality(evalCase, observation, rule);
            default:
                throw new IllegalArgumentException(
                        "Unknown grader type: " + rule.getType());
        }
    }

    private EvalGrade subjectMatch(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        boolean matched = false;
        for (EvalSubjectRef expected : evalCase.getExpectedSubjects()) {
            if (expected.getType() == ClaimSubjectType.CASE
                    && expected.getSlug().equals(observation.getSelectedCaseSlug())) {
                matched = true;
                break;
            }
            if (expected.getType() == ClaimSubjectType.PROJECT
                    && expected.getSlug().equals(observation.getSelectedProjectSlug())) {
                matched = true;
                break;
            }
        }
        return matched
                ? pass(evalCase, observation, rule, EvalReasonCode.PASS)
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.SUBJECT_MISMATCH, 0L, 1L);
    }

    private EvalGrade referenceIntegrity(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        List<String> allowed = evalCase.getAllowedEvidenceIds();
        if (allowed.isEmpty()) {
            return pass(evalCase, observation, rule, EvalReasonCode.PASS);
        }
        long total = observation.getSelectedEvidenceIds().size();
        long valid = observation.getSelectedEvidenceIds().stream()
                .filter(allowed::contains)
                .count();
        long denominator = Math.max(total, 1L);
        boolean passed = total == valid;
        return passed
                ? passedRatio(evalCase, observation, rule, valid, denominator)
                : grade(evalCase, observation, rule.getType(), EvalSeverity.BLOCKING,
                        false, EvalReasonCode.FAKE_CITATION, valid, denominator);
    }

    private EvalGrade resolution(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        boolean allowed = evalCase.getAllowedResolutions()
                .contains(observation.getResolution());
        return allowed
                ? pass(evalCase, observation, rule, EvalReasonCode.PASS)
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.STATUS_MISMATCH, 0L, 1L);
    }

    private EvalGrade answerScope(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        ConversationAnswerScope scope = observation.getAnswerScope();
        boolean allowed = scope != null && evalCase.getAllowedAnswerScopes().contains(scope);
        return allowed
                ? pass(evalCase, observation, rule, EvalReasonCode.PASS)
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.ANSWER_SCOPE_MISMATCH, 0L, 1L);
    }

    private EvalGrade requiredClaims(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        List<String> required = evalCase.getRequiredClaimIds();
        if (required.isEmpty()) {
            return pass(evalCase, observation, rule, EvalReasonCode.PASS);
        }
        long covered = required.stream()
                .filter(observation.getSelectedClaimIds()::contains)
                .count();
        boolean passed = covered == required.size();
        return passed
                ? passedRatio(evalCase, observation, rule, covered, required.size())
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.CLAIM_RECALL_MISSING, covered, required.size());
    }

    private EvalGrade grounding(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        List<String> allowed = evalCase.getAllowedEvidenceIds();
        if (allowed.isEmpty()) {
            return pass(evalCase, observation, rule, EvalReasonCode.PASS);
        }
        long total = observation.getSelectedEvidenceIds().size();
        long valid = observation.getSelectedEvidenceIds().stream()
                .filter(allowed::contains)
                .count();
        long denominator = Math.max(total, 1L);
        boolean passed = total == valid;
        return passed
                ? passedRatio(evalCase, observation, rule, valid, denominator)
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.GROUNDING_MISSING, valid, denominator);
    }

    private EvalGrade forbiddenSubject(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        for (String forbidden : evalCase.getForbiddenSubjectSlugs()) {
            if (forbidden.equals(observation.getSelectedProjectSlug())
                    || forbidden.equals(observation.getSelectedCaseSlug())) {
                return grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.FORBIDDEN_SUBJECT_MISMATCH, 0L, 1L);
            }
        }
        return pass(evalCase, observation, rule, EvalReasonCode.PASS);
    }

    private EvalGrade apiContract(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        boolean passed = observation.getStatus() == EvalObservationStatus.PASS;
        return passed
                ? pass(evalCase, observation, rule, EvalReasonCode.PASS)
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.API_CONTRACT_BROKEN, 0L, 1L);
    }

    private EvalGrade answerQuality(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule) {
        boolean passed = observation.getAnswerShape().isDirectAnswerPresent()
                && observation.getAnswerShape().getCharacterCount() > 0
                && observation.getAnswerShape().getRepeatedContentCount() == 0;
        return passed
                ? pass(evalCase, observation, rule, EvalReasonCode.PASS)
                : grade(evalCase, observation, rule.getType(), rule.getSeverity(),
                        false, EvalReasonCode.ANSWER_QUALITY_MISSING, 0L, 1L);
    }

    private boolean isFalseSufficient(
            EvalCase evalCase,
            EvalObservation observation) {
        return observation.getResolution() == AnswerResolution.ANSWERED
                && observation.getSelectedEvidenceIds().isEmpty()
                && !evalCase.getRequiredClaimIds().isEmpty();
    }

    private EvalGrade pass(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule,
            EvalReasonCode reasonCode) {
        return passedRatio(evalCase, observation, rule, 1L, 1L);
    }

    private EvalGrade passedRatio(
            EvalCase evalCase,
            EvalObservation observation,
            EvalGraderRule rule,
            long numerator,
            long denominator) {
        return grade(evalCase, observation, rule.getType(),
                EvalSeverity.SCORED, true, EvalReasonCode.PASS, numerator, denominator);
    }

    private EvalGrade grade(
            EvalCase evalCase,
            EvalObservation observation,
            String graderType,
            EvalSeverity severity,
            boolean passed,
            EvalReasonCode reasonCode,
            long numerator,
            long denominator) {
        return new EvalGrade(
                observation.getCaseId(), observation.getLayer(), observation.getTrialIndex(),
                graderType, severity, passed, reasonCode, numerator, denominator);
    }
}
