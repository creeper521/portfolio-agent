package com.portfolio.agent.evaluation.grading;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalSemanticTurnShape;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicEvalGraderTest {

    private final DeterministicEvalGrader grader = new DeterministicEvalGrader();

    @Test
    void subjectMatchPassesWhenSelectedSubjectMatchesOracle() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(
                        rule("SUBJECT_MATCH", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED));

        EvalGrade grade = only(grades, "SUBJECT_MATCH");
        assertThat(grade.isPassed()).isTrue();
        assertThat(grade.getNumerator()).isEqualTo(1L);
        assertThat(grade.getDenominator()).isEqualTo(1L);
    }

    @Test
    void subjectMatchFailsWhenSelectedSubjectIsMissing() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(
                        rule("SUBJECT_MATCH", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, null, null,
                        List.of(), List.of(), AnswerResolution.ANSWERED));

        EvalGrade grade = only(grades, "SUBJECT_MATCH");
        assertThat(grade.isPassed()).isFalse();
        assertThat(grade.getReasonCode()).isEqualTo(EvalReasonCode.SUBJECT_MISMATCH);
        assertThat(grade.getSeverity()).isEqualTo(EvalSeverity.BLOCKING);
    }

    @Test
    void referenceIntegrityPassesWhenEveryEvidenceIsAllowed() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(
                        rule("REFERENCE_INTEGRITY", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of("E-01", "E-02"), AnswerResolution.ANSWERED));

        EvalGrade grade = only(grades, "REFERENCE_INTEGRITY");
        assertThat(grade.isPassed()).isTrue();
        assertThat(grade.getNumerator()).isEqualTo(2L);
        assertThat(grade.getDenominator()).isEqualTo(2L);
    }

    @Test
    void referenceIntegrityFailsOnFakeEvidence() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(
                        rule("REFERENCE_INTEGRITY", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of("E-01", "FORGED-99"), AnswerResolution.ANSWERED));

        EvalGrade grade = only(grades, "REFERENCE_INTEGRITY");
        assertThat(grade.isPassed()).isFalse();
        assertThat(grade.getReasonCode()).isEqualTo(EvalReasonCode.FAKE_CITATION);
        assertThat(grade.getSeverity()).isEqualTo(EvalSeverity.BLOCKING);
        assertThat(grade.getNumerator()).isEqualTo(1L);
        assertThat(grade.getDenominator()).isEqualTo(2L);
    }

    @Test
    void resolutionPassesWhenAllowedAndFailsOnStatusMismatch() {
        EvalGrade passing = only(grade(
                caseWith(List.of(rule("RESOLUTION", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED)),
                "RESOLUTION");
        assertThat(passing.isPassed()).isTrue();

        List<EvalGrade> failing = grade(
                caseWith(List.of(rule("RESOLUTION", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.NEEDS_CLARIFICATION));
        EvalGrade failed = only(failing, "RESOLUTION");
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getReasonCode()).isEqualTo(EvalReasonCode.STATUS_MISMATCH);
    }

    @Test
    void answerScopePassesWhenAllowedAndFailsOtherwise() {
        List<EvalGrade> passing = grade(
                caseWith(List.of(rule("ANSWER_SCOPE", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED));
        assertThat(only(passing, "ANSWER_SCOPE").isPassed()).isTrue();

        EvalGrade failed = only(grade(
                caseWith(List.of(rule("ANSWER_SCOPE", EvalSeverity.BLOCKING))),
                observationWithScope(EvalObservationStatus.PASS, "case-a",
                        ConversationAnswerScope.GENERAL)),
                "ANSWER_SCOPE");
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getReasonCode()).isEqualTo(EvalReasonCode.ANSWER_SCOPE_MISMATCH);
    }

    @Test
    void requiredClaimsPassesWhenAllCoveredAndReportsFractionOtherwise() {
        List<EvalGrade> passing = grade(
                caseWith(List.of(rule("REQUIRED_CLAIMS", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of("claim-1", "claim-2", "claim-3"), List.of(), AnswerResolution.ANSWERED));
        EvalGrade passed = only(passing, "REQUIRED_CLAIMS");
        assertThat(passed.isPassed()).isTrue();
        assertThat(passed.getNumerator()).isEqualTo(3L);
        assertThat(passed.getDenominator()).isEqualTo(3L);

        EvalGrade failed = only(grade(
                caseWith(List.of(rule("REQUIRED_CLAIMS", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of("claim-1"), List.of(), AnswerResolution.ANSWERED)),
                "REQUIRED_CLAIMS");
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getNumerator()).isEqualTo(1L);
        assertThat(failed.getDenominator()).isEqualTo(3L);
    }

    @Test
    void groundingPassesWhenEvidenceIsAllowedAndFailsOnUngroundedEvidence() {
        List<EvalGrade> passing = grade(
                caseWith(List.of(rule("GROUNDING", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of("E-01"), AnswerResolution.ANSWERED));
        assertThat(only(passing, "GROUNDING").isPassed()).isTrue();

        EvalGrade failed = only(grade(
                caseWith(List.of(rule("GROUNDING", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of("E-01", "E-99"), AnswerResolution.ANSWERED)),
                "GROUNDING");
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getReasonCode()).isEqualTo(EvalReasonCode.GROUNDING_MISSING);
    }

    @Test
    void forbiddenSubjectFailsWhenSelectedSlugIsForbidden() {
        List<EvalGrade> passing = grade(
                caseWith(List.of(rule("FORBIDDEN_SUBJECT", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED));
        assertThat(only(passing, "FORBIDDEN_SUBJECT").isPassed()).isTrue();

        EvalGrade failed = only(grade(
                caseWith(List.of(rule("FORBIDDEN_SUBJECT", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "secret-project", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED)),
                "FORBIDDEN_SUBJECT");
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getReasonCode()).isEqualTo(EvalReasonCode.FORBIDDEN_SUBJECT_MISMATCH);
    }

    @Test
    void apiContractPassesForExecutedObservation() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(rule("API_CONTRACT", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED));

        assertThat(only(grades, "API_CONTRACT").isPassed()).isTrue();
    }

    @Test
    void answerQualityIsScoredAndNeverBlocksAlone() {
        List<EvalGrade> passing = grade(
                caseWith(List.of(rule("ANSWER_QUALITY", EvalSeverity.SCORED))),
                observationWithShape(EvalObservationStatus.PASS, "case-a"));
        EvalGrade passed = only(passing, "ANSWER_QUALITY");
        assertThat(passed.isPassed()).isTrue();
        assertThat(passed.getSeverity()).isEqualTo(EvalSeverity.SCORED);

        EvalGrade failed = only(grade(
                caseWith(List.of(rule("ANSWER_QUALITY", EvalSeverity.SCORED))),
                observationWithEmptyShape(EvalObservationStatus.PASS, "case-a")),
                "ANSWER_QUALITY");
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getSeverity()).isEqualTo(EvalSeverity.SCORED);
        assertThat(failed.getReasonCode()).isEqualTo(EvalReasonCode.ANSWER_QUALITY_MISSING);
    }

    @Test
    void answeredWithoutEvidenceIsAFalseSufficientHardError() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(rule("RESOLUTION", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.PASS, "case-a", null,
                        List.of(), List.of(), AnswerResolution.ANSWERED));

        EvalGrade hard = grades.stream()
                .filter(item -> item.getReasonCode() == EvalReasonCode.FALSE_SUFFICIENT)
                .findFirst().orElseThrow();
        assertThat(hard.isPassed()).isFalse();
        assertThat(hard.getSeverity()).isEqualTo(EvalSeverity.BLOCKING);
    }

    @Test
    void executorErrorProducesAHardErrorGrade() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(rule("SUBJECT_MATCH", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.ERROR, "case-a", null,
                        List.of(), List.of(), AnswerResolution.INVALID_INPUT));

        assertThat(grades).singleElement().satisfies(grade -> {
            assertThat(grade.getReasonCode()).isEqualTo(EvalReasonCode.EXECUTOR_ERROR);
            assertThat(grade.getSeverity()).isEqualTo(EvalSeverity.BLOCKING);
            assertThat(grade.isPassed()).isFalse();
        });
    }

    @Test
    void skippedObservationProducesNoGrades() {
        List<EvalGrade> grades = grade(
                caseWith(List.of(rule("SUBJECT_MATCH", EvalSeverity.BLOCKING))),
                observation(EvalObservationStatus.SKIPPED, null, null,
                        List.of(), List.of(), AnswerResolution.INVALID_INPUT));

        assertThat(grades).isEmpty();
    }

    @Test
    void semanticTurnStructureIsABlockingGradeOnlyWhenAnAgentTurnWasObserved()
            throws Exception {
        EvalSemanticTurnShape shape = EvalSemanticTurnShape.from(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"disposition":"READY","plan":{"taskCount":1,"tasks":[
                          {"sourceDomain":"PORTFOLIO"}
                        ]},"outcome":{"planOutcome":"SUCCEEDED","taskSummary":{
                          "totalCount":1,"answeredCount":1,"blockedCount":0,
                          "failedCount":0,"degradedCount":0,
                          "items":[{"sourceDomain":"PORTFOLIO"}]
                        }}}
                        """));
        EvalObservation base = observation(EvalObservationStatus.PASS, "case-a", null,
                List.of("claim-1"), List.of("E-01"), AnswerResolution.ANSWERED);
        EvalObservation observation = new EvalObservation(
                base.getCaseId(), base.getLayer(), base.getTrialIndex(), base.getStatus(),
                base.getSelectedProjectSlug(), base.getSelectedCaseSlug(),
                base.getSelectedClaimIds(), base.getSelectedEvidenceIds(), base.getSelectedChunkIds(),
                base.getResolution(), base.getAnswerScope(), base.getGenerationMode(),
                base.getAnswerSource(), base.getReasonCodes(), base.getDurationMilliseconds(),
                base.getProviderUsage(), base.getAnswerShape(), shape,
                base.isDegraded(), base.isProviderInvoked());

        EvalGrade grade = only(grade(caseWith(List.of(
                rule("RESOLUTION", EvalSeverity.BLOCKING))), observation),
                "SEMANTIC_TURN_STRUCTURE");

        assertThat(grade.isPassed()).isTrue();
        assertThat(grade.getSeverity()).isEqualTo(EvalSeverity.BLOCKING);
    }

    private EvalCase caseWith(List<EvalGraderRule> graders) {
        EvalSubjectRef caseSubject = new EvalSubjectRef(ClaimSubjectType.CASE, "case-a");
        EvalSubjectRef forbidden = new EvalSubjectRef(ClaimSubjectType.PROJECT, "secret-project");
        return new EvalCase(
                "answer.safe.001", "Answer case", EvalSplit.HOLDOUT, EvalOrigin.HUMAN_AUTHORED,
                EvalRiskLevel.STANDARD, "APPROVED", "reviewer", "TEST", "test", "2026-08-04.1",
                List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage("user", "Test question"))),
                new EvalCase.Oracle(List.of(caseSubject)),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of("claim-1", "claim-2", "claim-3"),
                        List.of("E-01", "E-02"),
                        List.of("secret-project"),
                        List.of()),
                new EvalCase.Execution(List.of(EvalLayer.INTELLIGENCE), 3),
                graders,
                new EvalCase.Maintenance(List.of(caseSubject), true));
    }

    private EvalObservation observation(
            EvalObservationStatus status,
            String caseSlug,
            String projectSlug,
            List<String> claims,
            List<String> evidence,
            AnswerResolution resolution) {
        return new EvalObservation(
                "answer.safe.001", EvalLayer.INTELLIGENCE, 1, status,
                projectSlug, caseSlug, claims, evidence, List.of(),
                resolution, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(), 12L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }

    private EvalObservation observationWithScope(
            EvalObservationStatus status,
            String caseSlug,
            ConversationAnswerScope scope) {
        EvalObservation base = observation(status, caseSlug, null,
                List.of(), List.of(), AnswerResolution.ANSWERED);
        return new EvalObservation(
                base.getCaseId(), base.getLayer(), base.getTrialIndex(), base.getStatus(),
                base.getSelectedProjectSlug(), base.getSelectedCaseSlug(),
                base.getSelectedClaimIds(), base.getSelectedEvidenceIds(),
                base.getSelectedChunkIds(), base.getResolution(), scope,
                base.getGenerationMode(), base.getAnswerSource(), base.getReasonCodes(),
                base.getDurationMilliseconds(), base.getProviderUsage(),
                base.getAnswerShape(), base.isDegraded(), base.isProviderInvoked());
    }

    private EvalObservation observationWithEmptyShape(
            EvalObservationStatus status,
            String caseSlug) {
        EvalObservation base = observation(status, caseSlug, null,
                List.of(), List.of(), AnswerResolution.ANSWERED);
        return new EvalObservation(
                base.getCaseId(), base.getLayer(), base.getTrialIndex(), base.getStatus(),
                base.getSelectedProjectSlug(), base.getSelectedCaseSlug(),
                base.getSelectedClaimIds(), base.getSelectedEvidenceIds(),
                base.getSelectedChunkIds(), base.getResolution(), base.getAnswerScope(),
                base.getGenerationMode(), base.getAnswerSource(), base.getReasonCodes(),
                base.getDurationMilliseconds(), base.getProviderUsage(),
                EvalAnswerShape.empty(), base.isDegraded(), base.isProviderInvoked());
    }

    private EvalObservation observationWithShape(
            EvalObservationStatus status,
            String caseSlug) {
        EvalObservation base = observation(status, caseSlug, null,
                List.of("claim-1"), List.of("E-01"), AnswerResolution.ANSWERED);
        EvalAnswerShape shape = EvalAnswerShape.from(List.of(
                new com.portfolio.agent.answer.domain.ConversationAnswerBlock(
                        com.portfolio.agent.answer.domain.ConversationSourceScope.PORTFOLIO,
                        "有效回答",
                        List.of("claim-1"),
                        List.of("E-01"))));
        return new EvalObservation(
                base.getCaseId(), base.getLayer(), base.getTrialIndex(), base.getStatus(),
                base.getSelectedProjectSlug(), base.getSelectedCaseSlug(),
                base.getSelectedClaimIds(), base.getSelectedEvidenceIds(),
                base.getSelectedChunkIds(), base.getResolution(), base.getAnswerScope(),
                base.getGenerationMode(), base.getAnswerSource(), base.getReasonCodes(),
                base.getDurationMilliseconds(), base.getProviderUsage(),
                shape, base.isDegraded(), base.isProviderInvoked());
    }

    private List<EvalGrade> grade(EvalCase evalCase, EvalObservation observation) {
        return grader.grade(evalCase, observation);
    }

    private EvalGraderRule rule(String type, EvalSeverity severity) {
        return new EvalGraderRule(type, severity);
    }

    private EvalGrade only(List<EvalGrade> grades, String type) {
        return grades.stream()
                .filter(grade -> grade.getGraderType().equals(type))
                .findFirst().orElseThrow();
    }
}
